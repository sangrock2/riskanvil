# Architecture Case Study

이 문서는 Stock-AI를 "왜 이렇게 설계했는가" 관점에서 설명하는 케이스 스터디다. 기존 `ARCHITECTURE_DECISIONS.md`가 상세 ADR 모음이라면, 이 문서는 면접/포트폴리오에서 바로 설명 가능한 수준으로 판단과 트레이드오프를 요약한다.

## 1. Problem Definition

이 프로젝트가 해결하려는 문제는 단순 시세 조회가 아니다.

- 사용자 인증이 있는 투자 도구가 필요하다.
- 시세, 인사이트, 백테스트, 포트폴리오, 워치리스트가 한 제품 안에서 이어져야 한다.
- AI 분석은 느리고 실패 가능성이 높다.
- 배포 환경은 Render처럼 저비용 환경을 전제로 한다.
- 사용자에게는 쉬운 UX가 필요하지만 운영자는 장애 원인을 추적할 수 있어야 한다.

이 제약 때문에 "기능이 돌아가는 코드"보다 "운영 가능한 구조"가 중요했다.

## 2. High-Level System Shape

현재 시스템은 3개 서비스로 나뉜다.

1. Frontend: React SPA
2. Backend: Spring Boot API + 인증 + 정책 경계
3. AI Service: FastAPI + 데이터/LLM/수치 계산

이 구조를 택한 이유는 계산 모델과 책임이 다르기 때문이다.

- 프론트는 사용자 상호작용과 상태 동기화에 집중한다.
- 백엔드는 인증, 권한, 캐시, 감사 로그, 에러 계약, 요청 추적을 맡는다.
- AI는 Python 생태계의 데이터/분석 라이브러리를 활용한다.

## 3. Why Frontend Does Not Call AI Directly

브라우저에서 AI 서버를 직접 호출하지 않고 반드시 Backend를 거치게 한 이유는 다음과 같다.

1. API 키와 내부 정책을 브라우저에 노출하지 않기 위해
2. 인증과 권한을 한 곳에서 강제하기 위해
3. 캐시, retry, circuit breaker, requestId 전파를 서버에서 통합하기 위해
4. 프런트가 외부 시스템 오류 형식을 직접 다루지 않게 하기 위해

이 결정 덕분에 프런트는 도메인 응답만 소비하고, 백엔드는 외부 장애를 흡수하는 완충 계층이 되었다.

## 4. Why Spring Boot For Backend And FastAPI For AI

### Backend를 Spring Boot로 둔 이유

- 인증/보안/필터 체인이 성숙하다.
- JPA, Actuator, Micrometer, Flyway 같은 운영 도구가 잘 붙는다.
- 타입 안정성과 테스트 인프라가 좋다.

### AI를 FastAPI로 둔 이유

- pandas, numpy, scipy, pandas-ta, yfinance, LLM SDK를 자연스럽게 쓸 수 있다.
- 모델/분석 로직을 Python에서 유지하는 것이 생산성이 높다.
- JSON API 중심 서비스로 구성하기 쉽다.

결론적으로 "정책 서버는 Java, 분석 서버는 Python"으로 역할을 나눈 셈이다.

## 5. Authentication And Session Design

인증은 `access token + refresh token` 분리 구조를 사용한다.

### 왜 access/refresh를 분리했는가

- 짧은 수명의 access token으로 노출 위험을 줄이기 위해
- refresh token으로 사용 중인 세션은 끊기지 않게 하기 위해

### 왜 refresh token을 sessionStorage에 두었는가

- 탭을 닫으면 장기 세션이 자연스럽게 끊어지게 하기 위해
- 브라우저 재실행 이후 무한 장기 로그인으로 흐르지 않게 하기 위해

### 왜 멀티탭 동기화를 별도 구현했는가

- access token만 공유하면 만료 시점에 탭별 상태가 어긋난다.
- BroadcastChannel + storage 이벤트로 인증 상태를 정렬해야 UX가 깨지지 않는다.

### 왜 JWT secret과 pepper를 fail-fast로 바꿨는가

- 인증 설정은 "없어도 뜨는" 편의 기능이 아니라 "없으면 죽어야 하는" 보안 경계이기 때문이다.
- 실제로 기본 JWT secret로 운영이 떠 버리는 위험을 코드 수준에서 막아야 했다.

## 6. Error Handling Strategy

이 프로젝트는 오류를 두 층으로 분리한다.

### 사용자용 오류

- 이해 가능한 짧은 문장
- 화면 맥락에 맞는 안내
- 예: "이미 관심종목에 추가된 종목입니다."

### 운영자용 오류

- `status`, `code`, `path`, `requestId`
- Render 로그와 Sentry에서 추적 가능한 구조화 정보

이 분리는 UX와 운영성을 동시에 잡기 위해서다. 사용자에게 JSON 에러 본문을 그대로 보여주는 것은 개발자 편의이지 제품 설계가 아니다.

## 7. Observability Design

관측성은 아래 세 가지로 나뉜다.

1. `X-Request-Id` 기반 요청 상관관계
2. Sentry 기반 예외 수집
3. Actuator/Micrometer/Prometheus 기반 수치 계측

이 세 가지를 같이 둔 이유는 하나의 도구만으로는 충분하지 않기 때문이다.

- requestId는 "이번 요청이 어디서 깨졌는지"를 찾는 데 좋다.
- Sentry는 예외 패턴과 재발 빈도를 보는 데 좋다.
- Prometheus는 latency, error rate, health를 시간축으로 보는 데 좋다.

## 8. Reliability Choices

### Redis를 선택적 의존성으로 둔 이유

- 캐시는 성능 최적화 수단이지 필수 생존 조건이 아니기 때문이다.
- Redis 장애가 전체 서비스 장애가 되면 저비용 운영 환경에서 복원력이 떨어진다.

### Retry / Circuit Breaker를 둔 이유

- AI/외부 데이터 소스는 본질적으로 느리고 불안정하다.
- 모든 실패를 사용자 요청 실패로 즉시 전파하면 체감 품질이 나빠진다.

### 수동 스모크 테스트를 유지한 이유

- 배포 후 사용자가 실제로 체감하는 문제는 E2E만으로 다 잡히지 않는다.
- 로그인, 워치리스트, 포트폴리오 삭제 같은 핵심 흐름은 사람이 한 번 더 밟아야 한다.

## 9. Testing Strategy As A Design Decision

테스트도 설계의 일부로 봤다.

- 백엔드 단위/통합 테스트
- 프론트 회귀 테스트
- 스크립트 기반 E2E 스모크
- 수동 스모크 체크리스트

특히 최근에는 "테스트가 많다"보다 "테스트를 믿을 수 있느냐"가 더 중요했다.

그래서 운영 DB를 타던 테스트를 격리했고, 실제 장애를 만든 인증/삭제/에러 UX 흐름에 회귀 테스트를 추가했다.

## 10. Important Tradeoffs We Intentionally Accepted

### 10.1 Local MySQL vs Render Postgres

아직 남아 있는 설계 부채지만, 운영 경로는 한 단계 정리했다.

- 장점: 기존 MySQL 기반 로컬 개발 흐름을 유지하면서도, 운영 Postgres는 Flyway baseline으로 히스토리에 편입시켰다.
- 단점: 로컬 기본 DB와 운영 DB가 여전히 달라 장기적으로는 방언 비용이 남아 있다.

즉 운영 안정화는 끝냈지만, 최종 상태는 로컬도 Postgres로 수렴하는 쪽이다.

### 10.2 Active Tab Session Convenience

- 장점: 사용 중인 탭에서는 재로그인 피로가 적다.
- 단점: 절대 세션 만료가 없어 정책을 더 강화하려면 추가 설계가 필요하다.

### 10.3 Render Low-Cost Hosting

- 장점: 빠른 공개 배포와 낮은 운영 비용
- 단점: cold start, 제한된 인프라 제어, 외부 의존성 지연 체감

즉 비용을 아낀 대신, 코드와 운영 문서에서 복원력/관측성을 더 챙겨야 했다.

## 11. Incidents That Changed The Design

이 프로젝트의 강점은 처음부터 완벽했다는 점이 아니라, 문제를 추적하고 구조를 고쳤다는 점이다.

### 사례 1. 백엔드 구조화 에러가 사용자 UI에 그대로 노출됨

- 문제: 사용자에게 JSON 에러 본문이 직접 보였다.
- 조치: 프런트에 사용자 친화적 에러 매퍼 추가
- 결과: 운영 로그는 유지하면서 UX 개선

### 사례 2. 백엔드 테스트가 실제 DB 오염 영향을 받음

- 문제: 테스트 실패 원인이 코드 회귀인지 환경 오염인지 구분되지 않았다.
- 조치: H2 기반 테스트 프로필 격리
- 결과: 테스트를 회귀 방지 도구로 다시 신뢰할 수 있게 됨

### 사례 3. 포트폴리오/포지션 삭제가 운영 DB FK 상태에 따라 깨짐

- 문제: 운영 스키마 편차로 삭제 API가 500을 낼 수 있었다.
- 조치: 서비스 레벨에서 종속 dividend 데이터를 선삭제
- 결과: DB 제약 편차가 있어도 삭제 흐름 방어 가능

### 사례 4. 운영에서 기본 JWT secret fallback 가능

- 문제: env 누락 시 취약한 상태로 앱이 뜰 수 있었다.
- 조치: 운영 프로필에서 fail-fast
- 결과: 배포 설정 실수를 조용히 넘기지 않게 됨

## 12. What This Project Demonstrates

이 프로젝트는 단순 기능 구현보다 아래 역량을 보여준다.

1. 멀티서비스 구조 설계
2. 인증과 세션 수명주기 설계
3. 운영 오류와 사용자 오류의 분리
4. 요청 추적, Sentry, Prometheus를 결합한 관측성 설계
5. 배포 후 문제를 재현하고 수정하고 회귀 테스트로 닫는 능력

## 13. What Should Improve Next

다음 단계는 기능 추가보다 아래가 우선이다.

1. 로컬 기본 DB를 Postgres로 전환
2. 로그인과 캐시 hit/miss 메트릭을 실제 대시보드와 주간 기준선으로 연결
3. 프런트 Web Vitals의 릴리즈별 추세 기록
4. 부하 테스트 결과의 정식 릴리즈 기록화

## 14. How To Explain This Project In One Paragraph

React, Spring Boot, FastAPI로 구성한 3서비스 투자 분석 플랫폼을 설계했고, 인증·세션·AI 연동·포트폴리오·워치리스트 기능뿐 아니라 Render 운영 환경에서 발생한 설정 불일치, 테스트 오염, 삭제 실패, 에러 UX 문제를 직접 추적해 보안 fail-fast, 회귀 테스트, 관측성, 수동/자동 스모크 절차로 안정화했다.
