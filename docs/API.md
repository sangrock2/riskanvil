# Stock-AI API Reference

이 문서는 Backend API 계약의 단일 기준 문서입니다.

## 1. Base URLs

- Local Backend: `http://localhost:8080`
- API Prefix: `/api`

OpenAPI snapshot and typed client:

- Snapshot: `docs/openapi/stock-ai.openapi.json`
- Sync script: `scripts/openapi_sync.ps1`
- Client generation: `scripts/generate_openapi_client.ps1`

## 2. Auth Model

- Access Token: Bearer JWT
- Refresh Token: `/api/auth/refresh`
- 2FA 로그인: `/api/auth/verify-2fa`

인증 필요 엔드포인트는 `Authorization: Bearer <token>` 헤더가 필요합니다.

## 3. Response Convention

- 성공: 도메인별 JSON payload
- 실패: HTTP status + 오류 JSON
- 요청 추적: `X-Request-Id` 헤더 사용 권장

## 4. Endpoint Index

## 4.1 Authentication

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | 회원가입 |
| POST | `/api/auth/login` | 로그인 |
| POST | `/api/auth/verify-2fa` | 로그인 2FA 검증 |
| POST | `/api/auth/refresh` | 토큰 갱신 |
| POST | `/api/auth/logout` | 로그아웃 |

## 4.2 Two-Factor (Settings)

| Method | Path | Description |
|---|---|---|
| POST | `/api/2fa/setup` | TOTP 시크릿/QR 생성 |
| POST | `/api/2fa/verify` | TOTP 활성화 검증 |
| POST | `/api/2fa/disable` | 2FA 비활성화 |

## 4.3 Analysis / Market

| Method | Path | Description |
|---|---|---|
| POST | `/api/analysis` | 종목 분석 실행 |
| GET | `/api/analysis/history` | 분석 이력 |
| GET | `/api/analysis/{id}` | 분석 상세 |
| GET | `/api/market/search` | 심볼 검색 |
| POST | `/api/market/insights` | 인사이트 생성 |
| GET | `/api/market/quote` | 현재가 조회 |
| GET | `/api/market/prices` | 과거 가격 데이터 |
| GET | `/api/market/ohlc` | OHLC 데이터 |
| POST | `/api/market/report` | AI 리포트 생성 |
| GET | `/api/market/report-history` | 리포트 이력 |
| POST | `/api/market/valuation` | 밸류에이션 계산 |
| POST | `/api/market/report/stream` | SSE 리포트 스트리밍 |
| POST | `/api/market/report/export/txt` | 텍스트 내보내기 |
| POST | `/api/market/report/export/json` | JSON 내보내기 |
| POST | `/api/market/report/export/pdf` | PDF 내보내기 |

## 4.4 Watchlist

| Method | Path | Description |
|---|---|---|
| GET | `/api/watchlist` | 관심종목 조회 |
| POST | `/api/watchlist` | 관심종목 추가 |
| DELETE | `/api/watchlist` | 관심종목 제거 |
| PUT | `/api/watchlist/{itemId}/tags` | 태그 연결 |
| PUT | `/api/watchlist/{itemId}/notes` | 메모 수정 |
| GET | `/api/watchlist/tags` | 태그 조회 |
| POST | `/api/watchlist/tags` | 태그 생성 |
| PUT | `/api/watchlist/tags/{tagId}` | 태그 수정 |
| DELETE | `/api/watchlist/tags/{tagId}` | 태그 삭제 |

## 4.5 Portfolio / Dividend / Risk

| Method | Path | Description |
|---|---|---|
| POST | `/api/portfolio` | 포트폴리오 생성 |
| GET | `/api/portfolio` | 포트폴리오 목록 |
| GET | `/api/portfolio/{id}` | 포트폴리오 상세 |
| PUT | `/api/portfolio/{id}` | 포트폴리오 수정 |
| DELETE | `/api/portfolio/{id}` | 포트폴리오 삭제 |
| POST | `/api/portfolio/{id}/position` | 포지션 추가 |
| PUT | `/api/portfolio/{portfolioId}/position/{positionId}` | 포지션 수정 |
| DELETE | `/api/portfolio/{portfolioId}/position/{positionId}` | 포지션 삭제 |
| POST | `/api/portfolio/{id}/rebalance` | 리밸런싱 |
| GET | `/api/portfolio/{id}/earnings-calendar` | 실적 캘린더 |
| GET | `/api/portfolio/{id}/risk-dashboard` | 리스크 대시보드 |
| POST | `/api/dividend/position/{positionId}/fetch` | 배당 데이터 동기화 |
| GET | `/api/dividend/position/{positionId}/history` | 배당 이력 |
| GET | `/api/dividend/portfolio/{portfolioId}/calendar` | 배당 캘린더 |

## 4.6 Paper Trading

| Method | Path | Description |
|---|---|---|
| GET | `/api/paper/accounts` | 계좌 조회/초기화 |
| POST | `/api/paper/accounts/reset` | 계좌 리셋 |
| POST | `/api/paper/order` | 주문 실행 |
| GET | `/api/paper/positions` | 보유 포지션 |
| GET | `/api/paper/orders` | 주문 이력 |

## 4.7 Quant / AI Assist

| Method | Path | Description |
|---|---|---|
| POST | `/api/backtest` | 백테스트 실행 |
| GET | `/api/backtest/history` | 백테스트 이력 |
| GET | `/api/backtest/{id}` | 백테스트 상세 |
| POST | `/api/correlation` | 상관관계 분석 |
| POST | `/api/monte-carlo` | 몬테카를로 시뮬레이션 |
| POST | `/api/screener` | 스크리너 실행 |
| POST | `/api/screener/preset` | 프리셋 저장 |
| GET | `/api/screener/preset` | 사용자 프리셋 조회 |
| GET | `/api/screener/preset/public` | 공개 프리셋 조회 |
| POST | `/api/chatbot/chat` | 챗봇 응답 생성 |
| GET | `/api/chatbot/conversations` | 대화 목록 |
| GET | `/api/chatbot/conversations/{id}/messages` | 대화 메시지 조회 |
| DELETE | `/api/chatbot/conversations/{id}` | 대화 삭제 |

## 4.8 Settings / Usage / Utility

| Method | Path | Description |
|---|---|---|
| GET | `/api/settings` | 사용자 설정 조회 |
| PUT | `/api/settings` | 사용자 설정 수정 |
| GET | `/api/usage/summary` | 사용량 요약 |
| GET | `/api/usage/dashboard` | 사용량 대시보드 |
| DELETE | `/api/usage/cleanup` | 오래된 로그 정리 |
| GET | `/api/insights` | 인사이트 조회 보조 엔드포인트 |
| POST | `/api/portfolio/efficient-frontier` | 효율적 프론티어 계산 |
| POST | `/api/similarity/find` | 유사 종목 탐색 |

## 5. AI Service Internal Endpoints

다음 엔드포인트는 주로 Backend가 내부 호출합니다.

- `/insights`, `/report`, `/backtest`, `/correlation`, `/screener`, `/monte-carlo`
- `/portfolio/risk`, `/earnings/calendar`, `/efficient-frontier`, `/find`
- `/dividend/history`, `/dividend/upcoming`
- `/symbol_search`, `/quote`, `/prices`, `/ohlc`, `/fundamentals`, `/news`
- `/index`, `/search`, `/status` (RAG)

## 6. API Change Process

1. Controller/DTO 변경
2. 프론트 API client 반영 (`frontend/src/api`)
3. 본 문서(`API.md`) 업데이트
4. 릴리즈 문서(`PATCH_NOTES.md`) 업데이트

## 7. Example Calls

```bash
# 로그인
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password"}'

# 종목 인사이트
curl -X POST http://localhost:8080/api/market/insights \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"ticker":"AAPL","market":"US"}'
```
