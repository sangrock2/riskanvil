# Product Manual

이 문서는 Stock-AI의 사용자 관점 기능 문서 정본입니다.

## 1. Product Positioning

Stock-AI는 개인 투자자/학습자를 위한 AI 기반 투자 분석 플랫폼입니다.

핵심 가치:

- 분석 자동화: 기술적/기본적/뉴스 기반 종합 인사이트
- 실행 지원: 워치리스트/포트폴리오/알림/모의투자까지 연결
- 리스크 인지: 실적 캘린더 + 리스크 대시보드로 이벤트/변동성 관리

## 2. Target Users

- 초중급 개인 투자자: 종목 리서치 시간을 줄이고 판단 근거를 확보하려는 사용자
- 학습형 사용자: 지표/전략/백테스트 결과를 통해 투자 개념을 익히는 사용자
- 실전 전 단계 사용자: 모의투자로 전략을 점검하려는 사용자

## 3. Feature Map (화면 기준)

| 화면 | 핵심 기능 |
|---|---|
| Landing | 서비스 소개, 가치 제안 |
| Dashboard | 시장 개요, 워치리스트 요약, 빠른 분석 진입 |
| Analyze | 종목 분석 실행, 인사이트/리포트 생성 |
| Insight Detail | 신호 상세, 근거 지표, 리포트 결과 확인 |
| Backtest | 전략 시뮬레이션, 성과/리스크 지표 비교 |
| Watchlist | 관심 종목, 태그, 노트, 테스트모드 관리 |
| Portfolio | 포트폴리오/포지션/리밸런싱 관리 |
| Dividends | 배당 이력/캘린더 기반 일정 관리 |
| Earnings | 보유 종목 실적 이벤트 캘린더 |
| Risk Dashboard | 포트폴리오 위험 지표(변동성, VaR, 낙폭 등) |
| Paper Trading | 가상 계좌 주문/포지션/이력 |
| Screener | 조건 필터 기반 종목 탐색 + 프리셋 |
| Correlation | 종목 상관관계 분석 |
| Chatbot | 포트폴리오 컨텍스트 기반 질의응답 |
| Usage | API/AI 사용량 및 상태 관측 |
| Settings | 언어/테마/알림/2FA 설정 |
| Glossary / Learn | 용어 학습, 개념 학습 |

## 4. Core User Flows

### 4.1 온보딩

1. 회원가입 (`/register`)
2. 로그인 (`/login`)
3. 필요 시 2FA 검증
4. 기본 설정(언어, 테마, 기본 시장)

### 4.2 종목 분석

1. Dashboard 또는 Analyze에서 티커 입력
2. 분석 실행 (`/api/analysis`, `/api/market/insights`)
3. Insight Detail에서 근거 지표/리포트 확인
4. 필요 시 워치리스트 추가

### 4.3 포트폴리오 기반 리스크 관리

1. Portfolio에서 자산/포지션 구성
2. Earnings에서 일정 리스크 확인
3. Risk Dashboard에서 포트폴리오 위험도 점검
4. 필요 시 리밸런싱 또는 종목 조정

### 4.4 모의투자 루프

1. Paper Trading에서 시장 선택(US/KR)
2. 주문 실행(BUY/SELL)
3. 포지션/손익 추적
4. 전략 수정 후 반복

## 5. Localization (KO/EN)

- 기본 언어: 한국어
- 지원 언어: 한국어/영어
- 번역 리소스: `frontend/src/i18n/translations.js`
- 정책:
  - 신규 UI 키 추가 시 KO/EN 동시 반영
  - 투자 용어는 의미 왜곡 방지를 위해 Glossary 기준 용어 유지
  - 숫자/통화 형식은 시장(US/KR)에 따라 표기 분리

## 6. UX & Accessibility Baseline

- 반응형: 모바일 우선 레이아웃 보장
- 데이터 테이블: 작은 화면에서 가로 스크롤 제공
- 모달: `Escape` 닫기, backdrop click 닫기, ARIA dialog 적용
- 네비게이션: SPA 라우팅 우선(강제 새로고침/새창 최소화)
- 상태 표현: 로딩/빈상태/오류 메시지 일관성 유지

## 7. KPI Suggestions (포트폴리오/운영 공통)

- 사용자 지표: DAU/WAU, 재방문율, 신규 가입 전환율
- 기능 지표: 분석 실행 수, 리포트 생성 수, 워치리스트 유지율
- 품질 지표: API 실패율, p95 응답시간, 클라이언트 오류율
- 학습 지표: Glossary/Learn 페이지 이용률

## 8. Release and Portfolio Narrative

면접/포트폴리오 설명 시 추천 포인트:

- 기능 완성도: 분석 -> 실행(포트폴리오/모의투자) -> 관리(리스크/실적) 전체 흐름
- 기술 설계: Frontend/Backend/AI Service 분리 + 운영 계층(Prometheus/Grafana)
- 실무성: 2FA, 토큰 하드닝, 배포 가이드, 운영 런북, 시드 데이터 자동화

## 9. Product Status Summary (2026-02-27)

- 운영 가능 핵심 기능: 인증, 분석, 워치리스트, 포트폴리오, 배당, 스크리너, 챗봇, 모의투자
- 신규 확장: 실적 캘린더, 리스크 대시보드
- 문서/운영 성숙도: 배포/운영/에러/체크리스트/서비스 매뉴얼 구축

## 10. Backlog Alignment

중장기 개선 항목은 [ROADMAP.md](./ROADMAP.md) 기준으로 관리합니다.
