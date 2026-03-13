#!/usr/bin/env python3
"""Smoke E2E test for Stock-AI.

Flows:
1) Authentication (register/login)
2) Settings round-trip
3) Watchlist lifecycle
4) Analysis run
5) Portfolio lifecycle + risk dashboard
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


def _extract_message(payload: Any) -> str:
    if isinstance(payload, dict):
        value = payload.get("message") or payload.get("error")
        return str(value or "")
    return str(payload)


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

    def flow_settings_round_trip() -> str:
        _, current = _request(
            "GET",
            "/api/settings",
            headers=state["auth_headers"],
            expected_status=(200,),
        )
        if "theme" not in current:
            raise RuntimeError("settings response missing theme")

        _, updated = _request(
            "PUT",
            "/api/settings",
            headers=state["auth_headers"],
            payload={
                "emailOnAlerts": False,
                "dailySummaryEnabled": True,
                "theme": "light",
                "language": "en",
                "defaultMarket": "KR",
            },
            expected_status=(200,),
        )
        if updated.get("theme") != "light" or updated.get("language") != "en":
            raise RuntimeError(f"settings update did not persist expected values: {updated}")

        _, fetched_again = _request(
            "GET",
            "/api/settings",
            headers=state["auth_headers"],
            expected_status=(200,),
        )
        if fetched_again.get("defaultMarket") != "KR":
            raise RuntimeError(f"settings fetch after update mismatch: {fetched_again}")

        return "theme=light language=en defaultMarket=KR"

    def flow_watchlist_lifecycle() -> str:
        _request(
            "POST",
            "/api/watchlist",
            headers=state["auth_headers"],
            payload={"ticker": "AAPL", "market": "US"},
            expected_status=(200,),
        )

        _, listed = _request(
            "GET",
            "/api/watchlist",
            headers=state["auth_headers"],
            expected_status=(200,),
        )
        if not any(item.get("ticker") == "AAPL" and item.get("market") == "US" for item in listed):
            raise RuntimeError(f"watchlist item not found after add: {listed}")

        duplicate_status, duplicate_body = _request(
            "POST",
            "/api/watchlist",
            headers=state["auth_headers"],
            payload={"ticker": "AAPL", "market": "US"},
            expected_status=(400,),
        )
        if duplicate_status != 400 or "already exists" not in _extract_message(duplicate_body).lower():
            raise RuntimeError(f"duplicate watchlist add did not return expected error: {duplicate_body}")

        _request(
            "DELETE",
            "/api/watchlist?ticker=AAPL&market=US",
            headers=state["auth_headers"],
            expected_status=(200,),
        )

        _, listed_after_delete = _request(
            "GET",
            "/api/watchlist",
            headers=state["auth_headers"],
            expected_status=(200,),
        )
        if any(item.get("ticker") == "AAPL" and item.get("market") == "US" for item in listed_after_delete):
            raise RuntimeError(f"watchlist item still present after delete: {listed_after_delete}")

        return "ticker=AAPL market=US"

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

    def flow_portfolio_lifecycle() -> str:
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

        duplicate_status, duplicate_body = _request(
            "POST",
            "/api/portfolio",
            headers=state["auth_headers"],
            payload={
                "name": f"E2E Portfolio {unique}",
                "description": "Duplicate name check",
                "targetReturn": 12.0,
                "riskProfile": "moderate",
            },
            expected_status=(400,),
        )
        if duplicate_status != 400 or "already exists" not in _extract_message(duplicate_body).lower():
            raise RuntimeError(f"duplicate portfolio create did not return expected error: {duplicate_body}")

        _, added = _request(
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
        position_id = added.get("positionId")
        if position_id is None:
            raise RuntimeError("add position response missing positionId")

        duplicate_position_status, duplicate_position_body = _request(
            "POST",
            f"/api/portfolio/{portfolio_id}/position",
            headers=state["auth_headers"],
            payload={
                "ticker": "AAPL",
                "market": "US",
                "quantity": 5,
                "entryPrice": 170,
                "notes": "e2e-duplicate-position",
            },
            expected_status=(400,),
        )
        if duplicate_position_status != 400 or "already exists" not in _extract_message(duplicate_position_body).lower():
            raise RuntimeError(
                f"duplicate portfolio position did not return expected error: {duplicate_position_body}"
            )

        _, detail = _request(
            "GET",
            f"/api/portfolio/{portfolio_id}",
            headers=state["auth_headers"],
            expected_status=(200,),
        )
        if not detail.get("positions"):
            raise RuntimeError(f"portfolio detail missing positions after add: {detail}")

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

        _request(
            "DELETE",
            f"/api/portfolio/{portfolio_id}/position/{position_id}",
            headers=state["auth_headers"],
            expected_status=(200,),
        )

        _, detail_after_position_delete = _request(
            "GET",
            f"/api/portfolio/{portfolio_id}",
            headers=state["auth_headers"],
            expected_status=(200,),
        )
        if detail_after_position_delete.get("positions"):
            raise RuntimeError(
                f"portfolio still has positions after delete: {detail_after_position_delete.get('positions')}"
            )

        _request(
            "DELETE",
            f"/api/portfolio/{portfolio_id}",
            headers=state["auth_headers"],
            expected_status=(200,),
        )

        _, portfolios_after_delete = _request(
            "GET",
            "/api/portfolio",
            headers=state["auth_headers"],
            expected_status=(200,),
        )
        if any(item.get("id") == portfolio_id for item in portfolios_after_delete):
            raise RuntimeError(f"portfolio still present after delete: {portfolios_after_delete}")

        return f"portfolioId={portfolio_id} positionId={position_id}"

    steps.append(_run_step("auth_register_login", flow_auth))
    if steps[-1].ok:
        steps.append(_run_step("settings_round_trip", flow_settings_round_trip))
        steps.append(_run_step("watchlist_lifecycle", flow_watchlist_lifecycle))
        steps.append(_run_step("analysis_run_and_detail", flow_analysis))
    else:
        steps.append(
            StepResult(
                name="settings_round_trip",
                ok=False,
                duration_ms=0,
                detail="skipped due to auth failure",
            )
        )
        steps.append(
            StepResult(
                name="watchlist_lifecycle",
                ok=False,
                duration_ms=0,
                detail="skipped due to auth failure",
            )
        )
        steps.append(
            StepResult(
                name="analysis_run_and_detail",
                ok=False,
                duration_ms=0,
                detail="skipped due to auth failure",
            )
        )

    if steps[0].ok:
        steps.append(_run_step("portfolio_lifecycle_and_risk_dashboard", flow_portfolio_lifecycle))
    else:
        steps.append(
            StepResult(
                name="portfolio_lifecycle_and_risk_dashboard",
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
