import React, { useEffect, useRef, useState, useCallback } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getToken } from "../auth/token";
import styles from "../css/Landing.module.css";

/* ── Intersection Observer 기반 스크롤 애니메이션 훅 ── */
function useScrollReveal() {
  const ref = useRef(null);
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
          observer.unobserve(el);
        }
      },
      { threshold: 0.15, rootMargin: "0px 0px -60px 0px" }
    );

    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return [ref, isVisible];
}

/* ── 숫자 카운트업 애니메이션 ── */
function CountUp({ end, suffix = "", duration = 2000, isVisible }) {
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (!isVisible) return;
    let start = 0;
    const step = end / (duration / 16);
    const timer = setInterval(() => {
      start += step;
      if (start >= end) {
        setCount(end);
        clearInterval(timer);
      } else {
        setCount(Math.floor(start));
      }
    }, 16);
    return () => clearInterval(timer);
  }, [isVisible, end, duration]);

  return <>{count.toLocaleString()}{suffix}</>;
}

/* ── 피쳐 카드 ── */
function FeatureSection({ icon, label, title, desc, items, index, visual }) {
  const [ref, isVisible] = useScrollReveal();
  const isEven = index % 2 === 0;

  return (
    <section
      ref={ref}
      className={`${styles.featureSection} ${isVisible ? styles.revealed : ""} ${isEven ? "" : styles.featureReverse}`}
    >
      <div className={styles.featureContent}>
        <span className={styles.featureLabel}>{label}</span>
        <h2 className={styles.featureTitle}>{title}</h2>
        <p className={styles.featureDesc}>{desc}</p>
        {items && (
          <ul className={styles.featureList}>
            {items.map((item, i) => (
              <li key={i} className={styles.featureItem}>
                <span className={styles.featureCheck}>&#10003;</span>
                {item}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className={styles.featureVisual}>
        <div className={styles.featureCard}>
          <div className={styles.featureIcon}>{icon}</div>
          {visual}
        </div>
      </div>
    </section>
  );
}

/* ── 메인 Landing 페이지 ── */
export default function Landing() {
  const nav = useNavigate();
  const loggedIn = !!getToken();
  const [scrollY, setScrollY] = useState(0);
  const [statsRef, statsVisible] = useScrollReveal();
  const heroRef = useRef(null);

  // 로그인 상태면 대시보드로
  useEffect(() => {
    if (loggedIn) nav("/dashboard", { replace: true });
  }, [loggedIn, nav]);

  // 스크롤 이벤트 (패럴랙스용)
  const handleScroll = useCallback(() => {
    setScrollY(window.scrollY);
  }, []);

  useEffect(() => {
    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => window.removeEventListener("scroll", handleScroll);
  }, [handleScroll]);

  // 스크롤 다운 화살표 클릭
  const scrollToFeatures = () => {
    const el = document.getElementById("features");
    if (el) el.scrollIntoView({ behavior: "smooth" });
  };

  /* ── 각 피처 섹션의 시각적 데모 ── */
  const analysisVisual = (
    <div className={styles.mockScreen}>
      <div className={styles.mockHeader}>
        <span className={styles.mockDot} style={{background: "#ef4444"}} />
        <span className={styles.mockDot} style={{background: "#f59e0b"}} />
        <span className={styles.mockDot} style={{background: "#22c55e"}} />
      </div>
      <div className={styles.mockBody}>
        <div className={styles.mockTicker}>AAPL</div>
        <div className={styles.mockPrice}>$198.42</div>
        <div className={styles.mockChange}>+2.34 (+1.19%)</div>
        <div className={styles.mockBar}><div className={styles.mockBarFill} style={{width: "78%"}} /></div>
        <div className={styles.mockLabel}>AI Score: 78/100</div>
        <div className={styles.mockTag}>Strong Buy</div>
      </div>
    </div>
  );

  const portfolioVisual = (
    <div className={styles.mockScreen}>
      <div className={styles.mockHeader}>
        <span className={styles.mockDot} style={{background: "#ef4444"}} />
        <span className={styles.mockDot} style={{background: "#f59e0b"}} />
        <span className={styles.mockDot} style={{background: "#22c55e"}} />
      </div>
      <div className={styles.mockBody}>
        <div className={styles.mockLabel} style={{fontSize: "11px", opacity: 0.6}}>Total Value</div>
        <div className={styles.mockPrice}>$142,580</div>
        <div className={styles.mockChange}>+$3,240 Today</div>
        <div className={styles.mockPie}>
          <svg viewBox="0 0 100 100" className={styles.mockPieSvg}>
            <circle cx="50" cy="50" r="45" fill="none" stroke="var(--color-primary)" strokeWidth="10" strokeDasharray="85 200" strokeDashoffset="0" />
            <circle cx="50" cy="50" r="45" fill="none" stroke="var(--color-success)" strokeWidth="10" strokeDasharray="60 200" strokeDashoffset="-85" />
            <circle cx="50" cy="50" r="45" fill="none" stroke="var(--color-warning)" strokeWidth="10" strokeDasharray="45 200" strokeDashoffset="-145" />
            <circle cx="50" cy="50" r="45" fill="none" stroke="var(--color-danger)" strokeWidth="10" strokeDasharray="35 200" strokeDashoffset="-190" />
          </svg>
        </div>
      </div>
    </div>
  );

  const correlationVisual = (
    <div className={styles.mockScreen}>
      <div className={styles.mockHeader}>
        <span className={styles.mockDot} style={{background: "#ef4444"}} />
        <span className={styles.mockDot} style={{background: "#f59e0b"}} />
        <span className={styles.mockDot} style={{background: "#22c55e"}} />
      </div>
      <div className={styles.mockBody}>
        <div className={styles.mockLabel} style={{fontSize: "11px", opacity: 0.6}}>Monte Carlo Simulation</div>
        <div className={styles.mockChart}>
          {[...Array(8)].map((_, i) => (
            <div key={i} className={styles.mockPath} style={{
              "--path-delay": `${i * 0.15}s`,
              "--path-height": `${30 + Math.random() * 50}%`,
              "--path-color": `hsl(${210 + i * 15}, 80%, ${50 + i * 4}%)`
            }} />
          ))}
        </div>
        <div style={{display:"flex", gap:"12px", marginTop:"8px"}}>
          <div className={styles.mockStatBox}>
            <div className={styles.mockStatLabel}>VaR 95%</div>
            <div className={styles.mockStatValue}>-$12.4</div>
          </div>
          <div className={styles.mockStatBox}>
            <div className={styles.mockStatLabel}>Expected</div>
            <div className={styles.mockStatValue}>+$8.2</div>
          </div>
        </div>
      </div>
    </div>
  );

  const chatVisual = (
    <div className={styles.mockScreen}>
      <div className={styles.mockHeader}>
        <span className={styles.mockDot} style={{background: "#ef4444"}} />
        <span className={styles.mockDot} style={{background: "#f59e0b"}} />
        <span className={styles.mockDot} style={{background: "#22c55e"}} />
      </div>
      <div className={styles.mockBody}>
        <div className={styles.mockChat}>
          <div className={styles.mockBubbleUser}>AAPL 매수 적기인가요?</div>
          <div className={styles.mockBubbleBot}>
            현재 AAPL은 RSI 45로 중립 구간이며, PER 28.5배로 적정 수준입니다.
            52주 고점 대비 -8% 하락한 상태로...
          </div>
          <div className={styles.mockTyping}>
            <span /><span /><span />
          </div>
        </div>
      </div>
    </div>
  );

  if (loggedIn) return null;

  return (
    <div className={styles.page}>
      {/* ── 상단 네비게이션 ── */}
      <nav className={`${styles.nav} ${scrollY > 40 ? styles.navScrolled : ""}`}>
        <div className={styles.navInner}>
          <div className={styles.logo}>Stock-AI</div>
          <div className={styles.navLinks}>
            <a href="#features" className={styles.navLink}>Features</a>
            <a href="#stats" className={styles.navLink}>Stats</a>
            <Link to="/login" className={styles.navBtn}>Login</Link>
            <Link to="/register" className={styles.navBtnPrimary}>Get Started</Link>
          </div>
        </div>
      </nav>

      {/* ── HERO 섹션 ── */}
      <section className={styles.hero} ref={heroRef}>
        {/* 패럴랙스 배경 */}
        <div
          className={styles.heroBg}
          style={{ transform: `translateY(${scrollY * 0.3}px)` }}
        />

        {/* 플로팅 오브 장식 */}
        <div className={styles.heroOrbs}>
          <div className={`${styles.orb} ${styles.orb1}`} />
          <div className={`${styles.orb} ${styles.orb2}`} />
          <div className={`${styles.orb} ${styles.orb3}`} />
        </div>

        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>
            AI-Powered Stock Analysis
          </div>

          <h1 className={styles.heroTitle}>
            <span className={styles.heroLine1}>
              주식 투자의 모든 것,
            </span>
            <span className={styles.heroLine2}>
              AI가 함께합니다
            </span>
          </h1>

          <p className={styles.heroSub}>
            실시간 AI 분석, 포트폴리오 관리, 리스크 시뮬레이션까지.
            <br />
            데이터 기반의 스마트한 투자 의사결정을 경험하세요.
          </p>

          <div className={styles.heroCta}>
            <Link to="/register" className={styles.ctaPrimary}>
              무료로 시작하기
            </Link>
            <a href="#features" className={styles.ctaSecondary}>
              자세히 알아보기
            </a>
          </div>

          {/* 히어로 하단 장식 라인 */}
          <div className={styles.heroTickers}>
            <div className={styles.tickerStrip}>
              {["AAPL +1.2%", "MSFT +0.8%", "GOOGL -0.3%", "TSLA +2.1%", "AMZN +0.5%", "NVDA +3.4%", "META -0.7%", "NFLX +1.8%"].map((t, i) => (
                <span key={i} className={styles.tickerItem}>
                  {t}
                </span>
              ))}
            </div>
          </div>
        </div>

        {/* 스크롤 다운 인디케이터 */}
        <button className={styles.scrollDown} onClick={scrollToFeatures} aria-label="Scroll to features">
          <div className={styles.scrollArrow} />
        </button>
      </section>

      {/* ── STATS 섹션 ── */}
      <section id="stats" className={styles.statsSection} ref={statsRef}>
        <div className={`${styles.statsInner} ${statsVisible ? styles.revealed : ""}`}>
          <div className={styles.statCard}>
            <div className={styles.statNum}>
              <CountUp end={12} suffix="+" isVisible={statsVisible} />
            </div>
            <div className={styles.statLabel}>AI 분석 지표</div>
          </div>
          <div className={styles.statCard}>
            <div className={styles.statNum}>
              <CountUp end={1000} suffix="+" isVisible={statsVisible} />
            </div>
            <div className={styles.statLabel}>Monte Carlo 시뮬레이션</div>
          </div>
          <div className={styles.statCard}>
            <div className={styles.statNum}>
              <CountUp end={50} suffix="+" isVisible={statsVisible} />
            </div>
            <div className={styles.statLabel}>스크리닝 필터</div>
          </div>
          <div className={styles.statCard}>
            <div className={styles.statNum}>
              <CountUp end={2} suffix="" isVisible={statsVisible} />
            </div>
            <div className={styles.statLabel}>Markets (US/KR)</div>
          </div>
        </div>
      </section>

      {/* ── FEATURES 섹션들 ── */}
      <div id="features">
        <FeatureSection
          index={0}
          icon={
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="1.5">
              <path d="M12 2L2 7l10 5 10-5-10-5z" />
              <path d="M2 17l10 5 10-5" />
              <path d="M2 12l10 5 10-5" />
            </svg>
          }
          label="AI Analysis"
          title={"AI가 분석하는\n종합 주식 인사이트"}
          desc="기술적 분석, 펀더멘탈 분석, 뉴스 감성 분석을 AI가 통합하여 투자 점수와 추천을 제공합니다."
          items={[
            "RSI, MACD, OBV 등 12가지 기술적 지표",
            "PER, PBR, ROE 기반 밸류에이션 분석",
            "뉴스 감성 분석으로 시장 분위기 파악",
            "AI 기반 투자 추천 점수 (0~100)"
          ]}
          visual={analysisVisual}
        />

        <FeatureSection
          index={1}
          icon={
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--color-success)" strokeWidth="1.5">
              <rect x="2" y="3" width="20" height="14" rx="2" />
              <path d="M8 21h8" />
              <path d="M12 17v4" />
              <path d="M7 10l3-3 2 2 5-5" />
            </svg>
          }
          label="Portfolio"
          title={"나만의 포트폴리오를\n한눈에 관리"}
          desc="실시간 시세 연동, 섹터별 자산 배분, 일/주/월별 수익률 추적으로 포트폴리오를 체계적으로 관리하세요."
          items={[
            "실시간 시세 연동 및 수익률 계산",
            "섹터별, 마켓별 자산 배분 분석",
            "일별/주별/월별 수익률 변화 추적",
            "배당금 캘린더 및 수입 추적"
          ]}
          visual={portfolioVisual}
        />

        <FeatureSection
          index={2}
          icon={
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--color-warning)" strokeWidth="1.5">
              <path d="M3 3v18h18" />
              <path d="M7 16l4-8 4 4 6-10" />
            </svg>
          }
          label="Risk Analysis"
          title={"Monte Carlo 시뮬레이션으로\n리스크를 예측합니다"}
          desc="1,000회 이상의 시뮬레이션으로 미래 주가를 확률적으로 예측하고, VaR로 최대 손실을 정량화합니다."
          items={[
            "Geometric Brownian Motion 기반 시뮬레이션",
            "VaR 95% / CVaR 리스크 지표",
            "상관관계 히트맵과 Beta 분석",
            "Max Drawdown 계산"
          ]}
          visual={correlationVisual}
        />

        <FeatureSection
          index={3}
          icon={
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="1.5">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
              <path d="M8 9h8" />
              <path d="M8 13h4" />
            </svg>
          }
          label="AI Chatbot"
          title={"AI 금융 어시스턴트에게\n무엇이든 물어보세요"}
          desc="Claude AI가 주식 분석, 투자 전략, 시장 동향에 대해 전문적인 답변을 제공합니다."
          items={[
            "실시간 대화형 주식 분석",
            "포트폴리오 최적화 조언",
            "투자 전략 및 리스크 관리 상담",
            "Opus / Sonnet / Haiku 모델 선택"
          ]}
          visual={chatVisual}
        />
      </div>

      {/* ── MORE FEATURES 그리드 ── */}
      <MoreFeaturesGrid />

      {/* ── CTA 섹션 ── */}
      <section className={styles.ctaSection}>
        <div className={styles.ctaSectionInner}>
          <h2 className={styles.ctaTitle}>
            지금 시작하세요
          </h2>
          <p className={styles.ctaDesc}>
            AI 기반 주식 분석의 새로운 기준을 경험하세요.
          </p>
          <Link to="/register" className={styles.ctaButton}>
            무료 계정 만들기
          </Link>
        </div>
      </section>

      {/* ── FOOTER ── */}
      <footer className={styles.footer}>
        <div className={styles.footerInner}>
          <div className={styles.footerBrand}>
            <div className={styles.footerLogo}>Stock-AI</div>
            <p className={styles.footerDesc}>
              AI-powered stock market analysis platform.
              <br />
              데이터 기반 투자 의사결정을 위한 종합 솔루션.
            </p>
          </div>
          <div className={styles.footerLinks}>
            <div className={styles.footerCol}>
              <h4>Product</h4>
              <Link to="/login">AI Analysis</Link>
              <Link to="/login">Portfolio</Link>
              <Link to="/login">Screener</Link>
            </div>
            <div className={styles.footerCol}>
              <h4>Resources</h4>
              <Link to="/glossary">Glossary</Link>
              <Link to="/learn">Learn</Link>
            </div>
          </div>
        </div>
        <div className={styles.footerBottom}>
          <span>&copy; 2026 Stock-AI. All rights reserved.</span>
        </div>
      </footer>
    </div>
  );
}

/* ── 추가 피처 그리드 컴포넌트 ── */
function MoreFeaturesGrid() {
  const [ref, isVisible] = useScrollReveal();

  const features = [
    { icon: "&#128200;", title: "백테스팅", desc: "과거 데이터로 투자 전략을 검증" },
    { icon: "&#128269;", title: "종목 스크리너", desc: "PER, ROE 등 50+ 필터로 종목 검색" },
    { icon: "&#128176;", title: "배당 추적", desc: "배당금 캘린더와 수입 예측" },
    { icon: "&#128202;", title: "실시간 시세", desc: "WebSocket 기반 실시간 가격 스트리밍" },
    { icon: "&#128196;", title: "PDF 리포트", desc: "전문적인 분석 리포트 내보내기" },
    { icon: "&#128274;", title: "2단계 인증", desc: "Google Authenticator 보안 강화" },
  ];

  return (
    <section ref={ref} className={`${styles.moreSection} ${isVisible ? styles.revealed : ""}`}>
      <h2 className={styles.moreSectionTitle}>그 외에도 다양한 기능</h2>
      <div className={styles.moreGrid}>
        {features.map((f, i) => (
          <div
            key={i}
            className={styles.moreCard}
            style={{ transitionDelay: `${i * 0.08}s` }}
          >
            <div className={styles.moreCardIcon} dangerouslySetInnerHTML={{ __html: f.icon }} />
            <h3 className={styles.moreCardTitle}>{f.title}</h3>
            <p className={styles.moreCardDesc}>{f.desc}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
