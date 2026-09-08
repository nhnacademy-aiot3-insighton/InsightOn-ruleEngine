# 초기 기획 대비 변경·의사결정 기록

## 1. 목적

기존 `docs/`에는 여러 시점의 기획, prototype, 후보 설계가 섞여 있다. 이 문서는 어떤 방향이 현행으로 확정됐고 무엇이 대체·제거·보류됐는지를 기록한다.

## 2. 현행 결정

| ID | 결정 | 이유·결과 | 상태 |
|---|---|---|---|
| DEC-001 | PostgreSQL을 Flow 원본, Redis를 route snapshot과 runtime state로 사용한다. | 패킷마다 DB 조회하는 병목을 피하면서 DB 원본성을 유지 | 확정 |
| DEC-002 | Schedule은 모든 인스턴스에 로컬 cron을 등록하고 Redis로 동일 시각 실행을 선점한다. | queue mapping과 무관하게 중복 실행을 억제 | 확정 |
| DEC-003 | Schedule 실행은 RabbitMQ를 왕복하지 않고 FlowRunner를 직접 호출한다. | Engine→AMQ→Engine 불필요한 사이클 제거 | 확정 |
| DEC-004 | Schedule Flow는 `SCHEDULE → ACTUATOR_CONTROL` 직접 연결만 허용하며 복수 Actuator fan-out을 지원한다. | 시간+Alert만 있는 목적 없는 Flow 방지, 예약 동작 목적 명확화 | 확정 |
| DEC-005 | Schedule 중단 시간의 missed run을 보정하지 않는다. | 재기동 폭주·늦은 장치 동작보다 누락 선택 | 확정 |
| DEC-006 | Schedule Redis 장애 시 실행하지 않는다. | 중복 장치 제어보다 미실행을 선택하는 fail-closed | 확정 |
| DEC-007 | Telemetry 오류는 ACK/drop하고 Engine retry·DLQ를 두지 않는다. | 패킷 손실 허용 정책과 재처리 폭주 방지 | 확정 |
| DEC-008 | 오류의 일시/영구 분류는 로그·metric에만 사용한다. | 자동 retry는 도입하지 않음 | 확정 |
| DEC-009 | 정상 packet/route/action 로그는 DEBUG로 둔다. | 고빈도 INFO 로그와 운영 비용 축소 | 확정 |
| DEC-010 | 액추에이터 Action은 Core 동기 HTTP를 사용한다. | 실제 상태 소유자 Core에 명령 위임 | 현행, 성공 의미 검증 필요 |
| DEC-011 | Alert Action은 Redis count/cooldown 후 AI Rabbit event를 발행한다. | 반복 알림 억제와 AI 소비 경로로 전달 | 현행, delivery 보강 여부 검증 필요 |
| DEC-012 | LOCATION은 현재 packet metrics만 사용한다. | 최신값 aggregate 저장·provenance 복잡도 제거 | 확정 |
| DEC-013 | TIMER는 delay가 아니라 interval당 최초 1회 통과 throttle이다. | 다중 인스턴스 공유 suppression | 확정 |
| DEC-014 | queue ownership은 16개를 두 인스턴스가 홀짝 8개씩 나눈다. | location hash 안정성과 단순 failover | 현행 운영 제약 |
| DEC-015 | Flow 수정은 기존 보관 + 새 INACTIVE ID 생성 방식이다. | 실행 중 정의의 불변성과 버전 흔적 유지. 같은 이름 수정 UX는 DEF-001로 보류 | 정책 확정·동일 이름 UX 보류 |
| DEC-016 | Core group/location 삭제 시 Flow를 영구 cleanup한다. | 존재하지 않는 scope의 실행 방지 | 확정 |
| DEC-017 | 같은 output port의 복수 Link는 모든 target이 Action일 때만 허용하고 Link ID 순서대로 실행한다. | 일반 DAG 의미를 도입하지 않고 하나의 판단 결과로 여러 부작용을 수행 | 확정. 실패 격리·부분 성공은 후속 |

## 3. 초기 기획 대비 변화

| 주제 | 초기·과거 방향 | 현행 | 분류 |
|---|---|---|---|
| 생성 주체 | USER/AI, `FlowOrigin` | 사용자 API만 존재 | 대체 |
| 초기 상태 | DRAFT 포함 | 생성·수정본 INACTIVE | 대체 |
| API 입력 | 단순 폼을 Engine이 그래프로 변환 | client가 완성된 nodes/links 전달 | 대체 |
| Trigger | SENSOR, SCHEDULE 중심 | SENSOR, LOCATION, SCHEDULE | 확장 완료 |
| Filter | THRESHOLD 중심, TimeWindow/Timer 예정 | 세 Filter executor 구현 | 완료 |
| Action | DEVICE_CONTROL/Rabbit 후보, 더미 Alert | Core HTTP ACTUATOR_CONTROL, AI Rabbit ALERT | 대체·완료 |
| AI 제안 | AI_SUGGESTION 발행 | 통합 dev에서 관련 경로 삭제, 실시간 복합 판단 가치 검증 전 재도입 보류 | 제거·재설계 보류 |
| External notification | Telegram/Email best effort 계획 | params만 있고 executor 없음 | 미구현 |
| Sensor route | devEUI/devName 후보 | groupId/locationId + sensorId | 대체 |
| Telemetry queue | Q1/Q2, ack NONE 설명 | 16 consistent-hash queue, manual ACK/drop | 대체 |
| Telemetry 보존 | 1초 TTL과 DLQ 후보 | durable queue, TTL·telemetry DLQ 없음 | 정책 변경 |
| Cache 동기화 | Outbox/poller/pub-sub/local swap | Redis route snapshot + 최대 사용 시간이 제한된 local snapshot과 DB fallback | 대체 |
| Location 데이터 | 장소별 최신 metric aggregation | 현재 단일 packet metrics | 제거·단순화 |
| Schedule | 실행 방식 미정/Rabbit 재진입 가능성 | local cron + Redis state/version/NX | 완료 |
| Schedule graph | 일반 Filter/Alert 가능성 | 직접 Actuator 전용, 복수 Actuator fan-out 허용 | 정책 변경 |
| 일회성 예약 | `executeAt` 후보 | 없음 | 보류 |
| missed run | 재기동 보정 후보 | 건너뜀 | 확정 |
| 장치 삭제 | Flow ERROR 전환 계획 | group/location만 cleanup, ERROR 진입 없음 | 미구현·재결정 필요 |
| 실행 실패 알림 | 실패 event/EngineAlert 방향 | 운영 로그·counter만 존재 | 보류 |
| Prototype endpoint | 수동 rule event test | 현재 endpoint 없음 | 제거 |
| 확장성 | 동적 scale 후보 | 정확히 2개 instance | 현행 제약 |
| Failover 감지 | heartbeat 1초·TTL 3초, 연속 복구 확인 후보 | heartbeat 5초·TTL 15초, 첫 정상 확인 때 즉시 handback | 안정성 방향 변경 |
| 장치 유효성 | Redis `deviceValid` 상세 응답과 주기 재조정 후보 | 해당 상태·응답·주기 작업 없음 | 제거·재결정 필요 |
| 복합 Trigger | `AND_CROSS_SENSOR`, 다중 Trigger stretch | Trigger 정확히 1개, 관련 타입·상태 없음 | 제거·단순화 |
| 권한 헤더 | Gateway `X-User-Role` 신뢰 후보 | `X-User-Id`로 Core membership을 매 요청 조회 | 대체 |
| Actuator API | device별 endpoint 후보 | location + actuatorType 일괄 상태 변경 endpoint | 대체 |
| fan-in | 복수 incoming Link 금지 | 복수 incoming 허용, join/AND/wait 의미 없음 | 정책 변경·의미 확정 필요 |

## 4. 제거된 개념

다음 용어·경로는 새 요구사항으로 취급하지 않는다.

- `DRAFT`
- `FlowOrigin`
- AI가 Flow를 생성·수정하는 기능
- 현행 구현으로서의 `AI_SUGGESTION` Action과 suggestion publisher. 재도입 후보는 [별도 제안서](13-ai-suggestion-node-deferred-proposal.md)에서 관리한다.
- devName/devEUI 기반 Engine route
- prototype bypass router와 수동 test endpoint
- dummy Alert
- Location 최신 metric aggregate/provenance 저장 계층
- Outbox/poller/Redis pub-sub 기반 Flow cache 갱신
- Schedule의 AMQ 재진입
- Telemetry replay/dedup/retry pipeline

## 5. 명시적으로 보류된 결정

| ID | 항목 | 현재 처리 |
|---|---|---|
| DEF-001 | 같은 이름 Flow 수정 | 전체 주요 작업 이후 재검토. 현재 unique 제약 유지 |
| DEF-002 | 사용자용 실행 이력·실패 통지 | Schedule/실행 기능 범위에서는 넘어가기로 함 |
| DEF-003 | 일회성 Schedule | 반복 cron만 유지 |
| DEF-004 | 3개 이상 인스턴스 | 현재 2개 운영 전제 유지 |
| DEF-005 | Front 구현 | Engine 작업과 분리하되 출시 전 계약 수정 필수 |
| DEF-006 | AI Suggestion Node 재도입 | 기존 계약은 복원하지 않는다. 실시간 복합 판단 시나리오·효과 지표·안전장치가 합의되면 상황 인지형 Action으로 재설계한다. |

보류는 구현된 것으로 간주하지 않는다. 보류 항목의 현재 제약은 API·운영 문서에 계속 노출한다.

## 6. 구현과 정책이 아직 일치하지 않는 항목

- `ERROR` status는 존재하지만 진입 경로가 없다.
- `EXTERNAL_NOTIFICATION` 주석은 전송 정책을 설명하지만 executor가 없다.
- `LocationParams` 과거 주석은 최신 metric 집계를 설명하지만 실제 실행은 현재 packet만 사용하므로 명확히 다르다. 주석 정리가 필요하다.
- 사용되지 않는 `FLOW_FAN_IN_NOT_ALLOWED` error code가 있지만 현재 fan-in을 막지 않는다.
- Alert 기본 3회/5분/30분은 구현값이나 제품 승인 기록이 분명하지 않다.
- Core actuator preset에는 실제 simulator/Front 확인 전 placeholder라는 주석이 있다.
- `rule_engine.execution.failures`는 기록되지만 외부 scrape 경로가 없다.
- 과거 AI report 요구사항의 Flow 실행 횟수 집계는 구현되지 않았다.

각 항목은 [미결·검증 대장](11-open-issues-and-validation.md)에서 추적한다.

## 7. 변경 기록 유지 방식

새 정책을 확정할 때 다음을 함께 기록한다.

1. 선택한 옵션과 배제한 옵션
2. 데이터·API·배포 호환성 영향
3. migration 또는 rollout 순서
4. 자동화 테스트와 운영 검증 방법
5. 기존 문서 중 더 이상 기준이 아닌 내용
