# Validation Report: Backtest & Risk Metrics

- Version: v1.1.0
- Date: 2026-02-28
- Scope: Backtest 계산, Portfolio Risk Dashboard 계산 일관성 검증

## 1. Objective

정량 지표가 비즈니스 의도와 일치하는지, 회귀 시 빠르게 이상을 감지할 수 있는지 검증합니다.

## 2. 대상 API

- Backtest: `POST /api/backtest`, `GET /api/backtest/{id}`
- Risk: `GET /api/portfolio/{id}/risk-dashboard`
- Earnings: `GET /api/portfolio/{id}/earnings-calendar`

## 3. Validation Strategy

### 3.1 Deterministic Inputs

- 동일 티커/기간/전략 파라미터를 고정
- seed 데이터 주입(`scripts/seed_demo_data.ps1`) 후 테스트 수행

### 3.2 Contract Assertions

- 필수 필드 존재: runId, summary, metrics 등
- 숫자 타입/범위 검증:
  - 변동성(volatility) >= 0
  - VaR/CVaR <= 0 (손실 방향 표기 기준)
  - maxDrawdown <= 0

### 3.3 Consistency Checks

- 포트폴리오 보유 종목이 risk response와 earnings response에 반영되는지 확인
- 동일 입력 재실행 시 지표 편차가 허용 범위 내인지 확인

## 4. Automated Procedure

1. 스택 실행
2. 사용자 생성/로그인
3. 포트폴리오 생성 + 포지션 추가
4. backtest 실행 및 상세 조회
5. risk/earnings 조회 후 규칙 검증

자동화 스크립트:

- `scripts/e2e_smoke.py` (core flow + report)

출력 리포트:

- `artifacts/reports/e2e-core-flows.json`

## 5. Acceptance Criteria

- 핵심 API 응답 200
- 필수 필드 누락 0건
- 범위 위반 0건
- E2E 리포트 failedSteps = 0

## 6. Manual Spot Checks

- Backtest 결과의 누적수익률/샤프비율이 극단치(비정상)인지 수동 확인
- Risk dashboard의 포지션 기여도가 직관과 일치하는지 확인

## 7. Known Limits

- 외부 시세 소스 지연/결측에 따른 결과 변동 가능
- 완전한 재현성을 위해 고정 데이터 공급자 모드 필요

## 8. Next Upgrade

1. 백테스트 계산 로직에 golden dataset 회귀테스트 도입
2. 리스크 계산식 단위테스트(핵심 함수) 확대
3. CI에서 편차 임계치 기반 알림 자동화
