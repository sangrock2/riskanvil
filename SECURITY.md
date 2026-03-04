# Security Policy

## 1. Supported Versions

현재 운영 기준은 `main` 브랜치 최신 버전입니다.

## 2. Vulnerability Reporting

보안 취약점은 공개 이슈 대신 비공개 채널로 제보하세요.

권장 제보 내용:

- 영향 범위(인증/인가/데이터노출/서비스거부)
- 재현 단계
- 예상/실제 동작
- 임시 완화 방법

## 3. Security Baseline

- JWT + Refresh Token(해시 저장) 사용
- 2FA(TOTP) 지원
- 민감정보는 `.env`로 주입, Git 커밋 금지
- CORS는 허용 출처만 설정
- 서버/클라이언트 에러는 Sentry로 추적 가능

## 4. Secret Handling

- `.env`/API Key/DB Password는 절대 커밋 금지
- 키 유출 시 즉시 회수/재발급
- 운영 비밀번호/토큰은 주기적 로테이션

## 5. Incident Handling

- 장애 대응 기준 문서: `docs/OPERATIONS_MANUAL.md`
- 릴리즈 영향 추적: `docs/PATCH_NOTES.md`

## 6. Security Review Trigger

다음 변경은 보안 리뷰를 권장합니다.

- 인증/인가 로직 수정
- 토큰 생성/저장 정책 변경
- 신규 외부 API 연동
- 프록시/HTTPS/헤더 정책 변경
