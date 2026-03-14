# Postgres + Flyway Transition Plan

이 문서는 `Render Postgres` 운영 환경을 기준으로 Flyway baseline 전략과 cutover 기준을 정리한 정본이다.

## 1. Executive Summary

- 코드베이스 기준 Postgres 전용 Flyway 경로는 정리됐다.
- 신규 Postgres DB는 `db/migration-postgres/V1__baseline_schema.sql`과 `V2__align_constraints_and_indexes.sql`로 초기화된다.
- 기존 운영 Postgres DB는 `baseline-on-migrate=true`, `baseline-version=2`로 첫 배포 시 baseline DDL을 다시 실행하지 않는다.
- 운영 기본값은 다음으로 정렬됐다.
  - `SPRING_PROFILES_ACTIVE=prod,postgres`
  - `FLYWAY_ENABLED=true`
  - `FLYWAY_BASELINE_VERSION=2`
  - `JPA_DDL_AUTO=validate`
- 남은 일은 코드 작성이 아니라 실제 Render 배포에서 첫 baseline 적용과 smoke verification이다.

## 2. Current State

| 구분 | 현재 동작 | 근거 |
|---|---|---|
| 로컬 기본 개발 | MySQL 기본, Flyway 활성화, JPA `update` | `backend/src/main/resources/application.properties` |
| Postgres 로컬 검증 | `docker-compose.postgres.yml` 기준 Postgres + Flyway + JPA `validate` | `docker-compose.postgres.yml` |
| 운영 배포 | Render Postgres, Flyway 활성화, baseline version 2, JPA `validate` | `backend/src/main/resources/application-postgres.properties`, `render.yaml` |
| 테스트 | H2 기본 테스트 + Testcontainers Postgres baseline 검증 | `backend/src/test/resources/application-test.properties`, `PostgresFlywayMigrationTest` |

즉 현재 저장소는 더 이상 "Postgres에서는 Flyway를 꺼 둔 상태"가 아니다.  
이제는 "기존 운영 DB를 baseline으로 편입하면서 신규 Postgres는 Flyway로 재생 가능한 상태"다.

## 3. What Was Added

### 3.1 Postgres Baseline Migrations

- `backend/src/main/resources/db/migration-postgres/V1__baseline_schema.sql`
  - 엔티티 모델 기준 PostgreSQL baseline schema
  - LOB 문자열 컬럼은 `oid` 대신 `text`로 정리
- `backend/src/main/resources/db/migration-postgres/V2__align_constraints_and_indexes.sql`
  - 운영 로직에 필요한 unique 제약 추가
  - `ON DELETE CASCADE` 정렬
  - 핵심 조회 경로용 인덱스 추가

### 3.2 Runtime Defaults

- `application-postgres.properties`
  - `spring.flyway.enabled=${FLYWAY_ENABLED:true}`
  - `spring.flyway.locations=classpath:db/migration-postgres`
  - `spring.flyway.baseline-version=${FLYWAY_BASELINE_VERSION:2}`
  - `spring.jpa.hibernate.ddl-auto=${JPA_DDL_AUTO:validate}`
- `render.yaml`
  - `FLYWAY_ENABLED=true`
  - `FLYWAY_BASELINE_VERSION=2`
  - `JPA_DDL_AUTO=validate`
- `docker-compose.postgres.yml`
  - Postgres 로컬 검증용으로 `FLYWAY_ENABLED=true`, `JPA_DDL_AUTO=validate`

### 3.3 Verification Assets

- `PostgresSchemaGenerationTest`
  - Hibernate schema generation으로 baseline 초안 생성
- `PostgresFlywayMigrationTest`
  - 빈 PostgreSQL(Testcontainers) 인스턴스에 V1/V2가 실제 적용되는지 검증
  - 핵심 제약/인덱스/`text` 컬럼 타입 확인

## 4. Baseline Strategy

### 4.1 Fresh PostgreSQL Database

빈 DB에서는 Flyway가 versioned migration을 그대로 실행한다.

1. `V1__baseline_schema.sql`
2. `V2__align_constraints_and_indexes.sql`

결과:
- 전체 테이블 생성
- 핵심 unique/FK/index 정렬
- 앱은 `JPA validate`로만 스키마를 검증

### 4.2 Existing Production PostgreSQL Database

기존 운영 DB는 이미 테이블이 존재한다.  
이 경우 `baseline-on-migrate=true`와 `baseline-version=2`가 핵심이다.

첫 배포 시 기대 동작:
1. Flyway가 비어 있지 않은 스키마를 감지
2. `flyway_schema_history`를 version `2`로 baseline
3. `V1`, `V2`는 다시 실행하지 않음
4. 이후 `V3+`부터만 누적 적용

이 방식의 목적은 명확하다.

- 기존 운영 스키마를 덮어쓰지 않는다.
- 앞으로의 Postgres 마이그레이션부터는 Flyway 히스토리로 추적한다.

## 5. Risks Still Remaining

코드는 정리됐지만, 운영 전환에는 여전히 확인해야 할 항목이 있다.

1. 실제 Render DB가 baseline version 2 전략과 충돌하지 않는지 첫 배포에서 확인해야 한다.
2. 운영 DB에 과거 수동 DDL 차이가 있다면 `JPA validate` 또는 후속 `V3+` 마이그레이션에서 드러날 수 있다.
3. 로컬 기본 DB는 아직 MySQL이다. 개발/운영 DB 방언 완전 일치까지는 남아 있다.

즉 "운영 Postgres를 Flyway 히스토리에 편입하는 코드 작업"은 끝났지만, "개발 환경까지 Postgres로 완전 통일"은 별도 단계다.

## 6. Deployment Runbook

### 6.1 Pre-Deploy

1. Render DB 백업
2. Backend env 확인
   - `SPRING_PROFILES_ACTIVE=prod,postgres`
   - `FLYWAY_ENABLED=true`
   - `FLYWAY_BASELINE_VERSION=2`
   - `JPA_DDL_AUTO=validate`
   - `JWT_SECRET`
   - `REFRESH_TOKEN_PEPPER`
3. `DB_URL`이 `jdbc:postgresql://...` 형식인지 확인

### 6.2 First Deploy After This Change

1. Backend 배포 시작
2. Flyway 로그 확인
   - 신규 DB면 `V1`, `V2` 적용
   - 기존 운영 DB면 baseline version `2` 기록
3. `/actuator/health` 확인
4. 수동 스모크 테스트 수행
   - 로그인
   - 워치리스트
   - 포트폴리오 생성/삭제
   - 포지션 생성/삭제

### 6.3 Rollback Signal

아래 중 하나면 즉시 롤백 또는 배포 중지 검토:

- Flyway가 baseline 대신 V1/V2를 기존 운영 DB에 실행하려고 함
- `JPA validate` 실패
- 로그인/워치리스트/포트폴리오 삭제가 배포 직후 깨짐

## 7. Definition of Done

아래 조건을 만족하면 "Postgres + Flyway 정리 완료"로 본다.

- 빈 Postgres 인스턴스에서 Flyway만으로 스키마 생성 가능
- Testcontainers 검증 통과
- Render 운영 DB가 baseline version 2로 편입됨
- 앱이 `JPA validate` 상태로 정상 부팅
- 이후 Postgres 스키마 변경이 `db/migration-postgres` 기준으로 누적 관리됨

## 8. Recommended Next Step

다음 단계는 DB 방언을 완전히 하나로 줄이는 것이다.

1. 로컬 기본 개발 DB를 Postgres로 전환
2. MySQL 전용 마이그레이션 경로는 레거시로 동결
3. 신규 스키마 변경은 Postgres migration만 정본으로 관리

이 단계까지 끝나면 "개발/테스트/운영 DB 일치"를 포트폴리오와 면접에서 더 강하게 설명할 수 있다.
