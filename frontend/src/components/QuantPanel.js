import React, { useMemo } from "react";
import { pct, num } from "../utils/formatters";
import styles from "../css/QuantPanel.module.css";

function actionMeta(action) {
    const a = String(action || "").toUpperCase();
    if (a === "BUY_CANDIDATE") return { label: "매수 후보", cls: "buy" };
    if (a === "SELL_CANDIDATE") return { label: "매도 후보", cls: "sell" };
    return { label: "보류", cls: "hold" };
}

export default function QuantPanel({ quant }) {
    const sig = quant?.signal;
    const ind = quant?.indicators;
    const val = quant?.valuation;
    const meta = quant?.meta;
    const prof = quant?.profitability;
    const vol = quant?.volume;

    const am = useMemo(() => actionMeta(sig?.action), [sig?.action]);

    if (!quant) {
        return (
            <div className={styles.card}>
                <div className={styles.h3}>Quant Signal</div>
                <div className={styles.small}>_quant 데이터가 없습니다. (백엔드 attachQuant 적용 여부 확인)</div>
            </div>
        );
    }

    return (
        <div className={styles.card}>
            <div className={styles.top}>
                <div>
                    <div className={styles.h3}>Quant Signal</div>

                    <div className={styles.small}>
                        규칙 기반(연구용) 신호입니다. 실제 서비스에서는 백테스트/검증(다음 단계)으로 고도화합니다.
                    </div>
                </div>

                <div className={styles.right}>
                    <span className={`${styles.badge} ${styles[`badge_${am.cls}`]}`}>
                        {am.label}
                    </span>

                    <div className={styles.score}>
                        score <b>{sig?.score == null ? "N/A" : num(sig.score, 3)}</b>
                    </div>
                </div>
            </div>

            {/* 이유 */}
            <div className={styles.section}>
                <div className={styles.sectionTitle}>근거</div>

                {Array.isArray(sig?.reasons) && sig.reasons.length > 0 ? (
                    <ul className={styles.reasons}>
                        {sig.reasons.map((r, i) => (
                        <li key={i}>{r}</li>
                        ))}
                    </ul>
                ) : (
                    <div className={styles.small}>근거 데이터가 없습니다.</div>
                )}
            </div>

            {/* 지표 */}
            <div className={styles.section}>
                <div className={styles.sectionTitle}>지표</div>

                <div className={styles.grid}>
                    <div className={styles.kv}>
                        <div className={styles.k}>Last Close</div>
                        <div className={styles.v}>{ind?.lastClose == null ? "N/A" : num(ind.lastClose, 2)}</div>
                    </div>

                    <div className={styles.kv}>
                        <div className={styles.k}>RSI(14)</div>
                        <div className={styles.v}>{ind?.rsi14 == null ? "N/A" : num(ind.rsi14, 2)}</div>
                    </div>

                    <div className={styles.kv}>
                        <div className={styles.k}>SMA(20)</div>
                        <div className={styles.v}>{ind?.sma20 == null ? "N/A" : num(ind.sma20, 2)}</div>
                    </div>

                    <div className={styles.kv}>
                        <div className={styles.k}>SMA(50)</div>
                        <div className={styles.v}>{ind?.sma50 == null ? "N/A" : num(ind.sma50, 2)}</div>
                    </div>

                    <div className={styles.kv}>
                        <div className={styles.k}>SMA(90)</div>
                        <div className={styles.v}>{ind?.sma90 == null ? "N/A" : num(ind.sma90, 2)}</div>
                    </div>

                    <div className={styles.kv}>
                        <div className={styles.k}>Volatility(20d)</div>
                        <div className={styles.v}>{ind?.volatility20d == null ? "N/A" : pct(ind.volatility20d, 2)}</div>
                    </div>

                    <div className={styles.kv}>
                        <div className={styles.k}>Max Drawdown</div>
                        <div className={styles.v}>{ind?.maxDrawdown == null ? "N/A" : pct(ind.maxDrawdown, 2)}</div>
                    </div>
                </div>

                {/* MACD */}
                <div className={styles.subGrid}>
                    <div className={styles.subTitle}>MACD</div>
                    {ind?.macd && ind.macd.macd != null ? (
                        <div className={styles.subRow}>
                            <div className={styles.subItem}>macd: <b>{num(ind.macd.macd, 4)}</b></div>
                            <div className={styles.subItem}>signal: <b>{num(ind.macd.signal, 4)}</b></div>
                            <div className={styles.subItem}>hist: <b>{num(ind.macd.hist, 4)}</b></div>
                        </div>
                    ) : (
                        <div className={styles.small}>MACD 데이터가 부족합니다.</div>
                    )}
                </div>

                {/* Bollinger */}
                <div className={styles.subGrid}>
                    <div className={styles.subTitle}>Bollinger(20)</div>
                    {ind?.bollinger20 && ind.bollinger20.mid != null ? (
                        <div className={styles.subRow}>
                            <div className={styles.subItem}>mid: <b>{num(ind.bollinger20.mid, 2)}</b></div>
                            <div className={styles.subItem}>upper: <b>{num(ind.bollinger20.upper, 2)}</b></div>
                            <div className={styles.subItem}>lower: <b>{num(ind.bollinger20.lower, 2)}</b></div>
                        </div>
                    ) : (
                        <div className={styles.small}>볼린저 데이터가 부족합니다.</div>
                    )}
                </div>
            </div>

            {/* 밸류 */}
            <div className={styles.section}>
                <div className={styles.sectionTitle}>밸류에이션</div>

                <div className={styles.grid}>
                    <div className={styles.kv}>
                        <div className={styles.k}>PE</div>
                        <div className={styles.v}>{val?.pe == null ? "N/A" : num(val.pe, 2)}</div>
                    </div>

                    <div className={styles.kv}>
                        <div className={styles.k}>Revenue YoY</div>
                        <div className={styles.v}>{val?.revYoY == null ? "N/A" : pct(val.revYoY, 2)}</div>
                    </div>

                    <div className={styles.kv}>
                        <div className={styles.k}>pegLike</div>

                        <div className={styles.v}>
                            {val?.pegLike == null ? "N/A" : num(val.pegLike, 3)}
                        </div>
                    </div>
                </div>

                <div className={styles.small}>
                    pegLike = PE / (매출 YoY 성장률%).
                    단순 참고치이며, 섹터/비즈니스 모델에 따라 해석이 크게 달라집니다.
                </div>
            </div>

            {/* 수익성 */}
            <div className={styles.section}>
                <div className={styles.sectionTitle}>수익성</div>

                <div className={styles.grid}>
                    <div className={styles.kv}>
                        <div className={styles.k}>ROE</div>
                        <div className={styles.v}>{prof?.roe == null ? "N/A" : pct(prof.roe, 1)}</div>
                    </div>

                    <div className={styles.kv}>
                        <div className={styles.k}>영업이익률</div>
                        <div className={styles.v}>{prof?.operatingMargin == null ? "N/A" : pct(prof.operatingMargin, 1)}</div>
                    </div>

                    <div className={styles.kv}>
                        <div className={styles.k}>순이익률</div>
                        <div className={styles.v}>{prof?.profitMargin == null ? "N/A" : pct(prof.profitMargin, 1)}</div>
                    </div>
                </div>

                <div className={styles.small}>
                    ROE 15% 이상: 우수, 영업이익률 15% 이상: 높은 수익성
                </div>
            </div>

            {/* 거래량 (OBV) */}
            <div className={styles.section}>
                <div className={styles.sectionTitle}>거래량 추세</div>

                <div className={styles.grid}>
                    <div className={styles.kv}>
                        <div className={styles.k}>OBV 추세</div>
                        <div className={styles.v}>
                            {vol?.obvTrend == null ? "N/A" :
                                vol.obvTrend === "RISING" ? "🔼 상승 (유입)" :
                                vol.obvTrend === "FALLING" ? "🔽 하락 (유출)" : "➡️ 중립"}
                        </div>
                    </div>

                    <div className={styles.kv}>
                        <div className={styles.k}>OBV 모멘텀</div>
                        <div className={styles.v}>{vol?.obvMomentum == null ? "N/A" : pct(vol.obvMomentum, 1)}</div>
                    </div>
                </div>

                <div className={styles.small}>
                    OBV(On-Balance Volume): 거래량 유입/유출 추세. 가격 상승 시 거래량 유입이면 추세 신뢰도 상승.
                </div>
            </div>

            {/* 메타 */}
            <div className={styles.footer}>
                <div className={styles.small}>
                    pricesCount: <b>{meta?.pricesCount ?? "N/A"}</b>
                    {"  "}· RSI14: <b>{meta?.hasEnoughForRsi14 ? "OK" : "NO"}</b>
                    {"  "}· MACD: <b>{meta?.hasEnoughForMacd ? "OK" : "NO"}</b>
                    {"  "}· SMA50: <b>{meta?.hasEnoughForSma50 ? "OK" : "NO"}</b>
                    {"  "}· SMA90: <b>{meta?.hasEnoughForSma90 ? "OK" : "NO"}</b>
                </div>
            </div>
        </div>
    );
}