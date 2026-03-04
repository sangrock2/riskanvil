# Frontend (Vite + React)

Stock-AI 프론트엔드 애플리케이션입니다.

## 요구사항

- Node.js 20+
- npm 10+

## 실행

```bash
npm install
npm run dev
```

- 기본 포트: `3000`
- 개발 프록시: `/api`, `/ws` -> `http://localhost:8080`

## 빌드

```bash
npm run build
```

산출물은 `dist/`에 생성됩니다.

## 테스트

```bash
npm run test
```

## API 계약 동기화

```bash
npm run openapi:generate
npm run openapi:check
```

- `openapi:check`는 생성 결과와 committed client의 diff가 있으면 실패합니다.
