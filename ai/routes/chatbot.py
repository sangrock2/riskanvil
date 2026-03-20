"""AI Chatbot endpoint"""
import asyncio
import os
import logging
from typing import Optional

import httpx
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from config import OLLAMA_BASE_URL, OLLAMA_MODEL

router = APIRouter()
logger = logging.getLogger("app")

# Lazy import OpenAI to avoid startup errors if not configured
openai_client = None


def get_openai_client():
    """Lazy load OpenAI client"""
    global openai_client
    if openai_client is None:
        try:
            from openai import OpenAI
            api_key = os.getenv("OPENAI_API_KEY", "")
            if not api_key:
                raise ValueError("OPENAI_API_KEY not set")
            openai_client = OpenAI(api_key=api_key)
        except Exception as e:
            raise RuntimeError(f"Failed to initialize OpenAI client: {e}")
    return openai_client


async def _chat_with_openai(model_id: str, messages: list[dict]) -> Optional[dict]:
    try:
        client = get_openai_client()
    except Exception as e:
        logger.warning("OpenAI client unavailable, falling back to Ollama: %s", e)
        return None

    def _request():
        return client.chat.completions.create(
            model=model_id,
            messages=messages,
            max_tokens=1500,
            temperature=0.7,
        )

    try:
        response = await asyncio.to_thread(_request)
    except Exception as e:
        logger.warning("OpenAI chat request failed, falling back to Ollama: %s", e)
        return None

    assistant_message = response.choices[0].message.content
    if isinstance(assistant_message, list):
        assistant_message = "".join(
            part.get("text", "")
            for part in assistant_message
            if isinstance(part, dict)
        )
    assistant_message = (assistant_message or "").strip()
    if not assistant_message:
        return None

    usage = getattr(response, "usage", None)
    return {
        "message": assistant_message,
        "tokensIn": getattr(usage, "prompt_tokens", 0) or 0,
        "tokensOut": getattr(usage, "completion_tokens", 0) or 0,
    }


async def _chat_with_ollama(messages: list[dict]) -> Optional[dict]:
    payload = {
        "model": OLLAMA_MODEL,
        "messages": messages,
        "stream": False,
        "options": {
            "temperature": 0.7,
            "num_predict": 1500,
        },
    }

    try:
        async with httpx.AsyncClient(timeout=120) as client:
            response = await client.post(f"{OLLAMA_BASE_URL}/api/chat", json=payload)
    except (httpx.ConnectError, httpx.TimeoutException) as e:
        logger.warning("Ollama unavailable after OpenAI failure: %s", e)
        return None
    except Exception as e:
        logger.error("Ollama fallback failed: %s", e)
        return None

    if response.status_code != 200:
        logger.warning("Ollama fallback returned HTTP %s", response.status_code)
        return None

    data = response.json()
    assistant_message = ((data.get("message") or {}).get("content") or "").strip()
    if not assistant_message:
        return None

    return {
        "message": assistant_message,
        "tokensIn": int(data.get("prompt_eval_count") or 0),
        "tokensOut": int(data.get("eval_count") or 0),
    }


class ChatMessage(BaseModel):
    role: str
    content: str


class ChatbotRequest(BaseModel):
    message: str
    model: str = "opus"
    history: list[ChatMessage] = Field(default_factory=list)
    context: Optional[str] = None  # Portfolio/watchlist context injected by backend


@router.post("/chatbot")
async def chatbot(req: ChatbotRequest):
    """AI chatbot for financial assistance.

    OpenAI is the primary provider. Ollama is used only as a fallback.
    """

    # Map model names to OpenAI model IDs
    model_map = {
        "opus": "gpt-4o",
        "sonnet": "gpt-4o",
        "haiku": "gpt-4o-mini"
    }

    model_id = model_map.get(req.model, "gpt-4o")

    # Build system prompt
    system_prompt = """당신은 Stock-AI 플랫폼의 전문 금융 AI 어시스턴트입니다.

**역할 및 전문성:**
- 주식 시장, 기술적/펀더멘탈 분석 전문가
- 포트폴리오 관리 및 자산 배분 전문가
- 한국 및 미국 주식 시장 전문가

**응답 규칙:**
- 기본 언어: 한국어 (사용자가 영어로 물으면 영어로 답변)
- 간결하고 명확하게, 데이터 기반으로 답변
- 투자 조언 시 항상 리스크를 언급
- 불확실한 정보는 솔직하게 모른다고 답변

**도움 가능 분야:**
- 주식 분석 및 투자 의견 (매수/매도/보유)
- P/E, ROE, EPS 등 재무 지표 설명
- 기술적 분석 (이동평균, RSI, MACD 등)
- 포트폴리오 분석 및 리밸런싱 조언
- 시장 동향 및 뉴스 해석
- 배당 투자 전략
- 섹터 분석 및 비교"""

    # Append user portfolio/watchlist context if provided
    if req.context:
        system_prompt += f"\n\n**사용자 포트폴리오 및 관심종목 정보:**\n{req.context}\n\n위 정보를 참고하여 사용자 상황에 맞는 맞춤형 답변을 제공하세요."

    # Build messages list from history
    messages = []
    for msg in req.history:
        messages.append({"role": msg.role, "content": msg.content})
    messages.append({"role": "user", "content": req.message})

    full_messages = [{"role": "system", "content": system_prompt}]
    full_messages.extend(messages)

    openai_result = await _chat_with_openai(model_id, full_messages)
    if openai_result is not None:
        return openai_result

    ollama_result = await _chat_with_ollama(full_messages)
    if ollama_result is not None:
        return ollama_result

    logger.error("Chatbot request failed across all providers")
    raise HTTPException(status_code=503, detail="AI provider unavailable")
