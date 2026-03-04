# Changelog

모든 주목할 만한 변경사항이 이 파일에 기록됩니다.

> 참고: 최신 릴리즈 기준 변경 이력은 `PATCH_NOTES.md`를 우선 사용합니다.

형식은 [Keep a Changelog](https://keepachangelog.com/)를 기반으로 하며,
버전 관리는 [Semantic Versioning](https://semver.org/)을 따릅니다.

---

## [0.4.0] - 2025-01-26

### ✨ Added (추가)

#### Frontend - New Components
- `Dashboard.js` - 커스터마이즈 가능한 위젯 기반 대시보드
- `ReportGenerator.js` - 고급 리포트 생성 및 내보내기 컴포넌트
- `AnalysisOptions.js` - 분석 옵션 선택 패널 (기간, 지표, 벤치마크)
- `InsightConfidence.js` - AI 분석 신뢰도 및 데이터 품질 시각화
- `InteractiveChart.js` - 줌/팬 기능이 있는 인터랙티브 차트
- `AdvancedCharts.js` - 캔들스틱, 거래량 차트 컴포넌트
- `OfflineIndicator.js` - 온/오프라인 상태 표시 컴포넌트

#### Frontend - Utilities
- `serviceWorker.js` - Service Worker 등록 및 캐시 관리 유틸리티
- `debounce.js` (확장) - throttle 함수 추가

#### Frontend - CSS Modules
- `Dashboard.module.css` - 대시보드 레이아웃 및 위젯 스타일
- `ReportGenerator.module.css` - 리포트 생성 UI 스타일
- `AnalysisOptions.module.css` - 분석 옵션 패널 스타일
- `InsightConfidence.module.css` - 신뢰도 표시 스타일
- `InteractiveChart.module.css` - 인터랙티브 차트 스타일
- `AdvancedCharts.module.css` - 캔들스틱 차트 스타일
- `OfflineIndicator.module.css` - 오프라인 인디케이터 스타일

#### Frontend - Service Worker
- `public/service-worker.js` - 오프라인 캐싱 및 백그라운드 동기화

#### Backend - New Entities & DTOs
- `WatchlistTag.java` - 워치리스트 태그 엔티티
- `WatchlistTagRepository.java` - 태그 저장소
- `WatchlistTagService.java` - 태그 서비스 로직
- `WatchlistTagController.java` - 태그 API 엔드포인트
- `TagCreateRequest.java` - 태그 생성 요청 DTO
- `TagUpdateRequest.java` - 태그 업데이트 요청 DTO
- `TagResponse.java` - 태그 응답 DTO

#### Backend - New Endpoints
- `GET /api/market/ohlc` - OHLC (시가, 고가, 저가, 종가) + 거래량 데이터
- `POST /api/watchlist/{id}/tags` - 워치리스트 항목에 태그 추가
- `DELETE /api/usage/cleanup` - 오래된 사용 기록 삭제

#### Backend - Database
- `V9__add_watchlist_tags.sql` - 워치리스트 태그 테이블 마이그레이션

#### AI Service - New Endpoints
- `GET /ohlc` - Alpha Vantage에서 OHLC 데이터 가져오기

#### Features
- ✅ 대시보드 커스터마이징 (위젯 표시/숨김, 순서 변경)
- ✅ 리포트 생성 템플릿 (Detailed, Summary, Technical, Fundamental)
- ✅ 리포트 섹션 선택 기능
- ✅ PDF, Markdown, 클립보드로 리포트 내보내기
- ✅ Service Worker 기반 오프라인 캐싱
- ✅ 온/오프라인 상태 시각적 표시
- ✅ 분석 기간 선택 (30일 ~ 1년)
- ✅ 지표 카테고리별 선택 (Technical, Fundamental, Sentiment)
- ✅ 벤치마크 비교 기능 (SPY, QQQ, DIA, IWM, VTI)
- ✅ AI 신뢰도 등급 표시 (A~F)
- ✅ 데이터 품질 지표 시각화
- ✅ 카테고리별 인사이트 분류
- ✅ 인터랙티브 차트 (줌, 팬, 슬라이더)
- ✅ 캔들스틱 및 거래량 차트
- ✅ SVG 차트 스냅샷 다운로드

### 🔧 Changed (수정)

#### Frontend
- `App.js` - Dashboard 라우트 추가, 홈경로 변경 (/ → /dashboard)
- `NavBar.js` - Dashboard 네비게이션 링크 추가
- `InsightDetail.js` - ConfidenceDetails, InteractiveChart, OHLCVolumeChart 통합
- `index.js` - Service Worker 등록 코드 추가
- `api/ai.js` - fetchOHLC 함수 추가
- `api/http.js` - 토큰 만료 처리 개선
- `package.json` - recharts 버전 업데이트

#### Backend
- `MarketCacheService.java` - InsightRequest 생성자 호출 수정 (새 필드 추가에 따른 변경)
- `AiClient.java` - ohlc() 메서드 추가, 캐싱 설정
- `MarketController.java` - /ohlc 엔드포인트 추가
- `InsightRequest.java` - 분석 옵션 필드 추가 (indicators, benchmark, includeForecasts, compareWithSector)

#### AI Service
- `main.py`:
  - `ReportRequest` - sections, template 필드 추가
  - `InsightRequest` - 분석 옵션 필드 추가
  - `/report` 엔드포인트 - 템플릿 기반 프롬프트 최적화
  - `/insights` 엔드포인트 - 분석 옵션 지원

### 🐛 Fixed (버그 수정)

- ✅ InsightRequest 레코드 생성자 오류 해결 (5개 vs 9개 파라미터)
- ✅ Service Worker 캐시 전략 최적화
- ✅ 대시보드 위젯 순서 정렬 로직 수정

### 🎨 Improved (개선)

#### UI/UX
- 대시보드 커스터마이징 UI (직관적인 토글 및 순서 변경)
- 리포트 생성 UI (템플릿 선택, 섹션 체크박스)
- 분석 옵션 UI (탭 기반, 카테고리별 구성)
- 신뢰도 표시 (배지, 진행률 바, 컬러 코딩)
- 오프라인 상태 피드백 (부드러운 애니메이션)

#### Performance
- Service Worker 캐싱으로 로드 시간 단축
- API 응답 캐싱 (Network-First 전략)
- 정적 자산 캐싱 (Cache-First 전략)

#### Code Quality
- 에러 처리 개선
- 타입 안정성 개선
- 코드 가독성 개선

---

## [0.3.0] - 2025-01-20 (이전 버전)

### Added
- 차트 인터랙션 강화 (줌, 팬, 브러시)
- 고급 차트 타입 (캔들스틱, 거래량)
- OHLC 데이터 API
- 퍼포먼스 최적화 (React.memo, lazy loading, virtual list)
- 워치리스트 태그 시스템
- 데이터 관리 기능 (내보내기, 삭제)
- 인사이트 시각화 (Recharts)

---

## Version History

| 버전 | 릴리즈 날짜 | 주요 내용 |
|------|----------|---------|
| 0.4.0 | 2025-01-26 | 대시보드, 리포트 강화, 오프라인, 분석 옵션, 신뢰도 |
| 0.3.0 | 2025-01-20 | 차트 강화, OHLC, 성능 최적화, 워치리스트, 시각화 |
| 0.2.0 | 2025-01-15 | AI 분석, 백테스트, 실시간 데이터 |
| 0.1.0 | 2025-01-10 | 초기 릴리즈 |

---

## Future Roadmap

### 계획 중인 기능
- [ ] 실시간 알림 (WebSocket)
- [ ] 포트폴리오 추적
- [ ] 자동 거래 전략
- [ ] 고급 차트 지표 (더 많은 기술적 지표)
- [ ] 모바일 앱 (React Native)
- [ ] 다국어 지원
- [ ] 어두운 테마 개선
- [ ] 음성 검색

---

## Migration Guide

### v0.3.0 → v0.4.0으로 업그레이드

#### Database
```sql
-- V9 마이그레이션 실행 (자동)
-- watchlist_tag, watchlist_item_tag 테이블 생성
```

#### Frontend
```bash
npm install  # 새 의존성 설치
npm start    # 개발 서버 시작
```

#### Backend
```bash
./gradlew clean build -x test
./gradlew bootRun
```

#### Service Worker
```javascript
// 자동으로 등록됨 (index.js)
// 강제 업데이트:
navigator.serviceWorker.controller?.postMessage({type: 'CLEAR_CACHE'})
```

---

## Troubleshooting

### Service Worker가 등록되지 않음
```bash
# 해결 방법:
1. F12 > Application > Service Workers 확인
2. 캐시 비우기 (F12 > Application > Clear storage)
3. 페이지 새로고침 (Ctrl+Shift+R)
```

### 리포트 생성 시간 초과
```
예상 시간: 30~60초
- Network 느림 시 더 오래 걸릴 수 있음
- OpenAI API 토큰 초과 시 실패
```

### 오프라인 데이터 표시 안 됨
```
해결 방법:
1. 온라인 상태에서 한 번 이상 방문
2. Service Worker 등록 확인
3. localhost 개발 환경에서는 https 필요 (프로덕션)
```

---

## Contributors

- Claude Code (AI Assistant)
- 사용자 (Stock-AI 프로젝트 소유자)

---

## License

Stock-AI v0.4.0 © 2025. All rights reserved.

---

**마지막 업데이트**: 2025-01-26
