# Alerting Templates

이 문서는 Render + Sentry 기준 실서비스 알림 템플릿입니다.  
운영 절차는 [OPERATIONS_MANUAL.md](./OPERATIONS_MANUAL.md), 실행 체크는 [OPERATIONS_CHECKLIST.md](./OPERATIONS_CHECKLIST.md)를 함께 사용합니다.

## 1. 우선순위 정의

- `P1`: 로그인/분석/핵심 API 전면 장애, 다수 사용자 영향
- `P2`: 일부 기능 장애, 우회 가능
- `P3`: 기능 저하/성능 저하/단건 오류

## 2. Render 알림 템플릿

### 2.1 Backend Web Service

- Trigger:
  - Deploy failed
  - Health check failed
  - Repeated restarts
- Channel:
  - Email (필수)
  - Slack/Discord/Webhook (권장)
- Severity mapping:
  - Deploy failed -> P1
  - Health check failed (5분 이상) -> P1
  - Repeated restarts (10분 내 3회 이상) -> P1

### 2.2 AI Private Service

- Trigger:
  - Deploy failed
  - Health check failed (`/health`)
- Severity mapping:
  - 분석 API 연쇄 실패와 동반 시 -> P1
  - 단독 실패, 백업 경로 존재 시 -> P2

## 3. Sentry 알림 템플릿

## 3.1 Backend 프로젝트

- Rule A: "새로운 에러(New Issue) 즉시 알림"
  - Condition: `event.level:error` + first seen
  - Action: 운영 채널 즉시 전송
  - Severity: P2 (핵심 API는 P1 승격)

- Rule B: "에러 급증"
  - Condition: 5분 내 이벤트 20건 이상
  - Action: 운영 채널 + 담당자 멘션
  - Severity: P1

- Rule C: "회귀(Resolved 이후 재발)"
  - Condition: regressed issue
  - Action: 운영 채널 전송
  - Severity: P2

### 3.2 Frontend 프로젝트

- Rule A: "화이트스크린/렌더 크래시"
  - Condition: `event.type:error`, `level:error`, 릴리즈 단위 급증
  - Severity: P1

- Rule B: "네트워크/API 에러 급증"
  - Condition: 5분 내 동일 fingerprint 30건 이상
  - Severity: P2

### 3.3 AI 프로젝트

- Rule A: "외부 API 연동 실패 급증"
  - Condition: 5분 내 동일 오류 15건 이상
  - Severity: P1/P2 (핵심 기능 영향도에 따라)

## 4. 공통 알림 메시지 템플릿

```text
[Stock-AI][{SEVERITY}] {SERVICE} 장애 감지
- Time: {UTC_ISO}
- Trigger: {RULE_NAME}
- Impact: {IMPACT_SUMMARY}
- Endpoint/Feature: {PATH_OR_FEATURE}
- requestId(example): {REQUEST_ID}
- Dashboard: {RENDER_OR_SENTRY_URL}
- Initial Action: {ROLLBACK_OR_MITIGATION}
```

## 5. 운영 팁

- 알림은 반드시 `행동 가능한 기준`(횟수/기간/영향 범위)으로 설정
- 너무 민감한 임계값은 노이즈를 만들므로 주 1회 튜닝
- 모든 P1 알림은 장애 종료 후 Postmortem 작성

