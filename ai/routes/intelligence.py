"""
인텔리전스 라우트: 인사이트 분석, 리포트 생성
"""
import asyncio
import json
import logging
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel

from config import LLM_PROVIDER, TESTDATA_DIR, RAG_ENABLED
from cache import cache_get, cache_set
from analysis.sectors import get_sector_bands
from analysis.scoring import (
    continuous_score, weighted_score,
    calculate_multi_timeframe_momentum,
    calculate_rsi_score, calculate_macd_score,
    calculate_obv_score, calculate_quality_score,
    calculate_confidence_v2,
)
from analysis.technicals import compute_technicals_from_points
from routes.market_data import prices, quote, fundamentals, news, obv, market_env
from llm import ollama_client, openai_client

logger = logging.getLogger("app")

router = APIRouter()


# ── Models ──

class InsightRequest(BaseModel):
    """인사이트 분석 입력 모델."""
    ticker: str
    market: str = "US"
    days: int = 90
    newsLimit: int = 20
    indicators: Optional[list[str]] = None
    benchmark: Optional[str] = None
    includeForecasts: Optional[bool] = False
    compareWithSector: Optional[bool] = True


class ReportRequest(BaseModel):
    """리포트 생성 입력 모델."""
    ticker: str
    market: str = "US"
    days: int = 90
    newsLimit: int = 20
    sections: list[str] = None
    template: str = "detailed"


# ── LLM 폴백 헬퍼 ──

async def _llm_summarize(prompt: str) -> Optional[str]:
    """LLM_PROVIDER에 따라 Ollama -> OpenAI 폴백"""
    if LLM_PROVIDER == "ollama":
        result = await ollama_client.summarize_korean(prompt)
        if result:
            return result
        logger.warning("Ollama summarize failed, falling back to OpenAI")
    return await openai_client.summarize_korean(prompt)


async def _llm_report(prompt: str, use_web: bool = True) -> Optional[str]:
    """LLM_PROVIDER에 따라 Ollama -> OpenAI 폴백"""
    if LLM_PROVIDER == "ollama":
        result = await ollama_client.report_korean(prompt)
        if result:
            return result
        logger.warning("Ollama report failed, falling back to OpenAI")
    return await openai_client.report_korean(prompt, use_web=use_web)


# ── Report Helpers ──

def _build_profitability_summary(fund: dict) -> str:
    """펀더멘털 딕셔너리에서 수익성 섹션용 요약 텍스트를 생성한다."""
    lines = []
    roe = fund.get("roe")
    opm = fund.get("operatingMargin")
    npm = fund.get("profitMargin")
    roa = fund.get("roa")

    if roe is not None:
        roe_pct = roe * 100
        assessment = "우수" if roe_pct > 15 else "양호" if roe_pct > 8 else "저조" if roe_pct > 0 else "적자"
        lines.append(f"- ROE: {roe_pct:.1f}% ({assessment})")
    if opm is not None:
        opm_pct = opm * 100
        assessment = "높음" if opm_pct > 15 else "보통" if opm_pct > 5 else "낮음" if opm_pct > 0 else "적자"
        lines.append(f"- 영업이익률: {opm_pct:.1f}% ({assessment})")
    if npm is not None:
        lines.append(f"- 순이익률: {npm*100:.1f}%")
    if roa is not None:
        lines.append(f"- ROA: {roa*100:.1f}%")
    return "\n".join(lines) if lines else "- 수익성 데이터 없음"


def _build_technical_summary(tech: dict, obv_data: dict | None) -> str:
    """기술지표/거래량(OBV) 데이터를 사람이 읽기 쉬운 문장으로 요약한다."""
    lines = []
    rsi = tech.get("rsi14")
    if rsi is not None:
        if rsi < 30:
            lines.append(f"- RSI(14): {rsi:.1f} (과매도 구간, 반등 가능성)")
        elif rsi > 70:
            lines.append(f"- RSI(14): {rsi:.1f} (과매수 구간, 조정 주의)")
        else:
            lines.append(f"- RSI(14): {rsi:.1f} (중립)")

    ma50 = tech.get("ma50")
    ma200 = tech.get("ma200")
    close = tech.get("close")

    if close and ma200:
        if close > ma200:
            lines.append("- 현재가 > 200일 이평선: 상승 추세")
        else:
            lines.append("- 현재가 < 200일 이평선: 하락 추세")
    if ma50 and ma200:
        if ma50 > ma200:
            lines.append("- 골든크로스 상태 (50일선 > 200일선)")
        else:
            lines.append("- 데드크로스 상태 (50일선 < 200일선)")

    if obv_data:
        obv_trend = obv_data.get("obvTrend")
        if obv_trend == "RISING":
            lines.append("- OBV: 상승 (거래량 유입, 가격 상승 신뢰도 높음)")
        elif obv_trend == "FALLING":
            lines.append("- OBV: 하락 (거래량 유출, 가격 하락 압력)")
        else:
            lines.append("- OBV: 중립")

    w52h = tech.get("week52High")
    w52l = tech.get("week52Low")
    if w52h and w52l and close:
        range_pct = (close - w52l) / (w52h - w52l) * 100 if w52h != w52l else 50
        lines.append(f"- 52주 범위 내 위치: {range_pct:.0f}% (저점 {w52l} ~ 고점 {w52h})")
    return "\n".join(lines) if lines else "- 기술적 분석 데이터 없음"


def _build_valuation_summary(fund: dict) -> str:
    """밸류에이션/성장성 핵심 지표를 보고서용 bullet 텍스트로 변환한다."""
    lines = []
    pe = fund.get("pe")
    ps = fund.get("ps")
    pb = fund.get("pb")
    ev_ebitda = fund.get("evToEbitda")

    if pe is not None:
        assessment = "저평가" if pe < 15 else "적정" if pe < 25 else "고평가"
        lines.append(f"- PER: {pe:.1f}배 ({assessment})")
    if ps is not None:
        assessment = "저평가" if ps < 2 else "적정" if ps < 5 else "고평가"
        lines.append(f"- PSR: {ps:.1f}배 ({assessment})")
    if pb is not None:
        assessment = "저평가" if pb < 1.5 else "적정" if pb < 3 else "고평가"
        lines.append(f"- PBR: {pb:.1f}배 ({assessment})")
    if ev_ebitda is not None:
        lines.append(f"- EV/EBITDA: {ev_ebitda:.1f}배")

    rev_yoy = fund.get("revYoY")
    earnings_growth = fund.get("quarterlyEarningsGrowth")
    if rev_yoy is not None:
        rev_pct = rev_yoy * 100
        assessment = "고성장" if rev_pct > 20 else "성장" if rev_pct > 0 else "역성장"
        lines.append(f"- 매출 YoY: {rev_pct:.1f}% ({assessment})")
    if earnings_growth is not None:
        lines.append(f"- 분기 이익 성장: {earnings_growth*100:.1f}%")
    return "\n".join(lines) if lines else "- 밸류에이션 데이터 없음"


def _format_market_env(market_env_data: dict | None) -> str:
    """시장 환경 데이터를 짧은 설명 문자열로 포맷한다."""
    if not market_env_data:
        return "데이터 없음"
    parts = []
    treasury = market_env_data.get("treasuryYield10Y")
    if treasury:
        parts.append(f"10년 국채 {treasury}%")
    fear = market_env_data.get("fearLevel")
    if fear:
        fear_kr = {"LOW": "낮음 (탐욕)", "MODERATE": "보통", "HIGH": "높음 (공포)"}.get(fear, fear)
        parts.append(f"공포지수 {fear_kr}")
    vol = market_env_data.get("spyVolatility20d")
    if vol:
        parts.append(f"S&P500 변동성 {vol*100:.1f}%")
    return ", ".join(parts) if parts else "데이터 없음"


def _report_path(ticker: str, market: str) -> Path:
    """테스트 모드에서 보고서 스냅샷을 저장/로드할 경로를 계산한다."""
    safe = f"{ticker}_{market}".replace("/", "_")
    return TESTDATA_DIR / f"report_{safe}.txt"


def _legacy_points(raw_score: float | None, weight: float, max_points: int = 15) -> int:
    """v3의 0~100 점수를 구버전 points 스케일로 역변환한다."""
    if raw_score is None:
        return 0
    deviation = (raw_score - 50) / 50
    return int(deviation * max_points)


# ── Endpoints ──

def _score_to_action(final_score: float) -> tuple[str, str]:
    """최종 점수를 액션 코드/한글 문구로 매핑한다."""
    if final_score >= 72:
        return "STRONG_BUY", "적극 매수 고려"
    if final_score >= 60:
        return "BUY", "매수 고려"
    if final_score >= 45:
        return "NEUTRAL", "중립"
    if final_score >= 32:
        return "SELL", "매도 고려"
    return "STRONG_SELL", "적극 매도 고려"


async def _fetch_insight_inputs(ticker: str, market: str, days: int, news_limit: int, test: bool):
    """insights 계산에 필요한 원천 데이터를 병렬 조회한다."""
    results = await asyncio.gather(
        quote(ticker, market, test=test),
        prices(ticker, market, max(days, 200), test=test),
        fundamentals(ticker, market, test=test),
        news(ticker, market, news_limit, test=test),
        obv(ticker, market, test=test),
        market_env(test=test),
        return_exceptions=True,
    )
    q, p, f, n, obv_result, market_env_result = results

    if isinstance(q, Exception):
        logger.warning("Quote unavailable for %s: %s", ticker, q)
        q = {
            "ticker": ticker,
            "market": market,
            "price": None,
            "change": None,
            "changePercent": None,
            "latestTradingDay": None,
            "degraded": True,
        }

    if isinstance(p, Exception):
        logger.warning("Prices unavailable for %s: %s", ticker, p)
        p = {
            "ticker": ticker,
            "market": market,
            "points": [],
            "degraded": True,
        }

    if isinstance(f, Exception):
        logger.warning("Fundamentals unavailable for %s: %s", ticker, f)
        f = {
            "ticker": ticker,
            "market": market,
            "sector": None,
            "industry": None,
            "pe": None,
            "ps": None,
            "pb": None,
            "roe": None,
            "roa": None,
            "operatingMargin": None,
            "profitMargin": None,
            "revYoY": None,
            "degraded": True,
        }

    news_data = n
    if isinstance(n, Exception):
        logger.warning("News sentiment unavailable for %s: %s", ticker, n)
        news_data = {
            "ticker": ticker,
            "market": market,
            "positiveRatio": None,
            "headlines": [],
            "items": [],
            "summary": "",
            "degraded": True,
        }

    obv_data = None
    if isinstance(obv_result, Exception):
        logger.warning("OBV data unavailable for %s: %s", ticker, obv_result)
    else:
        obv_data = obv_result

    market_env_data = None
    if isinstance(market_env_result, Exception):
        logger.warning("Market environment data unavailable: %s", market_env_result)
    else:
        market_env_data = market_env_result

    return q, p, f, news_data, obv_data, market_env_data


async def _build_recommendation(
    ticker: str,
    q: dict,
    p: dict,
    f: dict,
    n: dict,
    obv_data: dict | None,
    market_env_data: dict | None,
):
    """핵심 지표 점수화/근거 생성/LLM 요약까지 추천 결과를 구성한다."""
    tech = compute_technicals_from_points(p.get("points") or [], quote=q, overview=f.get("overview") or {})
    sector = f.get("sector")
    bands = get_sector_bands(sector)

    pts = p["points"]
    closes = [pt["close"] for pt in pts if isinstance(pt.get("close"), (int, float))]
    multi_momentum = calculate_multi_timeframe_momentum(closes)
    composite_momentum = multi_momentum.get("composite")

    reasons = []
    breakdown = []
    critical_metrics = [False, False, False, False]

    def add_break(metric, value, value_type, weight, raw_score, contribution, rule, evidence):
        breakdown.append({
            "metric": metric, "value": value, "valueType": value_type,
            "weight": weight, "rawScore": raw_score, "contribution": contribution,
            "rule": rule, "evidence": evidence,
        })

    W_PE = 0.12; W_PS = 0.08; W_EVEBITDA = 0.10
    W_ROE = 0.10; W_OPM = 0.07; W_QUALITY = 0.05
    W_REV = 0.13; W_MOM = 0.10
    W_RSI = 0.07; W_MACD = 0.04; W_OBV = 0.04
    W_NEWS = 0.10

    score_components = []

    pe = f.get("pe")
    pe_band = bands["pe"]
    ev_band = bands.get("ev_ebitda", {"low": 8, "high": 25})
    pe_raw = continuous_score(pe, pe_band["low"], pe_band["high"], reverse=False)
    if pe_raw is not None:
        score_components.append((pe_raw, W_PE))
        reasons.append(f"PER {pe:.1f} ({'저평가' if pe_raw >= 70 else '적정' if pe_raw >= 40 else '고평가'}, 섹터 기준 {pe_band['low']}~{pe_band['high']})")
        add_break("PER", pe, "number", W_PE, pe_raw, pe_raw * W_PE,
                  f"섹터({sector or 'DEFAULT'}) 기준 {pe_band['low']}~{pe_band['high']}",
                  f"연속점수={pe_raw:.1f}")
        critical_metrics[0] = True
    else:
        add_break("PER", None, "number", W_PE, None, 0, "데이터 없음", "N/A")

    ps = f.get("ps")
    ps_band = bands["ps"]
    ps_raw = continuous_score(ps, ps_band["low"], ps_band["high"], reverse=False)
    if ps_raw is not None:
        score_components.append((ps_raw, W_PS))
        reasons.append(f"PSR {ps:.1f} ({'저평가' if ps_raw >= 70 else '적정' if ps_raw >= 40 else '고평가'})")
        add_break("PSR", ps, "number", W_PS, ps_raw, ps_raw * W_PS,
                  f"섹터 기준 {ps_band['low']}~{ps_band['high']}",
                  f"연속점수={ps_raw:.1f}")

    ev_ebitda = f.get("evToEbitda")
    ev_raw = continuous_score(ev_ebitda, ev_band["low"], ev_band["high"], reverse=False) if ev_ebitda else None
    if ev_raw is not None:
        score_components.append((ev_raw, W_EVEBITDA))
        reasons.append(f"EV/EBITDA {ev_ebitda:.1f} ({'저평가' if ev_raw >= 70 else '적정' if ev_raw >= 40 else '고평가'})")
        add_break("EV/EBITDA", ev_ebitda, "number", W_EVEBITDA, ev_raw, ev_raw * W_EVEBITDA,
                  f"섹터 기준 {ev_band['low']}~{ev_band['high']} (낮을수록 좋음)",
                  f"연속점수={ev_raw:.1f}")

    roe = f.get("roe")
    roe_raw = continuous_score(roe, 0.05, 0.25, reverse=True) if roe else None
    if roe_raw is not None:
        score_components.append((roe_raw, W_ROE))
        reasons.append(f"ROE {roe*100:.1f}% ({'우수' if roe_raw >= 70 else '양호' if roe_raw >= 40 else '저조'})")
        add_break("ROE", roe, "pct", W_ROE, roe_raw, roe_raw * W_ROE,
                  "5%~25% 범위 (높을수록 좋음)", f"연속점수={roe_raw:.1f}")
        critical_metrics[1] = True

    opm = f.get("operatingMargin")
    opm_raw = continuous_score(opm, 0.05, 0.30, reverse=True) if opm else None
    if opm_raw is not None:
        score_components.append((opm_raw, W_OPM))
        reasons.append(f"영업이익률 {opm*100:.1f}% ({'높음' if opm_raw >= 70 else '보통' if opm_raw >= 40 else '낮음'})")
        add_break("Operating Margin", opm, "pct", W_OPM, opm_raw, opm_raw * W_OPM,
                  "5%~30% 범위 (높을수록 좋음)", f"연속점수={opm_raw:.1f}")

    quality_score, quality_flags = calculate_quality_score(f, bands)
    if quality_score is not None:
        score_components.append((quality_score, W_QUALITY))
        if quality_flags:
            reasons.append(f"품질: {', '.join(quality_flags[:2])}")
        add_break("Quality", quality_score, "number", W_QUALITY, quality_score, quality_score * W_QUALITY,
                  "ROE, 마진, 배당 종합", f"flags={quality_flags}")

    rev = f.get("revYoY")
    rev_raw = continuous_score(rev, -0.10, 0.30, reverse=True) if rev else None
    if rev_raw is not None:
        score_components.append((rev_raw, W_REV))
        reasons.append(f"매출 YoY {rev*100:.1f}% ({'고성장' if rev_raw >= 70 else '성장' if rev_raw >= 40 else '저성장/역성장'})")
        add_break("Revenue YoY", rev, "pct", W_REV, rev_raw, rev_raw * W_REV,
                  "-10%~30% 범위", f"연속점수={rev_raw:.1f}")
        critical_metrics[2] = True

    if composite_momentum is not None:
        mom_raw = continuous_score(composite_momentum, -0.15, 0.25, reverse=True)
        if mom_raw is not None:
            score_components.append((mom_raw, W_MOM))
            trend_str = multi_momentum.get("trend_strength", "MIXED")
            if trend_str == "STRONG_UP":
                mom_raw = min(100, mom_raw + 5)
                reasons.append("강한 상승 추세 (모든 시간대 상승)")
            elif trend_str == "STRONG_DOWN":
                mom_raw = max(0, mom_raw - 5)
                reasons.append("강한 하락 추세 (모든 시간대 하락)")
            else:
                reasons.append(f"복합 모멘텀 {composite_momentum*100:.1f}% ({trend_str})")
            add_break("Multi-TF Momentum", composite_momentum, "pct", W_MOM, mom_raw, mom_raw * W_MOM,
                      "20/60/120/200일 가중 평균",
                      f"20d={multi_momentum.get('mom20')}, 60d={multi_momentum.get('mom60')}, trend={trend_str}")
            critical_metrics[3] = True

    rsi = tech.get("rsi14")
    rsi_raw, rsi_zone = calculate_rsi_score(rsi)
    if rsi_raw is not None:
        score_components.append((rsi_raw, W_RSI))
        if rsi_zone in ["과매도", "과매수"]:
            reasons.append(f"RSI {rsi:.1f} ({rsi_zone})")
        add_break("RSI14", rsi, "number", W_RSI, rsi_raw, rsi_raw * W_RSI,
                  "과매도(<30)=매수기회, 과매수(>70)=주의",
                  f"zone={rsi_zone}, 연속점수={rsi_raw:.1f}")

    macd_data = tech.get("macd") if isinstance(tech.get("macd"), dict) else {}
    macd_hist = macd_data.get("hist")
    macd_raw, macd_signal = calculate_macd_score(macd_hist)
    if macd_raw is not None:
        score_components.append((macd_raw, W_MACD))
        reasons.append(f"MACD {macd_signal}")
        add_break("MACD", macd_hist, "number", W_MACD, macd_raw, macd_raw * W_MACD,
                  "히스토그램 기반 모멘텀",
                  f"signal={macd_signal}, hist={macd_hist:.4f}" if macd_hist else "N/A")

    obv_raw, obv_desc = calculate_obv_score(obv_data, composite_momentum)
    if obv_raw is not None:
        score_components.append((obv_raw, W_OBV))
        if "DIVERGENCE" in obv_desc:
            reasons.append(f"OBV {obv_desc}")
        elif obv_data and obv_data.get("obvTrend") in ["RISING", "FALLING"]:
            trend_kr = "상승 (거래량 유입)" if obv_data["obvTrend"] == "RISING" else "하락 (거래량 유출)"
            reasons.append(f"OBV {trend_kr}")
        add_break("OBV Trend", obv_data.get("obvTrend") if obv_data else None, "text", W_OBV, obv_raw, obv_raw * W_OBV,
                  "RISING=유입, FALLING=유출, 가격발산 감지",
                  f"desc={obv_desc}, momentum={(obv_data or {}).get('obvMomentum', 'N/A')}")

    pr = n.get("positiveRatio")
    news_raw = continuous_score(pr, 0.30, 0.70, reverse=True) if pr else None
    if news_raw is not None:
        score_components.append((news_raw, W_NEWS))
        sentiment = "긍정적" if pr > 0.6 else "부정적" if pr < 0.4 else "중립"
        reasons.append(f"뉴스 센티먼트 {pr*100:.0f}% ({sentiment})")
        add_break("News Sentiment", pr, "pct", W_NEWS, news_raw, news_raw * W_NEWS,
                  "30%~70% 범위", f"positiveRatio={pr:.2f}")

    final_score = max(0, min(100, weighted_score(score_components)))
    if market_env_data:
        fear_level = market_env_data.get("fearLevel")
        if fear_level == "HIGH" and final_score >= 60:
            final_score = max(0, final_score - 3)
            reasons.append("시장 변동성 높음 (보수적 조정)")
        elif fear_level == "LOW" and final_score <= 40:
            final_score = min(100, final_score + 3)
            reasons.append("시장 낙관적 (주의 필요)")

    action, action_kr = _score_to_action(final_score)
    total_metrics = 12
    confidence, confidence_grade = calculate_confidence_v2(len(score_components), total_metrics, critical_metrics)
    data_completeness = len(score_components) / total_metrics

    base_text = (
        f"종합 점수 {final_score:.0f}/100 ({action_kr}). "
        + "; ".join(reasons[:5]) if reasons else "분석 데이터 부족"
    )
    llm_text = await _llm_summarize(
        f"다음 정보를 바탕으로 투자 분석 요약을 한국어로 3~4문장 정도 써줘.\n"
        f"- 티커: {ticker}\n"
        f"- 섹터: {sector}\n"
        f"- 점수: {final_score:.0f}/100 ({action_kr})\n"
        f"- 신뢰도: {confidence*100:.0f}% ({confidence_grade})\n"
        f"- 주요 근거: {reasons}\n"
        f"- 현재가: {q.get('price')}, 변동: {q.get('changePercent')}\n"
        f"- 다중 시간대 추세: {multi_momentum.get('trend_strength', 'N/A')}\n"
        f"- 핵심만 간결하게, 투자 권유가 아닌 분석 정보임을 명시\n"
    )

    return {
        "sector": sector,
        "technicals": tech,
        "multiMomentum": multi_momentum,
        "recommendation": {
            "action": action,
            "actionKr": action_kr,
            "score": round(final_score, 1),
            "confidence": confidence,
            "confidenceGrade": confidence_grade,
            "dataCompleteness": round(data_completeness, 2),
            "reasons": reasons,
            "text": llm_text or base_text,
            "breakdown": breakdown,
        }
    }


def _build_compact_report_context(ins: dict) -> dict:
    """리포트 프롬프트에 필요한 최소 컨텍스트만 추려 토큰 사용량을 줄인다."""
    quote_data = ins.get("quote") or {}
    fund_data = ins.get("fundamentals") or {}
    tech_data = ins.get("technicals") or {}
    rec_data = ins.get("recommendation") or {}
    return {
        "ticker": ins.get("ticker"),
        "market": ins.get("market"),
        "quote": {
            "price": quote_data.get("price"),
            "changePercent": quote_data.get("changePercent"),
            "latestTradingDay": quote_data.get("latestTradingDay"),
        },
        "fundamentals": {
            "sector": fund_data.get("sector"),
            "industry": fund_data.get("industry"),
            "pe": fund_data.get("pe"),
            "pb": fund_data.get("pb"),
            "ps": fund_data.get("ps"),
            "roe": fund_data.get("roe"),
            "operatingMargin": fund_data.get("operatingMargin"),
            "revYoY": fund_data.get("revYoY"),
        },
        "technicals": {
            "rsi14": tech_data.get("rsi14"),
            "ma50": tech_data.get("ma50"),
            "ma200": tech_data.get("ma200"),
            "week52Position": tech_data.get("week52Position"),
            "macd": tech_data.get("macd"),
        },
        "recommendation": {
            "action": rec_data.get("action"),
            "score": rec_data.get("score"),
            "confidence": rec_data.get("confidence"),
            "confidenceGrade": rec_data.get("confidenceGrade"),
            "dataCompleteness": rec_data.get("dataCompleteness"),
            "reasons": (rec_data.get("reasons") or [])[:8],
        },
        "newsHeadlines": (ins.get("news") or {}).get("headlines", [])[:5],
    }


@router.post("/insights")
async def insights(req: InsightRequest, test: bool = Query(False)):
    """종합 분석 엔드포인트 v3"""
    ticker = req.ticker.strip().upper()
    if not ticker:
        raise HTTPException(status_code=400, detail="ticker required")

    ck = f"insights:v3:{ticker}:{req.market}:{req.days}:{req.newsLimit}"
    cached = cache_get(ck)
    if cached is not None:
        return cached

    q, p, f, n, obv_data, market_env_data = await _fetch_insight_inputs(
        ticker=ticker, market=req.market, days=req.days, news_limit=req.newsLimit, test=test
    )
    scored = await _build_recommendation(
        ticker=ticker, q=q, p=p, f=f, n=n, obv_data=obv_data, market_env_data=market_env_data
    )

    result = {
        "ticker": ticker,
        "market": req.market,
        "sector": scored["sector"],
        "quote": q,
        "prices": p,
        "news": n,
        "fundamentals": f,
        "technicals": scored["technicals"],
        "obv": obv_data,
        "marketEnv": market_env_data,
        "multiMomentum": scored["multiMomentum"],
        "recommendation": scored["recommendation"],
    }
    cache_set(ck, result, ttl_sec=120)
    return result


@router.post("/insights_legacy")
async def insights_legacy(req: InsightRequest, test: bool = Query(False)):
    """이전 버전 API 호환용"""
    result = await insights(req, test)
    legacy_breakdown = []
    for b in result.get("recommendation", {}).get("breakdown", []):
        legacy_breakdown.append({
            "metric": b["metric"],
            "value": b["value"],
            "valueType": b["valueType"],
            "weight": int(b["weight"] * 100),
            "points": _legacy_points(b.get("rawScore"), b["weight"]),
            "rule": b["rule"],
            "evidence": b["evidence"],
        })
    result["recommendation"]["breakdown"] = legacy_breakdown
    return result


@router.post("/report")
async def report(req: ReportRequest, test: bool = Query(False), web: bool = Query(True)):
    """A4 보고서 생성"""
    ticker = req.ticker.strip()
    if not ticker:
        raise HTTPException(status_code=400, detail="ticker required")

    p = _report_path(ticker, req.market)
    if test and p.exists():
        return {"ticker": ticker, "market": req.market, "text": p.read_text(encoding="utf-8")}

    ins = await insights(
        InsightRequest(ticker=ticker, market=req.market, days=req.days, newsLimit=req.newsLimit),
        test=test
    )

    # 보고서는 insights 결과를 재사용해 중복 API 호출을 줄이고, 누락 시에만 보조 조회한다.
    obv_data = ins.get("obv")
    market_env_data = ins.get("marketEnv")
    if obv_data is None:
        try:
            obv_data = await obv(ticker, req.market, test=test)
        except Exception:
            pass
    if market_env_data is None:
        try:
            market_env_data = await market_env(test=test)
        except Exception:
            pass

    fund = ins.get("fundamentals") or {}
    tech = ins.get("technicals") or {}
    rec = ins.get("recommendation") or {}
    news_data = ins.get("news") or {}

    profitability_summary = _build_profitability_summary(fund)
    technical_summary = _build_technical_summary(tech, obv_data)
    valuation_summary = _build_valuation_summary(fund)

    # RAG: search for related past articles
    rag_context = ""
    if RAG_ENABLED:
        try:
            from rag.store import search as rag_search
            rag_results = rag_search(ticker, f"{ticker} 투자 분석 실적 전망", n_results=5)
            if rag_results:
                rag_lines = []
                for r in rag_results:
                    title = r.get("title", "")
                    date = r.get("date", "")
                    snippet = r.get("content", "")[:200]
                    if title:
                        rag_lines.append(f"- [{date}] {title}: {snippet}")
                if rag_lines:
                    rag_context = "\n\n## 과거 관련 뉴스 (RAG 검색 결과)\n" + "\n".join(rag_lines)
        except Exception as e:
            logger.debug("RAG context unavailable for report: %s", e)

    template = req.template or "detailed"
    sections = req.sections or []

    template_guidelines = {
        "detailed": "한국어로 A4 1.5~2장 분량 (1500~2500자), 모든 섹션 포함",
        "summary": "한국어로 A4 0.5장 분량 (500~800자), 핵심 포인트만 간결하게",
        "technical": "한국어로 A4 1장 분량 (1000~1500자), 기술적 분석 중심",
        "fundamental": "한국어로 A4 1장 분량 (1000~1500자), 펀더멘털 분석 중심",
    }

    section_templates = {
        "executive_summary": f"### 1. 종목 개요 (2~3문장)\n- 회사명: {ticker}, 섹터: {fund.get('sector', 'N/A')}, 산업: {fund.get('industry', 'N/A')}\n- 핵심 사업 모델 간단 설명\n- 현재 투자 의견: {rec.get('action', 'N/A')} (점수: {rec.get('score', 'N/A')}/100)",
        "price_analysis": f"### 가격 분석\n- 현재가: ${ins.get('quote', {}).get('price', 'N/A')}\n- 변동: {ins.get('quote', {}).get('changePercent', 'N/A')}\n- 최근 {req.days}일 추세 해석",
        "fundamentals": f"### 펀더멘털 분석\n#### 밸류에이션\n{valuation_summary}\n- 동종 업계 대비 평가\n\n#### 수익성\n{profitability_summary}\n- 수익성 추세",
        "technical_indicators": f"### 기술적 분석\n{technical_summary}\n- 지지/저항 수준\n- 매매 시그널 해석",
        "news_sentiment": f"### 최근 이슈 및 뉴스\n뉴스 헤드라인: {json.dumps(news_data.get('headlines', [])[:5], ensure_ascii=False)}\n- 핵심 이슈만",
        "risk_assessment": f"### 리스크 분석\n- 주요 리스크 요소 (2~3개)\n- 시장 환경: {_format_market_env(market_env_data)}\n- 섹터 특수 리스크",
        "recommendation": f"### 결론 및 투자 체크리스트\n- 종합 점수: {rec.get('score', 'N/A')}/100\n- 투자 의견: {rec.get('action', 'N/A')}\n- 강점 (2~3개)\n- 약점/리스크 (2~3개)\n- 확인 사항 체크리스트",
    }

    if sections and len(sections) > 0:
        selected_sections = [section_templates.get(s, "") for s in sections if s in section_templates]
    elif template == "summary":
        selected_sections = [section_templates["executive_summary"], section_templates["recommendation"]]
    elif template == "technical":
        selected_sections = [section_templates["executive_summary"], section_templates["price_analysis"], section_templates["technical_indicators"], section_templates["risk_assessment"], section_templates["recommendation"]]
    elif template == "fundamental":
        selected_sections = [section_templates["executive_summary"], section_templates["fundamentals"], section_templates["news_sentiment"], section_templates["risk_assessment"], section_templates["recommendation"]]
    else:
        selected_sections = list(section_templates.values())

    sections_text = "\n\n".join(selected_sections)
    compact_context = _build_compact_report_context(ins)

    prompt = f"""당신은 전문 주식 애널리스트입니다. 아래 데이터를 바탕으로 투자 리서치 보고서를 작성해주세요.

## 보고서 작성 가이드라인
- {template_guidelines.get(template, template_guidelines['detailed'])}
- 전문적이지만 이해하기 쉬운 문체
- 구체적인 숫자와 근거 제시
- 투자 권유가 아닌 분석 정보 제공 명시

## 작성할 섹션

{sections_text}

---
## 입력 데이터

**티커**: {ticker}
**섹터**: {fund.get('sector', 'N/A')}
**산업**: {fund.get('industry', 'N/A')}

**가격 정보**:
- 현재가: ${ins.get('quote', {}).get('price', 'N/A')}
- 변동: {ins.get('quote', {}).get('changePercent', 'N/A')}

**분석 근거**:
{json.dumps(rec.get('reasons', []), ensure_ascii=False, indent=2)}

**뉴스 헤드라인**:
{json.dumps(news_data.get('headlines', [])[:5], ensure_ascii=False, indent=2)}

**핵심 데이터 요약 (참고용)**:
{json.dumps(compact_context, ensure_ascii=False, indent=2)}

추가로 웹에서 최근 정보가 필요하면 찾아서 반영하되, 출처 링크를 남겨라.
{rag_context}
"""

    text = await _llm_report(prompt, use_web=web)
    if not text:
        text = (
            "보고서 생성에 실패했습니다(LLM 미설정 또는 API 오류).\n"
            "대신 Insights JSON을 기반으로 수동 확인을 권장합니다.\n"
        )

    if test:
        p.write_text(text, encoding="utf-8")

    return {"ticker": ticker, "market": req.market, "text": text}
