# Stock-AI Portfolio Final Submission

## 1. Project Summary

Stock-AI는 개인 투자자를 위한 AI 기반 투자 의사결정 플랫폼입니다.  
핵심은 분석(Analysis) -> 실행(Portfolio/Paper Trading) -> 관리(Risk/Earnings) 흐름을 하나의 서비스 경험으로 통합한 점입니다.

## 2. Problem and Solution

### Problem

- 개인 투자자는 데이터 소스가 분산되어 분석-실행-리스크 관리를 한 번에 하기 어렵다.
- 분석 결과가 실제 포트폴리오 운영으로 이어지지 않는 단절이 있다.

### Solution

- React + Spring Boot + FastAPI 3계층 아키텍처로 역할 분리
- 인증/보안(2FA, refresh token hash), 분석 API, 포트폴리오 관리, 모의투자, 리스크 대시보드 통합
- 운영 가시성(Prometheus/Grafana)과 배포/장애 대응 문서 체계화

## 3. My Role

- 제품 기능 설계: 실적 캘린더, 리스크 대시보드, E2E 핵심 플로우
- 백엔드/프론트 통합: API 설계 및 라우트/화면 연결
- 품질/운영: 테스트 자동화, OpenAPI 기반 타입 클라이언트 생성 파이프라인, 운영 증빙 문서

## 4. Technical Highlights

1. 서비스 구조
   - Frontend(React), Backend(Spring Boot), AI(FastAPI) 분리
2. 보안
   - JWT + Refresh Token, 2FA, 토큰 해시 저장
3. 성능/복원력
   - Redis 캐시, Circuit Breaker, Retry/Timeout 정책
4. 운영성
   - Docker Compose 통합 실행, Prometheus/Grafana 모니터링
5. 문서 체계
   - Product/Engineering/Operations 정본화 + API 계약 문서 통합

## 5. Upgrade Evidence (Applied)

### 5.1 Public URL + Monitoring Evidence

- 검증 스크립트: `scripts/verify_public_service.ps1`
- 스크린샷 스크립트: `scripts/capture_monitoring_screenshots.ps1`
- 증빙 문서: `docs/PUBLIC_SERVICE_EVIDENCE.md`

### 5.2 3-Core-Flow E2E Automation

- 스크립트: `scripts/e2e_smoke.py`
- 플로우:
  1. auth register/login
  2. analysis run/detail
  3. portfolio risk + earnings
- 결과 리포트: `artifacts/reports/e2e-core-flows.json`

### 5.3 OpenAPI + Typed Client Generation

- 스냅샷 계약: `docs/openapi/stock-ai.openapi.json`
- 동기화: `scripts/openapi_sync.ps1`
- 클라이언트 생성: `scripts/generate_openapi_client.ps1`
- Frontend script: `npm --prefix frontend run openapi:generate`

### 5.4 Incident Documentation

- 사고 보고서: `docs/INCIDENT_POSTMORTEM_AI_TIMEOUT_2026-02-28.md`

### 5.5 Backtest/Risk Validation Report

- 검증 문서: `docs/VALIDATION_BACKTEST_RISK.md`

## 6. Metrics to Present in Interview

- 기능 지표: 분석 실행 수, 리포트 생성 수, 포트폴리오 생성 수
- 안정성 지표: API error rate, p95 latency, timeout 비율
- 개발 생산성 지표: E2E 자동화로 회귀 확인 시간 단축

## 7. Demo Script (3-5 Minutes)

1. 로그인 -> Dashboard 진입
2. Analyze에서 종목 분석 실행
3. Portfolio 생성 + 포지션 추가
4. Earnings + Risk Dashboard 확인
5. 운영 증빙(검증 리포트 + 모니터링 스크린샷) 제시

## 8. Interview Q&A Anchors

1. 왜 3계층으로 분리했는가?
2. 토큰 보안 하드닝에서 어떤 트레이드오프가 있었는가?
3. 리스크 지표를 제품 UX에 어떻게 연결했는가?
4. 장애를 어떻게 감지하고 복구했는가?
5. OpenAPI/타입 생성 파이프라인이 어떤 문제를 줄였는가?

## 9. Submission Checklist

- [ ] 공개 URL 및 health endpoint 검증 리포트 첨부
- [ ] 모니터링 스크린샷 첨부
- [ ] E2E 리포트 첨부
- [ ] PATCH_NOTES 최신 버전 반영
- [ ] README 문서 인덱스 최신화
