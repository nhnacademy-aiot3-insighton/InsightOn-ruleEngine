# insighton-ruleengine

InsightOn 플랫폼의 Rule Engine. 센서 텔레메트리를 실시간으로 평가해 자동화(Flow)를 실행하는 서비스로, "언제 시작해서, 무엇을 확인하고, 무엇을 실행할지"를 노드 그래프로 표현하고 그 실행을 담당한다.

## 역할

- Flow(자동화) 도메인의 CRUD와 실행 — 사용자가 만든 자동화를 저장·수정·활성화/비활성화·보관(archive)/복구·삭제한다.
- Core가 MQTT로 수신해 RabbitMQ로 발행한 센서 텔레메트리를 구독해, 활성(ACTIVE) Flow의 조건을 평가하고 조건을 만족하면 액션을 실행한다.
- 액션 실행 시 Core에 액추에이터 제어를 요청하거나 Alert(알림)를 발행한다.
- AI 서비스가 리포트 분석 결과로 자동화 초안(Flow)을 생성/조회할 수 있도록 서비스 간 전용 API(`/internal/v1/flows`)를 제공한다.
- 고정 2인스턴스 active-active로 운영되어, 한 인스턴스 장애 시에도 텔레메트리 처리와 스케줄 실행이 끊기지 않는다 (RabbitMQ consistent-hash 큐 분할 + Redis 기반 소유권/락).

## 기술 스택

- Java 21, Spring Boot 3.5
- Spring Data JPA + PostgreSQL (`engine` 스키마, `ddl-auto=validate` — 스키마 변경은 별도 SQL 마이그레이션으로 선적용)
- Spring AMQP (RabbitMQ) — 텔레메트리 구독(consistent-hash exchange), 그룹/위치 삭제 이벤트 구독
- Spring Data Redis — EVENT_GATE 횟수/쿨다운 상태, 스케줄 실행 락, Flow 라우팅 캐시
- Spring Cloud OpenFeign — Core 서비스 호출(그룹 권한 조회, 액추에이터 제어 요청)
- springdoc-openapi (WebMVC UI) — Swagger 문서
- Caffeine — 로컬 캐시, Micrometer + Zipkin — 관측성, Lombok, Maven

## 도메인 모델: Flow / Node / Link

하나의 Flow는 여러 Node가 Link로 연결된 그래프다. Node는 세 범주로 나뉜다.

| 범주 | node_type | 설명 |
| --- | --- | --- |
| 트리거 | `SENSOR` | 특정 센서(`sensorId`)의 이벤트로 시작 |
| 트리거 | `LOCATION` | 위치에 속한 모든 센서의 이벤트로 시작 (동일 metric은 최신 값 사용) |
| 트리거 | `SCHEDULE` | 텔레메트리 없이 cron(`ValidCron`)으로 직접 실행 |
| 필터 | `THRESHOLD` | SpEL 표현식(`expression`)으로 현재 이벤트의 metric 조건을 평가. 여러 조건의 AND는 Link 직렬 연결로 표현 |
| 필터 | `TIME_WINDOW` | 실행 시각이 `[startTime, endTime)` 범위인지만 판단 (상태 없음, 자정 넘김 지원) |
| 필터 | `EVENT_GATE` | 실행 안전장치. `requiredCount`회를 `countWindowSeconds` 안에 채워야 통과시키고, 통과 후 `cooldownSeconds` 동안 재통과를 막는다. `requiredCount=1`은 "통과 후 쿨다운"만 있는 최소 실행 간격으로 동작. 판정은 Redis Lua 스크립트로 원자 처리 |
| 액션 | `ACTUATOR_CONTROL` | Core에 기기 제어 명령(`actuatorType`/`command`/`commandValue`)을 요청. 지원 명령 목록은 `ActuatorControlParams`에 화이트리스트로 고정 |
| 액션 | `ALERT` | 제목/중요도(`Severity`)/메시지로 알림 발행. 반복 억제는 앞단 `EVENT_GATE`가 담당 |

> 과거 있었던 `TIMER`, `EXTERNAL_NOTIFICATION` node_type은 제거되었고 `EVENT_GATE`로 통합되었다. DB의 `nodes_node_type_check` 제약도 이 변경에 맞춰 갱신되어야 하며 (`project-docs/.../db-migrations/2026-09-03-reset-flows-and-finalize-event-gate.sql`), 마이그레이션 전에는 `EVENT_GATE` Node 저장이 제약 위반으로 실패한다.

## API 문서 (Swagger)

로컬에서 기동하면 다음 경로로 확인할 수 있다.

- Swagger UI: `http://localhost:8200/swagger-ui/index.html` (아래 [로컬 실행](#로컬-실행)에서처럼 `server.port=8200`으로 띄웠을 때 기준. 패키지 기본값은 8080)
- OpenAPI 스펙(JSON): `http://localhost:8200/v3/api-docs`

Gateway를 통해서는 `/ruleengine/v3/api-docs`로 프록시되며, 다른 서비스 문서와 함께 Gateway의 `/api/swagger`에서 통합해 볼 수 있다.

### 사용자용 API — `/api/v1/flows` (Gateway가 `X-User-Id` 헤더를 주입, MEMBER 이상 그룹 소속 필요)

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/v1/flows?groupId=` | Flow 생성 (201) |
| `GET` | `/api/v1/flows?groupId=&locationId=&status=` | 조건에 맞는 Flow 목록 조회 (locationId만 주면 400) |
| `GET` | `/api/v1/flows/{flowId}?groupId=` | 보관된 Flow 포함 상세 조회 |
| `PUT` | `/api/v1/flows/{flowId}?groupId=` | 기존 Flow를 보관하고 수정 내용으로 새 Flow 저장 |
| `PUT` | `/api/v1/flows/{flowId}/status?groupId=` | `ACTIVE`/`INACTIVE` 전환 |
| `POST` | `/api/v1/flows/{flowId}/archive?groupId=` | 휴지통(`ARCHIVED`)으로 이동 |
| `POST` | `/api/v1/flows/{archivedFlowId}/restore?groupId=` | 휴지통에서 `INACTIVE`로 복구 |
| `DELETE` | `/api/v1/flows/{flowId}?groupId=` | 휴지통 Flow 영구 삭제 (204) |

### 서비스 간 전용 API — `/internal/v1/flows` (인증/권한 체크 없음, 게이트웨이 뒤 내부망 전용)

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/internal/v1/flows?groupId=` | AI가 리포트 분석 결과로 Flow 초안 생성 |
| `GET` | `/internal/v1/flows?groupId=&locationId=` | 해당 위치의 ACTIVE Flow 전체 조회 (AI는 `[AI] ` 접두어로 자기 것만 구분) |

## 로컬 실행

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

dev 프로파일은 기본적으로 **팀 공유 dev 인프라**(DB/RabbitMQ/Redis)와 `insighton-core`(`http://localhost:8100`)를 향한다 — `DB_HOST`/`DB_PASSWORD`, `RABBITMQ_HOST`/`RABBITMQ_PASSWORD`, `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`/`REDIS_DATABASE` 환경변수가 그 접속 정보다. 완전히 로컬 인프라(로컬 PostgreSQL/RabbitMQ/Redis, 로컬에서 띄운 Core)로 붙이려면 이 값들과 `service-url.core`를 전부 `-D` 시스템 프로퍼티로 오버라이드해야 한다. 기본 포트는 8080이며, 로컬에서 여러 서비스를 동시에 띄울 때는 흔히 `-Dserver.port=8200`처럼 겹치지 않게 오버라이드한다.

### 텔레메트리 파이프라인을 로컬에서 실제로 태우려면

기본값만으로는 조용히 동작하지 않는 부분이 있다.

- RabbitMQ에 `rabbitmq_consistent_hash_exchange` 플러그인이 활성화돼 있어야 `insighton.core.telemetry.exchange-v2` Exchange가 정상 선언된다 (`rabbitmq-plugins enable rabbitmq_consistent_hash_exchange`).
- `rule-engine.telemetry-routing.enabled`(환경변수 `TELEMETRY_ROUTING_ENABLED`)는 기본값이 `false`라, 명시적으로 `true`로 켜지 않으면 텔레메트리 큐 구독 자체가 시작되지 않는다.
- 큐는 16개로 해시 분할되고, 2인스턴스 active-active를 가정해 `owned-queue-indices`(`TELEMETRY_OWNED_QUEUE_INDICES`)는 정확히 짝수 8개(`0,2,4,6,8,10,12,14`) 또는 홀수 8개(`1,3,5,7,9,11,13,15`) 세트여야 한다.

## 테스트

```bash
./mvnw test
```

## Docker

```bash
docker build -t insighton-ruleengine .
docker run -p 8080:8080 insighton-ruleengine
```

멀티스테이지 빌드(Maven → JRE 21)이며, `/actuator/health` 기반 `HEALTHCHECK`가 포함되어 있다.

## CI/CD

`main` / `dev-deploy` / `dev` 브랜치 push 및 PR 시 [InsightOn-infra](https://github.com/nhnacademy-aiot3-insighton/InsightOn-infra)의 재사용 워크플로우(`rule-engine-ci-cd.yml`)를 호출해 빌드/배포한다.
