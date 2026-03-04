"""AI Chatbot endpoint"""
from fastapi import APIRouter
from pydantic import BaseModel
from typing import Optional
import os
import logging

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


class ChatMessage(BaseModel):
    role: str
    content: str


class ChatbotRequest(BaseModel):
    message: str
    model: str = "opus"
    history: list[ChatMessage] = []
    context: Optional[str] = None  # Portfolio/watchlist context injected by backend


@router.post("/chatbot")
async def chatbot(req: ChatbotRequest):
    """AI chatbot for financial assistance"""

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

    try:
        client = get_openai_client()

        full_messages = [{"role": "system", "content": system_prompt}]
        full_messages.extend(messages)

        response = client.chat.completions.create(
            model=model_id,
            messages=full_messages,
            max_tokens=1500,
            temperature=0.7
        )

        assistant_message = response.choices[0].message.content

        return {
            "message": assistant_message,
            "tokensIn": response.usage.prompt_tokens,
            "tokensOut": response.usage.completion_tokens
        }
    except Exception as e:
        logger.exception("Chatbot request failed")
        return {
            "message": "현재 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.",
            "tokensIn": 0,
            "tokensOut": 0
        }
