"""
OpenAI LLM 클라이언트 (폴백용)
"""
import logging
import re
from typing import Optional, Dict, Any

import httpx
from fastapi import HTTPException

from config import OPENAI_KEY

logger = logging.getLogger("app")


def _extract_output_text(data: Dict[str, Any]) -> Optional[str]:
    """Responses API payload에서 output_text를 추출한다."""
    if isinstance(data, dict) and isinstance(data.get("output_text"), str) and data["output_text"].strip():
        return data["output_text"].strip()

    try:
        out = data.get("output") or []
        for item in out:
            if item.get("type") == "message":
                content = item.get("content") or []
                for c in content:
                    if c.get("type") == "output_text" and c.get("text"):
                        return c["text"].strip()
    except Exception:
        return None
    return None


def _has_reference_url(text: str) -> bool:
    """보고서에 URL 근거가 포함되어 있는지 검사한다."""
    return bool(re.search(r"https?://\S+", text or ""))


async def summarize_korean(prompt: str) -> Optional[str]:
    """OpenAI Responses API로 한국어 요약 생성"""
    if not OPENAI_KEY:
        return None

    headers = {
        "Authorization": f"Bearer {OPENAI_KEY}",
        "Content-Type": "application/json",
    }

    payload = {
        "model": "gpt-4.1-mini",
        "input": prompt,
        "max_output_tokens": 500,
    }

    async with httpx.AsyncClient(timeout=30) as client:
        r = await client.post("https://api.openai.com/v1/responses", headers=headers, json=payload)
        if r.status_code != 200:
            return None
        data = r.json()

    try:
        out = data.get("output") or []
        for item in out:
            if item.get("type") == "message":
                content = item.get("content") or []
                for c in content:
                    if c.get("type") == "output_text":
                        return c.get("text")
    except Exception:
        return None
    return None


async def report_korean(prompt: str, use_web: bool = True) -> Optional[str]:
    """OpenAI로 한국어 투자 리포트 생성"""
    if not OPENAI_KEY:
        return None

    headers = {
        "Authorization": f"Bearer {OPENAI_KEY}",
        "Content-Type": "application/json",
    }

    payload: Dict[str, Any] = {
        "model": "gpt-4.1-mini",
        "input": prompt,
        "max_output_tokens": 2200,
        "temperature": 0.3,
    }

    # Responses API의 웹 검색 tool 타입이 릴리스별로 다를 수 있어 후보를 순차 시도한다.
    tool_candidates = [None]
    if use_web:
        tool_candidates = ["web_search", "web_search_preview"]

    data = None
    last_error = None
    for tool_name in tool_candidates:
        attempt_payload = dict(payload)
        if tool_name is not None:
            attempt_payload["tools"] = [{"type": tool_name}]

        async with httpx.AsyncClient(timeout=180) as client:
            r = await client.post("https://api.openai.com/v1/responses", headers=headers, json=attempt_payload)

        if r.status_code == 200:
            data = r.json()
            break

        last_error = r.text
        logger.warning("openai report failed status=%s tool=%s body=%s", r.status_code, tool_name, r.text)

        # 웹 검색 타입 불일치로 보이면 다음 후보 타입 시도
        if use_web and r.status_code == 400 and tool_name is not None:
            lower = (r.text or "").lower()
            if "tool" in lower or "web_search" in lower:
                continue
        break

    if data is None:
        logger.error("openai report failed after retries: %s", last_error)
        raise HTTPException(status_code=502, detail="OpenAI API call failed")

    text = _extract_output_text(data)
    if text and (not use_web or _has_reference_url(text)):
        return text

    # 웹 근거 URL이 없으면 한 번 더 강하게 재요청
    if use_web:
        retry_prompt = (
            prompt
            + "\n\n[재요청]\n"
              "- 반드시 웹 검색 결과를 반영하고 참고 URL을 2개 이상 포함하세요.\n"
              "- 문서 마지막에 '## 참고 출처' 섹션을 만들고 URL을 나열하세요.\n"
        )
        for tool_name in ["web_search", "web_search_preview"]:
            retry_payload = dict(payload)
            retry_payload["input"] = retry_prompt
            retry_payload["tools"] = [{"type": tool_name}]

            async with httpx.AsyncClient(timeout=180) as client:
                retry_res = await client.post("https://api.openai.com/v1/responses", headers=headers, json=retry_payload)

            if retry_res.status_code == 200:
                retry_data = retry_res.json()
                retry_text = _extract_output_text(retry_data)
                if retry_text:
                    return retry_text
                continue

            logger.warning("openai report retry failed status=%s tool=%s", retry_res.status_code, tool_name)
            if retry_res.status_code == 400:
                lower = (retry_res.text or "").lower()
                if "tool" in lower or "web_search" in lower:
                    continue
            break

    raise HTTPException(status_code=502, detail="OpenAI response has no output_text")
