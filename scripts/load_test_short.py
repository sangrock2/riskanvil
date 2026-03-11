#!/usr/bin/env python3
"""Short concurrent load test for key authenticated backend APIs."""

from __future__ import annotations

import json
import os
import statistics
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


BASE_URL = os.getenv("LOAD_BASE_URL", "http://localhost:8080").rstrip("/")
EMAIL = os.getenv("LOAD_EMAIL", f"load_{uuid.uuid4().hex[:10]}@example.com")
PASSWORD = os.getenv("LOAD_PASSWORD", "Passw0rd!2345")
REGISTER_IF_MISSING = os.getenv("LOAD_REGISTER_IF_MISSING", "true").lower() == "true"
HTTP_TIMEOUT = float(os.getenv("LOAD_HTTP_TIMEOUT_SECONDS", "20"))
VUS = int(os.getenv("LOAD_VUS", "6"))
ITERATIONS = int(os.getenv("LOAD_ITERATIONS", "8"))
INSIGHTS_EVERY = int(os.getenv("LOAD_INSIGHTS_EVERY", "4"))
MAX_ERROR_RATE = float(os.getenv("LOAD_MAX_ERROR_RATE", "0.05"))
MAX_P95_MS = int(os.getenv("LOAD_MAX_P95_MS", "5000"))
OUTPUT_PATH = Path(
    os.getenv("LOAD_OUTPUT_PATH", "artifacts/reports/load-test-short-report.json")
)


@dataclass
class RequestResult:
    endpoint: str
    ok: bool
    status: int | None
    latency_ms: int
    detail: str


def _log(msg: str) -> None:
    print(f"[load] {msg}", flush=True)


def _request(
    method: str,
    path: str,
    *,
    payload: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    expected_status: tuple[int, ...] = (200,),
) -> tuple[int, str, int]:
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
    except urllib.error.HTTPError as exc:
        status = exc.code
        body = exc.read().decode("utf-8", errors="replace")
    latency_ms = int((time.perf_counter() - started) * 1000)
    if status not in expected_status:
        raise RuntimeError(f"{method} {path} got {status}. body={body[:300]}")
    return status, body, latency_ms


def _ensure_auth_token() -> str:
    if REGISTER_IF_MISSING:
        try:
            _request(
                "POST",
                "/api/auth/register",
                payload={"email": EMAIL, "password": PASSWORD},
                expected_status=(200, 400),
            )
        except Exception:
            pass

    _, body, _ = _request(
        "POST",
        "/api/auth/login",
        payload={"email": EMAIL, "password": PASSWORD},
        expected_status=(200,),
    )
    token = json.loads(body).get("accessToken")
    if not token:
        raise RuntimeError("login response missing accessToken")
    return token


def _percentile(values: list[int], p: float) -> int:
    if not values:
        return 0
    values_sorted = sorted(values)
    idx = round((p / 100.0) * (len(values_sorted) - 1))
    return values_sorted[idx]


def _hit(
    endpoint: str,
    method: str,
    path: str,
    headers: dict[str, str],
    *,
    payload: dict[str, Any] | None = None,
) -> RequestResult:
    try:
        status, _, latency = _request(
            method,
            path,
            headers=headers,
            payload=payload,
            expected_status=(200,),
        )
        return RequestResult(endpoint=endpoint, ok=True, status=status, latency_ms=latency, detail="ok")
    except Exception as exc:  # noqa: BLE001
        return RequestResult(endpoint=endpoint, ok=False, status=None, latency_ms=0, detail=str(exc))


def main() -> int:
    _log(f"baseUrl={BASE_URL}")
    _log(f"vus={VUS} iterations={ITERATIONS} insightsEvery={INSIGHTS_EVERY}")

    token = _ensure_auth_token()
    auth_headers = {"Authorization": f"Bearer {token}"}

    all_results: list[RequestResult] = []
    lock = threading.Lock()

    def worker(vu_id: int) -> None:
        local_results: list[RequestResult] = []
        for i in range(ITERATIONS):
            local_results.append(
                _hit(
                    "market_quote",
                    "GET",
                    "/api/market/quote?ticker=AAPL&market=US",
                    auth_headers,
                )
            )
            local_results.append(
                _hit(
                    "analysis_history",
                    "GET",
                    "/api/analysis/history?page=0&size=10",
                    auth_headers,
                )
            )
            local_results.append(
                _hit(
                    "portfolio_list",
                    "GET",
                    "/api/portfolio",
                    auth_headers,
                )
            )
            if INSIGHTS_EVERY > 0 and (i % INSIGHTS_EVERY == 0):
                local_results.append(
                    _hit(
                        "market_insights_test",
                        "POST",
                        "/api/market/insights?test=true&refresh=false",
                        auth_headers,
                        payload={
                            "ticker": "AAPL",
                            "market": "US",
                            "days": 90,
                            "newsLimit": 20,
                            "includeForecasts": False,
                            "compareWithSector": False,
                        },
                    )
                )
        with lock:
            all_results.extend(local_results)
        _log(f"vu={vu_id} finished requests={len(local_results)}")

    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=VUS) as pool:
        for vu in range(VUS):
            pool.submit(worker, vu)
    total_duration_ms = int((time.perf_counter() - started) * 1000)

    total = len(all_results)
    passed = sum(1 for r in all_results if r.ok)
    failed = total - passed
    error_rate = (failed / total) if total else 1.0
    latencies = [r.latency_ms for r in all_results if r.ok and r.latency_ms > 0]

    endpoint_stats: dict[str, dict[str, Any]] = {}
    grouped: dict[str, list[RequestResult]] = defaultdict(list)
    for r in all_results:
        grouped[r.endpoint].append(r)
    for endpoint, items in grouped.items():
        ok_items = [x for x in items if x.ok]
        lats = [x.latency_ms for x in ok_items if x.latency_ms > 0]
        endpoint_stats[endpoint] = {
            "total": len(items),
            "passed": len(ok_items),
            "failed": len(items) - len(ok_items),
            "errorRate": ((len(items) - len(ok_items)) / len(items)) if items else 1.0,
            "p50ms": _percentile(lats, 50),
            "p95ms": _percentile(lats, 95),
            "maxMs": max(lats) if lats else 0,
        }

    report = {
        "generatedAtEpochMs": int(time.time() * 1000),
        "baseUrl": BASE_URL,
        "vus": VUS,
        "iterations": ITERATIONS,
        "insightsEvery": INSIGHTS_EVERY,
        "durationMs": total_duration_ms,
        "thresholds": {"maxErrorRate": MAX_ERROR_RATE, "maxP95ms": MAX_P95_MS},
        "summary": {
            "totalRequests": total,
            "passedRequests": passed,
            "failedRequests": failed,
            "errorRate": error_rate,
            "p50ms": _percentile(latencies, 50),
            "p95ms": _percentile(latencies, 95),
            "p99ms": _percentile(latencies, 99),
            "maxMs": max(latencies) if latencies else 0,
            "avgMs": int(statistics.mean(latencies)) if latencies else 0,
        },
        "endpointStats": endpoint_stats,
        "sampleFailures": [asdict(r) for r in all_results if not r.ok][:20],
    }

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    _log(f"report written: {OUTPUT_PATH}")

    p95 = report["summary"]["p95ms"]
    overall_ok = (error_rate <= MAX_ERROR_RATE) and (p95 <= MAX_P95_MS)

    if overall_ok:
        _log("load test PASS")
        return 0

    _log(f"load test FAIL: errorRate={error_rate:.4f}, p95={p95}ms")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())

