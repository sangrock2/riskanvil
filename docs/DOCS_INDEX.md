# Documentation Index (v1.1)

이 문서는 Stock-AI의 문서 체계를 통합한 **단일 인덱스**입니다.

## 1. Canonical Docs (정본)

아래 문서를 기준으로 프로젝트를 이해/운영/확장합니다.

1. [PRODUCT_MANUAL.md](./PRODUCT_MANUAL.md)
   - 사용자 기능, 화면별 동작, 도메인 흐름, KPI/로드맵
2. [ENGINEERING_HANDBOOK.md](./ENGINEERING_HANDBOOK.md)
   - 아키텍처, 기술 스택, 코드 구조, 개발/테스트/품질 기준
3. [API.md](./API.md)
   - 프론트엔드가 사용하는 Backend API 계약(요청/응답/엔드포인트)
4. [OPERATIONS_MANUAL.md](./OPERATIONS_MANUAL.md)
   - 배포, 운영, 장애 대응, 모니터링, 릴리즈 절차
5. [PATCH_NOTES.md](./PATCH_NOTES.md)
   - 릴리즈 버전별 변경 이력
6. [ROADMAP.md](./ROADMAP.md)
   - 향후 기능 계획과 우선순위
7. [PUBLIC_SERVICE_EVIDENCE.md](./PUBLIC_SERVICE_EVIDENCE.md)
   - 공개 URL/모니터링 증빙 수집 기준
8. [VALIDATION_BACKTEST_RISK.md](./VALIDATION_BACKTEST_RISK.md)
   - 백테스트/리스크 지표 검증 기준
9. [PORTFOLIO_FINAL_SUBMISSION.md](./PORTFOLIO_FINAL_SUBMISSION.md)
   - 포트폴리오 제출용 최종 문서
10. [INCIDENT_POSTMORTEM_AI_TIMEOUT_2026-02-28.md](./INCIDENT_POSTMORTEM_AI_TIMEOUT_2026-02-28.md)
   - 장애 분석/재발 방지 기록
11. [DEPLOY_RENDER.md](./DEPLOY_RENDER.md)
   - Render 배포 환경변수/서비스 설정
12. [../CONTRIBUTING.md](../CONTRIBUTING.md)
   - 기여 방식, PR 기준, 테스트 규칙
13. [../SECURITY.md](../SECURITY.md)
   - 취약점 제보 및 보안 운영 정책
14. [OPERATIONS_CHECKLIST.md](./OPERATIONS_CHECKLIST.md)
   - 실서비스 일일/배포/장애 대응 실행 체크리스트
15. [ALERTING_TEMPLATES.md](./ALERTING_TEMPLATES.md)
   - Render/Sentry 운영 알림 규칙 템플릿
16. [POSTGRES_FLYWAY_TRANSITION_PLAN.md](./POSTGRES_FLYWAY_TRANSITION_PLAN.md)
   - Render Postgres baseline, cutover 방식, 운영 검증 기준
17. [PERFORMANCE_AND_LOAD_BASELINE.md](./PERFORMANCE_AND_LOAD_BASELINE.md)
   - 성능 기준선, SLI/SLO, 부하 테스트 게이트, 보고 주기
18. [ARCHITECTURE_CASE_STUDY.md](./ARCHITECTURE_CASE_STUDY.md)
   - 주요 설계 판단과 트레이드오프를 설명하는 요약 케이스 스터디

## 2. Audience Guide

- 제품/기획/포트폴리오 설명: `PRODUCT_MANUAL.md`
- 신규 개발자 온보딩: `ENGINEERING_HANDBOOK.md` + `API.md` + `ARCHITECTURE_CASE_STUDY.md`
- 운영/배포 담당자: `OPERATIONS_MANUAL.md` + `OPERATIONS_CHECKLIST.md` + `ALERTING_TEMPLATES.md`
- DB 전략 정리: `POSTGRES_FLYWAY_TRANSITION_PLAN.md`
- 성능/부하 기준 관리: `PERFORMANCE_AND_LOAD_BASELINE.md`
- 릴리즈 변경 확인: `PATCH_NOTES.md`

## 3. Consolidation Map (레거시 문서 통합 맵)

| 기존 문서 | 현재 권장 문서 |
|---|---|
| PROJECT_OVERVIEW.md | ENGINEERING_HANDBOOK.md |
| TECHNICAL_STACK.md | ENGINEERING_HANDBOOK.md |
| ARCHITECTURE.md | ENGINEERING_HANDBOOK.md |
| DATABASE_SCHEMA.md | ENGINEERING_HANDBOOK.md |
| DEVELOPMENT_GUIDE.md | ENGINEERING_HANDBOOK.md |
| FEATURES.md | PRODUCT_MANUAL.md |
| SERVICE_MANUAL.md | PRODUCT_MANUAL.md + OPERATIONS_MANUAL.md |
| API_DOCUMENTATION.md | API.md |
| PUBLIC_DEPLOYMENT_GUIDE.md | OPERATIONS_MANUAL.md |
| NGINX_SETUP.md | OPERATIONS_MANUAL.md |
| OPERATIONS_RUNBOOK.md | OPERATIONS_MANUAL.md |
| CONFIG_BASELINE.md | OPERATIONS_MANUAL.md |
| ERROR_CATALOG.md | OPERATIONS_MANUAL.md |
| DEPLOY_CHECKLIST.md | OPERATIONS_MANUAL.md |
| IMPLEMENTATION_STATUS.md | PRODUCT_MANUAL.md |
| MVP_vs_PRODUCTION_STATUS.md | PRODUCT_MANUAL.md |
| INCOMPLETE_FEATURES_FULL_REPORT.md | PRODUCT_MANUAL.md + ROADMAP.md |
| TESTING_GUIDE.md | ENGINEERING_HANDBOOK.md |

## 4. Maintenance Policy

- 신규 기능 추가 시:
  1. `API.md` 엔드포인트 반영
  2. `PRODUCT_MANUAL.md` 사용자 기능 섹션 반영
  3. `ENGINEERING_HANDBOOK.md` 구조/설계 영향 반영
  4. `PATCH_NOTES.md` 버전 섹션 반영
- 운영 정책 변경 시 `OPERATIONS_MANUAL.md` 우선 수정
- 운영 점검 항목 변경 시 `OPERATIONS_CHECKLIST.md` 동시 수정
- 운영 알림 기준 변경 시 `ALERTING_TEMPLATES.md` 동시 수정
- 중복 문서 신규 생성 금지. 필요 시 본 인덱스에 링크만 추가
