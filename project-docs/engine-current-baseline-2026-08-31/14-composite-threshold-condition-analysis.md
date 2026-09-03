# 복합 임계조건 지원 범위와 Front·Engine 계약 분석

## 1. 문서 상태

| 항목 | 내용 |
|---|---|
| 작성일 | 2026-08-31 (Asia/Seoul) |
| 상태 | 현행 분석 및 개선 제안 |
| 적용 범위 | InsightOn Front, Rule Engine, Core telemetry 계약 |
| 분석 기준 | Rule Engine `eed36d5`, Core `8fe84d0`, Front `ccb8540`의 현재 작업 트리 |
| 핵심 결정 필요 | LOCATION 복합조건을 제한할지, 여러 센서 최신값 집계를 별도 기능으로 도입할지 |

이 문서는 Front가 제공하는 여러 임계값 조건이 Rule Engine에서 어떤 의미로 실행되는지, 동일 센서와 서로 다른 센서 조건의 지원 범위가 어디까지인지, Front와 Backend 계약에 어떤 불일치와 오탐 위험이 있는지를 기록한다.

## 2. 결론

Rule Engine은 여러 `THRESHOLD` Node를 `true` Link로 직렬 연결하는 AND 복합조건을 지원한다. 다만 모든 조건은 **한 번 수신한 `SensorEvent` 하나의 `metrics` Map**을 공유한다.

따라서 현행에서 안전하게 지원되는 범위는 다음과 같다.

> 한 센서가 한 패킷에 함께 전송한 여러 metric을 AND로 평가하는 복합조건

다음 기능은 지원하지 않는다.

- 같은 센서의 서로 다른 시점 또는 서로 다른 패킷에 나뉘어 들어온 metric 결합
- 서로 다른 센서가 보낸 metric 결합
- LOCATION 내 센서별 최신값을 모은 snapshot 평가
- 여러 Trigger의 동기화 또는 cross-sensor AND

LOCATION Trigger는 위치의 통합 상태를 만드는 Trigger가 아니다. 해당 위치에서 어떤 센서 패킷이든 수신되면 그 **현재 패킷 하나**로 Flow를 실행하는 broad routing 조건이다.

또한 누락 metric을 숫자와 비교할 때 SpEL의 null 비교 규칙 때문에 `<`, `<=`, `!=`가 `true`가 되는 심각한 오탐 가능성이 확인됐다. 이 문제는 제품 계약을 결정하기 전이라도 Engine에서 우선 차단해야 한다.

## 3. Front가 만드는 복합조건

### 3.1 저장 구조

Front는 조건 하나마다 별도의 `THRESHOLD` Node를 만들고 다음과 같이 직렬 연결한다.

```text
Trigger
  └─ out → Threshold 1
              └─ true → Threshold 2
                            └─ true → ...
                                          └─ true → Alert
```

예를 들어 다음 두 조건은 하나의 SpEL 표현식으로 합쳐지지 않는다.

```text
temperature > 30
AND humidity < 60
```

저장 요청은 개념적으로 다음과 같다.

```json
{
  "nodes": [
    {"clientNodeKey": "trigger", "nodeType": "SENSOR", "configuration": {"sensorId": 100}},
    {"clientNodeKey": "filter-1", "nodeType": "THRESHOLD", "configuration": {"expression": "#metrics['temperature'] > 30"}},
    {"clientNodeKey": "filter-2", "nodeType": "THRESHOLD", "configuration": {"expression": "#metrics['humidity'] < 60"}},
    {"clientNodeKey": "action", "nodeType": "ALERT", "configuration": {"...": "..."}}
  ],
  "links": [
    {"sourceClientNodeKey": "trigger", "targetClientNodeKey": "filter-1", "sourcePort": "out", "targetPort": "in"},
    {"sourceClientNodeKey": "filter-1", "targetClientNodeKey": "filter-2", "sourcePort": "true", "targetPort": "in"},
    {"sourceClientNodeKey": "filter-2", "targetClientNodeKey": "action", "sourcePort": "true", "targetPort": "in"}
  ]
}
```

Front 화면의 AND 표시와 저장 구조는 서로 일치한다.

- `InsightOn-front/src/main/resources/static/js/flow-editor.js:185-202`: 조건 사이에 AND 표시
- `InsightOn-front/src/main/resources/static/js/flow-editor.js:580-596`: 조건마다 Threshold를 만들고 true Link로 직렬 연결
- `InsightOn-front/src/main/resources/templates/flow/editor.html:78`: 모든 조건을 만족해야 한다는 사용자 설명

### 3.2 metric 선택 범위

SENSOR Trigger:

- Trigger에서 센서 하나를 선택한다.
- 모든 조건 dropdown은 그 센서 하나의 등록 attribute 목록을 공유한다.
- 조건별로 다른 센서를 선택하는 UI는 없다.

LOCATION 또는 SCHEDULE Trigger:

- 실제 위치의 센서와 attribute를 조회하지 않는다.
- `temperature`, `humidity`, `co2`, `illuminance` 네 metric을 하드코딩해 제공한다.
- 화면에는 이를 `위치 전체의 공통 측정 항목`이라고 표시한다.

관련 근거:

- `InsightOn-front/src/main/resources/static/js/flow-editor.js:23-28`: 위치용 metric 하드코딩
- `InsightOn-front/src/main/resources/static/js/flow-editor.js:247-276`: SENSOR만 attribute API를 호출하고 LOCATION/SCHEDULE은 하드코딩 목록 사용

## 4. Core와 Engine의 실제 실행 단위

### 4.1 Core telemetry 발행 단위

Core는 MQTT 패킷에서 다음 값을 얻는다.

- 해당 패킷을 보낸 센서의 `sensorId`
- 해당 센서가 이번 패킷에 실어 보낸 `fields`
- `groupId`, `locationId`, `timestamp`

그 뒤 `fields`를 그대로 `TelemetryEventMessage.metrics`에 넣어 RabbitMQ로 발행한다. 같은 위치의 다른 센서 값이나 이전 패킷 값을 합치지 않는다.

관련 근거:

- `InsightOn-core/src/main/java/com/insighton/core/adapter/mqtt/listener/GatewayPacketInboundHandler.java:119-154`
- `InsightOn-core/src/main/java/com/insighton/core/adapter/mqtt/listener/dto/TelemetryEventMessage.java:6-12`

### 4.2 Engine 실행 컨텍스트

Engine의 `SensorEvent`도 센서 하나와 metrics Map 하나를 가진다. `FlowExecutionContext.metrics()`는 이 Map을 그대로 반환한다. FlowRunner는 Trigger부터 마지막 Action까지 동일한 `FlowExecutionContext`를 모든 Node에 전달한다.

따라서 직렬 Threshold가 의미하는 AND는 다음과 같다.

```text
현재 SensorEvent.metrics로 조건 1 평가
  → true이면 같은 SensorEvent.metrics로 조건 2 평가
  → 모두 true이면 Action 실행
```

관련 근거:

- `InsightOn-ruleEngine/src/main/java/com/nhnacademy/insightonruleengine/runner/model/SensorEvent.java:15-20`
- `InsightOn-ruleEngine/src/main/java/com/nhnacademy/insightonruleengine/runner/model/FlowExecutionContext.java:54-63`
- `InsightOn-ruleEngine/src/main/java/com/nhnacademy/insightonruleengine/runner/application/FlowRunner.java:79-127`
- `InsightOn-ruleEngine/src/main/java/com/nhnacademy/insightonruleengine/flow/domain/node/params/filter/ThresholdParams.java:7-14`

### 4.3 Trigger별 라우팅

SENSOR Trigger는 설정한 `sensorId`와 현재 이벤트의 `sensorId`가 같을 때만 실행된다.

LOCATION Trigger는 Flow의 group/location route에 들어온 이벤트라면 센서 ID와 관계없이 실행된다. 그러나 실행 컨텍스트에는 여전히 현재 센서의 현재 패킷만 들어간다.

```text
LOCATION Trigger = 위치 안의 모든 센서 패킷을 실행 후보로 선택
LOCATION Trigger ≠ 위치 안의 모든 센서 최신값을 합침
```

관련 근거:

- `InsightOn-ruleEngine/src/main/java/com/nhnacademy/insightonruleengine/runner/application/router/ActiveFlowRouter.java:23-43`
- `InsightOn-ruleEngine/src/main/java/com/nhnacademy/insightonruleengine/runner/execution/executor/LocationNodeExecutor.java:24-41`

`StaleTelemetryDetector`가 센서별 최신 timestamp를 보관하지만 이는 역순·중복 패킷 폐기용 watermark다. metric 값은 보관하지 않으므로 location snapshot 역할을 하지 않는다.

- `InsightOn-ruleEngine/src/main/java/com/nhnacademy/insightonruleengine/runner/application/telemetry/StaleTelemetryDetector.java:19-60`

## 5. 시나리오별 지원 여부

| 시나리오 | 현행 결과 | 판정 |
|---|---|---|
| 센서 A가 `{temperature, humidity}`를 한 패킷으로 보내고 두 조건을 평가 | 같은 Map에 두 값이 있으므로 순차 AND 가능 | 지원 |
| 센서 A가 온도와 습도를 서로 다른 패킷으로 전송 | 이전 패킷 값을 기억하거나 합치지 않음 | 미지원·오탐 가능 |
| SENSOR Trigger에서 센서 A 온도와 센서 B 습도를 결합 | 센서 A 이벤트만 라우팅되며 센서 B 값이 없음 | 미지원 |
| LOCATION Trigger에서 센서 A 온도와 센서 B 습도를 결합 | 각 패킷마다 Flow를 별도 실행하며 두 값을 합치지 않음 | 미지원·오탐 가능 |
| LOCATION Trigger에서 조건 하나만 사용하고 해당 metric이 현재 패킷에 존재 | 그 패킷에 대해서는 정상 평가 | 제한적 지원 |
| LOCATION Trigger에서 조건 하나를 사용하지만 다른 종류의 센서 패킷에 metric이 없음 | 누락값 연산자에 따라 오탐 가능 | 안전하지 않음 |
| 같은 sensorId의 최신 metric들을 시간에 걸쳐 누적한 뒤 평가 | metric state 저장소가 없음 | 미지원 |
| 여러 조건 중 하나만 만족하는 OR | Front가 false 분기나 OR 편집을 제공하지 않음 | Front 미지원 |

## 6. 누락 metric 오탐

### 6.1 확인 결과

현행 `ThresholdEvaluator`와 같은 Spring Expression 및 `SimpleEvaluationContext` 구성으로 없는 `humidity`를 비교한 결과는 다음과 같다.

| 표현식 | 결과 |
|---|---:|
| `#metrics['humidity'] > 50` | `false` |
| `#metrics['humidity'] >= 50` | `false` |
| `#metrics['humidity'] < 50` | `true` |
| `#metrics['humidity'] <= 50` | `true` |
| `#metrics['humidity'] != 50` | `true` |
| `#metrics['humidity'] == null` | `true` |

Map에 key가 없으면 조회값이 null이고, SpEL의 비교 규칙이 null을 숫자보다 작은 값으로 취급하기 때문이다.

현행 Front는 모든 비교 연산자 `>`, `>=`, `<`, `<=`, `==`, `!=`를 제공한다. Engine은 표현식 구문과 boolean 반환만 확인하고 참조 metric 존재 여부를 확인하지 않는다.

관련 근거:

- `InsightOn-front/src/main/resources/static/js/flow-editor.js:287-300`: 제공 비교 연산자
- `InsightOn-ruleEngine/src/main/java/com/nhnacademy/insightonruleengine/runner/execution/evaluator/ThresholdEvaluator.java:21-38`: metrics Map 직접 바인딩 및 평가
- `InsightOn-ruleEngine/src/main/java/com/nhnacademy/insightonruleengine/flow/application/validation/FlowActivationValidator.java:50-79`: 활성화 시 표현식 문법만 검증

### 6.2 오탐 예시

Flow:

```text
LOCATION
  → temperature > 30
  → humidity < 50
  → ALERT
```

센서 A 패킷:

```json
{
  "sensorId": 100,
  "metrics": {"temperature": 31}
}
```

의도한 의미가 `온도 센서 A > 30 AND 습도 센서 B < 50`이라면 습도 값이 없어 판단을 보류하거나 false여야 한다. 그러나 현행에서는 첫 조건이 true이고 없는 humidity의 `< 50`도 true가 되어 Alert까지 도달할 수 있다.

이 문제는 cross-sensor Flow에만 국한되지 않는다. 동일 센서가 매 패킷마다 전체 attribute를 보내지 않는 경우에도 발생한다.

## 7. Front·Backend 계약 불일치

### 7.1 LOCATION의 사용자 설명

| Front 표현 | Engine 실제 의미 |
|---|---|
| 위치 전체 | 위치 내 어느 센서 패킷이든 Flow 실행 후보 |
| 위치 전체의 공통 측정 항목 | 하드코딩된 4개 metric 이름 |
| 여러 조건을 모두 만족 | 현재 패킷 하나에 모든 metric이 있을 때만 의미가 정확함 |

Front 표현은 사용자가 여러 센서의 값을 결합한다고 이해하게 만들 수 있다. 실제로는 통합 location state가 없다.

### 7.2 metric 유효성 검증 부재

Engine 저장 검증은 Node configuration 타입과 graph 구조를 확인한다. 활성화 검증은 Threshold의 SpEL 문법과 executor 존재 여부를 확인한다. 다음 항목은 검사하지 않는다.

- SENSOR Trigger가 가리키는 센서에 expression의 metricKey가 등록돼 있는지
- 선택한 metric들이 한 패킷에 함께 들어오는지
- LOCATION의 센서 중 어떤 센서가 해당 metric을 제공하는지
- 복합조건의 모든 metric이 동일 source에서 나오는지

Front의 SENSOR attribute 조회는 편집 편의를 제공할 뿐 Backend 계약 검증을 대체하지 못한다. API를 직접 호출하거나 센서 attribute가 변경된 기존 Flow는 여전히 잘못된 expression을 저장·활성화할 수 있다.

### 7.3 LOCATION 주석과 구현 불일치

`LocationParams` 주석에는 모든 센서를 포함하고 동일 metric은 최신 이벤트 값을 사용한다고 적혀 있다. 실제 구현에는 이 집계가 없다.

- `InsightOn-ruleEngine/src/main/java/com/nhnacademy/insightonruleengine/flow/domain/node/params/trigger/LocationParams.java:5-10`

이 주석은 현행 결정 `DEC-012: LOCATION은 현재 packet metrics만 사용한다`와도 충돌하므로 수정해야 한다.

### 7.4 SCHEDULE 관련 별도 불일치

Front는 SCHEDULE에도 LOCATION과 같은 metric 조건을 제공하고 마지막 Action을 Alert로 만든다. Engine은 `SCHEDULE → ACTUATOR_CONTROL` 직접 연결만 허용한다. 따라서 SCHEDULE 편집 경로는 복합조건 여부와 별개로 현재 Engine 계약과 맞지 않는다.

- Front: `InsightOn-front/src/main/resources/static/js/flow-editor.js:262-264`, `566-596`
- Engine: `InsightOn-ruleEngine/src/main/java/com/nhnacademy/insightonruleengine/flow/application/validation/FlowGraphValidator.java:267-279`

이 항목은 기존 `OI-06 Front Schedule 계약`에서 추적한다.

### 7.5 조건 개수 상한

Front에는 조건 개수 상한이 별도로 표시되지 않는다. Engine 요청은 Node 최대 500개, Link 최대 1,000개로 제한한다. Trigger 1개와 Action 1개를 전제로 하면 구조상 Threshold는 최대 498개지만, 제품상 적절한 조건 수 제한과 UX는 정의되지 않았다.

- `InsightOn-ruleEngine/src/main/java/com/nhnacademy/insightonruleengine/flow/api/dto/request/FlowCreateRequest.java:23-27`
- `InsightOn-ruleEngine/src/main/java/com/nhnacademy/insightonruleengine/flow/api/dto/request/FlowUpdateRequest.java:17-20`

## 8. 권장 개선

### 8.1 P0: 누락 metric은 무조건 조건 불충족으로 처리

제품 정책과 관계없이 먼저 적용할 안전장치다.

권장 평가 순서:

```text
1. expression이 참조하는 metricKey 확인
2. metrics.containsKey(metricKey) 확인
3. 값의 null 여부와 숫자 타입 확인
4. 비교 수행
5. 누락 또는 타입 불일치는 false + 제한된 진단 로그/metric
```

권장 구현 방향은 자유 SpEL 문자열을 그대로 실행하기보다 Front가 이미 제한하고 있는 구조를 명시적 DTO로 바꾸는 것이다.

```json
{
  "metricKey": "temperature",
  "operator": "GT",
  "threshold": 30
}
```

장점:

- 누락값과 타입 처리 규칙을 Engine이 명확하게 소유한다.
- metricKey 추출과 Backend 유효성 검증이 쉬워진다.
- 임의 SpEL 확장으로 인한 계약 불명확성을 줄인다.

단점:

- 기존 `expression` configuration migration이 필요하다.
- 향후 복잡한 식을 직접 입력하려는 요구에는 별도 expression mode가 필요하다.

단기적으로 configuration 변경이 어렵다면 Front가 생성하는 expression 패턴을 파싱해 `containsKey` guard를 적용할 수 있다. 다만 장기 계약으로는 구조화된 조건이 더 안전하다.

### 8.2 P1: 현재 제품 범위를 same-sensor/same-packet으로 명시

현재 구조를 유지한다면 다음 정책을 권장한다.

- 복합조건은 SENSOR Trigger에서만 제공한다.
- 모든 조건은 선택한 센서의 attribute 중에서만 고른다.
- 사용자 문구에 `같은 센서가 한 번에 전송한 측정값을 함께 확인합니다`를 명시한다.
- LOCATION에서는 조건을 하나만 허용하거나, 조건을 추가하는 순간 SENSOR Trigger 선택을 요구한다.
- LOCATION 단일 조건도 해당 metric이 없는 패킷에서는 false가 되도록 Engine guard를 적용한다.

장점:

- 현재 이벤트 모델을 바꾸지 않고 정확한 기능을 제공한다.
- Redis 상태, freshness, source provenance를 새로 도입하지 않아도 된다.
- 사용자가 기대하는 AND와 실제 평가 의미가 일치한다.

단점:

- 위치 전체 상태를 이용한 자동화는 제공하지 못한다.
- 서로 다른 전용 센서를 조합해야 하는 환경에는 기능이 부족하다.

### 8.3 P1: Backend metric 계약 검증

SENSOR Trigger Flow 저장 또는 활성화 시 다음을 검증한다.

1. `sensorId`가 Flow의 group/location에 속하는지
2. Threshold의 metricKey가 해당 센서 attribute에 등록돼 있는지
3. 기존 ACTIVE Flow는 sensor attribute 삭제 시 비활성화 또는 수정 필요 상태로 전환할지 결정

단, attribute 등록은 해당 metric이 모든 패킷에 항상 포함된다는 보장이 아니다. 패킷 completeness는 sensor protocol/schema 계약에서 별도로 정해야 한다.

### 8.4 P2: 서로 다른 센서 복합조건이 필요할 때 별도 기능으로 설계

cross-sensor 조건은 기존 LOCATION Trigger에 암묵적으로 추가하지 않는다. `LOCATION_SNAPSHOT` 또는 `COMPOSITE_SENSOR`처럼 별도의 실행 의미로 도입하는 편이 안전하다.

필요 구성:

```text
Telemetry 수신
  → location + sensor + metric별 최신값과 event time 저장
  → freshness TTL 적용
  → source/provenance가 포함된 snapshot 생성
  → 모든 필수 metric이 존재하고 freshness를 만족할 때만 조건 평가
  → 마지막 평가 snapshot/version과 Action 중복 억제
```

반드시 결정할 정책:

- 동일 metric을 여러 센서가 제공할 때 source 선택 방식
- latest, average, min/max 중 어떤 aggregation을 사용할지
- metric별 freshness TTL
- 일부 센서가 끊겼을 때 false, unknown, stale 중 어떤 상태로 처리할지
- event time과 processing time 중 기준
- 여러 Engine instance가 공유할 상태 저장소
- 같은 snapshot으로 Action이 반복되지 않도록 할 idempotency/version
- 센서 이동·삭제·교체 시 state cleanup

장점:

- 온도 전용 센서와 습도 전용 센서 같은 실제 설치 구성을 조합할 수 있다.
- 위치 단위 자동화라는 사용자 기대를 충족한다.
- 상태의 출처와 신선도를 명시하면 설명 가능성과 감사 가능성이 높아진다.

단점:

- Redis 또는 별도 state store와 lifecycle 정책이 필요하다.
- 늦은 패킷, 센서 장애, stale state, 다중 인스턴스 정합성 문제가 추가된다.
- 단순 Threshold 기능보다 테스트와 운영 복잡도가 크게 증가한다.

현 단계 권장은 **P0 오탐 차단 → same-sensor/same-packet 계약 명시 → 실제 cross-sensor 사용자 요구가 확인되면 별도 snapshot 기능 설계** 순서다.

## 9. 권장 테스트

### 9.1 Engine 단위·통합 테스트

- 같은 패킷에 두 metric이 있고 두 Threshold가 true이면 Action 실행
- 첫 번째 또는 두 번째 Threshold가 false이면 Action 미실행
- metric 누락 상태에서 `>`, `>=`, `<`, `<=`, `==`, `!=`가 모두 false 또는 명시된 missing 결과
- 숫자가 아닌 metric 값에 Threshold를 적용하면 false 처리하고 Flow 전체를 실패시키지 않음
- SENSOR Trigger가 다른 sensorId 이벤트를 실행하지 않음
- LOCATION Trigger가 서로 다른 센서 이벤트를 합치지 않음을 계약 테스트로 고정
- 저장·활성화 시 선택 센서에 없는 metricKey 거부
- sensor attribute 삭제 후 ACTIVE Flow 처리 정책 테스트

### 9.2 Front 테스트

- 조건 N개가 Threshold N개와 true Link N개로 직렬 생성됨
- SENSOR 조건 목록이 선택 센서 attribute만 사용함
- LOCATION 복합조건 제한과 안내 문구
- Backend validation error가 해당 조건 행에 표시됨
- 최대 조건 수를 정하면 버튼 비활성화와 기존 초과 Flow 표시

### 9.3 Contract/E2E 테스트

- Core가 한 sensor packet의 fields만 `metrics`로 발행함을 고정
- 동일 센서가 전체 metric을 한 패킷에 보내는 프로토콜 사례
- 일부 metric만 포함하는 패킷에서 오탐이 발생하지 않음
- 센서 A와 센서 B가 차례로 패킷을 보내도 암묵적 cross-sensor AND가 발생하지 않음

## 10. 이번 검증 결과

다음 기존 테스트를 실행했고 모두 통과했다.

```bash
./mvnw -q -Dtest=ThresholdEvaluatorTest,ActiveFlowRouterTest,FlowGraphValidatorTest test
```

기존 테스트에서 확인되는 범위:

- Threshold가 현재 metrics 값을 사용해 true/false를 반환
- SENSOR Trigger가 설정된 센서만 선택
- LOCATION Trigger가 위치의 어떤 센서든 선택
- Filter true Link와 Schedule 직접 연결 규칙

기존 테스트에 없는 범위:

- 실제 다중 Threshold AND 실행
- 누락 metric의 모든 연산자 동작
- same-sensor/same-packet completeness
- cross-sensor 비집계 계약
- Front 생성 JSON과 Engine 실행의 E2E
- 저장·활성화 시 metric 소유권 검증

## 11. 의사결정 제안

| 결정 항목 | 권장안 |
|---|---|
| 현재 복합조건 제품 계약 | 동일 센서·동일 패킷의 metric만 AND 지원 |
| LOCATION 복합조건 | 누락 metric guard 전에는 비활성화, 이후에도 단일 조건 또는 명시적 센서 선택으로 제한 |
| 누락 metric | 모든 비교에서 조건 불충족 처리 |
| Threshold configuration | 장기적으로 `metricKey/operator/threshold` 구조화 DTO로 전환 |
| 서로 다른 센서 AND | 기존 LOCATION 확장이 아니라 freshness와 provenance를 가진 별도 snapshot 기능으로 검토 |
| 우선순위 | P0 오탐 차단, P1 Front 계약 정렬·Backend 검증, P2 cross-sensor 제품 검증·설계 |

최종적으로 사용자에게 노출할 수 있는 가장 정확한 설명은 다음과 같다.

> 여러 조건은 모두 같은 센서가 한 번에 보낸 측정값을 기준으로 확인합니다. 서로 다른 센서의 값을 결합하는 위치 복합조건은 현재 지원하지 않습니다.
