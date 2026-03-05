# Operations Checklist

이 문서는 실서비스 운영 시 바로 체크하는 실행용 체크리스트입니다.  
상세 절차는 [OPERATIONS_MANUAL.md](./OPERATIONS_MANUAL.md)를 기준으로 합니다.

## 0. 운영 기준값 (최초 1회)

- [ ] Backend Health URL 확인: `https://<backend-domain>/actuator/health`
- [ ] AI Health URL 확인: `https://<ai-domain>/health`
- [ ] Render 알림 채널 설정(배포 실패, 헬스체크 실패, 재시작 반복)
- [ ] Sentry 프로젝트 3개 연결(Frontend/Backend/AI)
- [ ] `docs/ALERTING_TEMPLATES.md` 기준으로 알림 룰 생성/검증
- [ ] Backend `JWT_SECRET`(32바이트 이상) 설정
- [ ] Backend `DB_URL` 형식 확인: `jdbc:postgresql://<host>:5432/<db_name>`
- [ ] Backend `APP_CORS_ALLOWED_ORIGIN_PATTERNS`를 실제 Frontend URL로 설정
- [ ] `requestId` 기반 장애 추적 규칙 팀 공지

## 1. 일일 점검 (영업일 시작/종료 시)

- [ ] Render Dashboard에서 Backend/AI 서비스 상태 `Live` 확인
- [ ] 최근 24시간 배포 실패/재시작 이력 확인
- [ ] Backend `/actuator/health` 상태 `UP` 확인
- [ ] AI `/health` 상태 `ok` 확인
- [ ] Sentry 신규 이슈/재오픈 이슈 확인
- [ ] `app_error_total` 메트릭 급증(error_code/status) 여부 확인
- [ ] Backend 5xx 급증 여부 확인(Logs 또는 Metrics)
- [ ] 사용자 핵심 플로우 스모크 점검(로그인 -> 분석 -> 포트폴리오)

## 2. 주간 점검

- [ ] 상위 에러 5개 원인 분류 및 재발 방지 액션 등록
- [ ] 평균 응답시간(p95), 에러율, 타임아웃 비율 추이 점검
- [ ] DB 연결 수/슬로우 쿼리/스토리지 사용량 확인
- [ ] 만료 예정 비밀값(API Key, 토큰) 확인
- [ ] 불필요 로그(민감정보 포함 가능성) 샘플 리뷰

## 3. 배포 전 체크리스트

- [ ] 배포 커밋/브랜치 확인
- [ ] 환경변수 diff 확인(누락/오타/값 형식)
- [ ] DB 마이그레이션 영향 검토(Flyway/JPA 설정 포함)
- [ ] 헬스체크 경로 유효성 확인
- [ ] 롤백 대상 버전(직전 정상 배포) 확인
- [ ] 변경사항을 `docs/PATCH_NOTES.md`에 기록
- [ ] 알림 규칙 변경 시 `docs/ALERTING_TEMPLATES.md` 동기화

## 4. 배포 후 체크리스트 (15분 내)

- [ ] 배포 로그에서 `Started` 및 포트 바인딩 확인
- [ ] Backend `/actuator/health` 확인
- [ ] AI `/health` 확인
- [ ] Frontend에서 API 호출 200/4xx/5xx 비율 확인
- [ ] 로그인/회원가입/분석/포트폴리오 최소 1회 실행
- [ ] Sentry 신규 치명 오류 발생 여부 확인

## 5. 장애 대응 체크리스트

### 5.1 공통

- [ ] 장애 심각도 분류(P1/P2/P3)
- [ ] 최초 인지 시각/증상/영향 범위 기록
- [ ] 최근 배포/환경변수 변경 여부 확인
- [ ] 사용자 에러 응답의 `requestId` 확보
- [ ] 동일 `requestId`로 Backend -> AI 로그 순서 추적

### 5.2 DB 연결 오류

- [ ] `DB_URL`가 `jdbc:postgresql://...` 형식인지 확인
- [ ] DB 이름(`/<db_name>`)이 실제 존재하는지 확인
- [ ] `DB_USERNAME`, `DB_PASSWORD` 재검증
- [ ] Render PostgreSQL 인스턴스 상태 및 연결 정보 재확인

### 5.3 AI 분석 오류(HTTP 500)

- [ ] Backend 로그에서 AI 호출 실패 지점 확인
- [ ] AI 서비스 로그에서 동일 시각 예외 확인
- [ ] 외부 데이터/API 한도 초과 여부 확인
- [ ] 임시 완화: 캐시 응답/재시도 정책 사용 여부 점검

## 6. 롤백 체크리스트

- [ ] Render `Manual Deploy`에서 직전 정상 배포 선택
- [ ] 롤백 완료 후 헬스체크 2종(Backend/AI) 재확인
- [ ] 핵심 사용자 플로우 재점검
- [ ] 장애 타임라인과 롤백 시점 기록

## 7. DB 운영 체크리스트

- [ ] 월 1회 백업 복구 리허설 수행
- [ ] 테이블 증감/인덱스 상태 점검
- [ ] 보관 정책에 맞게 오래된 데이터 정리 계획 점검
- [ ] `SELECT current_database();`로 연결 DB 재확인

## 8. 보안 운영 체크리스트

- [ ] 운영 비밀값 정기 로테이션
- [ ] 기본/개발용 시크릿 사용 여부 점검
- [ ] 관리자 계정 최소 권한 원칙 준수
- [ ] 에러 응답에 내부 스택/비밀정보 노출 없는지 점검
