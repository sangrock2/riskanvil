"""
OpenAI LLM 클라이언트 (폴백용)
"""
import logging
from typing import Optional, Dict, Any

import httpx
from fastapi import HTTPException

from config import OPENAI_KEY

logger = logging.getLogger("app")


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
        "max_output_tokens": 1400,
    }

    if use_web:
        payload["tools"] = [{"type": "web_search"}]

    async with httpx.AsyncClient(timeout=180) as client:
        r = await client.post("https://api.openai.com/v1/responses", headers=headers, json=payload)

        if r.status_code != 200:
            body = r.text
            logger.error(f"openai report http {r.status_code}: {body}")
            raise HTTPException(status_code=502, detail="OpenAI API call failed")

        data = r.json()

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

    raise HTTPException(status_code=502, detail="OpenAI response has no output_text")
