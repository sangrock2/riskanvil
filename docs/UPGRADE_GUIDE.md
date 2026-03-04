# v0.4.0 업그레이드 가이드

## 📋 개요

Stock-AI v0.3.0에서 v0.4.0으로 업그레이드하는 방법을 설명합니다.

---

## ✅ 업그레이드 전 확인사항

- [ ] 현재 v0.3.0 버전 실행 중
- [ ] 데이터베이스 백업 완료
- [ ] Node.js 16+ 설치됨
- [ ] Java 21 설치됨
- [ ] Python 3.8+ 설치됨

---

## 🚀 업그레이드 단계

### 1단계: 코드 업데이트

```bash
# 저장소 최신화
cd stock-ai
git fetch origin
git pull origin main

# 또는 직접 다운로드
# https://github.com/yourusername/stock-ai/archive/refs/heads/main.zip
```

### 2단계: 백엔드 업그레이드

```bash
cd backend

# 의존성 업데이트
./gradlew clean build -x test

# 또는 점진적 빌드
./gradlew build -x test

# Flyway 마이그레이션 자동 실행 (부팅 시)
./gradlew bootRun
```

**마이그레이션 내용**:
- 새 테이블: `watchlist_tag`, `watchlist_item_tag`
- 새 컬럼: `watchlist.notes`
- 자동 실행: 처음 부팅 시 V9 마이그레이션 수행

### 3단계: 프론트엔드 업그레이드

```bash
cd frontend

# 의존성 설치
npm install

# 새 Service Worker 확인
# src/index.js에서 serviceWorker.register() 호출

# 개발 서버 시작
npm start
```

### 4단계: AI 서비스 업그레이드

```bash
cd ai

# 의존성 확인
pip install -r requirements.txt

# 개발 서버 시작
python -m uvicorn main:app --reload --port 8000
```

**새 엔드포인트**:
- `GET /ohlc?ticker=AAPL&market=US&days=90` - OHLC 데이터

### 5단계: 통합 테스트

```bash
# 모든 서비스 실행 확인
# - Backend: http://localhost:8080
# - Frontend: http://localhost:3000
# - AI Service: http://localhost:8000

# 각 기능 테스트
npm test   # frontend 테스트 (선택)
```

---

## 🔄 데이터 마이그레이션

### 자동 처리 항목

다음은 자동으로 처리됩니다:

✅ 워치리스트 테이블 변경:
- 새 컬럼 `notes` 추가
- 새 관계 `tags` 추가 (ManyToMany)

✅ 새 테이블 생성:
- `watchlist_tag` - 태그 저장소
- `watchlist_item_tag` - 교차 참조

### 수동 처리 필요 항목

없음 - 모든 것이 자동으로 처리됩니다.

---

## 📝 설정 업데이트

### Backend (application.properties)

기존 설정 유지, 추가 설정 없음

### Frontend (localStorage)

기존 사용자 설정은 자동으로 다음으로 마이그레이션됩니다:

```javascript
// 자동으로 생성됨:
localStorage.dashboard_config = JSON.stringify([...])
localStorage.analysis_options = JSON.stringify({...})
```

사용자가 수동으로 설정할 필요 없음.

---

## 🆕 새 기능 활성화

### 1. 대시보드 활성화

```
경로: /dashboard
방법: 자동 (홈 경로 변경)
```

### 2. Service Worker 활성화

```javascript
// 자동 등록 (src/index.js)
// 브라우저 F12 > Application > Service Workers 확인
```

### 3. 리포트 생성 기능

```
InsightDetail 페이지에서:
1. "Report Generator" 섹션 확인
2. 템플릿 선택
3. 섹션 체크
4. "Generate Report" 클릭
```

### 4. 분석 옵션

```
InsightDetail 또는 Analyze 페이지:
1. "Analysis Options" 패널 열기
2. 기간, 지표, 벤치마크 선택
```

### 5. 신뢰도 표시

```
InsightDetail 페이지:
1. "Analysis Confidence" 섹션 자동 표시
2. 신뢰도 등급 (A~F) 확인
```

---

## ⚠️ 주의사항

### 호환성

- ✅ v0.3.0 → v0.4.0: 완전히 호환 (자동 마이그레이션)
- ⚠️ v0.4.0 → v0.3.0: 다운그레이드 불가능 (새 컬럼)

### 성능

- 새 Service Worker로 인한 약간의 초기 로드 시간 증가 (한 번만)
- 이후 캐싱으로 인한 로드 시간 단축

### 스토리지

- localStorage 사용량 증가 (대시보드 + 분석 옵션)
- 일반적으로 1~2KB 정도

---

## 🐛 트러블슈팅

### 마이그레이션 실패

```bash
# 원인: V9 마이그레이션 충돌
# 해결:
1. DB 백업
2. 수동 롤백 (선택)
3. 로그 확인: backend/logs/

# 마이그레이션 강제 재실행
./gradlew flywayRepair  # (주의: 오직 특수 경우만)
./gradlew bootRun
```

### Service Worker 등록 안 됨

```javascript
// 해결 방법:
// 1. 개발자 도구 확인
F12 > Application > Service Workers > 확인

// 2. 캐시 비우기
F12 > Application > Clear storage > 비우기

// 3. 페이지 새로고침
Ctrl + Shift + R

// 4. 수동 등록 (console)
navigator.serviceWorker.register('/service-worker.js')
```

### 리포트 생성 시간 초과

```
예상 시간: 30~60초
문제: 네트워크 느림 또는 API 토큰 초과

해결:
1. 인터넷 속도 확인
2. OpenAI API 키 확인
3. 기존 설정 캐시 제거
```

### 오프라인 모드 데이터 없음

```
원인: Service Worker 미등록 또는 미캐시

해결:
1. 온라인 상태에서 먼저 방문 (캐싱)
2. Service Worker 등록 확인
3. 캐시 비우기 후 재방문
```

---

## ✨ 새 기능 테스트 체크리스트

### 대시보드
- [ ] /dashboard 경로 접속
- [ ] 위젯 표시/숨김 토글
- [ ] 위젯 순서 변경
- [ ] 리셋 기능 작동
- [ ] 새로고침 후에도 설정 유지

### 리포트 생성
- [ ] 템플릿 선택 가능
- [ ] 섹션 체크박스 작동
- [ ] "Generate Report" 버튼 작동
- [ ] PDF 내보내기 작동
- [ ] Markdown 내보내기 작동
- [ ] 클립보드 복사 작동

### 오프라인 지원
- [ ] Service Worker 등록 확인
- [ ] 온라인 상태 표시 안 됨 (정상)
- [ ] 개발자 도구 Network Offline 선택
- [ ] 캐시된 데이터 표시 확인
- [ ] 온라인 복귀 시 "Back online" 메시지

### 분석 옵션
- [ ] Analysis Options 패널 열림
- [ ] 기간 선택 가능
- [ ] 지표 카테고리별 선택 가능
- [ ] 벤치마크 선택 가능
- [ ] 고급 옵션 설정 가능

### 신뢰도 표시
- [ ] InsightDetail에서 "Analysis Confidence" 섹션 보임
- [ ] 신뢰도 등급 (A~F) 표시
- [ ] 데이터 품질 진행률 바 표시
- [ ] 카테고리별 인사이트 분류 표시

---

## 📚 추가 문서

- `PATCH_NOTES.md` - 상세 변경사항
- `CHANGELOG.md` - 버전별 변경 이력
- 각 컴포넌트의 코멘트 참고

---

## 🆘 지원 및 피드백

업그레이드 중 문제가 발생하면:

1. **로그 확인**
   - Backend: `logs/` 디렉토리
   - Frontend: 브라우저 콘솔 (F12)
   - AI: 터미널 출력

2. **GitHub Issues 확인**
   - 유사한 문제 검색
   - 새 이슈 생성

3. **디버깅**
   - 단계별 배포 (Backend → Frontend → AI)
   - 각 서비스 독립적 테스트

---

## ✅ 업그레이드 완료!

축하합니다! Stock-AI v0.4.0으로 성공적으로 업그레이드했습니다.

새로운 기능을 즐겨보세요:
- 📊 대시보드 커스터마이징
- 📝 강화된 리포트 생성
- 🔌 오프라인 지원
- 🔧 분석 옵션 확대
- 🧠 AI 신뢰도 표시

**Happy Analyzing! 🚀**

---

마지막 업데이트: 2025-01-26
