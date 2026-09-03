# 요구사항 추적표

## 1. 목적

이 문서는 [요구사항 명세](02-requirements-specification.md)의 각 요구사항이 어떤 설계·코드·테스트로 확인되는지 추적한다. `보류`, `미구현`, `결정 필요` 항목에는 구현 근거 대신 남은 검증 위치를 기록한다.

## 2. 업무 요구사항 추적

| 요구사항 | 설계·인터페이스 | 주요 코드 근거 | 주요 테스트·검증 |
|---|---|---|---|
| BR-001 Flow 관리 | [상세 설계 §2](05-detailed-design.md#2-flow-생명주기), [REST API](06-interface-specification.md#2-flow-rest-api) | `FlowController`, `FlowService` | `FlowControllerTest`, `FlowServiceTest`, `FlowLifecycleE2ETest` |
| BR-002 텔레메트리 Flow | [아키텍처 §4](04-system-architecture.md#4-텔레메트리-실행-시퀀스) | `TelemetryMessageConsumer`, `ActiveFlowRouter`, `FlowRunner` | `TelemetryMessageConsumerTest`, `ActiveFlowRouterTest`, `FlowRunnerTest` |
| BR-003 예약 제어 | [아키텍처 §5](04-system-architecture.md#5-schedule-실행-시퀀스) | `ScheduleFlowScheduler`, `ScheduleExecutionRedisRepository` | `ScheduleFlowSchedulerTest`, `ScheduledExecutionTriggerTest` |
| BR-004 Alert | [상세 설계 §5.8](05-detailed-design.md#58-alert) | `AlertNodeExecutor`, `AlertCountService`, `RabbitActionPublisher` | `AlertNodeExecutorTest`, `AlertRuntimeStateRedisIntegrationTest`, `RabbitActionPublisherTest` |
| BR-005 lifecycle 정리 | [아키텍처 §7](04-system-architecture.md#7-core-lifecycle-정리-시퀀스) | `FlowCleanupService`, lifecycle listeners | `FlowCleanupServiceTest`, `CoreLifecycleEventRabbitIntegrationTest` |
| BR-006 사용자 실행 이력 | [미결 이슈 OI-03](11-open-issues-and-validation.md#oi-03-사용자용-flow-실행-이력과-실패-통지) | 구현 없음 | 제품·데이터·API 설계 필요 |

## 3. 기능 요구사항 추적

| 요구사항 | 주요 코드 근거 | 자동화 검증 또는 상태 |
|---|---|---|
| FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, FR-009, FR-010, FR-011, FR-012, FR-013 | [`FlowController`](../../src/main/java/com/nhnacademy/insightonruleengine/flow/api/controller/FlowController.java), [`FlowService`](../../src/main/java/com/nhnacademy/insightonruleengine/flow/application/FlowService.java), request DTO | `FlowControllerTest`, `FlowServiceTest`, `FlowServiceIntegrationTest`, `FlowLifecycleE2ETest` |
| FR-014 group-location 소유관계 | 현재 검증 코드 없음 | [보안 우선 이슈 OI-01](11-open-issues-and-validation.md#oi-01-group-location-권한-경계) |
| FR-020, FR-021, FR-022, FR-023, FR-024, FR-025, FR-026, FR-027, FR-028, FR-029, FR-030, FR-031 | [`FlowStructureValidator`](../../src/main/java/com/nhnacademy/insightonruleengine/flow/application/validation/FlowStructureValidator.java), [`FlowRequestFieldValidator`](../../src/main/java/com/nhnacademy/insightonruleengine/flow/application/validation/FlowRequestFieldValidator.java), [`FlowGraphValidator`](../../src/main/java/com/nhnacademy/insightonruleengine/flow/application/validation/FlowGraphValidator.java), `FlowActivationValidator` | 통합 Validator 단위 테스트와 `FlowActivationValidatorTest` |
| FR-040, FR-041, FR-042 | [`SensorEvent`](../../src/main/java/com/nhnacademy/insightonruleengine/runner/model/SensorEvent.java), [`TelemetryMessageConsumer`](../../src/main/java/com/nhnacademy/insightonruleengine/runner/infrastructure/inbound/rabbitmq/TelemetryMessageConsumer.java) | `SensorEventTest`, `TelemetryMessageConsumerTest` |
| FR-043 consistent-hash 라우팅 | `TelemetryRoutingConfiguration`, `TelemetryRoutingProperties` | `TelemetryRoutingConfigurationTest`, Rabbit integration test, Core producer 교차 확인 |
| FR-044, FR-045, FR-046 | [`ActiveFlowRouter`](../../src/main/java/com/nhnacademy/insightonruleengine/runner/application/router/ActiveFlowRouter.java), Sensor/Location executors | `ActiveFlowRouterTest`, `SensorNodeExecutor`·`LocationNodeExecutorTest` |
| FR-047 인스턴스 로컬 stale 패킷 | [`StaleTelemetryDetector`](../../src/main/java/com/nhnacademy/insightonruleengine/runner/application/telemetry/StaleTelemetryDetector.java) | `StaleTelemetryDetectorTest`, `TelemetryRuntimeRecoveryTest`; 재시작·failover 전역 보장은 미검증 |
| FR-048 ACK·drop | `TelemetryMessageConsumer` | `TelemetryMessageConsumerTest` |
| FR-049 Flow 격리 | [`FlowRunner`](../../src/main/java/com/nhnacademy/insightonruleengine/runner/application/FlowRunner.java) | `FlowRunnerTest` |
| FR-050 Threshold | `ThresholdNodeExecutor`, `ThresholdEvaluator` | `ThresholdEvaluatorTest`, Node executor/activation tests |
| FR-051 Time window | `TimeWindowNodeExecutor`, `TimeWindowParams` | `TimeWindowNodeExecutorTest`, `RuntimeStateParamsTest` |
| FR-052 Timer | `TimerNodeExecutor`, `TimerStateRedisRepository` | `TimerNodeExecutorTest`, `RedisRuntimeRepositoryIntegrationTest` |
| FR-053, FR-054 | `ActuatorControlParams`, `ActuatorControlNodeExecutor`, `CoreActuatorClient` | `ActuatorControlNodeExecutorTest`, Core 계약 수동 교차 검증 |
| FR-055, FR-056 | `AlertParams`, `AlertCountRedisRepository`, `AlertNodeExecutor`, `RabbitActionPublisher` | Alert 단위·Redis 통합·Publisher 테스트, AI 계약 교차 확인 |
| FR-057 External notification | NodeType과 Params만 존재 | Executor가 없어 ACTIVE 검증에서 거부됨. [OI-04](11-open-issues-and-validation.md#oi-04-external-notification과-error-상태) |
| FR-060 cron 검증 | `ScheduleParams`, `CronExpressionValidator` | `ScheduleParamsTest`, `ScheduleExecutionPropertiesTest` |
| FR-061, FR-062, FR-063, FR-064, FR-065, FR-066 | [`ScheduleFlowScheduler`](../../src/main/java/com/nhnacademy/insightonruleengine/runner/application/schedule/ScheduleFlowScheduler.java), [`ScheduleExecutionRedisRepository`](../../src/main/java/com/nhnacademy/insightonruleengine/runner/infrastructure/persistence/redis/ScheduleExecutionRedisRepository.java) | `ScheduleFlowSchedulerTest`, `ScheduleExecutionRedisRepositoryTest`, `ScheduledExecutionTriggerTest` |
| FR-067 일회성 예약 | 구현 없음 | 제품 범위 보류 |
| FR-070, FR-071, FR-072 | [`ActiveFlowDefinitionProvider`](../../src/main/java/com/nhnacademy/insightonruleengine/runner/infrastructure/cache/ActiveFlowDefinitionProvider.java), `RedisFlowDefinitionCache` | cache 단위 테스트, `ActiveFlowDefinitionProviderCrossInstanceIntegrationTest` |
| FR-073, FR-074, FR-075 | `RuleEngineInstanceEnvironmentPostProcessor`, heartbeat, `TelemetryQueueFailoverMonitor` | 환경·heartbeat·failover 단위 테스트, 실제 2-Pod 검증은 남음 |
| FR-076, FR-077 | `FlowCleanupService`, `CoreLifecycleEventConfiguration`, listeners | cleanup·configuration·Rabbit integration tests; lifecycle cleanup의 Timer key 잔존은 명세에 명시 |
| FR-078 sensor/actuator 삭제 | Engine listener 없음 | [OI-05](11-open-issues-and-validation.md#oi-05-sensoractuator-삭제-정책) |
| FR-080, FR-081, FR-082, FR-083, FR-084 | [`ExecutionLogger`](../../src/main/java/com/nhnacademy/insightonruleengine/runner/observability/ExecutionLogger.java), cache/schedule/failover logging | `ExecutionLoggerTest`, telemetry recovery/failover tests |
| FR-085 사용자 실행 이력 | 구현 없음 | [OI-03](11-open-issues-and-validation.md#oi-03-사용자용-flow-실행-이력과-실패-통지) |

## 4. 비기능 요구사항 추적

| 요구사항 | 구현 근거 | 검증 상태 |
|---|---|---|
| NFR-001 두 인스턴스 failover | heartbeat, `TelemetryQueueFailoverMonitor`, listener manager | 단위 테스트 있음, TTL 15초+점검 최대 5초, 실제 Pod 장애 시험 필요 |
| NFR-002 Schedule 중복 억제 | Redis `CLAIM_IF_ACTIVE_SCRIPT` | repository/scheduler 테스트 있음, 실제 다중 JVM 시험 권장 |
| NFR-003 commit 이후 비원자 갱신 | `afterCommit` in FlowService/provider/scheduler | Flow service runtime event 테스트; commit→callback 경합은 OI-23 |
| NFR-004 local fallback snapshot 제한 | local snapshot createdAt + max age | cache 단위·공유-cache integration 테스트; stale Redis 값은 OI-23 |
| NFR-005 DB 없는 hot path | Redis-first provider | cache provider 테스트, 부하 실측 없음 |
| NFR-006 메모리 상한 | Caffeine max size, failure state 10,000, expression cache 1,024 | 관련 단위 테스트 |
| NFR-007 고정 2인스턴스 | queue property validator + pod ordinal | config 테스트, 3개 이상은 명시적 미지원 |
| NFR-008, NFR-009 | `X-User-Id`, Core 내부 API | NetworkPolicy·서비스 인증·교차 그룹 공격 시나리오 검증 필요 |
| NFR-010 schema validate | `spring.jpa.hibernate.ddl-auto=validate` | migration 없음. 신규 환경 기동 검증 필요 |
| NFR-011 관측 | 한국어 레벨 정책·counter·tracing | 로그 단위 테스트, 수집 대시보드 현장 점검 필요 |
| NFR-012 자동화 테스트 | `src/test/java` 89개 소스 | 현행 suite 결과와 미구현 외부 E2E는 [테스트 결과](09-test-plan-and-results.md#4-현행-실행-결과) 참조 |
| NFR-013 성능 SLO | 기준 없음 | 부하 모델·수치 합의 후 시험 필요 |

## 5. 추적 공백 처리 규칙

- 코드 경로가 있어도 외부 시스템의 최종 결과까지 검증하지 못하면 `외부 연동 필요`로 유지한다.
- 단위 테스트만으로 분산 동작을 보장했다고 간주하지 않는다.
- `src/test`가 Docker/Testcontainers 없이 skip되거나 context 초기화에 실패하면 전체 통과로 집계하지 않는다.
- 미구현 항목은 임시 주석을 요구사항 충족 근거로 사용하지 않는다.
