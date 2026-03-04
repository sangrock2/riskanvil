# Contributing Guide

Stock-AI에 기여해주셔서 감사합니다.

## 1. 기본 원칙

- 기능 변경은 문서/API/테스트와 함께 제출
- 보안 관련 변경은 `SECURITY.md` 기준 준수
- 큰 구조 변경은 PR 설명에 설계 의도와 트레이드오프 명시

## 2. 개발 환경

1. `.env.example` -> `.env` 복사
2. 인프라 실행: `docker compose up -d mysql redis`
3. 서비스 실행
   - Backend: `cd backend && ./gradlew bootRun`
   - AI: `cd ai && uvicorn main:app --reload`
   - Frontend: `cd frontend && npm install && npm start`

## 3. 브랜치/커밋 규칙

- 브랜치: `feature/*`, `fix/*`, `docs/*`
- 커밋 메시지 예시:
  - `feat: add earnings calendar endpoint`
  - `fix: handle refresh token hash mismatch`
  - `docs: consolidate operations manual`

## 4. PR 체크리스트

- [ ] 기능/버그 설명이 명확한가
- [ ] 관련 테스트를 추가/수정했는가
- [ ] `docs/API.md` 업데이트가 필요한 변경인가
- [ ] `docs/PATCH_NOTES.md`에 릴리즈 노트 반영이 필요한가
- [ ] 민감정보(.env, 키, 토큰)가 커밋되지 않았는가

## 5. 테스트 가이드

```bash
cd backend && ./gradlew test
cd ai && pytest
cd frontend && npm test -- --watchAll=false
```

## 6. 문서 정책

- 문서 정본 인덱스: `docs/DOCS_INDEX.md`
- 중복 문서 생성 대신 정본 문서 업데이트
