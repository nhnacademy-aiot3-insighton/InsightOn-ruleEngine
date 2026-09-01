# 미구현·검증·확정 필요 대장

## 1. 우선순위 기준

| 우선순위 | 의미 |
|---|---|
| P0 | 출시 전 해결 또는 명시적 위험 수용이 필요 |
| P1 | 운영 안정성·정합성에 영향, 가까운 후속 작업 권장 |
| P2 | 제품 확장·사용성 개선, 현행 기능을 즉시 막지는 않음 |

## 2. 요약 대장

| ID | 우선 | 구분 | 항목 | 현재 상태 | 권장 책임 |
|---|---:|---|---|---|---|
| OI-01 | P0 | 보안/정합성 | group-location 권한 경계 | 검증 없음 | Engine + Core + Gateway |
| OI-02 | P0 | 배포/데이터 | DB migration 소유권 | schema validate만 존재 | Engine + Infra |
| OI-03 | P2 | 제품 | 사용자용 Flow 실행 이력과 실패 통지 | 보류 | Product + Engine + Front |
| OI-04 | P2 | 미구현 | External notification과 ERROR 상태 | 타입만/enum만 존재 | Product + Engine |
| OI-05 | P1 | lifecycle | Sensor/Actuator 삭제 정책 | Engine 미수신 | Core + Engine |
| OI-06 | P0 | 외부 release blocker | Front Schedule 계약 | 5필드 cron + Alert 생성 | Front + Engine |
| OI-07 | P0* | 제품/연동 | Actuator 성공 의미와 실제 장비 전달 | Core DB 상태·run log까지만 확인 | Core + Product |
| OI-08 | P1 | 계약 | Actuator preset 중복·placeholder | 양쪽 하드코딩 | Core + Engine + Front |
| OI-09 | P1 | Redis | state 수명·cleanup 정책 | 일부 TTL 잔존/영구 key | Engine + Ops |
| OI-10 | P1 | Redis/배포 | DB 324와 cluster 비호환 | 운영 검증 필요 | Infra + Engine |
| OI-11 | P1 | 메모리 | local route snapshot 항목 증가 | 최대 age만 있고 항목 상한 없음 | Engine |
| OI-12 | P1 | 메시징 | Alert delivery 보장 | confirm/outbox/retry 없음 | Engine + AI |
| OI-13 | P1 | HA | 실제 2-Pod failover·flapping | 단위 테스트 중심 | Infra + Engine |
| OI-14 | P1 | 데이터 신선도 | durable queue stale backlog | max age 정책 없음 | Core + Engine + Product |
| OI-15 | P1 | 관측 | failure metric 외부 수집 | counter만 존재 | Engine + Infra |
| OI-16 | P2 | 그래프 | fan-in 의미 | 허용되나 join 의미 없음 | Product + Engine + Front |
| OI-17 | P2 | API UX | 구조 오류 상세 응답 | 요약 message만 노출 | Engine + Front |
| OI-18 | P2 | 시간 | 장소/사용자별 timezone | 전역 1개 | Product + Engine |
| OI-19 | P2 | 상태 | 같은 이름 Flow 수정 | 명시적 보류 | Product + Engine |
| OI-20 | P2 | 보고 | AI report용 Flow 실행 횟수 | 구현 없음 | Product + AI + Engine |
| OI-21 | P1 | 용량 | 처리량·지연 SLO | 수치 미확정 | Product + Ops + Engine |
| OI-22 | P1 | 배포 | 운영 image가 기준 commit과 다름 | manifest tag 과거 버전 | Infra |
| OI-23 | P1 | 정합성 | DB commit→runtime 반영과 stale Redis 창 | afterCommit·Redis TTL 기반 eventual consistency | Engine |
| OI-24 | P0 | 보안/배포 | Secret 소유권과 내부 전송 암호화 | manifest 생성 주체·앱 TLS 설정 없음 | Infra + Engine + Core |
| OI-25 | P1 | 실행 정책 | 여러 Flow의 Action 충돌과 순서 | 순차 실행이나 조회 순서·priority 없음 | Product + Engine |
| OI-26 | P2 | 제품/AI | AI Suggestion Node 재도입 | 기존 경로 제거, 실용성 보완 전 보류 | Product + AI + Engine + Core + Front |
| OI-27 | P0 | 실행 정합성 | 복합 Threshold의 누락 metric 오탐과 LOCATION 의미 | 같은 packet만 평가하며 null `<`, `<=`, `!=`가 true | Product + Engine + Front + Core |

`OI-07`의 P0는 제품 목표가 실제 물리 장치 제어인 경우다. 시뮬레이션 상태 변경만 목표라면 성공 의미를 그 수준으로 명시하고 우선순위를 낮출 수 있다.

## 3. 상세 항목

### OI-01 group-location 권한 경계

현행:

- Engine은 요청자의 group role만 확인한다.
- request locationId가 group 소속인지 확인하지 않는다.
- Actuator Action은 저장된 locationId만 Core에 보내며, Schedule은 telemetry group route 없이 직접 실행된다.
- Core 내부 actuator API는 group 소유권과 service credential을 검증하지 않고 `callerService != USER`만 확인한다.

위험:

- 다른 그룹 locationId를 아는 관리자가 교차 그룹 액추에이터 상태를 변경할 수 있다.

권장:

1. Flow 생성·수정에서 Core group-location lookup으로 소속 검증
2. 기존 저장 데이터 감사와 ACTIVE 전환 시 재검증
3. actuator request에 groupId 추가, Core에서 재검증
4. 내부 API service authentication과 NetworkPolicy
5. 악의적 locationId 통합 테스트

완료 기준:

- 신규·기존 교차 그룹 location Flow의 저장·활성화·actuator 호출이 모두 불가능하고 자동화 테스트가 이를 고정한다.

### OI-02 DB migration 소유권

현행:

- `ddl-auto=validate`
- Flyway/Liquibase/schema SQL 없음

결정할 것:

- schema 생성 주체
- 기존 운영 DB baseline 방법
- FK 적용 여부
- JPA index/unique와 실제 DB 일치 보장 방법

권장:

- Engine 또는 명시적으로 지정된 Infra/DBA 소유자가 versioned DDL을 관리한다. Engine이 소유한다면 Flyway가 가장 직접적인 선택이며, 어떤 도구를 쓰든 빈 PostgreSQL integration test를 추가한다.

완료 기준:

- 새 환경에서 수동 DDL 없이 migration→application start가 재현된다.

### OI-03 사용자용 Flow 실행 이력과 실패 통지

현행:

- DB/파일 실행 이력 없음
- 성공·실패 조회 API 없음
- 실패 Rabbit event 없음
- AI Alert는 사용자가 만든 정상 Action 결과이지 engine failure 알림이 아님

이 항목은 이전 결정상 보류다. 구현 시에는 executionId, triggerType, scheduled/event time, terminal node, action request/result, failure kind를 어느 서비스가 보관할지 먼저 정해야 한다.

### OI-04 External notification과 ERROR 상태

`EXTERNAL_NOTIFICATION`은 params만 있고 executor가 없어 활성화가 거부된다. `ERROR`는 enum과 archive 전이만 있고 진입 경로가 없다.

선택지:

- 실제 요구가 확정될 때까지 public NodeType/status에서 제거
- 미래 예약값으로 유지하되 API 문서에 미지원 명시
- 전송·오류 전이 정책을 완성해 구현

현 단계 권장: 미지원 상태를 명시하고 실제 담당 서비스 계약이 생기기 전 executor를 억지로 만들지 않는다.

### OI-05 Sensor/Actuator 삭제 정책

Core는 `sensor.deleted`, `actuator.deleted`를 발행하지만 Engine은 수신하지 않는다.

검토:

- SENSOR Flow를 INACTIVE/ERROR/ARCHIVED 중 어디로 옮길지
- Actuator Flow는 actuatorId가 아니라 type만 저장하므로 삭제 event의 id로 영향 범위를 알 수 있는지
- Core 조회를 추가할지, location/type payload로 event 계약을 확장할지
- 사용자에게 수정 필요 상태를 어떻게 보여줄지

### OI-06 Front Schedule 계약

2026-08-31 Front dev:

- 5필드 cron을 생성
- action을 Alert로 고정
- optional Threshold도 Schedule 뒤에 배치 가능

Engine:

- seconds=0인 6필드 cron
- `SCHEDULE → ACTUATOR_CONTROL` 직접 연결만 허용

완료 기준:

- Front가 daily/weekly/monthly를 6필드로 만들고 actuator 설정 UI를 제공하며 생성→활성화→Core 호출 E2E가 통과한다.

### OI-07 Actuator 성공 의미와 실제 장비 전달

현재 Core code에서는 actuator entity `currentState`와 run log 저장까지 확인되며 MQTT 또는 물리 장비 command 발행 경로는 확인되지 않았다.

결정할 것:

- 제품 목적이 시뮬레이션 상태 변경인지 실제 장치 제어인지
- HTTP 200을 최종 성공으로 볼지 접수 성공으로 볼지
- device acknowledgment, timeout, idempotency, 결과 event가 필요한지

### OI-08 Actuator preset 중복

Engine과 Core에 AIRCON/AIR_PURIFIER/VENTILATION_FAN 규칙이 각각 하드코딩돼 있다. Core 주석은 placeholder라고 명시한다.

양쪽 검증은 `command`와 일부 enum 값을 대소문자 무시로 받지만 Engine은 원문을 전달하고 Core는 원문 command를 상태 JSON key로 저장한다. `POWER`와 `power`가 별도 key가 될 수 있으므로 API 정규형 강제 또는 저장 전 normalization도 필요하다.

권장:

- Core를 계약 소유자로 정하고 OpenAPI/공유 schema 또는 consumer contract test로 Engine/Front를 검증한다.
- 실제 simulator 값 확정 전 새 actuator type을 Engine 단독으로 추가하지 않는다.

### OI-09 Redis state 수명과 cleanup

검토 항목:

- Schedule 삭제 후 `schedule-state:*` INACTIVE key 영구 잔존
- Flow 비활성화·보관·복구 시 Alert/Timer 남은 TTL 유지
- Alert publish 실패 후 cooldown 유지
- Timer true 선점 후 Action 실패에도 interval 유지

각 상태가 “Flow 활성 세션” 기준인지 “Node ID의 연속 상태” 기준인지 제품 정책을 확정해야 한다.

### OI-10 Redis DB 324와 cluster 비호환

- prod logical DB가 324로 고정돼 있다.
- multi-key Lua key에 hash tag가 없어 Redis Cluster와 호환되지 않는다.

완료 기준:

- 운영 Redis의 database 수와 topology가 검증되고 배포 문서에 non-cluster 전제가 명시되거나, cluster용 key scheme으로 migration한다.

### OI-11 local route snapshot 항목 증가

snapshot은 1분 이후 사용되지 않지만 map entry를 자동 축출하지 않는다. 실제 route cardinality와 heap을 측정하고 필요할 때만 Caffeine max-size/expire policy로 바꾼다. 현재 규모를 모른 채 새 cache 계층을 추가하지 않는다.

### OI-12 Alert delivery 보장

Engine에는 publisher confirm, mandatory return, outbox, retry가 없다. RabbitTemplate 호출 성공을 AI 저장 성공으로 보면 안 된다.

결정할 것:

- Alert 유실 허용 여부
- publisher confirm과 return만으로 충분한지
- transactional outbox가 필요한 업무 중요도인지
- eventId 중복 처리와 retry owner를 Engine/AI 중 어디로 둘지

### OI-13 실제 2-Pod failover와 flapping

현행은 5초 heartbeat, 15초 TTL, 첫 UP 확인 시 즉시 handback이다. failover 단위 테스트는 있으나 실제 두 Pod 장애 E2E는 없다. takeover/handback 전환 중 기존 owner와 takeover consumer가 잠시 공존할 가능성도 포함해 실제 staging에서 다음을 검증한다.

- pod kill 후 takeover 지연
- queue별 consumer 경합과 backlog
- Redis 순간 지연 시 오판 방지
- peer heartbeat가 흔들릴 때 takeover/handback 반복 여부
- rolling update 동안 안정성. 초기 기획의 “트래픽 무손실 graceful shutdown”은 heartbeat 즉시 삭제·명시적 handoff가 없는 현행 코드로 아직 보장되지 않음

### OI-14 durable queue stale backlog

Telemetry는 timestamp watermark로 인스턴스 내 역순을 막지만 watermark가 인스턴스 로컬 상태이므로, 최초로 읽는 오래된 packet이나 failover 후 local watermark가 없는 packet은 실행될 수 있다. 또한 Action 이후 ACK 전에 consumer가 종료되면 다른 인스턴스에서 redelivery돼 중복 효과가 날 수 있다. message 최대 허용 age가 없다.

선택지:

- 현재처럼 backlog 실행 허용
- event timestamp가 now보다 일정 시간 이상 오래되면 drop
- Rabbit queue TTL/length limit 적용

장치 제어의 안전성과 데이터 유실 우선 정책을 함께 고려해 결정한다.

### OI-15 failure metric 외부 수집

Micrometer counter는 있으나 Prometheus registry와 exposure가 없다. metric을 유지할 가치가 있다면 수집 backend와 alert threshold를 연결하고, 아니라면 코드 내부 counter만 존재하는 상태를 운영 지표로 소개하지 않는다.

### OI-16 fan-in 의미

현재 multiple incoming Link는 허용되지만 join/AND/wait 의미가 없다. Action fan-out은 각 Action을 독립 target으로 순차 실행할 뿐이며, 서로 다른 경로의 합류 상태를 병합하거나 기다리지 않는다.

권장 현행 설명: “경로 합류는 가능하지만 데이터 병합이나 동기화 의미가 없다.” Front가 이를 표현하지 않는다면 Validator에서 명시적으로 막는 편이 사용자 혼란을 줄일 수 있다.

### OI-17 구조 오류 상세 응답

Validator는 node별 오류 목록을 만들지만 REST body는 첫 오류 요약만 보낸다. Front가 graph field를 강조해야 한다면 error response에 stable error code와 `errors[]`를 추가해야 한다. API 호환성 버전을 함께 검토한다.

### OI-18 timezone

Schedule과 TimeWindow가 전체 Engine 공통 timezone 하나를 사용한다. 장소별 timezone이 필요하면 Flow configuration에 zone을 넣고 기존 Flow migration과 DST 정책을 정해야 한다. 국내 단일 zone 운영이면 현행이 더 단순하다.

### OI-19 같은 이름 Flow 수정

명시적 보류 항목이다. 후보는 다음과 같다.

- ARCHIVED 제외 partial unique index
- version/revision 테이블과 logical flow ID 분리
- 수정 중 임시 이름/삭제 순서 조정

다른 우선 작업이 끝난 뒤 이력·복구 UX와 함께 결정한다.

### OI-20 AI report용 Flow 실행 횟수

과거 요구 문서에는 report용 실행 횟수가 있었지만 현재 publisher, counter persistence, query contract가 없다. 폐기인지 후속인지 Product/AI가 확정해야 한다.

### OI-21 처리량·지연 SLO

현재 cache·thread pool 수치는 있으나 workload 근거가 없다. packet rate, route당 Flow, 동시 Schedule을 기준으로 load test 후 SLO와 resource를 확정한다.

### OI-22 운영 image 기준선

Kubernetes manifest가 기준 dev보다 과거 image tag를 가리킨다. 개발 중 아직 배포 PR이 반영되지 않은 정상 상태인지, release 누락인지 구분해 확인하고 배포 검증 시 image digest와 git commit을 함께 기록한다.

### OI-23 DB commit→runtime 반영과 stale Redis 창

현행:

- Flow 상태·정의 변경은 DB commit 뒤 `afterCommit` callback으로 route cache와 Schedule을 갱신한다.
- DB transaction과 Redis·로컬 cron 갱신은 하나의 원자 transaction이 아니다.
- 비활성화·보관 commit 직후 callback 전에는 기존 cron이 Redis ACTIVE를 선점해 마지막 한 번 실행할 수 있다.
- local cancel은 `cancel(false)`이므로 callback 전에 이미 시작해 claim한 Schedule은 취소 뒤에도 Core 요청까지 끝낼 수 있다.
- route snapshot Redis 저장이 실패하면 기존 Redis 값은 삭제되지 않는다. local fallback의 1분 제한과 무관하게 기본 30분 cache TTL까지 정상 hit로 읽힐 수 있다.
- cross-instance cache 테스트는 공유 cache test double을 사용하며 실제 Redis cache round-trip·두 provider 시나리오는 없다.

후보:

1. snapshot에 DB revision/version을 포함하고 읽을 때 실행 가능 version을 검증한다.
2. 상태 변경 시 stale Redis key를 먼저 안전하게 무효화하고 실패를 retry/reconcile하는 별도 경로를 둔다.
3. transactional outbox 또는 주기 poller로 DB→runtime 반영을 내구성 있게 재시도한다.
4. 현재 eventual consistency를 수용하되 최대 창, 로그·metric, 운영 대응을 명시한다.

현재 규모 권장: 먼저 실제 Redis Testcontainer 교차 provider 회귀 테스트와 경합 재현 테스트를 추가하고, 허용 가능한 stale 실행 창을 제품과 합의한 뒤 1 또는 2를 선택한다. 요구가 없는 상태에서 곧바로 outbox까지 도입하는 것은 과하다.

### OI-24 Secret 소유권과 내부 전송 암호화

현행:

- 배포 manifest는 `insighton-ruleengine-secret`, `insighton-ghcr-secret`을 참조하지만 이 저장소군에서 생성 리소스와 회전 절차가 확인되지 않는다.
- prod PostgreSQL, Redis, RabbitMQ, Core, Zipkin 연결은 애플리케이션 설정상 평문 프로토콜이다.
- 별도 서비스 메시 또는 인프라 계층 암호화 여부가 문서화되지 않았다.

출시 전 확정:

1. secret 프로비저닝·회전·폐기 소유자와 필수 key 목록
2. 저장소에 secret 값을 커밋하지 않는 배포 방식
3. 내부 전송 암호화 소유 계층과 인증서·신뢰 저장소 운영
4. NetworkPolicy와 Engine→Core 서비스 인증
5. staging에서 암호화·인증 실패와 회전 시험

### OI-25 여러 Flow의 Action 충돌과 순서

한 telemetry packet의 후보 Flow는 현재 순차 실행되지만 repository에 정렬 조건이 없고 Flow priority 모델도 없다. 같은 packet을 받은 여러 Flow가 같은 actuatorType에 상충하는 명령을 보내면 마지막 상태가 조회 순서에 좌우되며 그 순서는 계약상 보장되지 않는다. 서로 다른 queue의 Flow는 병렬이므로 전역 순서도 없다.

결정할 것:

- 같은 route·actuatorType에 상충 Flow를 허용할지
- 허용한다면 explicit priority, conflict rule, 최근 사용자 변경 우선 중 무엇을 쓸지
- 순서를 보장하지 않는 독립 명령 모델을 유지하고 Core에서 arbitration할지
- Schedule과 telemetry 명령이 동시에 도착할 때의 우선순위

현재 규모 권장: 먼저 중복 target을 생성·활성화할 때 사용자에게 경고하고 실제 충돌 사례를 측정한다. 명확한 제품 규칙 없이 범용 workflow priority·분산 락을 먼저 도입하지 않는다.

### OI-26 AI Suggestion Node 재도입

현행:

- Engine의 `AI_SUGGESTION` NodeType, executor, event publisher는 통합 `dev`에서 제거됐다.
- AI 서비스에는 정기 제안과 Rule Engine event listener가 있으나 이벤트 생산자가 없다.
- 이벤트 기반 경로는 단일 실시간 metric을 추가한 뒤 최근 한 시간 통계와 정기 경로의 공통 로직을 재사용하므로 정기 제안과의 제품 차이가 작다.

결정:

- 기존 event DTO와 publisher를 그대로 복원하지 않는다.
- 실시간 복합 판단의 사용자 시나리오와 성공 지표가 승인될 때까지 보류한다.
- 재도입 시 cooldown, event id 멱등성, stale event·suggestion 만료, 호출 상한, Core 재검증을 필수 조건으로 둔다.

상세 선택지, 장단점, 후보 계약과 PoC 계획은 [AI Suggestion Node 보류 및 재설계 제안](13-ai-suggestion-node-deferred-proposal.md)을 따른다.

### OI-27 복합 Threshold의 누락 metric 오탐과 LOCATION 의미

현행:

- Front는 여러 조건을 Threshold Node로 직렬 연결해 AND를 표현한다.
- 모든 Threshold는 한 `SensorEvent`의 metrics Map을 공유하므로 같은 센서가 같은 패킷에 보낸 값만 정확히 결합할 수 있다.
- LOCATION Trigger는 위치의 여러 센서 최신값을 합치지 않고 현재 도착한 센서 패킷 하나만 평가한다.
- Front는 LOCATION에 실제 센서 attribute를 조회하지 않고 온도·습도·CO₂·조도 네 metric을 `위치 전체의 공통 측정 항목`으로 표시한다.
- 없는 Map key는 SpEL에서 null이 되며 숫자와의 `<`, `<=`, `!=` 비교가 true가 되어 Alert 또는 Actuator 오탐으로 이어질 수 있다.
- 저장·활성화 검증은 expression 문법만 확인하고 metric 존재·센서 소유권·패킷 completeness를 확인하지 않는다.

우선 조치:

1. 누락·null·비숫자 metric은 모든 비교에서 조건 불충족으로 처리한다.
2. Front의 LOCATION 복합조건을 제한하고 현재 packet 의미를 사용자에게 명시한다.
3. SENSOR Flow 저장·활성화 시 metricKey와 센서 attribute의 관계를 Backend에서 검증한다.
4. cross-sensor 요구가 확인되면 freshness TTL, source provenance, snapshot version을 가진 별도 기능으로 설계한다.

완료 기준:

- 누락 metric의 모든 비교 연산자가 Action을 통과시키지 않는 자동화 테스트가 있다.
- 동일 센서·동일 패킷만 현재 복합조건 범위라는 Front 문구와 API 계약이 일치한다.
- LOCATION이 aggregate가 아니라 현재 packet route라는 코드·주석·문서가 일치한다.
- 서로 다른 센서 조건을 허용할 경우 stale/unknown/source/중복 실행 정책과 shared state 설계가 승인된다.

상세 분석과 선택지의 장단점은 [복합 임계조건 지원 범위와 Front·Engine 계약 분석](14-composite-threshold-condition-analysis.md)을 따른다.

## 4. 결정 회의 시 기록할 형식

각 항목을 닫을 때 다음을 남긴다.

- 결정 일자와 참여자
- 선택한 옵션과 근거
- API/DB/Redis/Rabbit/Front 호환 영향
- rollout과 rollback
- 자동화 테스트
- 운영 관측 지표
- 갱신한 요구사항·설계 문서 ID
