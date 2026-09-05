# 테스트 계획과 현행 결과

## 1. 목적

테스트는 다음 위험을 우선 검증한다.

- 잘못된 Flow가 저장되거나 ACTIVE가 되는 위험
- 다중 인스턴스에서 Timer/Schedule/Alert 상태가 중복 처리되는 위험
- Redis 장애 중 오래된 Flow가 계속 실행되는 위험
- RabbitMQ topology·ACK·retry/DLQ 계약이 코드 설명과 달라지는 위험
- Core lifecycle 삭제와 runtime cache 사이의 경합
- 고빈도 오류 로그가 무제한 상태를 남기는 위험

## 2. 테스트 계층

| 계층 | 대상 | 대표 테스트 |
|---|---|---|
| 도메인 단위 | Flow 상태, Node/Link, params, error code | `FlowTest`, `NodeParamsValidationTest`, `ScheduleParamsTest` |
| 구조 검증 단위 | 노드·링크·경로·활성화 | `FlowStructureValidatorTest`, `FlowPathValidatorTest`, `FlowActivationValidatorTest` |
| 실행기 단위 | Trigger/Filter/Action 결과 | `*NodeExecutorTest`, `ThresholdEvaluatorTest` |
| Application 단위 | FlowRunner, router, scheduler, cleanup, log | `FlowRunnerTest`, `ActiveFlowRouterTest`, `ScheduleFlowSchedulerTest`, `ExecutionLoggerTest` |
| Web slice | Endpoint, validation, status, body | `FlowControllerTest`, `GlobalExceptionHandlerTest` |
| PostgreSQL 통합 | JPA mapping·constraint·CRUD·생명주기·수동 증분 migration | repository tests, `FlowServiceIntegrationTest`, `FlowLifecycleE2ETest`, `ActionFanOutMigrationTest` |
| Redis 통합 | runtime Lua, TTL, 동시 선점 | `RedisRuntimeRepositoryIntegrationTest`, `AlertRuntimeStateRedisIntegrationTest` |
| 공유 cache+PostgreSQL 통합 | 두 provider의 공유 snapshot 관찰과 DB fallback | `ActiveFlowDefinitionProviderCrossInstanceIntegrationTest` (`ConcurrentHashMap` 공유 cache test double 사용) |
| RabbitMQ 통합 | consistent-hash topology, lifecycle retry/DLQ | `TelemetryRoutingRabbitIntegrationTest`, `CoreLifecycleEventRabbitIntegrationTest` |
| Context smoke | 전체 Spring wiring | `InsightonRuleengineApplicationTests` |

## 3. 테스트 환경

- Java 21
- Maven Surefire + JUnit 5
- Mockito
- Testcontainers
  - PostgreSQL 16
  - Redis 7.4
  - RabbitMQ 4.1
- JaCoCo 0.8.12

전체 테스트에는 Docker daemon 접근이 필요하다. Docker 없이 일부 Redis/Rabbit test가 skip되는 것만으로 끝나지 않고 PostgreSQL context가 초기화되지 않아 suite가 실패하므로, 제한된 sandbox 결과를 release 결과로 사용하면 안 된다.

## 4. 현행 실행 결과

### 4.1 최근 실행

- 일시: 2026-09-01 17:10~17:11 KST
- 코드: `feature/action-fanout` 작업 트리 (`dev@307094b216c88a0d475c81117b7d523d5eb21ba5` 기반)
- 명령:

```bash
./mvnw test
```

### 4.2 결과

```text
Tests run: 436
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

PostgreSQL, Redis, RabbitMQ Testcontainers를 Docker 환경에서 실행했으며 전부 통과했다.
`ActionFanOutMigrationTest`는 기존 `uk_links_flow_source_port` 제약이 있는 PostgreSQL 16에 저장소의 수동 migration SQL을 직접 실행하고, 새 제약으로의 교체·서로 다른 Target fan-out 허용·완전 중복 Link 차단을 검증한다.

### 4.3 Coverage

2026-08-31 기준 실행의 `jacoco.exec`로 생성한 참고 수치이며, 이번 변경 후 report는 별도로 재생성하지 않았다.

| 기준 | Coverage |
|---|---:|
| Instruction | 92.21% |
| Branch | 79.64% |
| Line | 92.39% |

현재 POM에는 coverage 최소 threshold로 build를 실패시키는 rule이 없다. 수치는 품질 참고값이지 release gate로 자동 강제되지 않는다.

## 5. 핵심 회귀 시나리오

### 5.1 Flow 관리

- 생성은 INACTIVE와 201을 반환한다.
- MEMBER/MANAGER/SUPER_MANAGER 역할 경계가 맞다.
- location만 지정한 목록 query가 400이다.
- ACTIVE 전환 전에 executor·params·SpEL을 검증한다.
- 보관·복구·영구 삭제 상태 전이가 맞다.
- 수정은 새 ID를 만들고 기존 Flow를 ARCHIVED로 만든다.

### 5.2 그래프

- trigger 0/2개, action 0개 거부
- cycle, self-loop, dangling reference, 완전히 동일한 Link 중복 거부
- Action 전용 fan-out 허용과 비Action target 포함 fan-out 거부
- 비Action fan-out 오류가 실제 위반 Link의 `targetClientNodeKey` 경로를 지목
- Action fan-out의 Link ID 순차 실행
- fan-out 중 Action 실패 시 남은 Action 중단
- invalid source/target port 거부
- unreachable node와 action에 닿지 않는 path 거부
- false Filter Link 생략 허용
- Schedule의 Alert/Filter target 거부
- executor 없는 External notification ACTIVE 거부

### 5.3 Telemetry

- Core payload 역직렬화와 `time` alias
- 크기·metric count·key·null 제한
- consistent-hash header와 16 queue 선언
- sensorId route와 location route
- stale/동일 timestamp drop
- route/Flow 실패 후 다른 Flow 격리
- 모든 성공·실패에서 manual ACK, requeue 없음

### 5.4 Redis·cache

- route snapshot 직렬화와 route/status 검증
- v2 route namespace 사용과 legacy route snapshot 미조회
- Redis miss DB rebuild와 single-flight
- Redis 장애 local fallback
- 1분을 넘긴 로컬 fallback snapshot으로 비활성 Flow를 실행하지 않음
- route별 failure recovery와 10,000 상태 상한
- EVENT_GATE Lua count window/cooldown
- EVENT_GATE 전환 SQL의 데이터 초기화·Node/Link/Flow 제약

### 5.5 Schedule

- 6필드·seconds=0 cron 검증
- warm-up, register, cancel, 60초 reconcile
- stale registration 제거
- DB 조회와 상태 변경 경합에서 version 보호
- 두 호출 중 Redis claim 하나만 성공
- Redis 장애 fail-closed
- 등록 실패 상태 10,000 상한과 복구 로그
- cleanup이 DB 삭제 전에 Schedule을 취소

### 5.6 장애·관측

- 일시/영구 DataAccessException 분류
- Feign status별 분류
- 정상 경로 DEBUG, 최초 실패 WARN/ERROR, 복구 INFO
- 반복 실패 억제 횟수와 마지막 message
- heartbeat check 실패가 takeover로 오판되지 않음
- queue transition 실패·복구 기록
- lifecycle 3회 후 DLQ republish

## 6. 아직 자동 검증되지 않은 범위

| 영역 | 누락 | 권장 검증 |
|---|---|---|
| 실제 2-Pod HA | 두 JVM/Pod와 실제 queue takeover·handback | staging 장애 주입 E2E |
| 보안 경계 | Gateway→Engine→Core 교차 그룹 location | 악의적 locationId contract test |
| Front Schedule | 5필드 cron, Schedule→Alert 불일치 | Front 수정 후 browser/API E2E |
| 복합 Threshold | 누락 metric 오탐, same-packet AND, cross-sensor 비집계 | 연산자별 evaluator·다중 Node runner·Front/API E2E |
| 물리 장치 | Core 이후 MQTT/장비 반영 | simulator 또는 device acknowledgment E2E |
| DB 배포 | 빈 DB migration | Flyway 도입 후 clean-schema CI |
| 운영 Redis | DB 324, non-cluster 전제 | staging connection 및 Lua 실행 |
| 실제 Redis Flow cache | `RedisFlowDefinitionCache` round-trip과 두 provider 간 snapshot 공유 | Redis Testcontainer 기반 cache integration test |
| 성능 | packet burst, 많은 ACTIVE Flow, 출퇴근 Schedule 집중 | load/soak test |
| 장기 상태 | schedule-state와 local route map 증가 | 장시간 cardinality test |
| 메시지 backlog | 오래된 durable queue packet의 늦은 실행 | outage/backlog replay 시험과 허용 age 정책 |
| 사용자 이력 | 실패·성공 결과 조회 | 기능 구현 후 API/E2E |
| Observability export | failure counter scrape와 alert | Prometheus/OTel 연동 시험 |

`test/test-integration` 브랜치의 추가 resilience 테스트는 현재 dev에 병합되지 않았고 이전 결정에 따라 release 필수 기준으로 포함하지 않는다.

## 7. 성능 시험 계획

운영 SLO가 확정되지 않았으므로 먼저 workload를 합의한다.

측정해야 할 축:

- 초당 telemetry packet 수와 location cardinality
- route당 ACTIVE Flow 수와 Flow당 Node 수
- 한 시각에 동시에 실행되는 Schedule 수
- Redis hit/miss/error 비율
- Core actuator API 지연과 timeout 비율
- DB reconnect 중 route rebuild 동시성

최소 결과:

- p50/p95/p99 telemetry 처리 지연
- queue backlog와 소비율
- Redis/DB connection과 CPU·heap
- Schedule 예정 시각 대비 Core 요청 시작 지연
- 장애·복구 시 중복·누락 건수

측정 전 임의의 목표 수치를 문서에 확정하지 않는다.

## 8. Release 검증 체크리스트

- [ ] 기준 commit의 전체 `mvn clean test` suite 실행, failure/error/skip 0 및 실행 건수 기록
- [ ] 기준 commit image로 staging 배포
- [ ] 빈 schema 또는 검증된 migration에서 기동
- [ ] Core telemetry payload와 hash header contract test
- [ ] Front 6필드 cron과 Schedule→Actuator E2E
- [ ] group-location 교차 권한 공격 차단
- [ ] Core actuator 성공 의미와 실제 장비/시뮬레이터 결과 확인
- [ ] engine-a 중단 후 heartbeat TTL 만료와 다음 점검을 거쳐 이론상 약 20초 이내(+listener 전환 지연)에 engine-b takeover 확인
- [ ] Redis 장애 시 Schedule 미실행과 cache fallback 확인
- [ ] EVENT_GATE 전환 시 Engine 0개, DB 백업·초기화, v1/v2 route key 0건, 동일 image의 두 pod Ready를 순서대로 확인
- [ ] lifecycle 실패 3회 후 DLQ와 재처리 절차 확인
- [ ] 로그·trace·metric이 운영 관측 도구에서 보이는지 확인
- [ ] 출퇴근 시간대 Schedule 집중 부하 결과 검토
