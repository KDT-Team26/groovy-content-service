# groovy-content-service

**Groovy**(태그 기반 스터디 매칭 플랫폼) MSA의 **회고록(Memoir) · 댓글 · 좋아요** 도메인을 담당하는 백엔드 서비스입니다. 스터디 활동을 기록하는 회고록 게시글과, 그에 달리는 댓글/좋아요를 처리합니다.

## 1. 이 레포는 무엇인가

Groovy 폴리레포 중 `content-service` 하나만 담은 독립 배포 단위입니다. identity-service와 study-service 둘 다에 동기 호출하는 유일한 서비스이며(작성자 이름 조회 + 스터디 정보/경험치 적립), 5개 공유 라이브러리를 전부 사용합니다.

## 2. 주요 기능

- 회고록 작성 / 목록 조회(검색·정렬·페이징) / 상세 조회 / 수정 / 삭제
- 회고록 작성 가능한 "내 스터디" 목록, 내가 쓴 회고록 목록
- 좋아요 / 좋아요 취소
- 댓글 작성 / 목록 조회 / 수정 / 삭제
- 회고록/댓글 작성 시 소속 스터디에 **경험치 적립**을 요청(부가 효과 — 실패해도 본 동작은 유지)

## 3. 시스템 아키텍처

### 데이터베이스

| 항목 | 값 |
|---|---|
| DB(스키마)명 | `content_db` |
| 전용 계정 | `content_service` (다른 서비스 DB 접근 불가) |
| DBMS | MySQL 8.0, Flyway (`V1__baseline_schema.sql`) |

| 테이블 | 역할 | 주요 컬럼 | 관계 |
|---|---|---|---|
| `memoirs` | 회고록 | `id` PK, `title`, `content`, `study_id`, `author_id` | `study_id` → **study_db.studies.id**, `author_id` → **identity_db.users.id**(둘 다 서비스 간 참조, FK 없음) |
| `memoir_comments` | 댓글 | `id` PK, `memoir_id` FK→`memoirs.id`, `content`, `author_id` | `memoir_id`는 같은 DB 내 FK, `author_id` → identity_db.users.id |
| `memoir_likes` | 좋아요 | `id` PK, `memoir_id` FK→`memoirs.id`, `user_id`, unique(`memoir_id`,`user_id`) | `user_id` → identity_db.users.id |
| `outbox_events` | Transactional Outbox | `id` PK, `event_id`(unique), `event_type`, `payload`(JSON), `published` | Kafka 발행 대기열 (댓글/좋아요 알림) |

## 4. 기술 스택

| 카테고리 | 기술 |
|---|---|
| Language / Framework | Java 21, Spring Boot 4.1.0 |
| Build | Gradle 멀티모듈 (`event-contract`, `observability`, `web-common`, `security-common`, `client-common` 5개 lib 전부 + `services:content-service`) |
| Data | Spring Data JPA + MySQL, Flyway |
| Security | `security-common`의 `JwtAuthenticationFilter`/`JwksKeyLocator` — JWT 검증만 |
| 메시징 | Spring Kafka — Transactional Outbox 패턴으로 발행 |
| 동기 통신 | `RestClient` + resilience4j — `client-common`의 `UserServiceClient`(직접 사용), 로컬 `StudyServiceClient` |
| Observability | Actuator, Micrometer(Prometheus), OpenTelemetry(OTLP → Tempo) |

## 5. 다른 MSA 서비스와의 네트워크 호출 관계

### 동기 HTTP (Out)

| 대상 | 엔드포인트 | 용도 |
|---|---|---|
| identity-service | `GET /.well-known/jwks.json` | JWT 서명 검증 |
| identity-service | `GET /api/users/names?ids=...` | 회고록/댓글 작성자 이름 배치 조회 |
| study-service | `GET /api/studies/{id}` | 스터디 상세 + 멤버십 확인 |
| study-service | `GET /api/studies/summary?ids=...` | 회고록 목록에 스터디 제목/레벨/경험치 배치 표시 |
| study-service | `POST /api/studies/{id}/exp` | 회고록/댓글 작성 시 경험치 적립 |
| study-service | `GET /api/users/me/studies`, `GET /api/users/me/applications` | "내 스터디" 옵션 목록 조합 |

이 서비스는 identity-service와 study-service **둘 다**에 의존하는 유일한 서비스입니다(calendar-service는 study-service만 호출).

### 비동기 Kafka (Out)

댓글/좋아요 발생 시 트랜잭션 내에서 `outbox_events`에 기록 → 스케줄러가 Kafka 토픽 **`notification-events`**로 발행 → notification-service가 소비해 SSE 푸시.

### 외부 노출

api-gateway가 `Path=/api/memoirs/**`를 이 서비스로 라우팅합니다.

## 6. 로컬 실행 방법

### 방법 A — 독립 빌드

```bash
./gradlew :services:content-service:bootJar
docker build -t groovy-content-service .
```

### 방법 B — 로컬 JVM (MySQL/Kafka/identity-service/study-service 필요)

```bash
export SPRING_DEV_DB_URL="jdbc:mysql://localhost:3306/content_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul"
export SPRING_DEV_DB_USERNAME=content_service
export SPRING_DEV_DB_PASSWORD=content_service_local_only_pw
export JWT_JWKS_URL=http://localhost:8081/.well-known/jwks.json
export IDENTITY_SERVICE_URL=http://localhost:8081
export STUDY_SERVICE_URL=http://localhost:8082
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT

./gradlew :services:content-service:bootRun
```

### 방법 C — 전체 스택 (권장)

```bash
# 원본 Groovy 레포 루트에서
cp .env.example .env
docker compose -f docker-compose.local.yml up -d --build content-service
```

기본 포트는 `8083`입니다.

## 7. 기존 모노레포에서 어느 부분을 떼온 것인가

레거시 모놀리스 `groovy/`의 `domain/memoir` 패키지(회고록, 댓글, 좋아요)가 이 서비스로 이전되었습니다.

- **그대로 옮겨온 것**: 엔티티/서비스/레포지토리, 댓글/좋아요를 Memoir와 함께 조회·캐스케이드 삭제하는 단일 Aggregate 구조
- **MSA 전환 과정에서 새로 생긴 것**: `client` 패키지의 `UserServiceClient`(공유)/`StudyServiceClient`(로컬) — 원래 같은 프로세스 내 JPA 조인/서비스 호출로 처리하던 것을, 서비스 분리 후 동기 HTTP 호출로 재구현. `outbox`/`notification` 패키지도 신규.
- **격리 작업 상세**: `docs/transfer/groovy-content-service.md`(원본 레포 기준) — content-service를 끝으로 계획서상 6개 서비스(gateway/notification/identity/study/calendar/content) 격리가 완료되었다고 기록되어 있습니다.

## 8. 모니터링 스택에서 관측되는 부분

| 스택 | 관측 내용 |
|---|---|
| **Prometheus** | `job=content-service`로 `:8083/actuator/prometheus` 15초 스크레이프. JVM/HikariCP(`content_db` 풀)/HTTP 지표 |
| **Alertmanager** | HikariCP 커넥션 대기, JVM 힙 40% 초과, CPU 95% 초과 알림 |
| **Grafana** | `springboot-dashboard.json`(JVM), `backend-app-logs-dashboard.json`(Loki 로그) |
| **Loki + Alloy** | 컨테이너 stdout JSON 로그 자동 수집 |
| **Tempo** | 요청이 identity-service와 study-service 양쪽을 순차 호출하는 흐름이 하나의 트레이스로 이어짐 — 이 서비스가 가장 많은 서비스 간 홉을 가진 트레이스를 만듭니다 |
