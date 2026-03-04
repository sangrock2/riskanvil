#!/usr/bin/env python3
"""Core E2E smoke test for Stock-AI.

Flows:
1) Authentication (register/login)
2) Analysis run
3) Portfolio risk dashboard
"""

from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable


BASE_URL = os.getenv("E2E_BASE_URL", "http://localhost").rstrip("/")
WAIT_SECONDS = int(os.getenv("E2E_WAIT_SECONDS", "240"))
HTTP_TIMEOUT = float(os.getenv("E2E_HTTP_TIMEOUT", "10"))
REPORT_PATH = Path(
    os.getenv("E2E_REPORT_PATH", "artifacts/reports/e2e-core-flows.json")
)


@dataclass
class StepResult:
    name: str
    ok: bool
    duration_ms: int
    detail: str


def _log(message: str) -> None:
    print(f"[e2e] {message}", flush=True)


def _request(
    method: str,
    path: str,
    *,
    payload: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    expected_status: Iterable[int] = (200,),
) -> tuple[int, Any]:
    url = f"{BASE_URL}{path}"
    body = None
    req_headers = {"Accept": "application/json"}
    if headers:
        req_headers.update(headers)
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
        req_headers["Content-Type"] = "application/json"

    request = urllib.request.Request(url, method=method, data=body, headers=req_headers)

    try:
        with urllib.request.urlopen(request, timeout=HTTP_TIMEOUT) as response:
            status = response.getcode()
            raw = response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        status = exc.code
        raw = exc.read().decode("utf-8", errors="replace")
    except Exception as exc:  # noqa: BLE001
        raise RuntimeError(f"{method} {path} failed: {exc}") from exc

    if status not in set(expected_status):
        raise RuntimeError(
            f"{method} {path} expected {list(expected_status)}, got {status}. body={raw[:500]}"
        )

    if not raw.strip():
        return status, {}

    try:
        return status, json.loads(raw)
    except json.JSONDecodeError:
        return status, raw


def _wait_for_stack() -> None:
    deadline = time.time() + WAIT_SECONDS
    last_error = "unknown"

    while time.time() < deadline:
        try:
            _request("GET", "/nginx-health", expected_status=(200,))
            _request("GET", "/ai/health", expected_status=(200,))
            _request("GET", "/", expected_status=(200, 301, 302))
            return
        except Exception as exc:  # noqa: BLE001
            last_error = str(exc)
            time.sleep(3)

    raise RuntimeError(f"stack did not become healthy in {WAIT_SECONDS}s: {last_error}")


def _run_step(name: str, fn) -> StepResult:
    started = time.perf_counter()
    try:
        detail = fn()
        elapsed = int((time.perf_counter() - started) * 1000)
        _log(f"[PASS] {name} ({elapsed}ms)")
        return StepResult(name=name, ok=True, duration_ms=elapsed, detail=str(detail))
    except Exception as exc:  # noqa: BLE001
        elapsed = int((time.perf_counter() - started) * 1000)
        _log(f"[FAIL] {name} ({elapsed}ms): {exc}")
        return StepResult(name=name, ok=False, duration_ms=elapsed, detail=str(exc))


def _write_report(steps: list[StepResult]) -> None:
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "baseUrl": BASE_URL,
        "generatedAtEpochMs": int(time.time() * 1000),
        "totalSteps": len(steps),
        "passedSteps": sum(1 for s in steps if s.ok),
        "failedSteps": sum(1 for s in steps if not s.ok),
        "steps": [asdict(s) for s in steps],
    }
    REPORT_PATH.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    _log(f"report written: {REPORT_PATH}")


def main() -> int:
    _log(f"base url: {BASE_URL}")
    _log("waiting for nginx/frontend/backend/ai to be reachable")
    _wait_for_stack()

    unique = uuid.uuid4().hex[:12]
    email = f"e2e_{unique}@example.com"
    password = "Passw0rd!2345"

    state: dict[str, Any] = {}
    steps: list[StepResult] = []

    def flow_auth() -> str:
        _request(
            "POST",
            "/api/auth/register",
            payload={"email": email, "password": password},
            expected_status=(200,),
        )
        _, logged_in = _request(
            "POST",
            "/api/auth/login",
            payload={"email": email, "password": password},
            expected_status=(200,),
        )
        access_token = logged_in.get("accessToken")
        if not access_token:
            raise RuntimeError("login response missing accessToken")
        state["auth_headers"] = {"Authorization": f"Bearer {access_token}"}
        return f"user={email}"

    def flow_analysis() -> str:
        _, analysis = _request(
            "POST",
            "/api/analysis",
            headers=state["auth_headers"],
            payload={
                "ticker": "AAPL",
                "market": "US",
                "horizonDays": 120,
                "riskProfile": "moderate",
            },
            expected_status=(200,),
        )
        run_id = analysis.get("runId")
        if not run_id:
            raise RuntimeError("analysis response missing runId")
        _request(
            "GET",
            f"/api/analysis/{run_id}",
            headers=state["auth_headers"],
            expected_status=(200,),
        )
        return f"runId={run_id}"

    def flow_portfolio_risk() -> str:
        _, created = _request(
            "POST",
            "/api/portfolio",
            headers=state["auth_headers"],
            payload={
                "name": f"E2E Portfolio {unique}",
                "description": "Core flow smoke test portfolio",
                "targetReturn": 12.0,
                "riskProfile": "moderate",
            },
            expected_status=(200,),
        )
        portfolio_id = created.get("id")
        if portfolio_id is None:
            raise RuntimeError("create portfolio response missing id")

        _request(
            "POST",
            f"/api/portfolio/{portfolio_id}/position",
            headers=state["auth_headers"],
            payload={
                "ticker": "AAPL",
                "market": "US",
                "quantity": 10,
                "entryPrice": 180,
                "notes": "e2e-risk-flow",
            },
            expected_status=(200,),
        )
        _request(
            "GET",
            f"/api/portfolio/{portfolio_id}/risk-dashboard?lookbackDays=252",
            headers=state["auth_headers"],
            expected_status=(200,),
        )
        _request(
            "GET",
            f"/api/portfolio/{portfolio_id}/earnings-calendar?daysAhead=60",
            headers=state["auth_headers"],
            expected_status=(200,),
        )
        return f"portfolioId={portfolio_id}"

    steps.append(_run_step("auth_register_login", flow_auth))
    if steps[-1].ok:
        steps.append(_run_step("analysis_run_and_detail", flow_analysis))
    else:
        steps.append(
            StepResult(
                name="analysis_run_and_detail",
                ok=False,
                duration_ms=0,
                detail="skipped due to auth failure",
            )
        )

    if steps[0].ok:
        steps.append(_run_step("portfolio_risk_dashboard", flow_portfolio_risk))
    else:
        steps.append(
            StepResult(
                name="portfolio_risk_dashboard",
                ok=False,
                duration_ms=0,
                detail="skipped due to auth failure",
            )
        )

    _write_report(steps)

    failed = [s for s in steps if not s.ok]
    if failed:
        raise RuntimeError(f"{len(failed)} step(s) failed")

    _log("core E2E smoke test passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # noqa: BLE001
        _log(f"FAILED: {exc}")
        raise
