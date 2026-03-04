# Public Service Evidence

이 문서는 실제 공개 URL 운영 증빙을 수집하기 위한 체크리스트입니다.

## 1. Service URLs

- Public App URL: `https://<your-domain>`
- Backend Health URL: `https://<your-domain>/api/actuator/health` (또는 내부 운영 URL)
- AI Health URL: `https://<your-domain>/ai/health` (프록시 구성 시)

## 2. Automated Verification

아래 명령으로 접근성 검증 리포트를 생성합니다.

```powershell
pwsh -File scripts/verify_public_service.ps1 `
  -PublicAppUrl "https://your-domain.com" `
  -BackendHealthUrl "https://api.your-domain.com/actuator/health" `
  -AiHealthUrl "https://ai.your-domain.com/health"
```

출력 파일:

- `artifacts/reports/public-service-verification.json`

## 3. Monitoring Screenshots

아래 명령으로 대시보드 스크린샷을 생성합니다.

```powershell
pwsh -File scripts/capture_monitoring_screenshots.ps1 `
  -AppUrl "https://your-domain.com" `
  -PrometheusUrl "https://prometheus.your-domain.com" `
  -GrafanaUrl "https://grafana.your-domain.com"
```

출력 파일:

- `artifacts/screenshots/app-home.png`
- `artifacts/screenshots/prometheus-overview.png`
- `artifacts/screenshots/grafana-overview.png`

## 4. Evidence Matrix

| 항목 | 파일/링크 | 확인 결과 |
|---|---|---|
| App 도메인 접근 | `artifacts/reports/public-service-verification.json` |  |
| Backend 헬스 | `artifacts/reports/public-service-verification.json` |  |
| AI 헬스 | `artifacts/reports/public-service-verification.json` |  |
| 메인 화면 캡처 | `artifacts/screenshots/app-home.png` |  |
| Prometheus 캡처 | `artifacts/screenshots/prometheus-overview.png` |  |
| Grafana 캡처 | `artifacts/screenshots/grafana-overview.png` |  |

## 5. Portfolio Submission Rule

- 포트폴리오 제출 전, 본 문서의 Evidence Matrix를 모두 채웁니다.
- 누락 항목이 있으면 `PATCH_NOTES.md`에 배포 보류 사유를 기록합니다.
