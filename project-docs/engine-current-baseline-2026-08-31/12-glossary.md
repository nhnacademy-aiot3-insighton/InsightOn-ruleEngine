# 용어집

| 용어 | 뜻 |
|---|---|
| Flow | 하나의 Trigger에서 시작해 Filter를 거쳐 Action에 도달하는 자동화 정의 |
| FlowDefinition | DB의 Flow/Node/Link를 실행용 불변 snapshot으로 조립한 값 |
| Trigger | Flow의 단일 시작 Node. SENSOR, LOCATION, SCHEDULE |
| Filter | true/false 출력으로 다음 경로를 선택하는 Node |
| Action | 외부 효과를 만들고 실행을 끝내는 terminal Node |
| ACTIVE | runtime route 또는 scheduler에 포함되는 실행 상태 |
| INACTIVE | 저장돼 있지만 실행하지 않는 상태 |
| ARCHIVED | 휴지통 상태. 실행되지 않으며 복구 또는 영구 삭제 가능 |
| ERROR | enum은 있지만 현행 진입 경로가 없는 예약 상태 |
| current packet | 지금 수신한 단일 SensorEvent와 그 metrics. 장소 aggregate가 아님 |
| route | `(groupId, locationId)`로 묶은 ACTIVE Flow 조회 단위 |
| route snapshot | route의 `List<FlowDefinition>`을 한 값으로 저장한 Redis cache |
| local fallback | Redis 장애 시 짧게 사용하는 인스턴스 로컬 route snapshot |
| scheduledAt | CronTrigger가 계산한 원래 실행 예정 Instant |
| Schedule claim | 같은 flowId/scheduledAt을 한 인스턴스만 실행하도록 Redis NX key를 얻는 작업 |
| fail-closed | 상태 저장소 오류 때 중복 가능성을 감수해 실행하지 않고 미실행을 선택하는 정책 |
| missed run | Engine 중단 중 지나간 cron 실행. 현행은 보정하지 않음 |
| watermark | sensor별 마지막 처리 timestamp. 이전·동일 timestamp를 stale로 판단 |
| cooldown | Alert가 발행된 뒤 같은 Action의 재발행을 막는 시간 |
| count window | Alert가 requiredCount에 도달해야 하는 제한 시간 |
| Timer | 지연 타이머가 아니라 interval 내 최초 이벤트만 통과시키는 throttle Filter |
| x-consistent-hash | header 값을 해시해 같은 location 메시지를 안정적으로 queue에 분배하는 RabbitMQ exchange type |
| queue ownership | engine-a/b가 정상 상태에서 소비할 고정 8개 queue 집합 |
| takeover | peer 장애 시 상대 queue listener를 시작하는 것 |
| handback | peer 복구 시 takeover listener를 중지해 queue를 반환하는 것 |
| at-most-once | 실패 시 재처리하지 않아 중복은 줄지만 유실될 수 있는 전달 성격 |
| exactly-once | 한 번만 최종 효과가 발생하는 보장. 현행 Schedule은 이를 보장하지 않음 |
| duplicate suppression | Redis 선점으로 동일 실행 시도의 중복 가능성을 줄이는 것 |
| lifecycle event | Core의 group/location 삭제를 Engine에 알리는 Rabbit event |
| DLQ | 반복 처리 실패 메시지를 격리하는 dead-letter queue |
| executionId | 한 Flow 실행 로그를 묶는 로컬 UUID. 사용자 조회용 영속 ID가 아님 |
| failure kind | transient dependency, permanent configuration/rejected, internal 관측 분류 |
| Core actuator success | 현행상 Core HTTP가 정상 반환한 상태. 물리 장비 완료 보장은 아님 |
| 구현 | 코드 경로와 검증 근거가 있는 현행 기능 |
| 보류 | 의도적으로 다음 단계로 미룬 항목. 구현과 동의어가 아님 |
| 결정 필요 | 코드 변경 전에 제품·데이터·운영 책임 합의가 필요한 항목 |
