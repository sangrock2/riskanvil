import { useState } from "react";
import styles from "../css/Learn.module.css";

const categories = {
  basics: {
    title: "투자 기초",
    icon: "📚",
    articles: [
      {
        id: "stocks-101",
        title: "주식이란 무엇인가?",
        description: "주식의 기본 개념과 투자 원리를 이해합니다.",
        content: `
# 주식이란 무엇인가?

주식(Stock)은 기업의 소유권을 나타내는 증서입니다. 주식을 구매하면 그 기업의 일부를 소유하게 되며, 기업의 이익과 손실을 함께 나누게 됩니다.

## 주식의 종류

### 1. 보통주 (Common Stock)
- 가장 일반적인 주식 형태
- 의결권이 있어 경영 참여 가능
- 배당금 수령 (기업 이익 발생 시)

### 2. 우선주 (Preferred Stock)
- 배당금 우선 지급
- 일반적으로 의결권 없음
- 안정적인 배당 수익 추구

## 주가가 변동하는 이유

주가는 다음 요인들에 의해 변동합니다:
- **수요와 공급**: 사려는 사람 > 팔려는 사람 → 가격 상승
- **기업 실적**: 매출, 이익 증가 → 주가 상승
- **경제 상황**: 금리, 환율, 경기 전망
- **시장 심리**: 투자자들의 기대와 우려
        `,
      },
      {
        id: "how-to-start",
        title: "주식 투자 시작하기",
        description: "첫 투자를 위한 단계별 가이드입니다.",
        content: `
# 주식 투자 시작하기

## 1단계: 증권 계좌 개설

국내 증권사를 통해 계좌를 개설합니다:
- 신분증 준비
- 온라인/모바일 앱으로 간편 개설
- 해외 주식 거래 시 해외 주식 계좌도 필요

## 2단계: 투자 목표 설정

명확한 목표를 설정하세요:
- **투자 기간**: 단기 (1년 이하) vs 장기 (5년 이상)
- **목표 수익률**: 현실적인 목표 (연 5-10%)
- **리스크 허용도**: 얼마나 손실을 견딜 수 있는가?

## 3단계: 투자 원칙 정하기

성공적인 투자를 위한 원칙:
- 여유 자금으로만 투자
- 분산 투자 (계란을 한 바구니에 담지 말것)
- 장기적 관점 유지
- 감정적 판단 지양

## 4단계: 종목 선택

기본적 분석과 기술적 분석을 활용:
- **기본적 분석**: 기업의 재무제표, 사업 모델 분석
- **기술적 분석**: 차트, 거래량 패턴 분석
- 관심 있는 산업/기업부터 시작
        `,
      },
    ],
  },
  analysis: {
    title: "분석 방법",
    icon: "📊",
    articles: [
      {
        id: "fundamental-analysis",
        title: "기본적 분석 (Fundamental Analysis)",
        description: "기업의 내재 가치를 평가하는 방법을 배웁니다.",
        content: `
# 기본적 분석

기본적 분석은 기업의 재무 상태, 경영 성과, 산업 전망 등을 분석하여 주식의 내재 가치를 평가하는 방법입니다.

## 주요 재무 지표

### 1. P/E Ratio (주가수익비율)
- 계산: 주가 / 주당순이익(EPS)
- 의미: 주가가 이익의 몇 배로 거래되는가?
- 해석: 낮을수록 저평가, 높을수록 고평가 (업종별 차이 존재)

### 2. P/B Ratio (주가순자산비율)
- 계산: 주가 / 주당순자산(BPS)
- 의미: 회사의 자산 대비 주가 수준
- 해석: 1 미만이면 자산가치보다 싸게 거래 중

### 3. ROE (자기자본이익률)
- 계산: 순이익 / 자기자본 × 100
- 의미: 자본을 얼마나 효율적으로 사용하는가?
- 해석: 높을수록 수익성이 좋음 (15% 이상이 우량)

## 재무제표 읽는 법

### 손익계산서
- **매출액**: 제품/서비스 판매로 얻은 수익
- **영업이익**: 본업에서 발생한 이익
- **순이익**: 모든 비용을 제한 후 최종 이익

### 재무상태표
- **자산**: 기업이 보유한 재산
- **부채**: 갚아야 할 빚
- **자본**: 자산 - 부채 (주주의 몫)
        `,
      },
      {
        id: "technical-analysis",
        title: "기술적 분석 (Technical Analysis)",
        description: "차트와 지표를 활용한 매매 타이밍 포착 방법입니다.",
        content: `
# 기술적 분석

기술적 분석은 과거 가격과 거래량 데이터를 분석하여 미래 가격을 예측하는 방법입니다.

## 주요 기술적 지표

### 1. 이동평균선 (Moving Average)
- **단기 이동평균 (5일, 20일)**: 단기 추세 파악
- **장기 이동평균 (60일, 120일)**: 장기 추세 파악
- **골든크로스**: 단기선이 장기선을 상향 돌파 → 매수 신호
- **데드크로스**: 단기선이 장기선을 하향 돌파 → 매도 신호

### 2. RSI (상대강도지수)
- 범위: 0~100
- **70 이상**: 과매수 상태 (조정 가능성)
- **30 이하**: 과매도 상태 (반등 가능성)

### 3. MACD
- 단기/장기 이동평균의 차이를 이용
- **MACD선이 시그널선 상향 돌파**: 매수 신호
- **MACD선이 시그널선 하향 돌파**: 매도 신호

## 캔들 차트 읽기

- **양봉** (빨간색): 시가 < 종가 (상승)
- **음봉** (파란색): 시가 > 종가 (하락)
- **긴 몸통**: 강한 추세
- **긴 꼬리**: 반전 가능성

## 지지선과 저항선

- **지지선**: 주가가 하락하다 반등하는 가격대
- **저항선**: 주가가 상승하다 막히는 가격대
- 돌파 시 새로운 추세 시작 가능성
        `,
      },
    ],
  },
  strategy: {
    title: "투자 전략",
    icon: "🎯",
    articles: [
      {
        id: "diversification",
        title: "분산 투자 전략",
        description: "리스크를 줄이는 포트폴리오 구성법을 배웁니다.",
        content: `
# 분산 투자 전략

"계란을 한 바구니에 담지 마라" - 투자의 황금률입니다.

## 분산 투자가 필요한 이유

단일 종목에 집중 투자 시:
- 해당 기업의 악재에 전체 자산이 타격
- 산업 전체의 침체 시 회복 불가능
- 심리적 스트레스 증가

분산 투자의 효과:
- **리스크 감소**: 한 종목 하락 시 다른 종목이 상승
- **안정적 수익**: 변동성 완화
- **기회 확대**: 여러 섹터의 성장 기회 포착

## 분산 투자 방법

### 1. 섹터별 분산
- 기술주, 금융주, 헬스케어, 에너지 등
- 각 섹터별로 20-30% 배분

### 2. 지역별 분산
- 국내 주식 50%
- 미국 주식 30%
- 신흥국 주식 20%

### 3. 자산 클래스 분산
- 주식 60%
- 채권 30%
- 현금/금 10%

## 포트폴리오 리밸런싱

정기적으로 (분기/반기) 비율 조정:
- 상승한 자산 일부 매도
- 하락한 자산 추가 매수
- 목표 비율 유지
        `,
      },
      {
        id: "dollar-cost-averaging",
        title: "적립식 투자 (Dollar Cost Averaging)",
        description: "시장 타이밍에 관계없이 꾸준히 투자하는 방법입니다.",
        content: `
# 적립식 투자 (DCA)

매월 일정 금액을 정기적으로 투자하는 전략입니다.

## 적립식 투자의 장점

### 1. 매수 단가 평준화
- 주가 하락 시: 더 많은 주식 매수
- 주가 상승 시: 더 적은 주식 매수
- 결과: 평균 매수 단가 낮아짐

### 2. 심리적 부담 감소
- 시장 타이밍 고민 불필요
- 일시적 하락에 흔들리지 않음
- 장기 투자 습관 형성

### 3. 복리 효과
- 배당금 재투자
- 시간이 지날수록 투자 금액 증가
- 눈덩이 효과

## 적립식 투자 실행 방법

1. **월 투자 금액 결정**
   - 수입의 10-20% 권장
   - 무리하지 않는 범위 내에서

2. **투자 종목/펀드 선택**
   - 지수 추종 ETF (S&P500, KOSPI200)
   - 우량 개별 종목
   - 분산 펀드

3. **자동 이체 설정**
   - 월급날 다음 날 자동 이체
   - 강제 저축 효과

4. **장기 유지**
   - 최소 5년 이상 유지
   - 단기 변동에 신경 쓰지 않기
        `,
      },
    ],
  },
  risk: {
    title: "리스크 관리",
    icon: "⚠️",
    articles: [
      {
        id: "stop-loss",
        title: "손절매와 손실 관리",
        description: "손실을 제한하고 원금을 보호하는 방법입니다.",
        content: `
# 손절매와 손실 관리

투자에서 손실은 불가피합니다. 중요한 것은 손실을 어떻게 관리하느냐입니다.

## 손절매란?

미리 정한 손실 한도에 도달하면 자동으로 매도하는 전략입니다.

### 손절매가 필요한 이유
- **큰 손실 방지**: 작은 손실로 제한
- **심리적 안정**: 미리 정한 규칙대로 실행
- **자금 회전**: 손실 종목에 묶이지 않음

## 손절매 기준 설정

### 1. 비율 기준
- 매수가 대비 -5%: 공격적 투자자
- 매수가 대비 -10%: 일반 투자자
- 매수가 대비 -15%: 보수적 투자자

### 2. 기술적 기준
- 주요 지지선 이탈
- 이동평균선 하향 돌파
- 추세선 이탈

### 3. 시간 기준
- 일정 기간 (3개월) 내 목표 미달성
- 투자 논리가 무너졌을 때

## 손절매 실행 시 주의사항

- **감정 배제**: 미련 없이 실행
- **재진입 가능**: 상황 개선 시 다시 매수 가능
- **학습 기회**: 왜 손실이 발생했는지 분석

## 2% 룰

- 한 종목의 손실을 총 자산의 2% 이내로 제한
- 예: 1,000만원 → 한 종목 최대 손실 20만원
- 리스크 관리의 핵심 원칙
        `,
      },
    ],
  },
};

export default function Learn() {
  const [selectedCategory, setSelectedCategory] = useState("basics");
  const [selectedArticle, setSelectedArticle] = useState(null);

  const currentCategory = categories[selectedCategory];
  const article = selectedArticle
    ? currentCategory.articles.find((a) => a.id === selectedArticle)
    : null;

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1 className={styles.title}>학습 자료</h1>
        <p className={styles.subtitle}>
          주식 투자의 기초부터 고급 전략까지 체계적으로 학습하세요
        </p>
      </div>

      <div className={styles.layout}>
        {/* Sidebar */}
        <aside className={styles.sidebar}>
          <nav className={styles.nav}>
            {Object.entries(categories).map(([key, cat]) => (
              <button
                key={key}
                className={
                  selectedCategory === key
                    ? styles.navItemActive
                    : styles.navItem
                }
                onClick={() => {
                  setSelectedCategory(key);
                  setSelectedArticle(null);
                }}
              >
                <span className={styles.navIcon}>{cat.icon}</span>
                <span className={styles.navText}>{cat.title}</span>
              </button>
            ))}
          </nav>
        </aside>

        {/* Main Content */}
        <main className={styles.main}>
          {!article ? (
            <div className={styles.articleList}>
              <h2 className={styles.categoryTitle}>
                {currentCategory.icon} {currentCategory.title}
              </h2>
              <div className={styles.articles}>
                {currentCategory.articles.map((art) => (
                  <button
                    key={art.id}
                    className={styles.articleCard}
                    onClick={() => setSelectedArticle(art.id)}
                  >
                    <h3 className={styles.articleTitle}>{art.title}</h3>
                    <p className={styles.articleDesc}>{art.description}</p>
                    <span className={styles.readMore}>자세히 보기 →</span>
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <article className={styles.articleContent}>
              <button
                className={styles.backBtn}
                onClick={() => setSelectedArticle(null)}
              >
                ← 목록으로
              </button>
              <div
                className={styles.markdown}
                dangerouslySetInnerHTML={{
                  __html: article.content
                    .split("\n")
                    .map((line) => {
                      // Simple markdown parsing
                      if (line.startsWith("# "))
                        return `<h1>${line.slice(2)}</h1>`;
                      if (line.startsWith("## "))
                        return `<h2>${line.slice(3)}</h2>`;
                      if (line.startsWith("### "))
                        return `<h3>${line.slice(4)}</h3>`;
                      if (line.startsWith("- "))
                        return `<li>${line.slice(2)}</li>`;
                      if (line.startsWith("**") && line.endsWith("**"))
                        return `<p><strong>${line.slice(2, -2)}</strong></p>`;
                      if (line.trim() === "") return "<br/>";
                      return `<p>${line}</p>`;
                    })
                    .join(""),
                }}
              />
            </article>
          )}
        </main>
      </div>
    </div>
  );
}
