import React, { memo } from "react";
import LineChartCanvas from "./LineChartCanvas";
import { pct, num, toDisplayText, toHttpUrl } from "../utils/formatters";
import s from "../css/InsightDetail.module.css";

// 신뢰도 등급 클래스
function getConfidenceClass(grade) {
    switch (grade) {
        case "HIGH": return s.confidenceHigh;
        case "MEDIUM": return s.confidenceMedium;
        case "LOW": return s.confidenceLow;
        case "VERY_LOW": return s.confidenceVeryLow;
        default: return "";
    }
}

// 값 색상 클래스
function getValueClass(value, positiveThreshold = 0, negativeThreshold = 0) {
    if (value > positiveThreshold) return s.valuePositive;
    if (value < negativeThreshold) return s.valueNegative;
    return s.valueNeutral;
}

// RSI 색상 클래스
function getRsiClass(rsi) {
    if (rsi < 30) return s.valuePositive;
    if (rsi > 70) return s.valueNegative;
    return s.valueNeutral;
}

// MA Cross 색상 클래스
function getMaCrossClass(maCross) {
    if (maCross === "GOLDEN") return s.valuePositive;
    if (maCross === "DEATH") return s.valueNegative;
    return s.valueNeutral;
}

// 공포 레벨 클래스
function getFearLevelClass(level) {
    if (level === "HIGH") return s.valueNegative;
    if (level === "LOW") return s.valuePositive;
    return s.valueWarning;
}

// 추천 박스 클래스
function getRecommendationClass(score) {
    if (score >= 60) return `${s.recommendationBox} ${s.recommendationBuy}`;
    if (score >= 45) return `${s.recommendationBox} ${s.recommendationHold}`;
    return `${s.recommendationBox} ${s.recommendationSell}`;
}

// 점수 색상 클래스
function getScoreClass(score) {
    if (score >= 70) return s.valuePositive;
    if (score >= 40) return s.valueWarning;
    return s.valueNegative;
}

// 센티먼트 배지 클래스
function getSentimentBadgeClass(label) {
    if (label === "positive") return `${s.sentimentBadge} ${s.sentimentPositive}`;
    if (label === "negative") return `${s.sentimentBadge} ${s.sentimentNegative}`;
    return `${s.sentimentBadge} ${s.sentimentNeutral}`;
}

// 추세 강도 표시
function getTrendIcon(strength) {
    switch (strength) {
        case "STRONG_UP": return "📈";
        case "WEAK_UP": return "↗️";
        case "STRONG_DOWN": return "📉";
        case "WEAK_DOWN": return "↘️";
        case "MIXED": return "↔️";
        default: return "—";
    }
}

const InsightsPanel = memo(function InsightsPanel({ insights, market = "US" }) {
    if (!insights) return null;

    const rec = insights?.recommendation;
    const quote = insights?.quote;
    const fund = insights?.fundamentals;
    const news = insights?.news;
    const tech = insights?.technicals;
    const marketEnv = insights?.marketEnv;
    const multiMomentum = insights?.multiMomentum;
    const obvData = insights?.obv;

    const breakdown = rec?.breakdown || insights?.breakdown || [];
    const points = insights?.prices?.points || [];

    const hasBreakdownTable = Array.isArray(breakdown) && breakdown.length > 0 && breakdown[0] && typeof breakdown[0] === "object" && "metric" in breakdown[0];

    return (
        <div className={s.insightsSection}>
            <h3 className={s.sectionTitle}>Insights</h3>

            {/* Quote + Chart */}
            <div className={s.small}>
                {quote?.latestTradingDay ? `latest: ${quote.latestTradingDay}` : null}
            </div>

            <div className={s.priceLine}>
                <b>Price</b>: {quote?.price != null ? `$${Number(quote.price).toFixed(2)}` : "N/A"}{" "}
                <span className={s.small}>
                    (change: {quote?.change != null ? Number(quote.change).toFixed(2) : "N/A"} / {quote?.changePercent ?? "N/A"})
                </span>
            </div>

            <div className={s.chartWrap}>
                <LineChartCanvas points={points} valueKey="close" labelKey="date" height={220} />
            </div>

            {/* Technicals */}
            {tech ? (
                <div className={s.sectionBox}>
                    <h4 className={s.sectionTitle}>Technicals</h4>

                    {/* 기본 지표 */}
                    <div className={s.indicatorGrid}>
                        <div className={s.indicatorItem}>
                            <span className={s.small}>RSI14</span><br/>
                            <b className={getRsiClass(tech.rsi14)}>
                                {tech.rsi14?.toFixed?.(1) ?? "N/A"}
                            </b>
                            {tech.rsi14 < 30 && <span className={s.small}> (과매도)</span>}
                            {tech.rsi14 > 70 && <span className={s.small}> (과매수)</span>}
                        </div>
                        <div className={s.indicatorItem}>
                            <span className={s.small}>52W 위치</span><br/>
                            <b>{tech.week52Position != null ? `${(tech.week52Position * 100).toFixed(0)}%` : "N/A"}</b>
                        </div>
                        <div className={s.indicatorItem}>
                            <span className={s.small}>MA Cross</span><br/>
                            <b className={getMaCrossClass(tech.maCross)}>
                                {tech.maCross === "GOLDEN" ? "골든크로스" : tech.maCross === "DEATH" ? "데드크로스" : "N/A"}
                            </b>
                        </div>
                    </div>

                    {/* 이동평균 */}
                    <div className={s.indicatorGrid}>
                        <div>SMA20: {num(tech.sma20)}</div>
                        <div>MA50: {num(tech.ma50)}</div>
                        <div>MA200: {num(tech.ma200)}</div>
                    </div>

                    {/* 52주 범위 */}
                    <div className={s.indicatorGrid2}>
                        <div>52W High: {num(tech.week52High)}</div>
                        <div>52W Low: {num(tech.week52Low)}</div>
                    </div>

                    {/* MACD */}
                    {tech.macd && (
                        <div className={s.dividerDashed}>
                            <div className={s.sectionSubtitle}>MACD (12/26/9)</div>
                            <div className={s.indicatorGrid}>
                                <div>MACD: {num(tech.macd.macd, 4)}</div>
                                <div>Signal: {num(tech.macd.signal, 4)}</div>
                                <div>
                                    Hist: <span className={getValueClass(tech.macd.hist)}>
                                        {num(tech.macd.hist, 4)}
                                    </span>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Bollinger Bands */}
                    {tech.bollinger && (
                        <div className={s.dividerDashed}>
                            <div className={s.sectionSubtitle}>Bollinger Bands (20)</div>
                            <div className={s.indicatorGrid4}>
                                <div>Upper: {num(tech.bollinger.upper)}</div>
                                <div>Middle: {num(tech.bollinger.middle)}</div>
                                <div>Lower: {num(tech.bollinger.lower)}</div>
                                <div>Position: {tech.bollinger.position != null ? num(tech.bollinger.position, 2) : "N/A"}</div>
                            </div>
                        </div>
                    )}

                    {/* 변동성 */}
                    <div className={s.dividerDashed}>
                        <div className={s.indicatorGrid2}>
                            <div>20일 변동성: {tech.volatility20d != null ? pct(tech.volatility20d) : "N/A"}</div>
                            <div>연환산 변동성: {tech.volatility20dAnnual != null ? pct(tech.volatility20dAnnual) : "N/A"}</div>
                        </div>
                    </div>
                </div>
            ) : null}

            {/* Multi-Timeframe Momentum */}
            {multiMomentum && (
                <div className={s.sectionBoxInfo}>
                    <h4 className={s.sectionTitle}>
                        <span className={s.statusText}>
                            다중 시간대 모멘텀
                            <span className={s.statusIcon}>{getTrendIcon(multiMomentum.trend_strength)}</span>
                        </span>
                    </h4>
                    <div className={s.indicatorGrid4}>
                        <div className={s.indicatorItem}>
                            <span className={s.small}>20일</span><br/>
                            <b className={getValueClass(multiMomentum.mom20)}>
                                {multiMomentum.mom20 != null ? pct(multiMomentum.mom20) : "N/A"}
                            </b>
                        </div>
                        <div className={s.indicatorItem}>
                            <span className={s.small}>60일</span><br/>
                            <b className={getValueClass(multiMomentum.mom60)}>
                                {multiMomentum.mom60 != null ? pct(multiMomentum.mom60) : "N/A"}
                            </b>
                        </div>
                        <div className={s.indicatorItem}>
                            <span className={s.small}>120일</span><br/>
                            <b className={getValueClass(multiMomentum.mom120)}>
                                {multiMomentum.mom120 != null ? pct(multiMomentum.mom120) : "N/A"}
                            </b>
                        </div>
                        <div className={s.indicatorItem}>
                            <span className={s.small}>200일</span><br/>
                            <b className={getValueClass(multiMomentum.mom200)}>
                                {multiMomentum.mom200 != null ? pct(multiMomentum.mom200) : "N/A"}
                            </b>
                        </div>
                    </div>
                    <div className={s.metaRow}>
                        <span className={s.small}>추세 강도: </span>
                        <b>{multiMomentum.trend_strength ?? "N/A"}</b>
                        {multiMomentum.composite != null && (
                            <span className={s.small}> · 복합 모멘텀: {pct(multiMomentum.composite)}</span>
                        )}
                    </div>
                </div>
            )}

            {/* OBV (거래량 분석) */}
            {obvData && obvData.obvTrend && (
                <div className={s.sectionBox}>
                    <h4 className={s.sectionTitle}>거래량 분석 (OBV)</h4>
                    <div className={s.indicatorGrid}>
                        <div>
                            추세: <b className={getValueClass(
                                obvData.obvTrend === "RISING" ? 1 : obvData.obvTrend === "FALLING" ? -1 : 0
                            )}>
                                {obvData.obvTrend === "RISING" ? "상승 (유입)" : obvData.obvTrend === "FALLING" ? "하락 (유출)" : "중립"}
                            </b>
                        </div>
                        <div>모멘텀: {obvData.obvMomentum != null ? pct(obvData.obvMomentum) : "N/A"}</div>
                        <div>기준일: {obvData.asOf ?? "N/A"}</div>
                    </div>
                </div>
            )}

            {/* Market Environment */}
            {marketEnv && (
                <div className={s.sectionBoxWarning}>
                    <h4 className={s.sectionTitle}>시장 환경</h4>
                    <div className={s.indicatorGrid}>
                        <div className={s.indicatorItem}>
                            <span className={s.small}>10Y 국채</span><br/>
                            <b>{marketEnv.treasuryYield10Y != null ? `${marketEnv.treasuryYield10Y}%` : "N/A"}</b>
                        </div>
                        <div className={s.indicatorItem}>
                            <span className={s.small}>S&P500 변동성</span><br/>
                            <b>{marketEnv.spyVolatility20d != null ? pct(marketEnv.spyVolatility20d) : "N/A"}</b>
                        </div>
                        <div className={s.indicatorItem}>
                            <span className={s.small}>공포 수준</span><br/>
                            <b className={getFearLevelClass(marketEnv.fearLevel)}>
                                {marketEnv.fearLevel === "HIGH" ? "높음 (공포)" :
                                 marketEnv.fearLevel === "LOW" ? "낮음 (탐욕)" :
                                 marketEnv.fearLevel === "MODERATE" ? "보통" : "N/A"}
                            </b>
                        </div>
                    </div>
                </div>
            )}

            {/* Recommendation */}
            <div className={s.insightsSection}>
                <h4 className={s.sectionTitle}>Recommendation</h4>

                <div className={getRecommendationClass(rec?.score)}>
                    <div className={s.recommendationAction}>
                        {toDisplayText(rec?.actionKr ?? rec?.action, "N/A")}
                    </div>
                    <div className={s.recommendationScore}>
                        점수: <b>{rec?.score ?? "N/A"}</b>/100
                        {" · "}신뢰도: {rec?.confidence ? `${(rec.confidence * 100).toFixed(0)}%` : "N/A"}
                        {rec?.confidenceGrade && (
                            <span className={`${s.confidenceBadge} ${getConfidenceClass(rec.confidenceGrade)}`}>
                                {rec.confidenceGrade}
                            </span>
                        )}
                        {rec?.dataCompleteness ? ` · 데이터 완성도: ${(rec.dataCompleteness * 100).toFixed(0)}%` : ""}
                    </div>
                </div>

                <div className={s.recommendationText}>{toDisplayText(rec?.text, "")}</div>

                {Array.isArray(rec?.reasons) && rec.reasons.length > 0 ? (
                    <div className={s.insightsSection}>
                        <div className={s.sectionSubtitle}>분석 근거:</div>
                        <ul className={s.reasonsList}>
                            {rec.reasons.map((r, idx) => (
                                <li key={idx}>{toDisplayText(r, "N/A")}</li>
                            ))}
                        </ul>
                    </div>
                ) : null}
            </div>

            {/* Score Breakdown */}
            {Array.isArray(breakdown) && breakdown.length > 0 ? (
                <div className={s.insightsSection}>
                    <h4 className={s.sectionTitle}>Score Breakdown</h4>

                    {hasBreakdownTable ? (
                        <div className={s.tableWrap}>
                            <table className={s.table}>
                                <thead>
                                    <tr>
                                        <th>지표</th>
                                        <th>값</th>
                                        <th>가중치</th>
                                        <th>원점수</th>
                                        <th>기여</th>
                                        <th>기준</th>
                                    </tr>
                                </thead>

                                <tbody>
                                    {breakdown.map((b, i) => {
                                    const v =
                                        b.valueType === "pct" ? pct(b.value) :
                                        b.valueType === "number" ? num(b.value) :
                                        (b.value ?? "N/A");

                                    const rawScore = b.rawScore != null ? num(b.rawScore, 0) : null;
                                    const contribution = b.contribution != null ? num(b.contribution, 1) : null;
                                    const points = b.points;

                                    return (
                                        <tr key={i}>
                                            <td><b>{b.metric}</b></td>
                                            <td>{v}</td>
                                            <td>{typeof b.weight === "number" ? `${(b.weight * 100).toFixed(0)}%` : (b.weight ?? "—")}</td>
                                            <td className={getScoreClass(rawScore)}>
                                                {rawScore ?? points ?? "—"}
                                            </td>
                                            <td>{contribution ?? "—"}</td>
                                            <td className={s.small}>{b.rule ?? ""}</td>
                                        </tr>
                                    );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    ) : (
                        <ul className={s.list}>
                            {breakdown.map((b, i) => (
                                <li key={i}>{typeof b === "string" ? b : JSON.stringify(b)}</li>
                            ))}
                        </ul>
                    )}

                    <div className={s.metaText}>
                        총점: {rec?.score ?? "N/A"}/100 (가중 평균)
                    </div>
                </div>
            ) : null}

            {/* Fundamentals */}
            <div className={s.insightsSection}>
                <h4 className={s.sectionTitle}>Fundamentals</h4>

                <div className={s.small}>
                    sector: {fund?.sector ?? "N/A"} / industry: {fund?.industry ?? "N/A"}
                </div>

                {/* 밸류에이션 */}
                <div className={s.dividerDashed}>
                    <div className={s.sectionSubtitle}>밸류에이션</div>
                    <div className={s.indicatorGrid4}>
                        <div>PER: {num(fund?.pe, 1)}</div>
                        <div>PSR: {num(fund?.ps, 1)}</div>
                        <div>PBR: {num(fund?.pb, 1)}</div>
                        <div>EV/EBITDA: {num(fund?.evToEbitda, 1)}</div>
                    </div>
                    <div className={s.indicatorGrid2}>
                        <div>MarketCap: {fund?.marketCap ? `$${(fund.marketCap / 1e9).toFixed(1)}B` : "N/A"}</div>
                        <div>배당률: {fund?.dividendYield != null ? pct(fund.dividendYield) : "N/A"}</div>
                    </div>
                </div>

                {/* 수익성 지표 */}
                <div className={s.dividerDashed}>
                    <div className={s.sectionSubtitle}>수익성 지표</div>
                    <div className={s.indicatorGrid4}>
                        <div>ROE: {fund?.roe != null ? pct(fund.roe) : "N/A"}</div>
                        <div>ROA: {fund?.roa != null ? pct(fund.roa) : "N/A"}</div>
                        <div>영업이익률: {fund?.operatingMargin != null ? pct(fund.operatingMargin) : "N/A"}</div>
                        <div>순이익률: {fund?.profitMargin != null ? pct(fund.profitMargin) : "N/A"}</div>
                    </div>
                </div>

                {/* 성장 지표 */}
                <div className={s.dividerDashed}>
                    <div className={s.sectionSubtitle}>성장 지표</div>
                    <div className={s.indicatorGrid4}>
                        <div>Revenue YoY: {fund?.revYoY != null ? pct(fund.revYoY) : "N/A"}</div>
                        <div>분기 매출: {fund?.quarterlyRevenueGrowth != null ? pct(fund.quarterlyRevenueGrowth) : "N/A"}</div>
                        <div>분기 이익: {fund?.quarterlyEarningsGrowth != null ? pct(fund.quarterlyEarningsGrowth) : "N/A"}</div>
                        <div>Beta: {num(fund?.beta, 2)}</div>
                    </div>
                </div>
            </div>

            {/* News */}
            <div className={s.insightsSection}>
                <h4 className={s.sectionTitle}>News</h4>

                <div>
                    positiveRatio:{" "}
                    <b className={getValueClass(news?.positiveRatio, 0.6, 0.4)}>
                        {news?.positiveRatio != null ? `${Math.round(news.positiveRatio * 100)}%` : "N/A"}
                    </b>
                </div>

                {Array.isArray(news?.items) && news.items.length > 0 ? (
                    <ul className={s.newsList}>
                        {news.items.map((it, idx) => {
                            const title = toDisplayText(it?.title, "untitled");
                            const url = toHttpUrl(it?.url) || toHttpUrl(it?.source?.url);
                            const sentiment = it?.sentiment;

                            const searchUrl =
                                market === "KR"
                                ? `https://search.naver.com/search.naver?query=${encodeURIComponent(title)}`
                                : `https://www.google.com/search?q=${encodeURIComponent(title)}`;

                            const href = url || searchUrl;

                            return (
                                <li key={idx} className={s.newsItem}>
                                    <div className={s.newsTop}>
                                        <a href={href} target="_blank" rel="noreferrer noopener" className={s.newsLink}>
                                            {title}
                                        </a>
                                        {sentiment?.label && (
                                            <span className={getSentimentBadgeClass(sentiment.label)}>
                                                {sentiment.label}
                                            </span>
                                        )}
                                    </div>
                                    {it?.source && (
                                        <div className={s.newsMeta}>
                                            <span>{toDisplayText(it.source, "N/A")}</span>
                                        </div>
                                    )}
                                </li>
                            );
                        })}
                    </ul>
                ) : Array.isArray(news?.headlines) && news.headlines.length > 0 ? (
                    <ul className={s.list}>
                        {news.headlines.map((h, idx) => <li key={idx}>{toDisplayText(h, "untitled")}</li>)}
                    </ul>
                ) : (
                    <div className={s.metaText}>N/A</div>
                )}
            </div>
        </div>
    );
});

export default InsightsPanel;
