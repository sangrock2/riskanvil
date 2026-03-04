"""
인메모리 TTL 캐시 및 Alpha Vantage 레이트 리미터
"""
import time
import asyncio
import hashlib
import json
import re
import logging
import httpx

from typing import Any, Dict, Optional
from pathlib import Path
from fastapi import HTTPException

from config import ALPHA_KEY, ALPHA_BASE, TESTDATA_DIR
from request_id import request_id_header

logger = logging.getLogger("app")

# ── TTL 캐시 ──
_cache: Dict[str, Any] = {}
_cache_exp: Dict[str, float] = {}
_cache_gc_last = 0.0
_cache_gc_interval_sec = 60.0


def _cache_gc(now: Optional[float] = None):
    """만료된 키를 주기적으로 정리해 인메모리 사용량 누적을 방지한다."""
    global _cache_gc_last
    ts = now if now is not None else time.time()
    if ts - _cache_gc_last < _cache_gc_interval_sec:
        return

    expired = [k for k, exp in _cache_exp.items() if exp <= ts]
    for k in expired:
        _cache_exp.pop(k, None)
        _cache.pop(k, None)
    _cache_gc_last = ts


def cache_get(key: str):
    now = time.time()
    exp = _cache_exp.get(key, 0)
    if now < exp:
        return _cache.get(key)
    # 만료 시점에 즉시 정리
    _cache_exp.pop(key, None)
    _cache.pop(key, None)
    _cache_gc(now)
    return None


def cache_set(key: str, val: Any, ttl_sec: int = 60):
    now = time.time()
    _cache_gc(now)
    _cache[key] = val
    _cache_exp[key] = now + ttl_sec


# ── Alpha Vantage 레이트 리미터 ──
_alpha_lock = asyncio.Lock()
_alpha_next_ok = 0.0
_alpha_http_client: Optional[httpx.AsyncClient] = None
_alpha_http_client_lock = asyncio.Lock()


# ── 테스트 데이터 파일 유틸 ──
def _safe_name(s: str) -> str:
    s = (s or "").strip()
    s = re.sub(r"[^a-zA-Z0-9_.-]+", "_", s)
    return s[:120] if len(s) > 120 else s


def _safe(s: str) -> str:
    return "".join(ch if ch.isalnum() or ch in ("-", "_") else "_" for ch in s)[:60]


def _file_path(params: dict) -> Path:
    fn = _safe_name(str(params.get("function", "unknown")))
    sym = _safe_name(str(params.get("symbol") or params.get("tickers") or params.get("keywords") or "na"))
    return TESTDATA_DIR / f"alpha__{fn}__{sym}.txt"


def _alpha_key_str(params: dict) -> str:
    parts = [f"{k}={params[k]}" for k in sorted(params.keys())]
    return "&".join(parts)


def _alpha_file_path(params: dict) -> Path:
    func = _safe(str(params.get("function", "unknown")))
    sym = _safe(str(params.get("symbol") or params.get("keywords") or params.get("tickers") or "na"))
    key = _alpha_key_str(params)
    h = hashlib.sha1(key.encode("utf-8")).hexdigest()[:12]
    return TESTDATA_DIR / f"alpha_{func}_{sym}_{h}.txt"


def _alpha_legacy_file_path(params: dict) -> Path:
    func = _safe(str(params.get("function", "unknown")))
    sym = params.get("symbol") or params.get("keywords") or params.get("tickers") or "NA"
    sym = _safe(str(sym))
    return TESTDATA_DIR / f"alpha__{func}__{sym}.txt"


def _save_txt(params: dict, data: dict) -> None:
    p = _file_path(params)
    p.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")


def _load_txt(params: dict) -> Optional[dict]:
    p = _file_path(params)
    if not p.exists():
        return None
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        return None


def testdata_load(params: dict) -> Optional[dict]:
    path = _alpha_file_path(params)
    if path.exists():
        try:
            return json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            return None
    legacy = _alpha_legacy_file_path(params)
    if legacy.exists():
        try:
            return json.loads(legacy.read_text(encoding="utf-8"))
        except Exception:
            return None
    return None


def testdata_save(params: dict, data: dict) -> None:
    TESTDATA_DIR.mkdir(parents=True, exist_ok=True)
    path = _alpha_file_path(params)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)


async def alpha_get(params: Dict[str, Any], ttl_sec: int = 60, test: bool = False) -> Dict[str, Any]:
    """Alpha Vantage API 호출 (레이트 리밋 + 캐시)"""
    global _alpha_next_ok

    if test:
        data = _load_txt(params)
        if data is None:
            raise HTTPException(status_code=400, detail=f"test data missing: {str(_file_path(params))}")
        return data

    if not ALPHA_KEY:
        raise HTTPException(status_code=500, detail="ALPHAVANTAGE_API_KEY is not set")

    params = dict(params)
    params["apikey"] = ALPHA_KEY

    ck = "alpha:" + "&".join([f"{k}={params[k]}" for k in sorted(params.keys())])
    cached = cache_get(ck)
    if cached is not None:
        return cached

    async with _alpha_lock:
        now = time.time()
        wait = _alpha_next_ok - now
        if wait > 0:
            await asyncio.sleep(wait)
        _alpha_next_ok = time.time() + 1.05

        client = await _alpha_client()
        r = await client.get(ALPHA_BASE, params=params, headers=request_id_header())
        if r.status_code != 200:
            raise HTTPException(status_code=502, detail=f"alpha http {r.status_code}")
        data = r.json()

    if isinstance(data, dict) and ("Error Message" in data or "Information" in data or "Note" in data):
        raise HTTPException(status_code=429, detail=data.get("Note") or data.get("Information") or data.get("Error Message"))

    cache_set(ck, data, ttl_sec=ttl_sec)

    try:
        _save_txt(params, data)
    except Exception:
        pass

    return data


async def _alpha_client() -> httpx.AsyncClient:
    """Alpha Vantage 호출용 AsyncClient 싱글톤."""
    global _alpha_http_client
    if _alpha_http_client is not None:
        return _alpha_http_client
    async with _alpha_http_client_lock:
        if _alpha_http_client is None:
            _alpha_http_client = httpx.AsyncClient(timeout=20)
    return _alpha_http_client


async def close_alpha_client():
    """앱 종료 시 HTTP 커넥션 풀을 정리한다."""
    global _alpha_http_client
    client = _alpha_http_client
    _alpha_http_client = None
    if client is not None:
        await client.aclose()
