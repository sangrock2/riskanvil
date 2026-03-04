# Stock-AI 개선 로드맵

> 마지막 업데이트: 2026-03-04
> 기준 릴리즈: v1.2.0 (2026-02-28)

## 1. 현재 상태 (완료된 핵심 기능)

다음 항목은 로드맵 후보가 아니라 이미 제품에 반영되어 있습니다.

- 포트폴리오 관리 (CRUD, 리밸런싱, 리스크 대시보드)
- 가격 알림/배당/실적 캘린더
- 스크리너, 상관관계, Monte Carlo
- Paper Trading
- 2FA 로그인 연동
- PDF/TXT/JSON 리포트 내보내기
- OpenAPI 스냅샷 + 프론트 typed client 생성
- 핵심 E2E 스모크/운영 증빙 자동화

## 2. 다음 우선순위 (2026 Q2)

### P0 - 안정성/품질 게이트

1. AI 라우트 계약 테스트 확대
- `insights/report/chatbot/screener` 회귀 테스트 유지
- 목표: 주요 라우트 변경 시 CI에서 즉시 실패

2. API 계약 드리프트 차단
- CI에서 `openapi:check`를 필수 게이트로 유지
- 스냅샷과 생성 클라이언트 불일치 시 merge 차단

3. 고비용 엔드포인트 Rate Limit 운영화
- auth 외 `analysis/report/chatbot/backtest` 보호
- 모니터링 지표 기반 임계치 재조정

### P1 - 프론트 플랫폼 현대화

1. Vite 기반 빌드 체인 정착
- CRA 의존성 제거 및 테스트 러너 안정화
- 개발/빌드 시간 단축, 번들 분석 자동화

2. WebSocket 복원력 강화
- heartbeat, pong timeout, exponential backoff + jitter
- 탭 visibility 기반 재연결 최적화

### P1 - 코드 구조 개선

1. 대형 파일 분할 리팩터링
- `SystemMap`, `PortfolioService`, 번역 리소스 모듈 분리
- 목표: 핵심 파일 1개당 500줄 이하

## 3. 중기 확장 (2026 H2)

- 커뮤니티/공유 기능 (공개 리포트 링크, 피드)
- 세금 최적화 시뮬레이션
- 옵션 분석 (Greeks, 전략 시뮬레이션)
- 멀티 자산 확장 (크립토/FX)

## 4. 운영 원칙

- 신규 기능은 계약 테스트/회귀 테스트 동시 추가
- DB 변경은 Flyway 마이그레이션 필수
- API 스키마 변경은 OpenAPI 스냅샷 갱신 필수
- 장애 대응 항목은 Postmortem + Runbook 동시 업데이트
