#!/usr/bin/env python3
"""High-load concurrent test for authenticated backend APIs.

This script is intended for stronger load than load_test_short.py.
It supports:
- Ramp stages (e.g. 20 VUs -> 40 VUs -> 60 VUs)
- Weighted endpoint mix
- Per-worker auth users
- Aggregated latency/error/RPS report
"""

from __future__ import annotations

import json
import os
import random
import socket
import statistics
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from pathlib import Path
from typing import Any


BASE_URL = os.getenv("LOAD_BASE_URL", "http://localhost:8080").rstrip("/")
HTTP_TIMEOUT = float(os.getenv("LOAD_HTTP_TIMEOUT_SECONDS", "20"))
REGISTER_IF_MISSING = os.getenv("LOAD_REGISTER_IF_MISSING", "true").lower() == "true"

# Stage format: "<vus>:<seconds>,<vus>:<seconds>,..."
# Example: "10:60,25:120,40:180"
STAGES_RAW = os.getenv("LOAD_HEAVY_STAGES", "12:60,24:120,36:180")
MAX_VUS = int(os.getenv("LOAD_HEAVY_MAX_VUS", "60"))
AUTH_RELOGIN_EVERY = int(os.getenv("LOAD_HEAVY_AUTH_RELOGIN_EVERY", "0"))
AUTH_LOGIN_COOLDOWN_SECONDS = float(os.getenv("LOAD_HEAVY_AUTH_LOGIN_COOLDOWN_SECONDS", "10"))
AUTH_MAX_BACKOFF_SECONDS = float(os.getenv("LOAD_HEAVY_AUTH_MAX_BACKOFF_SECONDS", "60"))
AUTH_STARTUP_STAGGER_MS = int(os.getenv("LOAD_HEAVY_AUTH_STARTUP_STAGGER_MS", "400"))
THINK_MIN_MS = int(os.getenv("LOAD_HEAVY_THINK_MIN_MS", "20"))
THINK_MAX_MS = int(os.getenv("LOAD_HEAVY_THINK_MAX_MS", "120"))
FAILURE_SAMPLE_LIMIT = int(os.getenv("LOAD_HEAVY_FAILURE_SAMPLE_LIMIT", "30"))
EMAIL_PREFIX = os.getenv("LOAD_HEAVY_EMAIL_PREFIX", "loadheavy")
PASSWORD = os.getenv("LOAD_PASSWORD", "Passw0rd!2345")

# Endpoint mix format: "<endpoint>:<weight>,..."
# Keys: quote,analysis_history,portfolio,insights_test,insights_refresh
WEIGHTS_RAW = os.getenv(
    "LOAD_HEAVY_WEIGHTS",
    "quote:34,analysis_history:18,portfolio:20,insights_test:22,insights_refresh:6",
)
TICKERS = [
    t.strip().upper()
    for t in os.getenv("LOAD_HEAVY_TICKERS", "AAPL,MSFT,NVDA,AMZN,TSLA,META,GOOGL,AMD").split(",")
    if t.strip()
]

MAX_ERROR_RATE = float(os.getenv("LOAD_HEAVY_MAX_ERROR_RATE", "0.08"))
MAX_P95_MS = int(os.getenv("LOAD_HEAVY_MAX_P95_MS", "9000"))
MIN_RPS = float(os.getenv("LOAD_HEAVY_MIN_RPS", "0"))
OUTPUT_PATH = Path(
    os.getenv("LOAD_HEAVY_OUTPUT_PATH", "artifacts/reports/load-test-heavy-report.json")
)


@dataclass(frozen=True)
class Stage:
    vus: int
    seconds: int


@dataclass
class RequestResult:
    endpoint: str
    ok: bool
    status: int | None
    latency_ms: int
    detail: str
    request_id: str | None


class AuthLoginError(RuntimeError):
    def __init__(self, status: int | None, detail: str, retry_after_seconds: float | None = None):
        super().__init__(detail)
        self.status = status
        self.detail = detail
        self.retry_after_seconds = retry_after_seconds


def _log(msg: str) -> None:
    print(f"[load-heavy] {msg}", flush=True)


def _percentile(values: list[int], p: float) -> int:
    if not values:
        return 0
    if len(values) == 1:
        return values[0]
    ordered = sorted(values)
    idx = round((p / 100.0) * (len(ordered) - 1))
    return ordered[idx]


def _parse_stages(raw: str) -> list[Stage]:
    stages: list[Stage] = []
    for token in raw.split(","):
        token = token.strip()
        if not token:
            continue
        if ":" not in token:
            raise ValueError(f"invalid stage token: {token}")
        vus_text, sec_text = token.split(":", 1)
        vus = int(vus_text.strip())
        seconds = int(sec_text.strip())
        if vus <= 0 or seconds <= 0:
            raise ValueError(f"stage values must be > 0: {token}")
        stages.append(Stage(vus=vus, seconds=seconds))
    if not stages:
        raise ValueError("no valid stages configured")
    return stages


def _parse_weights(raw: str) -> dict[str, int]:
    allowed = {"quote", "analysis_history", "portfolio", "insights_test", "insights_refresh"}
    out: dict[str, int] = {}
    for token in raw.split(","):
        token = token.strip()
        if not token:
            continue
        if ":" not in token:
            raise ValueError(f"invalid weight token: {token}")
        name, w_text = token.split(":", 1)
        name = name.strip()
        weight = int(w_text.strip())
        if name not in allowed:
            raise ValueError(f"unsupported endpoint key in weights: {name}")
        if weight < 0:
            raise ValueError(f"weight must be >= 0: {token}")
        out[name] = weight
    for key in allowed:
        out.setdefault(key, 0)
    if sum(out.values()) <= 0:
        raise ValueError("total endpoint weight must be > 0")
    return out


def _request(
    method: str,
    path: str,
    *,
    headers: dict[str, str] | None = None,
    payload: dict[str, Any] | None = None,
) -> tuple[int, str, int, dict[str, str]]:
    url = f"{BASE_URL}{path}"
    req_headers = {"Accept": "application/json"}
    if headers:
        req_headers.update(headers)

    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        req_headers["Content-Type"] = "application/json"

    req = urllib.request.Request(url=url, method=method, data=data, headers=req_headers)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT) as resp:
            status = resp.getcode()
            body = resp.read().decode("utf-8", errors="replace")
            resp_headers = {k: v for k, v in resp.headers.items()}
    except urllib.error.HTTPError as exc:
        status = exc.code
        body = exc.read().decode("utf-8", errors="replace")
        resp_headers = {k: v for k, v in exc.headers.items()}
    except (urllib.error.URLError, TimeoutError, socket.timeout) as exc:
        raise RuntimeError(f"{method} {path} network timeout/error: {exc}") from exc

    latency_ms = int((time.perf_counter() - started) * 1000)
    return status, body, latency_ms, resp_headers


def _parse_retry_after_seconds(headers: dict[str, str]) -> float | None:
    for k, v in headers.items():
        if k.lower() != "retry-after":
            continue
        try:
            seconds = float(v.strip())
            if seconds >= 0:
                return seconds
        except Exception:  # noqa: BLE001
            return None
    return None


def _ensure_auth_token(email: str, password: str, *, allow_register: bool) -> str:
    if allow_register and REGISTER_IF_MISSING:
        try:
            _request(
                "POST",
                "/api/auth/register",
                payload={"email": email, "password": password},
            )
        except Exception:
            pass

    status, body, _, resp_headers = _request(
        "POST",
        "/api/auth/login",
        payload={"email": email, "password": password},
    )
    if status != 200:
        retry_after = _parse_retry_after_seconds(resp_headers)
        raise AuthLoginError(
            status=status,
            detail=f"login failed ({status}): {body[:240]}",
            retry_after_seconds=retry_after,
        )

    try:
        payload = json.loads(body)
    except json.JSONDecodeError as exc:
        raise AuthLoginError(status=status, detail=f"login response is not json: {body[:240]}") from exc

    token = payload.get("accessToken")
    if token:
        return token
    if payload.get("requires2FA"):
        raise AuthLoginError(
            status=status,
            detail="login requires2FA=true (synthetic account should not require 2FA)",
        )
    raise AuthLoginError(status=status, detail="login response missing accessToken")


def _pick_endpoint(rng: random.Random, weights: dict[str, int]) -> str:
    names = list(weights.keys())
    vals = [weights[n] for n in names]
    return rng.choices(names, weights=vals, k=1)[0]


def _build_request(
    endpoint: str,
    rng: random.Random,
    auth_headers: dict[str, str],
) -> tuple[str, str, str, dict[str, str], dict[str, Any] | None]:
    ticker = rng.choice(TICKERS)
    market = "US"
    if endpoint == "quote":
        return (
            "market_quote",
            "GET",
            f"/api/market/quote?ticker={urllib.parse.quote(ticker)}&market={market}",
            auth_headers,
            None,
        )
    if endpoint == "analysis_history":
        return ("analysis_history", "GET", "/api/analysis/history?page=0&size=20", auth_headers, None)
    if endpoint == "portfolio":
        return ("portfolio_list", "GET", "/api/portfolio", auth_headers, None)
    if endpoint == "insights_test":
        return (
            "market_insights_test",
            "POST",
            "/api/market/insights?test=true&refresh=false",
            auth_headers,
            {
                "ticker": ticker,
                "market": market,
                "days": 90,
                "newsLimit": 20,
                "includeForecasts": False,
                "compareWithSector": False,
            },
        )
    if endpoint == "insights_refresh":
        return (
            "market_insights_refresh",
            "POST",
            "/api/market/insights?test=true&refresh=true",
            auth_headers,
            {
                "ticker": ticker,
                "market": market,
                "days": 90,
                "newsLimit": 20,
                "includeForecasts": False,
                "compareWithSector": False,
            },
        )
    raise ValueError(f"unsupported endpoint key: {endpoint}")


def main() -> int:
    stages = _parse_stages(STAGES_RAW)
    weights = _parse_weights(WEIGHTS_RAW)
    run_id = uuid.uuid4().hex[:8]

    _log(f"baseUrl={BASE_URL}")
    _log(f"runId={run_id}")
    _log(f"stages={[(s.vus, s.seconds) for s in stages]}")
    _log(f"maxVus={MAX_VUS} httpTimeout={HTTP_TIMEOUT}s")
    _log(f"weights={weights}")
    _log(f"tickers={TICKERS}")
    _log(
        "authControl="
        f"reloginEvery={AUTH_RELOGIN_EVERY}, "
        f"cooldown={AUTH_LOGIN_COOLDOWN_SECONDS}s, "
        f"maxBackoff={AUTH_MAX_BACKOFF_SECONDS}s, "
        f"startupStaggerMs={AUTH_STARTUP_STAGGER_MS}"
    )

    if not TICKERS:
        raise RuntimeError("LOAD_HEAVY_TICKERS produced empty ticker list")

    max_stage_vus = max(s.vus for s in stages)
    worker_count = min(MAX_VUS, max_stage_vus)
    if worker_count <= 0:
        raise RuntimeError("worker_count must be > 0")

    # shared state
    state_lock = threading.Lock()
    target_vus = 0
    stop_event = threading.Event()

    # metrics
    summary_total = 0
    summary_failed = 0
    summary_latencies: list[int] = []
    endpoint_total: dict[str, int] = defaultdict(int)
    endpoint_failed: dict[str, int] = defaultdict(int)
    endpoint_latencies: dict[str, list[int]] = defaultdict(list)
    status_counts: dict[str, int] = defaultdict(int)
    sample_failures: list[dict[str, Any]] = []
    auth_failures = 0
    auth_rate_limited = 0
    global_auth_cooldown_until = 0.0

    started = time.perf_counter()

    def record_result(vu_id: int, res: RequestResult) -> None:
        nonlocal summary_total, summary_failed, auth_failures, auth_rate_limited
        now_epoch_ms = int(time.time() * 1000)
        with state_lock:
            summary_total += 1
            endpoint_total[res.endpoint] += 1
            if res.status is not None:
                status_counts[str(res.status)] += 1

            if res.ok:
                if res.latency_ms > 0:
                    summary_latencies.append(res.latency_ms)
                    endpoint_latencies[res.endpoint].append(res.latency_ms)
            else:
                summary_failed += 1
                endpoint_failed[res.endpoint] += 1
                if res.endpoint == "auth_login":
                    auth_failures += 1
                    if res.status == 429:
                        auth_rate_limited += 1
                if len(sample_failures) < FAILURE_SAMPLE_LIMIT:
                    sample_failures.append(
                        {
                            "vuId": vu_id,
                            "endpoint": res.endpoint,
                            "status": res.status,
                            "detail": res.detail[:500],
                            "requestId": res.request_id,
                            "epochMs": now_epoch_ms,
                        }
                    )

    def worker(vu_id: int) -> None:
        nonlocal target_vus, global_auth_cooldown_until
        rng = random.Random((vu_id + 1) * 1000003)
        email = f"{EMAIL_PREFIX}_{run_id}_vu{vu_id}@example.com"
        token: str | None = None
        req_count = 0
        did_register = False
        next_auth_attempt_at = 0.0
        auth_backoff_seconds = max(1.0, AUTH_LOGIN_COOLDOWN_SECONDS)

        if AUTH_STARTUP_STAGGER_MS > 0:
            # Spread first auth attempts to avoid synchronized login spikes.
            time.sleep(rng.randint(0, AUTH_STARTUP_STAGGER_MS) / 1000.0)

        while not stop_event.is_set():
            with state_lock:
                active = vu_id < target_vus
            if not active:
                time.sleep(0.1)
                continue

            if token is None or (AUTH_RELOGIN_EVERY > 0 and req_count > 0 and req_count % AUTH_RELOGIN_EVERY == 0):
                now = time.monotonic()
                with state_lock:
                    global_cooldown = global_auth_cooldown_until
                wait_until = max(next_auth_attempt_at, global_cooldown)
                if now < wait_until:
                    time.sleep(min(wait_until - now, 0.5))
                    continue

                try:
                    token = _ensure_auth_token(email, PASSWORD, allow_register=not did_register)
                    did_register = True
                    auth_backoff_seconds = max(1.0, AUTH_LOGIN_COOLDOWN_SECONDS)
                except AuthLoginError as exc:
                    did_register = True
                    retry_after = exc.retry_after_seconds if exc.retry_after_seconds is not None else auth_backoff_seconds
                    backoff = min(max(retry_after, AUTH_LOGIN_COOLDOWN_SECONDS), AUTH_MAX_BACKOFF_SECONDS)
                    backoff += rng.randint(0, 250) / 1000.0
                    next_auth_attempt_at = time.monotonic() + backoff

                    if exc.status == 429:
                        with state_lock:
                            if next_auth_attempt_at > global_auth_cooldown_until:
                                global_auth_cooldown_until = next_auth_attempt_at

                    auth_backoff_seconds = min(
                        AUTH_MAX_BACKOFF_SECONDS,
                        max(AUTH_LOGIN_COOLDOWN_SECONDS, auth_backoff_seconds * 2),
                    )
                    record_result(
                        vu_id,
                        RequestResult(
                            endpoint="auth_login",
                            ok=False,
                            status=exc.status,
                            latency_ms=0,
                            detail=exc.detail,
                            request_id=None,
                        ),
                    )
                    continue
                except Exception as exc:  # noqa: BLE001
                    backoff = min(max(AUTH_LOGIN_COOLDOWN_SECONDS, auth_backoff_seconds), AUTH_MAX_BACKOFF_SECONDS)
                    backoff += rng.randint(0, 250) / 1000.0
                    next_auth_attempt_at = time.monotonic() + backoff
                    auth_backoff_seconds = min(
                        AUTH_MAX_BACKOFF_SECONDS,
                        max(AUTH_LOGIN_COOLDOWN_SECONDS, auth_backoff_seconds * 2),
                    )
                    record_result(
                        vu_id,
                        RequestResult(
                            endpoint="auth_login",
                            ok=False,
                            status=None,
                            latency_ms=0,
                            detail=str(exc),
                            request_id=None,
                        ),
                    )
                    continue

            endpoint_key = _pick_endpoint(rng, weights)
            endpoint_name, method, path, headers, payload = _build_request(
                endpoint_key,
                rng,
                {"Authorization": f"Bearer {token}"},
            )

            try:
                status, body, latency, resp_headers = _request(
                    method,
                    path,
                    headers=headers,
                    payload=payload,
                )
                rid = resp_headers.get("X-Request-Id")
                if status == 200:
                    record_result(
                        vu_id,
                        RequestResult(
                            endpoint=endpoint_name,
                            ok=True,
                            status=status,
                            latency_ms=latency,
                            detail="ok",
                            request_id=rid,
                        ),
                    )
                elif status == 401:
                    token = None
                    next_auth_attempt_at = time.monotonic()
                    record_result(
                        vu_id,
                        RequestResult(
                            endpoint=endpoint_name,
                            ok=False,
                            status=status,
                            latency_ms=latency,
                            detail=f"unauthorized: {body[:220]}",
                            request_id=rid,
                        ),
                    )
                else:
                    record_result(
                        vu_id,
                        RequestResult(
                            endpoint=endpoint_name,
                            ok=False,
                            status=status,
                            latency_ms=latency,
                            detail=body[:220],
                            request_id=rid,
                        ),
                    )
            except Exception as exc:  # noqa: BLE001
                record_result(
                    vu_id,
                    RequestResult(
                        endpoint=endpoint_name,
                        ok=False,
                        status=None,
                        latency_ms=0,
                        detail=str(exc),
                        request_id=None,
                    ),
                )

            req_count += 1
            if THINK_MAX_MS > 0:
                delay_ms = rng.randint(max(0, THINK_MIN_MS), max(THINK_MIN_MS, THINK_MAX_MS))
                time.sleep(delay_ms / 1000.0)

    with ThreadPoolExecutor(max_workers=worker_count) as pool:
        futures = [pool.submit(worker, vu_id) for vu_id in range(worker_count)]

        elapsed = 0
        for idx, stg in enumerate(stages):
            next_vus = min(stg.vus, worker_count)
            with state_lock:
                target_vus = next_vus
            _log(f"stage {idx + 1}/{len(stages)} start: vus={next_vus}, seconds={stg.seconds}")

            stage_end = time.perf_counter() + stg.seconds
            while time.perf_counter() < stage_end:
                time.sleep(5)
                elapsed = int(time.perf_counter() - started)
                with state_lock:
                    done = summary_total
                    failed = summary_failed
                _log(f"progress elapsed={elapsed}s requests={done} failed={failed}")

        stop_event.set()
        with state_lock:
            target_vus = 0

        for fut in futures:
            fut.result(timeout=30)

    duration_ms = int((time.perf_counter() - started) * 1000)
    total = summary_total
    failed = summary_failed
    passed = total - failed
    error_rate = (failed / total) if total else 1.0
    rps = (total / (duration_ms / 1000.0)) if duration_ms > 0 else 0.0

    summary = {
        "totalRequests": total,
        "passedRequests": passed,
        "failedRequests": failed,
        "errorRate": error_rate,
        "rps": round(rps, 2),
        "avgMs": int(statistics.mean(summary_latencies)) if summary_latencies else 0,
        "p50ms": _percentile(summary_latencies, 50),
        "p95ms": _percentile(summary_latencies, 95),
        "p99ms": _percentile(summary_latencies, 99),
        "maxMs": max(summary_latencies) if summary_latencies else 0,
        "authFailures": auth_failures,
        "authRateLimited429": auth_rate_limited,
    }

    endpoint_stats: dict[str, Any] = {}
    for endpoint in sorted(endpoint_total.keys()):
        total_ep = endpoint_total[endpoint]
        failed_ep = endpoint_failed[endpoint]
        ok_lats = endpoint_latencies[endpoint]
        endpoint_stats[endpoint] = {
            "total": total_ep,
            "failed": failed_ep,
            "errorRate": (failed_ep / total_ep) if total_ep else 0.0,
            "rps": round((total_ep / (duration_ms / 1000.0)) if duration_ms > 0 else 0.0, 3),
            "avgMs": int(statistics.mean(ok_lats)) if ok_lats else 0,
            "p50ms": _percentile(ok_lats, 50),
            "p95ms": _percentile(ok_lats, 95),
            "p99ms": _percentile(ok_lats, 99),
            "maxMs": max(ok_lats) if ok_lats else 0,
        }

    thresholds = {
        "maxErrorRate": MAX_ERROR_RATE,
        "maxP95ms": MAX_P95_MS,
        "minRps": MIN_RPS,
    }
    overall_ok = (
        error_rate <= MAX_ERROR_RATE
        and summary["p95ms"] <= MAX_P95_MS
        and (MIN_RPS <= 0 or rps >= MIN_RPS)
    )

    report = {
        "generatedAtEpochMs": int(time.time() * 1000),
        "runId": run_id,
        "baseUrl": BASE_URL,
        "durationMs": duration_ms,
        "stages": [{"vus": s.vus, "seconds": s.seconds} for s in stages],
        "effectiveMaxVus": worker_count,
        "weights": weights,
        "tickers": TICKERS,
        "authControl": {
            "reloginEvery": AUTH_RELOGIN_EVERY,
            "loginCooldownSeconds": AUTH_LOGIN_COOLDOWN_SECONDS,
            "maxBackoffSeconds": AUTH_MAX_BACKOFF_SECONDS,
            "startupStaggerMs": AUTH_STARTUP_STAGGER_MS,
        },
        "thresholds": thresholds,
        "summary": summary,
        "endpointStats": endpoint_stats,
        "statusCounts": dict(status_counts),
        "sampleFailures": sample_failures,
        "overallOk": overall_ok,
    }

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    _log(f"report written: {OUTPUT_PATH}")

    if overall_ok:
        _log("load-heavy PASS")
        return 0
    _log(
        "load-heavy FAIL: "
        f"errorRate={error_rate:.4f}, p95={summary['p95ms']}ms, rps={summary['rps']}"
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
