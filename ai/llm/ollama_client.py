"""
Ollama 로컬 LLM 클라이언트
"""
import logging
from typing import Optional

import httpx

from config import OLLAMA_BASE_URL, OLLAMA_MODEL

logger = logging.getLogger("app")


async def _generate(prompt: str, max_tokens: int = 1400) -> Optional[str]:
    """Ollama /api/generate 호출"""
    try:
        payload = {
            "model": OLLAMA_MODEL,
            "prompt": prompt,
            "stream": False,
            "options": {
                "num_predict": max_tokens,
                "temperature": 0.7,
            }
        }

        async with httpx.AsyncClient(timeout=120) as client:
            r = await client.post(f"{OLLAMA_BASE_URL}/api/generate", json=payload)
            if r.status_code != 200:
                logger.warning(f"Ollama generate failed: HTTP {r.status_code}")
                return None
            data = r.json()
            return data.get("response", "").strip() or None

    except (httpx.ConnectError, httpx.TimeoutException) as e:
        logger.warning(f"Ollama unavailable: {e}")
        return None
    except Exception as e:
        logger.error(f"Ollama error: {e}")
        return None


async def summarize_korean(prompt: str) -> Optional[str]:
    """뉴스/인사이트 요약 (max 500 tokens)"""
    return await _generate(prompt, max_tokens=500)


async def report_korean(prompt: str) -> Optional[str]:
    """투자 리포트 생성 (max 1400 tokens)"""
    return await _generate(prompt, max_tokens=1400)


async def is_available() -> bool:
    """Ollama 서버 접속 가능 여부 확인"""
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            r = await client.get(f"{OLLAMA_BASE_URL}/api/tags")
            return r.status_code == 200
    except Exception:
        return False
