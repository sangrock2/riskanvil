"""
중앙 설정 모듈 - 환경변수, 상수, 경로
"""
import os
from pathlib import Path
from dotenv import load_dotenv

load_dotenv()

# ── API Keys ──
ALPHA_KEY = (
    os.getenv("ALPHAVANTAGE_API_KEY", "").strip()
    or os.getenv("ALPHA_VANTAGE_API_KEY", "").strip()
)
OPENAI_KEY = os.getenv("OPENAI_API_KEY", "").strip()
FINNHUB_KEY = os.getenv("FINNHUB_API_KEY", "").strip()

# ── Provider 설정 ──
DATA_PROVIDER = os.getenv("DATA_PROVIDER", "yfinance").strip().lower()  # yfinance | alpha_vantage
LLM_PROVIDER = os.getenv("LLM_PROVIDER", "ollama").strip().lower()      # ollama | openai

# ── Ollama 설정 ──
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434").strip()
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3.1:8b").strip()

# ── URLs ──
ALPHA_BASE = "https://www.alphavantage.co/query"

# ── 경로 ──
BASE_DIR = Path(__file__).resolve().parent
TESTDATA_DIR = Path(os.getenv("TESTDATA_DIR", str(BASE_DIR / "testdata")))
TESTDATA_DIR.mkdir(parents=True, exist_ok=True)

# ── 감성 분석 단어 리스트 ──
POS_WORDS = [
    "beat", "surge", "soar", "record", "strong", "upgrade", "growth", "profit", "bullish",
    "rise", "up", "outperform", "buy", "positive", "expands", "wins", "accelerate"
]

NEG_WORDS = [
    "miss", "plunge", "fall", "weak", "downgrade", "lawsuit", "decline", "loss", "bearish",
    "down", "underperform", "sell", "negative", "cuts", "warning", "slowdown"
]

# ── RAG 설정 ──
RAG_ENABLED = os.getenv("RAG_ENABLED", "true").strip().lower() == "true"
RAG_DATA_DIR = Path(os.getenv("RAG_DATA_DIR", str(BASE_DIR / "rag_data")))
