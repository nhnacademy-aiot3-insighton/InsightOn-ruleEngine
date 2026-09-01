# Rule Engine 요구사항 명세서

## 1. 범위와 표기

본 명세는 기준 커밋 `eed36d5`의 현행 기능을 요구사항 형태로 정리한다. 미래 목표를 구현된 요구사항처럼 쓰지 않는다.

- `BR`: 업무 요구사항
- `FR`: 기능 요구사항
- `NFR`: 비기능 요구사항
- 상태는 [README.md](README.md)의 상태 정의를 사용한다.

## 2. 업무 요구사항

| ID | 요구사항 | 상태 | 인수 기준 |
|---|---|---|---|
| BR-001 | 그룹 관리자는 장소 단위 자동화 Flow를 정의하고 관리할 수 있어야 한다. | 구현 | 생성·조회·활성화·보관·복구·삭제 API가 역할에 따라 동작한다. |
| BR-002 | ACTIVE 텔레메트리 Flow는 대상 장소/센서의 패킷을 받아 조건과 Action을 실행해야 한다. | 구현 | 현재 패킷이 trigger와 일치할 때 그래프 경로 하나가 실행된다. |
| BR-003 | 반복 예약 Flow는 정해진 시각에 액추에이터 동작을 요청해야 한다. | 구현 | 6필드 cron과 전역 timezone을 사용하고 한 인스턴스만 실행 시도한다. |
| BR-004 | Alert Flow는 반복 횟수와 cooldown을 적용한 뒤 AI가 소비할 이벤트를 발행해야 한다. | 구현 | Redis 원자 전이 통과 시 호환 payload가 RabbitMQ로 전달된다. |
| BR-005 | 그룹·장소 삭제 후 해당 Flow가 더 이상 실행되지 않아야 한다. | 구현 | Schedule 취소와 cache 제거를 DB 삭제 전에 수행하고 영속 데이터가 제거된다. |
| BR-006 | 사용자는 실행 실패 여부와 이력을 확인할 수 있어야 한다. | 보류 | 현재 사용자 API·저장소·이벤트가 없으며 별도 제품 설계가 필요하다. |

## 3. 기능 요구사항

### 3.1 Flow 관리와 권한

| ID | 요구사항 | 상태 | 검증 가능한 조건 |
|---|---|---|---|
| FR-001 | 새 Flow는 항상 `INACTIVE`로 생성해야 한다. | 구현 | POST 성공 응답이 201이고 저장 상태가 INACTIVE다. |
| FR-002 | MEMBER 이상은 목록·상세를 조회할 수 있어야 한다. | 구현 | Core가 반환한 role이 MEMBER 이상이면 GET을 허용한다. |
| FR-003 | MANAGER 이상만 생성·수정·상태변경·보관·복구·삭제할 수 있어야 한다. | 구현 | 낮은 role은 403을 받는다. |
| FR-004 | 권한은 Core의 그룹 멤버 내부 API 결과로 판단해야 한다. | 구현 | Core 404는 403, 기타 Feign 실패는 502로 변환한다. |
| FR-005 | Flow 목록은 기본적으로 ARCHIVED를 제외해야 한다. | 구현 | 조건 없는 목록은 `status != ARCHIVED`만 반환한다. |
| FR-006 | 상태 필터 또는 location+status 조합 조회를 제공해야 한다. | 구현 | location만 지정한 쿼리는 400으로 거부한다. |
| FR-007 | 활성 상태 전환은 `INACTIVE ↔ ACTIVE`만 허용해야 한다. | 구현 | 그 외 전이는 409다. |
| FR-008 | `ACTIVE`, `INACTIVE`, `ERROR` Flow는 `ARCHIVED`로 보관할 수 있어야 한다. | 부분 구현 | 전이는 존재하지만 ERROR로 들어가는 운영 경로는 없다. |
| FR-009 | `ARCHIVED` Flow는 같은 ID로 `INACTIVE` 복구할 수 있어야 한다. | 구현 | restore 후 INACTIVE다. |
| FR-010 | 영구 삭제는 `ARCHIVED` Flow만 허용해야 한다. | 구현 | 다른 상태 삭제는 409, 성공은 204다. |
| FR-011 | Flow 수정은 기존 Flow를 보관하고 새 ID의 INACTIVE Flow를 만들어야 한다. | 부분 구현 | 새 버전 방식은 구현됐으나 같은 이름 사용은 unique 제약으로 실패한다. |
| FR-012 | 같은 그룹·장소에서 Flow 이름은 유일해야 한다. | 구현 | `(groupId, locationId, name)` 중복은 409다. |
| FR-013 | 요청 크기는 이름 100자, 설명 2,000자, 노드 500개, 링크 1,000개 이하로 제한해야 한다. | 구현 | Bean Validation과 구조 검증이 위반 요청을 거부한다. |
| FR-014 | 요청의 location이 group 소속인지 검증해야 한다. | 결정 필요 | 현재 엔진은 그룹 멤버십만 확인하고 group-location 관계는 확인하지 않는다. |

### 3.2 그래프와 활성화 검증

| ID | 요구사항 | 상태 | 검증 가능한 조건 |
|---|---|---|---|
| FR-020 | Flow에는 Trigger가 정확히 하나 있어야 한다. | 구현 | 0개 또는 2개 이상이면 ACTIVE/저장을 거부한다. |
| FR-021 | Flow에는 Action이 하나 이상 있어야 한다. | 구현 | Action이 없으면 거부한다. |
| FR-022 | Trigger는 입력 Link, Action은 출력 Link를 가질 수 없어야 한다. | 구현 | 위반 링크는 구조 오류다. |
| FR-023 | source node의 한 output port는 단일 경로 또는 Action 전용 fan-out으로 연결돼야 한다. | 구현 | 같은 output port의 복수 Link는 모든 target이 Action일 때만 허용한다. |
| FR-024 | 모든 target port는 `in`이어야 한다. | 구현 | 다른 값은 거부한다. |
| FR-025 | 모든 Node는 Trigger에서 도달 가능하고 각 비Action Node는 Action으로 이어져야 한다. | 구현 | 고립 노드와 막힌 경로를 거부한다. |
| FR-026 | self-loop와 cycle을 금지해야 한다. | 구현 | 저장 검증과 실행 중 visited 방어가 있다. |
| FR-027 | Filter의 `true` Link는 필수이고 `false` Link는 선택이어야 한다. | 구현 | false Link가 없고 결과가 false면 정상 종료한다. |
| FR-028 | 저장 시 Node configuration을 타입별 DTO로 파싱·검증해야 한다. | 구현 | 필수값·범위·enum·cron 위반은 구조 오류다. |
| FR-029 | ACTIVE 전환 전 모든 NodeExecutor와 Threshold 문법을 검증해야 한다. | 구현 | 실행기 누락 또는 SpEL 구문 오류 Flow는 활성화되지 않는다. |
| FR-030 | Schedule Trigger의 직접 대상은 하나 이상의 `ACTUATOR_CONTROL`만 허용해야 한다. | 구현 | 복수 Actuator fan-out은 허용하고 Alert·Filter 등 다른 target을 거부한다. |
| FR-031 | 한 output port에서 여러 Action으로 fan-out할 수 있어야 한다. | 구현 | Link ID 순서대로 Action을 모두 실행하며 비Action fan-out과 완전히 동일한 Link 중복은 거부한다. |

### 3.3 텔레메트리 수신과 라우팅

| ID | 요구사항 | 상태 | 검증 가능한 조건 |
|---|---|---|---|
| FR-040 | Core의 `{groupId,locationId,sensorId,metrics,timestamp}` 메시지를 수신해야 한다. | 구현 | 양수 ID, 비어 있지 않은 metrics, timestamp를 검증한다. |
| FR-041 | 메시지 body는 256 KiB 이하, metrics는 256개 이하, key는 100자 이하로 제한해야 한다. | 구현 | 위반 메시지는 ACK 후 폐기한다. |
| FR-042 | `time` 필드는 `timestamp` 별칭으로도 수용해야 한다. | 구현 | Jackson alias가 적용된다. |
| FR-043 | 같은 location의 메시지는 consistent-hash header `locationId`로 같은 queue에 전달돼야 한다. | 구현 | Core producer와 Engine topology의 exchange/header가 일치한다. |
| FR-044 | SENSOR Flow는 trigger의 `sensorId`가 이벤트와 같을 때만 실행해야 한다. | 구현 | 다른 sensorId의 Flow는 route 결과에서 제외된다. |
| FR-045 | LOCATION Flow는 같은 group/location의 모든 센서 패킷을 후보로 삼아야 한다. | 구현 | 해당 route의 LOCATION trigger를 실행한다. |
| FR-046 | LOCATION과 Threshold는 한 실행에서 현재 패킷 metrics만 사용해야 한다. | 구현 | 과거 패킷을 합치는 장소 집계 저장소가 없다. |
| FR-047 | 동일 인스턴스가 연속 처리하는 `(group,location,sensor)`에서 timestamp가 이전 값 이하인 메시지는 폐기해야 한다. | 부분 구현 | 로컬 Caffeine watermark가 비교한다. 재시작·failover 뒤에는 watermark가 초기화된다. |
| FR-048 | 변환·검증·실행 실패 메시지는 재시도·requeue하지 않아야 한다. | 구현 | manual ACK 후 폐기하며 telemetry DLQ가 없다. |
| FR-049 | 한 Flow 실행 실패가 같은 이벤트의 다른 Flow 실행을 막지 않아야 한다. | 구현 | FlowRunner가 Flow별 예외를 기록하고 다음 Flow를 실행한다. |

### 3.4 Node 실행

| ID | 요구사항 | 상태 | 검증 가능한 조건 |
|---|---|---|---|
| FR-050 | `THRESHOLD`는 제한된 SpEL에서 `#event`, `#metrics`를 읽고 boolean을 반환해야 한다. | 구현 | read-only `SimpleEvaluationContext`, 최대 1,000자, 최대 1,024 expression cache다. |
| FR-051 | `TIME_WINDOW`는 전역 zone에서 `[startTime,endTime)`을 평가하고 자정 통과를 지원해야 한다. | 구현 | 같은 시작·종료 시각은 거부한다. |
| FR-052 | `TIMER`는 `(nodeId,locationId)`별 interval 내 첫 이벤트만 true로 보내야 한다. | 구현 | Redis `SET NX`와 interval TTL을 사용한다. |
| FR-053 | `ACTUATOR_CONTROL`은 지원 타입·명령·값만 허용해야 한다. | 구현 | Engine 설정 검증 후 Core HTTP를 동기 호출한다. |
| FR-054 | 액추에이터 호출에는 `callerService=RULE_ENGINE`을 강제해야 한다. | 구현 | 사용자 configuration이 아닌 outbound DTO가 주입한다. |
| FR-055 | `ALERT`는 requiredCount, count timeout, cooldown을 원자적으로 적용해야 한다. | 구현 | Redis Lua 한 연산에서 count/cooldown을 전이한다. |
| FR-056 | Alert 통과 시 AI용 eventId UUID와 Flow 문맥, metrics를 발행해야 한다. | 구현 | `insighton.rule-engine-events` / `ai.alert.action`을 사용한다. |
| FR-057 | `EXTERNAL_NOTIFICATION` 타입의 제품 목적과 외부 전송 계약을 확정해야 한다. | 결정 필요 | 과거 Telegram/Email 후보를 설명하는 configuration DTO만 있고, 현행 확정 계약과 NodeExecutor는 없다. |

### 3.5 Schedule 실행

| ID | 요구사항 | 상태 | 검증 가능한 조건 |
|---|---|---|---|
| FR-060 | Schedule은 Spring 6필드 cron을 사용하고 seconds 필드는 `0`이어야 한다. | 구현 | 다른 필드 수·초 값·유효하지 않은 식을 거부한다. |
| FR-061 | 모든 인스턴스는 기동 시 ACTIVE Schedule을 등록해야 한다. | 구현 | ApplicationReady warm-up이 DB를 조회해 등록한다. |
| FR-062 | DB와 로컬 등록은 기본 60초마다 재조정해야 한다. | 구현 | 누락 등록·stale 등록·Redis ACTIVE state를 보정한다. |
| FR-063 | 상태 변경 commit 후 Schedule을 등록 또는 취소해야 한다. | 구현 | transaction afterCommit hook을 사용한다. |
| FR-064 | 동일 `flowId + scheduledAt`은 한 인스턴스만 실행 시도해야 한다. | 구현 | ACTIVE 확인과 execution-key NX를 Lua로 수행한다. |
| FR-065 | Redis 접근 실패 시 Schedule을 실행하지 않아야 한다. | 구현 | fail-closed하고 오류를 억제 기록한다. |
| FR-066 | 엔진 중단 중 놓친 시각은 보정 실행하지 않아야 한다. | 구현 | 다음 CronTrigger 시각부터 재개하며 backfill이 없다. |
| FR-067 | 일회성 예약을 제공해야 한다. | 보류 | `executeAt` 모델과 스케줄러가 없다. |

### 3.6 캐시·장애조치·정리

| ID | 요구사항 | 상태 | 검증 가능한 조건 |
|---|---|---|---|
| FR-070 | route의 ACTIVE FlowDefinition 목록을 Redis 단일 값으로 저장해야 한다. | 구현 | `groupId:locationId` key의 JSON snapshot을 TTL과 함께 원자 교체한다. |
| FR-071 | Redis miss는 DB에서 route만 재구축해야 한다. | 구현 | route별 single-flight lock을 사용한다. |
| FR-072 | Redis 장애 시 최대 1분의 로컬 snapshot을 사용하고, 만료 후 DB를 시도해야 한다. | 구현 | 오래된 snapshot을 무기한 사용하지 않는다. |
| FR-073 | 두 인스턴스는 16개 queue를 짝수/홀수로 나눠 소유해야 한다. | 구현 | pod ordinal 0/1로 engine-a/b와 queue 목록을 주입한다. |
| FR-074 | peer heartbeat가 15초 동안 없으면 상대 queue를 인계하고 복구 시 반환해야 한다. | 구현 | 5초 단위 확인과 takeover/handback이 있다. |
| FR-075 | Redis heartbeat 확인 자체가 실패하면 queue를 인계하지 않아야 한다. | 구현 | 상태를 null로 처리해 split-brain 오판을 막는다. |
| FR-076 | Core group/location 삭제 시 관련 로컬 Schedule, route cache, Alert 상태와 DB Flow를 정리해야 한다. | 구현 | 삭제 전 cancel/cache evict, Alert count/cooldown 삭제, DB 삭제, 후속 재정리를 수행한다. Timer key는 TTL 만료까지 남는다. |
| FR-077 | lifecycle 처리 실패는 최대 3회 재시도 후 DLQ로 보내야 한다. | 구현 | stateless retry와 republish recoverer가 설정돼 있다. |
| FR-078 | sensor/actuator 삭제 시 관련 Flow를 비활성화 또는 오류 처리해야 한다. | 미구현 | Engine listener와 정책이 없다. |

### 3.7 오류와 관측

| ID | 요구사항 | 상태 | 검증 가능한 조건 |
|---|---|---|---|
| FR-080 | 정상 라우팅·Flow 완료·Action 전달은 DEBUG로 기록해야 한다. | 구현 | 패킷당 INFO 로그가 없다. |
| FR-081 | 일시 의존성 오류는 WARN, 영구 설정·거부·내부 오류는 ERROR로 기록해야 한다. | 구현 | exception chain 분류기가 적용된다. |
| FR-082 | 컴포넌트별 추적 scope에서 반복 실패를 억제하고 복구 시 마지막 문맥과 억제 횟수를 INFO로 남겨야 한다. | 구현 | Flow·Schedule은 signature 변화 시 새 실패로 기록하고, cache·failover는 scope 내 후속 문맥을 갱신한다. |
| FR-083 | 실패 추적 상태는 각 범위 최대 10,000개로 제한해야 한다. | 구현 | 새 scope 추가 전 상한과 축출이 적용된다. |
| FR-084 | 실행 실패를 `stage`와 `kind`별 counter로 기록해야 한다. | 구현 | `rule_engine.execution.failures` counter가 증가한다. |
| FR-085 | 사용자가 Flow 실행 성공·실패 이력을 조회할 수 있어야 한다. | 보류 | 실행 이력 저장소와 API가 없다. |

## 4. 비기능 요구사항

| ID | 분류 | 요구사항 | 상태·비고 |
|---|---|---|---|
| NFR-001 | 가용성 | 두 엔진 중 한 인스턴스 장애 시 15초 TTL 만료 후 다음 5초 점검에서 peer queue를 인계해야 한다. | 구현. 이론상 약 20초와 listener 전환 지연, 실제 Kubernetes 장애 시험 필요 |
| NFR-002 | 일관성 | Schedule 중복 실행 가능성을 Redis NX로 억제해야 한다. | 구현. exactly-once가 아니라 단일 실행 시도 선점 |
| NFR-003 | 일관성 | Flow 변경 후 DB commit이 성공한 뒤 실행 snapshot을 갱신해야 한다. | 부분 구현. afterCommit callback이므로 DB와 Redis·로컬 Schedule 반영은 원자적이지 않다. |
| NFR-004 | 안전성 | Redis 장애 fallback에서 오래된 로컬 Flow snapshot을 1분보다 오래 실행하지 않아야 한다. | 부분 구현. 로컬 snapshot만 제한하며 갱신 실패로 Redis에 남은 이전 값은 cache TTL까지 읽힐 수 있다. |
| NFR-005 | 성능 | 정상 telemetry hot path는 DB 대신 Redis route snapshot을 우선 사용해야 한다. | 구현 |
| NFR-006 | 자원 | stale sensor, 실패 추적, expression cache는 상한을 가져야 한다. | 구현 |
| NFR-007 | 확장성 | 현재 배포는 정확히 두 인스턴스와 16개 queue에서 동작해야 한다. | 구현이나 3개 이상 확장은 미지원 |
| NFR-008 | 보안 | 외부 사용자는 Gateway가 검증한 사용자 식별자로만 Flow API에 접근해야 한다. | 외부 연동 검증 필요 |
| NFR-009 | 보안 | Engine-Core 내부 API는 신뢰된 서비스만 호출할 수 있어야 한다. | 네트워크/서비스 인증 정책 결정 필요 |
| NFR-010 | 데이터 | 애플리케이션은 DB schema를 임의 생성하지 않고 기대 schema를 검증해야 한다. | 구현. migration 부재는 출시 blocker |
| NFR-011 | 관측 | 정상 고빈도 이벤트는 DEBUG, 장애·복구는 운영 가능한 수준으로 기록해야 한다. | 구현 |
| NFR-012 | 테스트 | 단위·통합 및 필요한 외부 E2E 자동화 테스트는 release 전 모두 통과해야 한다. | 부분 구현. 현행 자동 suite는 통과했으나 2-Pod·Front·실장치 E2E는 없음 |
| NFR-013 | 성능 | 목표 처리량·p95 실행 지연·Schedule 집중 시간대 용량을 수치로 합의해야 한다. | 결정 필요·부하 시험 전 미확정 |

## 5. 데이터와 입력 제약 요약

| 항목 | 제약 |
|---|---|
| Flow name | trim 후 1~100자, 같은 group/location에서 unique |
| description | 최대 2,000자, null 허용 |
| nodes | 생성 2~500개, 수정 1~500개이나 구조상 Trigger·Action 필요 |
| links | 최대 1,000개, 구조 검증상 최소 1개 |
| client node key | 1~100자, 요청 안에서 unique |
| port | source는 NodeType별 `out` 또는 `true/false`, target은 `in` |
| telemetry | body 256 KiB 이하, metrics 1~256개, key 1~100자, null value 금지 |
| cron | 공백 구분 6필드, seconds=`0`, Spring `CronExpression` 유효 |
| threshold | 1~1,000자, boolean 결과 |
| time window | LocalTime 두 값, 서로 달라야 함 |
| timer | intervalSeconds 양수 정수 |
| alert | title 200자, message 2,000자, severity 3종, count/cooldown 범위 준수 |

## 6. 요구사항 해석 주의사항

- `Schedule 중복 억제`는 장치 동작의 exactly-once 보장이 아니다. 선점 후 프로세스나 Core 호출이 실패하면 재시도하지 않는다.
- `Alert 발행 성공`은 RabbitTemplate 호출 성공을 뜻하며 AI 저장·사용자 노출 완료를 뜻하지 않는다.
- `Actuator Action 성공`은 Core HTTP가 오류 없이 반환한 것을 뜻하며 실제 물리 장치의 최종 상태 확인을 포함하지 않는다.
- `LOCATION`은 현재 패킷 기준이지 장소의 최신 센서 값 전체를 합친 snapshot이 아니다.
- 오류 분류는 관측 정책이며 자동 재시도 여부를 바꾸지 않는다.
- stale telemetry 차단은 인스턴스 로컬 best-effort다. 재시작·failover 후 최초로 읽는 과거 패킷까지 막는 전역 보장은 아니다.
- group/location lifecycle cleanup은 Timer key를 즉시 지우지 않는다. 남은 Timer 상태는 기존 TTL이 끝날 때까지 Redis에 존재한다.
- afterCommit 반영과 Redis snapshot 갱신 실패에 따른 정합성 창은 [미구현·검증·확정 필요 대장](11-open-issues-and-validation.md)의 OI-23에서 추적한다.
