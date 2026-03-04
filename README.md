# Stock-AI

AI 기반 주식 분석 및 백테스팅 풀스택 웹 애플리케이션

## 프로젝트 구조

```
stock-ai/
├── frontend/          # React 19 + Vite SPA
├── backend/           # Spring Boot 4.0.0 (Java 21)
├── ai/                # FastAPI (Python) AI 서비스
└── docker-compose.yml # 전체 서비스 오케스트레이션
```

## 기술 스택

### Frontend
- React 19 + React Router v7
- Vite 5 (dev/build toolchain)
- CSS Modules + CSS Custom Properties
- Chart.js (LineChartCanvas, MultiLineChartCanvas)

### Backend
- Spring Boot 4.0.0, Java 21
- Spring Security (JWT 인증)
- Spring Data JPA + MySQL 8.4
- Redis (캐싱)
- Resilience4j (서킷 브레이커)

### AI Service
- FastAPI (Python)
- Alpha Vantage API 연동
- 기술적 분석 (RSI, MACD, Bollinger Bands 등)
- AI 감성 분석

## 실행 방법

### Docker (권장)
```bash
docker-compose up -d
```

### 개별 실행

**Frontend**
```bash
cd frontend
npm install
npm run dev  # http://localhost:3000
```

**Backend**
```bash
cd backend
./gradlew bootRun  # http://localhost:8080
```

**AI Service**
```bash
cd ai
pip install -r requirements.txt
python -m uvicorn main:app --reload  # http://localhost:8000
```

### Render 배포

- Render 배포 가이드: **[docs/DEPLOY_RENDER.md](docs/DEPLOY_RENDER.md)**
- Blueprint: 저장소 루트 `render.yaml` 사용
- Frontend Static Site에는 `VITE_API_BASE_URL` 설정 필요
- Backend Web Service에는 `JWT_SECRET`, `DB_URL(또는 DB_HOSTPORT + 계정)`, `APP_CORS_ALLOWED_ORIGIN_PATTERNS` 설정 필요

## 문서

### 통합 문서 (정본)

- **[문서 인덱스](docs/DOCS_INDEX.md)** - 전체 문서 구조 및 통합 맵
- **[Product Manual](docs/PRODUCT_MANUAL.md)** - 사용자 기능/서비스 흐름/제품 관점 설명
- **[Engineering Handbook](docs/ENGINEERING_HANDBOOK.md)** - 아키텍처/기술스택/개발/테스트 기준
- **[API Reference](docs/API.md)** - Backend API 엔드포인트 계약
- **[Operations Manual](docs/OPERATIONS_MANUAL.md)** - 배포/운영/장애 대응/릴리즈 절차
- **[Patch Notes](docs/PATCH_NOTES.md)** - 버전별 변경 이력
- **[Roadmap](docs/ROADMAP.md)** - 향후 확장 로드맵
- **[Public Service Evidence](docs/PUBLIC_SERVICE_EVIDENCE.md)** - 공개 URL/모니터링 증빙 템플릿
- **[Validation Report (Backtest/Risk)](docs/VALIDATION_BACKTEST_RISK.md)** - 정량 검증 기준 문서
- **[Incident Postmortem](docs/INCIDENT_POSTMORTEM_AI_TIMEOUT_2026-02-28.md)** - 장애 분석 및 재발 방지 기록
- **[Portfolio Final Submission](docs/PORTFOLIO_FINAL_SUBMISSION.md)** - 제출용 최종 포트폴리오 문서
- **[Service Manual HTML](docs/SERVICE_MANUAL.html)** - 브라우저 가독성 버전
- **[Render Deploy Guide](docs/DEPLOY_RENDER.md)** - Render 배포용 환경변수/서비스 설정

### 보조 문서

- **[Refresh Token 설계](docs/REFRESH_TOKEN.md)** - 인증 토큰 동작 및 다중 탭 동기화
- **[Demo Seed Guide](docs/DEMO_SEED_GUIDE.md)** - 예시 데이터 일괄 주입 가이드
- **[Contributing](CONTRIBUTING.md)** - 기여/PR 규칙
- **[Security](SECURITY.md)** - 취약점 제보 및 보안 정책
- **[CLAUDE.md](CLAUDE.md)** - Claude Code 작업 가이드

---

## Changelog

- 전체 릴리즈 히스토리: **[docs/PATCH_NOTES.md](docs/PATCH_NOTES.md)**
- 레거시 버전 기록: **[docs/CHANGELOG.md](docs/CHANGELOG.md)**

## 환경 변수

`.env.example` 파일을 참고하여 `.env` 파일을 생성하세요.

```env
# Backend
DB_URL=jdbc:mysql://localhost:3306/stock_ai?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password
JWT_SECRET=your-secret-key-min-32-bytes
REFRESH_TOKEN_PEPPER=optional-extra-secret

# AI Service
ALPHA_VANTAGE_API_KEY=your-api-key
OPENAI_API_KEY=your-api-key
```

## 라이선스

MIT License
