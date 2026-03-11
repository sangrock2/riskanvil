#!/usr/bin/env python3
"""Long-running synthetic monitor for production API health and core flows.

Default behavior:
- Duration: 24h
- Interval: 5m
- Checks:
  1) backend health
  2) authenticated quote
  3) authenticated insights (test mode)
  4) authenticated portfolio list
"""

from __future__ import annotations

import json
import os
import socket
import statistics
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


BASE_URL = os.getenv("SYN_BASE_URL", "http://localhost:8080").rstrip("/")
AI_HEALTH_URL = os.getenv("SYN_AI_HEALTH_URL", "").strip()
EMAIL = os.getenv("SYN_EMAIL", f"synthetic_{uuid.uuid4().hex[:10]}@example.com")
PASSWORD = os.getenv("SYN_PASSWORD", "Passw0rd!2345")
REGISTER_IF_MISSING = os.getenv("SYN_REGISTER_IF_MISSING", "true").lower() == "true"
DURATION_MINUTES = int(os.getenv("SYN_DURATION_MINUTES", "1440"))  # 24h
INTERVAL_SECONDS = int(os.getenv("SYN_INTERVAL_SECONDS", "300"))   # 5m
HTTP_TIMEOUT = float(os.getenv("SYN_HTTP_TIMEOUT_SECONDS", "15"))
MIN_SUCCESS_RATIO = float(os.getenv("SYN_MIN_SUCCESS_RATIO", "0.99"))
MAX_CONSECUTIVE_FAILURES = int(os.getenv("SYN_MAX_CONSECUTIVE_FAILURES", "3"))
STARTUP_WAIT_SECONDS = int(os.getenv("SYN_STARTUP_WAIT_SECONDS", "300"))
AUTH_RETRIES = int(os.getenv("SYN_AUTH_RETRIES", "6"))
AUTH_RETRY_BACKOFF_SECONDS = int(os.getenv("SYN_AUTH_RETRY_BACKOFF_SECONDS", "5"))
OUTPUT_PATH = Path(
    os.getenv("SYN_OUTPUT_PATH", "artifacts/reports/synthetic-monitor-report.json")
)


@dataclass
class CheckResult:
    name: str
    ok: bool
    status: int | None
    latency_ms: int
    detail: str
    request_id: str | None


@dataclass
class CycleResult:
    started_at_epoch_ms: int
    duration_ms: int
    ok: bool
    checks: list[CheckResult]


def _log(msg: str) -> None:
    print(f"[synthetic] {msg}", flush=True)


def _request(
    method: str,
    path_or_url: str,
    *,
    payload: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    expected_status: tuple[int, ...] = (200,),
    absolute: bool = False,
) -> tuple[int, str, dict[str, str], int]:
    url = path_or_url if absolute else f"{BASE_URL}{path_or_url}"
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
        raise RuntimeError(f"{method} {url} network timeout/error: {exc}") from exc
    latency_ms = int((time.perf_counter() - started) * 1000)

    if status not in expected_status:
        raise RuntimeError(
            f"{method} {url} expected {expected_status}, got {status}, body={body[:400]}"
        )
    return status, body, resp_headers, latency_ms


def _ensure_auth_token() -> str:
    if REGISTER_IF_MISSING:
        try:
            _request(
                "POST",
                "/api/auth/register",
                payload={"email": EMAIL, "password": PASSWORD},
                expected_status=(200, 400),
            )
        except Exception as exc:  # noqa: BLE001
            _log(f"register step warning: {exc}")

    last_error: Exception | None = None
    for attempt in range(1, AUTH_RETRIES + 1):
        try:
            status, body, _, _ = _request(
                "POST",
                "/api/auth/login",
                payload={"email": EMAIL, "password": PASSWORD},
                expected_status=(200, 401),
            )
            if status == 401:
                raise RuntimeError("login unauthorized (401)")

            try:
                token = json.loads(body).get("accessToken")
            except json.JSONDecodeError as exc:
                raise RuntimeError(f"login response is not json: {body[:200]}") from exc
            if not token:
                raise RuntimeError("login response missing accessToken")
            return token
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            _log(f"auth attempt {attempt}/{AUTH_RETRIES} failed: {exc}")

            if REGISTER_IF_MISSING and attempt == 1:
                try:
                    _request(
                        "POST",
                        "/api/auth/register",
                        payload={"email": EMAIL, "password": PASSWORD},
                        expected_status=(200, 400),
                    )
                except Exception as register_exc:  # noqa: BLE001
                    _log(f"register retry warning: {register_exc}")

            if attempt < AUTH_RETRIES:
                time.sleep(AUTH_RETRY_BACKOFF_SECONDS)

    raise RuntimeError(f"failed to obtain auth token after {AUTH_RETRIES} attempts: {last_error}")


def _wait_for_backend_ready() -> None:
    deadline = time.time() + STARTUP_WAIT_SECONDS
    last_error = "unknown"
    while time.time() < deadline:
        try:
            _request("GET", "/actuator/health", expected_status=(200,))
            _log("backend readiness check passed")
            return
        except Exception as exc:  # noqa: BLE001
            last_error = str(exc)
            time.sleep(5)
    raise RuntimeError(
        f"backend did not become ready within {STARTUP_WAIT_SECONDS}s: {last_error}"
    )


def _safe_json_status(body: str) -> str:
    try:
        payload = json.loads(body)
        if isinstance(payload, dict):
            status = payload.get("status")
            return str(status) if status is not None else "object"
        if isinstance(payload, list):
            return f"list[{len(payload)}]"
        return type(payload).__name__
    except json.JSONDecodeError:
        return "non_json"


def _run_cycle(token: str) -> CycleResult:
    started_epoch_ms = int(time.time() * 1000)
    cycle_start = time.perf_counter()
    checks: list[CheckResult] = []
    auth_headers = {"Authorization": f"Bearer {token}"}

    def record(name: str, fn) -> None:
        try:
            status, body, hdrs, latency = fn()
            rid = hdrs.get("X-Request-Id")
            checks.append(
                CheckResult(
                    name=name,
                    ok=True,
                    status=status,
                    latency_ms=latency,
                    detail=f"ok ({_safe_json_status(body)})",
                    request_id=rid,
                )
            )
        except Exception as exc:  # noqa: BLE001
            checks.append(
                CheckResult(
                    name=name,
                    ok=False,
                    status=None,
                    latency_ms=0,
                    detail=str(exc),
                    request_id=None,
                )
            )

    record("backend_health", lambda: _request("GET", "/actuator/health", expected_status=(200,)))

    if AI_HEALTH_URL:
        record(
            "ai_health",
            lambda: _request("GET", AI_HEALTH_URL, expected_status=(200,), absolute=True),
        )

    record(
        "market_quote",
        lambda: _request(
            "GET",
            "/api/market/quote?ticker=AAPL&market=US",
            headers=auth_headers,
            expected_status=(200,),
        ),
    )

    record(
        "market_insights_test",
        lambda: _request(
            "POST",
            "/api/market/insights?test=true&refresh=false",
            headers=auth_headers,
            expected_status=(200,),
            payload={
                "ticker": "AAPL",
                "market": "US",
                "days": 90,
                "newsLimit": 20,
                "includeForecasts": False,
                "compareWithSector": False,
            },
        ),
    )

    record(
        "portfolio_list",
        lambda: _request("GET", "/api/portfolio", headers=auth_headers, expected_status=(200,)),
    )

    ok = all(c.ok for c in checks)
    duration_ms = int((time.perf_counter() - cycle_start) * 1000)
    return CycleResult(
        started_at_epoch_ms=started_epoch_ms,
        duration_ms=duration_ms,
        ok=ok,
        checks=checks,
    )


def _percentile(values: list[int], p: float) -> int:
    if not values:
        return 0
    if len(values) == 1:
        return values[0]
    values_sorted = sorted(values)
    idx = round((p / 100.0) * (len(values_sorted) - 1))
    return values_sorted[idx]


def _write_report(cycles: list[CycleResult], overall_ok: bool, reason: str) -> None:
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)

    total_cycles = len(cycles)
    passed_cycles = sum(1 for c in cycles if c.ok)
    success_ratio = (passed_cycles / total_cycles) if total_cycles else 0.0
    cycle_durations = [c.duration_ms for c in cycles]

    endpoint_failures: dict[str, int] = {}
    for c in cycles:
        for chk in c.checks:
            if not chk.ok:
                endpoint_failures[chk.name] = endpoint_failures.get(chk.name, 0) + 1

    payload = {
        "generatedAtEpochMs": int(time.time() * 1000),
        "baseUrl": BASE_URL,
        "aiHealthUrl": AI_HEALTH_URL,
        "durationMinutes": DURATION_MINUTES,
        "intervalSeconds": INTERVAL_SECONDS,
        "totalCycles": total_cycles,
        "passedCycles": passed_cycles,
        "successRatio": success_ratio,
        "minRequiredSuccessRatio": MIN_SUCCESS_RATIO,
        "overallOk": overall_ok,
        "resultReason": reason,
        "cycleDurationMs": {
            "avg": int(statistics.mean(cycle_durations)) if cycle_durations else 0,
            "p50": _percentile(cycle_durations, 50),
            "p95": _percentile(cycle_durations, 95),
            "max": max(cycle_durations) if cycle_durations else 0,
        },
        "endpointFailures": endpoint_failures,
        "cycles": [asdict(c) for c in cycles],
    }

    OUTPUT_PATH.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    _log(f"report written: {OUTPUT_PATH}")


def main() -> int:
    _log(f"baseUrl={BASE_URL}")
    _log(f"duration={DURATION_MINUTES}m interval={INTERVAL_SECONDS}s")
    _log(f"monitor user={EMAIL}")
    _log(
        f"httpTimeout={HTTP_TIMEOUT}s startupWait={STARTUP_WAIT_SECONDS}s "
        f"authRetries={AUTH_RETRIES}"
    )

    deadline = time.time() + (DURATION_MINUTES * 60)
    cycles: list[CycleResult] = []
    consecutive_failures = 0
    reason = "completed"

    _wait_for_backend_ready()
    token = _ensure_auth_token()

    while time.time() < deadline:
        cycle_start = time.time()
        try:
            token = _ensure_auth_token()
        except Exception as exc:  # noqa: BLE001
            _log(f"token refresh failed: {exc}")

        result = _run_cycle(token)
        cycles.append(result)

        if result.ok:
            consecutive_failures = 0
            _log(f"cycle PASS ({result.duration_ms}ms)")
        else:
            consecutive_failures += 1
            first_error = next((c.detail for c in result.checks if not c.ok), "unknown")
            _log(f"cycle FAIL ({result.duration_ms}ms): {first_error}")
            if consecutive_failures >= MAX_CONSECUTIVE_FAILURES:
                reason = f"consecutive_failures_reached({MAX_CONSECUTIVE_FAILURES})"
                _log(reason)
                break

        elapsed = time.time() - cycle_start
        sleep_for = max(0.0, INTERVAL_SECONDS - elapsed)
        if sleep_for > 0:
            time.sleep(sleep_for)

    passed = sum(1 for c in cycles if c.ok)
    total = len(cycles)
    ratio = (passed / total) if total else 0.0
    overall_ok = total > 0 and ratio >= MIN_SUCCESS_RATIO and consecutive_failures < MAX_CONSECUTIVE_FAILURES

    if not overall_ok and reason == "completed":
        reason = f"success_ratio_below_threshold({ratio:.4f}<{MIN_SUCCESS_RATIO:.4f})"

    _write_report(cycles, overall_ok=overall_ok, reason=reason)
    if overall_ok:
        _log("synthetic monitor PASS")
        return 0

    _log(f"synthetic monitor FAIL: {reason}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
