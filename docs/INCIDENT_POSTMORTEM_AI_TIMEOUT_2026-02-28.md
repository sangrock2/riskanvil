# Incident Postmortem: AI Timeout Spike

- Incident ID: `INC-2026-02-28-AI-TIMEOUT`
- Date (KST): 2026-02-28
- Severity: SEV-2 (핵심 기능 지연, 부분 기능 저하)
- Owner: Backend + AI Service

## 1. Summary

`/api/market/report` 요청 지연이 급증하면서 사용자 보고서 생성 실패율이 일시적으로 증가했습니다.

## 2. Impact

- 영향 기능: 인사이트 리포트 생성, 일부 챗봇 응답
- 사용자 영향: 요청 대기시간 증가, 5xx/timeout 응답 증가
- 지속 시간: 약 19분 (탐지부터 정상화까지)

## 3. Detection

- Grafana 알림: report endpoint p95 latency 임계치 초과
- Backend 로그: WebClient timeout and retry exhaustion
- Sentry: timeout exception 이벤트 급증

## 4. Timeline (KST)

1. 14:03 - p95 latency 급증 감지
2. 14:05 - on-call 확인, AI 서비스 상태 점검 시작
3. 14:09 - 외부 LLM 응답 지연 확인
4. 14:12 - report timeout/retry 정책 임시 완화 적용
5. 14:19 - 에러율 정상 범위 회복
6. 14:22 - 사용자 공지/내부 후속 분석 시작

## 5. Root Cause

- 1차 원인: 외부 LLM 응답 지연
- 2차 원인: report 요청 특성 대비 timeout/retry 기준이 과도하게 공격적이어서 실패를 증폭

## 6. What Worked

- request id 기반 트레이싱으로 원인 경로 분리가 빨랐음
- Circuit breaker가 전체 장애 전파를 제한
- 운영 문서 기반 롤포워드 절차 수행 가능

## 7. What Failed

- 사전 정의된 AI degraded mode 전환 기준이 불명확
- 경고 임계치가 리포트 특성과 완전하게 맞지 않음

## 8. Corrective Actions

1. timeout/retry 정책을 endpoint 별로 분리 유지 (`AI_CLIENT_*` 환경변수)
2. report 전용 degraded mode(요약 응답 fallback) 추가
3. Grafana 경보 임계치 재보정 (p95/p99 분리)
4. 주간 카오스 테스트에 AI 지연 시나리오 추가

## 9. Preventive Controls

- 릴리즈 체크리스트에 `AI timeout load test` 항목 추가
- 운영 메뉴얼의 incident runbook에 해당 시나리오 반영

## 10. Verification

- 재현 테스트: AI 응답 지연 주입 시 백엔드 에러율 임계치 이하 유지 확인
- 회귀 테스트: `scripts/e2e_smoke.py` 통과
