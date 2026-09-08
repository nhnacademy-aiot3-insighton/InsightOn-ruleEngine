# 인터페이스 명세서

## 1. 공통 규칙

- Flow API base path: `/api/v1/flows`
- 모든 Flow API는 query parameter `groupId`와 header `X-User-Id`를 요구한다.
- body가 있는 POST/PUT 요청과 성공 응답은 JSON이다. GET은 request body가 없고 DELETE 성공은 body가 없다.
- 시간은 JSON ISO-8601 형식이다. Flow `createdAt`은 UTC 기준으로 생성된 `OffsetDateTime`이다.
- `GlobalExceptionHandler`가 명시적으로 처리하는 Engine·요청·권한·Core 의존성 예외는 error code나 구조 오류 목록을 노출하지 않고 `status`, `message`만 반환한다.

```json
{
  "status": 400,
  "message": "요청값이 올바르지 않습니다."
}
```

예상 밖 예외와 별도로 처리하지 않은 DB 오류에는 generic handler가 없다. 이 경우 Spring Boot 기본 오류 응답이 사용될 수 있으므로 위 JSON 형식을 모든 5xx 응답의 안정 계약으로 간주하면 안 된다.

## 2. Flow REST API

### 2.1 Endpoint 목록

| Method | Path | 권한 | 성공 | 설명 |
|---|---|---|---|---|
| POST | `/api/v1/flows?groupId={id}` | MANAGER | 201 | INACTIVE Flow 생성 |
| GET | `/api/v1/flows?groupId={id}` | MEMBER | 200 | ARCHIVED 제외 목록 |
| GET | `/api/v1/flows?groupId={id}&status={status}` | MEMBER | 200 | 상태별 목록 |
| GET | `/api/v1/flows?groupId={id}&locationId={id}&status={status}` | MEMBER | 200 | 장소·상태 목록 |
| GET | `/api/v1/flows/{flowId}?groupId={id}` | MEMBER | 200 | 보관 포함 상세 |
| PUT | `/api/v1/flows/{flowId}/status?groupId={id}` | MANAGER | 200 | ACTIVE/INACTIVE 전환 |
| POST | `/api/v1/flows/{flowId}/archive?groupId={id}` | MANAGER | 200 | ARCHIVED 전환 |
| PUT | `/api/v1/flows/{flowId}?groupId={id}` | MANAGER | 200 | 기존 보관 + 새 INACTIVE 생성 |
| POST | `/api/v1/flows/{flowId}/restore?groupId={id}` | MANAGER | 200 | ARCHIVED → INACTIVE |
| DELETE | `/api/v1/flows/{flowId}?groupId={id}` | MANAGER | 204 | ARCHIVED 영구 삭제 |

`locationId`만 넣고 `status`를 생략한 목록 요청은 400이다.
목록 API에는 pagination과 명시적 정렬 계약이 없다. 반환 순서를 업무 우선순위나 실행 순서로 사용하면 안 된다.

### 2.2 생성 요청

#### 요청 필드 제약

| 필드 | 생성 | 수정 | 제약 |
|---|---|---|---|
| `locationId` | 필수 | 변경 불가·요청 없음 | 양수 Long |
| `name` | 필수 | 필수 | 공백 제외 1~100자 |
| `description` | 선택 | 선택 | null 허용, 최대 2,000자 |
| `nodes` | 필수 | 필수 | 생성 2~500개, 수정 1~500개. 실제 유효 그래프는 Trigger·Action 때문에 최소 2개 |
| `links` | 필수 | 필수 | DTO상 생성 0~1,000개, 수정 1~1,000개. 실제 유효 그래프는 최소 1개 |
| `clientNodeKey` | 필수 | 필수 | 공백 제외 1~100자, 요청 안에서 유일 |
| `nodeType` | 필수 | 필수 | `NodeType` enum |
| `configuration` | 필수 | 필수 | NodeType별 JSON 객체 |
| link source/target key | 필수 | 필수 | 각각 공백 제외 1~100자, 존재하는 node key 참조 |
| link source/target port | 필수 | 필수 | 각각 공백 제외 1~50자, NodeType별 port 계약 충족 |

```json
{
  "locationId": 42,
  "name": "고온 경고",
  "description": "온도가 28도를 넘으면 경고",
  "nodes": [
    {
      "clientNodeKey": "sensor-1",
      "nodeType": "SENSOR",
      "configuration": { "sensorId": 1001 }
    },
    {
      "clientNodeKey": "threshold-1",
      "nodeType": "THRESHOLD",
      "configuration": { "expression": "#metrics['temperature'] > 28" }
    },
    {
      "clientNodeKey": "alert-1",
      "nodeType": "ALERT",
      "configuration": {
        "title": "고온 감지",
        "message": "온도를 확인해 주세요.",
        "severity": "WARNING",
        "requiredCount": 3,
        "countTimeoutSeconds": 300,
        "cooldownSeconds": 1800
      }
    }
  ],
  "links": [
    {
      "sourceClientNodeKey": "sensor-1",
      "targetClientNodeKey": "threshold-1",
      "sourcePort": "out",
      "targetPort": "in"
    },
    {
      "sourceClientNodeKey": "threshold-1",
      "targetClientNodeKey": "alert-1",
      "sourcePort": "true",
      "targetPort": "in"
    }
  ]
}
```

### 2.3 Schedule 생성 요청

```json
{
  "locationId": 42,
  "name": "평일 오전 에어컨",
  "description": "평일 오전 9시에 냉방 시작",
  "nodes": [
    {
      "clientNodeKey": "schedule-1",
      "nodeType": "SCHEDULE",
      "configuration": { "cron": "0 0 9 * * MON-FRI" }
    },
    {
      "clientNodeKey": "actuator-1",
      "nodeType": "ACTUATOR_CONTROL",
      "configuration": {
        "actuatorType": "AIRCON",
        "command": "power",
        "commandValue": "ON"
      }
    }
  ],
  "links": [
    {
      "sourceClientNodeKey": "schedule-1",
      "targetClientNodeKey": "actuator-1",
      "sourcePort": "out",
      "targetPort": "in"
    }
  ]
}
```

Schedule은 5필드 Unix cron이 아니라 seconds를 포함한 6필드다. `0 9 * * *`는 거부되고 `0 0 9 * * *`가 매일 09:00을 뜻한다.

### 2.4 상태 변경 요청

```json
{ "status": "ACTIVE" }
```

허용 request 값은 `ACTIVE`와 `INACTIVE`다. ARCHIVED/ERROR 전환은 이 endpoint로 할 수 없다.

### 2.5 요약 응답

```json
{
  "flowId": 10,
  "groupId": 7,
  "locationId": 42,
  "name": "고온 경고",
  "description": "온도가 28도를 넘으면 경고",
  "status": "INACTIVE",
  "createdAt": "2026-08-31T04:00:00Z"
}
```

### 2.6 상세 응답

상세 응답은 요약 필드에 다음을 추가한다.

```json
{
  "nodes": [
    {
      "nodeId": 101,
      "nodeType": "SENSOR",
      "configuration": { "sensorId": 1001 }
    }
  ],
  "links": [
    {
      "linkId": 501,
      "flowId": 10,
      "sourceNodeId": 101,
      "targetNodeId": 102,
      "sourcePort": "out",
      "targetPort": "in"
    }
  ]
}
```

### 2.7 대표 오류 상태

| 상태 | 조건 |
|---|---|
| 400 | DTO/JSON/enum/쿼리/Flow 구조 오류 |
| 403 | 그룹 멤버 아님 또는 역할 부족 |
| 404 | group 범위에 해당 Flow 없음 |
| 409 | 이름 중복, 상태 전이 오류, 보관되지 않은 Flow 삭제 |
| 502 | Core 그룹 권한 의존성 실패 |
| 500 | 명시적으로 처리되는 내부 FlowDefinition 불일치 등 server-side 오류. 그 밖의 예외 body 형식은 안정 계약 아님 |

현재 `InvalidFlowStructureException` 내부에는 node별 `code`, `clientNodeKey`, `fieldPath`, `message`가 있지만 HTTP body에는 요약 message만 들어간다.

## 3. Engine → Core HTTP

### 3.1 그룹 멤버 조회

```http
GET {service-url.core}/internal/v1/groups/{group-id}/members?userId={userId}
```

Engine이 사용하는 응답 필드:

```json
{
  "groupId": 7,
  "groupRole": "MANAGER"
}
```

Core의 추가 응답 필드는 무시한다. `groupRole`은 `MEMBER`, `MANAGER`, `SUPER_MANAGER`다.

### 3.2 액추에이터 상태 변경

```http
PUT {service-url.core}/internal/v1/locations/{location-id}/actuators/state
Content-Type: application/json
```

```json
{
  "actuatorType": "AIRCON",
  "command": "temperature",
  "commandValue": "24",
  "callerService": "RULE_ENGINE"
}
```

- Engine timeout 기본값: connect 2초, read 5초
- Engine 자동 retry: 없음
- 응답 body: 없음, Core 성공은 200
- Core는 location 내 같은 actuatorType 전체에 적용한다.
- 정규 문자열 계약은 `actuatorType` 대문자, `command` 소문자, enum형 `commandValue` 대문자다. 양쪽 검증이 일부 대소문자를 무시하더라도 비정규 문자열을 호환 계약으로 사용하지 않는다.
- 현재 두 서비스의 타입·명령·값 목록은 일치하지만 양쪽 코드에 중복 정의돼 있다.
- Core 내부 endpoint는 현재 groupId와 서비스 자격 증명을 검증하지 않는다. 보안 보완 전 교차 그룹 location 오용 위험이 있다.

## 4. Core → Engine Telemetry RabbitMQ

### 4.1 Topology

| 항목 | 값 |
|---|---|
| Exchange | `insighton.core.telemetry.exchange-v2` |
| Type | `x-consistent-hash` |
| Hash header | `locationId` |
| Queue | `insighton.ruleengine.telemetry.queue.00` ~ `.15` |
| Binding weight | 각 queue `1` |
| Consumer | queue당 1, manual ACK |

RabbitMQ server에 `rabbitmq_consistent_hash_exchange` plugin이 필요하다.

### 4.2 Payload

```json
{
  "groupId": 7,
  "locationId": 42,
  "sensorId": 1001,
  "metrics": {
    "temperature": 28.7,
    "humidity": 54.2
  },
  "timestamp": "2026-08-31T00:00:00Z"
}
```

Producer는 `locationId` header를 문자열로 넣는다. body의 `timestamp` 대신 `time`도 Engine이 수용한다.

입력 제약:

| 필드 | 제약 |
|---|---|
| `groupId`, `locationId`, `sensorId` | 필수 양수 Long |
| `metrics` | 필수, 1~256개 |
| metric key | null·blank 금지, 최대 100자 |
| metric value | null 금지. 값의 구체 타입·범위는 Engine 공통 DTO에서 제한하지 않음 |
| `timestamp` 또는 `time` | 필수 ISO-8601 Instant |
| AMQP body | 최대 256 KiB |

### 4.3 소비 정책

- body 없음, 256 KiB 초과, JSON/계약 오류: WARN 후 ACK/drop
- stale timestamp: DEBUG 후 ACK/drop
- 실행 오류: ERROR/WARN 정책에 따라 기록 후 ACK/drop
- requeue, application retry, telemetry DLQ: 없음

Core producer는 RabbitMQ 발행 실패를 log 후 drop하고 Engine도 처리 오류를 ACK/drop하므로 application-level retry는 없다. 다만 broker가 메시지를 전달한 뒤 Engine이 Action을 수행하고 ACK하기 전에 종료되면 RabbitMQ가 redelivery할 수 있어, end-to-end를 엄밀한 at-most-once 또는 exactly-once로 보장하지는 않는다.

## 5. Engine → AI Alert RabbitMQ

### 5.1 Topology

| 항목 | 값 |
|---|---|
| Exchange | `insighton.rule-engine-events` |
| Type | topic |
| Routing key | `ai.alert.action` |
| AI queue | `ai-service.alert-action.queue` |
| AI DLX | `insighton.rule-engine-events.dlx` |
| AI DLQ | `ai-service.alert-action.dlq` |

### 5.2 Payload

```json
{
  "eventId": "2250075b-6a86-44d3-a7f1-f836cf745fc6",
  "groupId": 7,
  "locationId": 42,
  "flowId": 10,
  "title": "고온 감지",
  "message": "온도를 확인해 주세요.",
  "severity": "WARNING",
  "triggerValue": {
    "temperature": 28.7,
    "humidity": 54.2
  }
}
```

Schedule은 Alert와 연결되지 않으므로 현행 Alert의 `triggerValue`는 telemetry 현재 metrics다. Engine payload에는 `triggerType`, `triggeredAt`, `nodeId`가 없다.

Engine의 publish call 반환은 AI 저장·사용자 노출 완료를 뜻하지 않는다. Engine에는 publisher confirm, mandatory return, outbox, 자체 retry가 없다. AI 쪽 소비·DLQ 정책은 AI 서비스 소유다.

## 6. Core → Engine Lifecycle RabbitMQ

### 6.1 공통 topology

| 항목 | 값 |
|---|---|
| Exchange | `insighton.core-events` topic |
| 처리 시도 | 최대 3회 |
| backoff | 1초 시작, 2배, 최대 10초 |
| 최종 실패 | event별 DLX/DLQ로 republish |

### 6.2 Group 삭제

| 항목 | 값 |
|---|---|
| Routing key | `group.deleted` |
| Queue | `rule-engine.group-deleted.queue` |
| DLX | `rule-engine.group-deleted.dlx` |
| DLQ | `rule-engine.group-deleted.dlq` |

```json
{
  "groupId": 7,
  "locationIds": [41, 42, 43]
}
```

`locationIds`는 null일 수 없지만 빈 배열은 허용된다.
`groupId`와 배열의 각 `locationId`는 양수여야 한다.

### 6.3 Location 삭제

| 항목 | 값 |
|---|---|
| Routing key | `location.deleted` |
| Queue | `rule-engine.location-deleted.queue` |
| DLX | `rule-engine.location-deleted.dlx` |
| DLQ | `rule-engine.location-deleted.dlq` |

```json
{ "locationId": 42 }
```

`locationId`는 양수여야 한다.

### 6.4 미수신 lifecycle 계약

Core는 `sensor.deleted`, `actuator.deleted`도 발행하지만 Engine에는 binding, DTO, listener가 없다. Sensor Flow와 Actuator Flow의 후속 상태 정책을 먼저 확정해야 한다.

## 7. Front 연동 확인

2026-08-31 Front `dev` 기준 Schedule 생성은 Engine과 확정적으로 호환되지 않는다.

- Front `buildCron`은 5필드 `분 시 일 월 요일`을 생성한다.
- Engine은 6필드이고 첫 seconds 필드 `0`을 요구한다.
- Front는 Schedule에도 선택적 THRESHOLD를 붙인 뒤 ALERT Action을 생성하며 ACTUATOR_CONTROL 편집 UI가 없다.
- Front의 cron parser와 상세 요약도 5필드를 전제로 한다.
- 따라서 현재 Front에서 만든 모든 Schedule Flow는 cron 또는 그래프 검증에서 Engine에 거부된다.

최소 수정 계약:

```text
Front DAILY   : 0 {minute} {hour} * * *
Front WEEKLY  : 0 {minute} {hour} * * {days}
Front MONTHLY : 0 {minute} {hour} {day} * *
```

Front 작업은 본 엔진 문서 작업 범위 밖이며 출시 전 외부 연동 항목으로 남긴다.
