# AI Suggestion Node 보류 및 재설계 제안

## 1. 문서 상태

| 항목 | 내용 |
|---|---|
| 작성일 | 2026-08-31 (Asia/Seoul) |
| 상태 | 보류 제안 |
| 적용 범위 | InsightOn Rule Engine, AI, Core, Front, Gateway |
| 현행 결정 | Rule Engine의 AI Suggestion Action을 복원하지 않는다. |
| 재검토 시점 | 실시간·복합 상황 판단이라는 독립된 제품 가치와 측정 가능한 성공 기준이 합의된 이후 |

이 문서는 현행 코드에 기능을 추가하기 위한 즉시 구현 명세가 아니다. 제거된 AI Suggestion Action을 왜 당장 복원하지 않는지 기록하고, 추후 실용성을 보완해 재도입하려면 어떤 제품 역할과 기술 계약이 필요한지 제안한다.

## 2. 결론

현재 AI 서버의 이벤트 기반 제안 경로는 정기 제안 경로와 대부분 같은 처리를 반복한다. Rule Engine 이벤트가 전달하는 차별화 정보는 단일 `metricKey/value/timestamp`에 가깝고, AI는 직전 한 시간 집계·날씨·액추에이터 정보를 다시 조회한 뒤 같은 LLM 판단과 저장·실행 로직을 사용한다.

이 구조에서 AI Suggestion Node를 복원하면 다음 문제가 생긴다.

- 정기 제안과 이벤트 제안의 제품 역할이 겹친다.
- 단순 Threshold와 Actuator Control로 해결할 수 있는 상황에도 LLM 호출이 추가될 수 있다.
- 센서 패킷마다 같은 조건이 반복 충족되면 중복 제안과 호출 비용이 급증할 수 있다.
- 이벤트가 왜 발생했는지, 최근 추세가 어떤지, 이전 조치가 효과가 있었는지에 대한 정보가 부족하다.
- AI가 추가한 가치와 기존 규칙 기반 동작의 차이를 측정할 기준이 없다.

따라서 현 단계 권장은 **복원 보류**다. 재도입한다면 “조건 충족 시 AI를 한 번 호출하는 Action”이 아니라 **단순 규칙으로 결정하기 어려운 복합 상황에 대해 제한된 선택지 중 하나를 고르는 상황 인지형 의사결정 Action**으로 재정의한다.

## 3. 현행 기능과 단절 지점

### 3.1 AI 서비스의 두 제안 경로

AI 서비스에는 다음 두 경로가 있다.

1. 정기 경로
   - 평일 업무시간 매시 5분 실행
   - 직전 한 시간과 그 전 한 시간의 통계, 날씨, 액추에이터 가동 정보를 사용
   - 위치별로 LLM이 조치 필요 여부와 액추에이터 명령을 생성
2. 이벤트 경로
   - RabbitMQ `ai.suggestion.action` 이벤트 수신 시 즉시 실행
   - 이벤트의 단일 실시간 측정값을 프롬프트 상단에 추가
   - 나머지는 최근 완료된 한 시간 집계, 날씨, 액추에이터 정보를 조회
   - 정기 경로와 동일한 제안 저장·자동 실행 로직 사용

두 경로 모두 LLM 결과가 `actionNeeded=false`면 종료하고, 조치가 필요하면 위치 모드에 따라 처리한다.

- `SUGGESTION`: 대기 상태로 저장하고 관리자의 수락을 기다린다.
- `AI_DIRECT`: 실행할 액추에이터 명령이 있으면 저장과 동시에 Core를 호출한다.

### 3.2 Rule Engine 현행

통합 `dev` 기준 Rule Engine에는 다음 요소가 없다.

- `AI_SUGGESTION` NodeType
- AI Suggestion node params
- AI Suggestion executor
- suggestion event DTO와 publisher

AI 서비스에는 큐·리스너·이벤트 처리 코드가 남아 있으므로 현재 이벤트 경로는 소비자는 있지만 생산자가 없는 상태다. 이 상태 자체는 운영 장애를 만들지는 않지만, 사용되지 않는 계약의 소유권과 향후 유지 여부를 별도로 결정해야 한다.

### 3.3 변경 이력

| 일자 | 변경 | 해석 |
|---|---|---|
| 2026-07-30 | 초기 `AI_SUGGESTION` 타입과 `AiSuggestionParams(context)` 삭제 | 서비스 의미와 책임이 불명확했음 |
| 2026-08-05 | AI 서비스가 정기·이벤트 제안 경로 구현 | Rule Engine node의 향후 구현을 전제로 소비 경로를 준비 |
| 2026-08-20 | Engine에 event DTO와 Rabbit publisher 추가 | 실제 NodeType·executor·호출부는 추가되지 않음 |
| 2026-08-30 | 미사용 publisher와 DTO 제거 (`759e2ef`) | 통합 코드에서 호출되지 않는 계약을 정리 |

과거에도 실행 가능한 AI Suggestion Node가 완성된 적은 없다. 따라서 재도입은 단순 revert가 아니라 신규 기능 설계에 해당한다.

## 4. 제품 역할 재정의

### 4.1 정기 제안과 이벤트 제안의 분리

| 구분 | 정기 AI 제안 | Rule Engine 기반 AI 제안 |
|---|---|---|
| 목적 | 지난 기간의 운영 최적화·진단 | 지금 발생한 상황의 즉시 의사결정 |
| 시간 범위 | 직전 1시간 이상 | 현재값과 최근 5~15분 추세 |
| 호출 빈도 | 정해진 배치 주기 | 의미 있는 상태 변화가 발생한 경우만 |
| 주요 입력 | 시간 집계, 날씨, 가동 시간 | 발화 이유, 현재 측정값, 변화율, 지속 시간, 현재 장치 상태, 최근 조치 |
| 기대 결과 | 일반적인 개선 제안 | 무조치·제안·에스컬레이션 중 제한된 결정 |
| 허용 지연 | 수십 초 수준 허용 가능 | 짧고 예측 가능한 상한 필요 |

이 구분이 제품 요구로 성립하지 않으면 이벤트 기반 AI Suggestion Node는 만들지 않는다.

### 4.2 AI를 사용하지 않아야 하는 경우

다음처럼 조건과 결과가 명확하면 기존 Node가 더 적합하다.

- `온도 > 30 → 에어컨 ON`: `THRESHOLD → ACTUATOR_CONTROL`
- `CO₂ > 1,000 → 관리자 알림`: `THRESHOLD → ALERT`
- `매일 09:00 → 환풍기 ON`: `SCHEDULE → ACTUATOR_CONTROL`

이 경우 AI를 추가하면 지연, 비용, 실패 가능성만 늘고 결정 품질의 이점은 작다.

### 4.3 AI가 가치를 만들 수 있는 경우

AI는 다음처럼 다수의 신호와 상충하는 선택지를 해석해야 하는 경우에 한정한다.

- 실내 온도는 높지만 외부 기온·미세먼지·강수 상태가 좋아 냉방과 자연 환기 중 선택해야 할 때
- 환풍기가 이미 동작 중인데 CO₂가 계속 상승해 추가 제어보다 장비 이상 또는 재실 과밀 알림이 적절할 때
- 에어컨 실행 후 일정 시간 동안 온도가 개선되지 않아 설정 변경, 다른 액추에이터 조합, 점검 요청 중 선택해야 할 때
- 온도·습도·공기질이 함께 악화돼 냉방·제습·환기의 조합과 순서를 결정해야 할 때
- 최근 같은 제안이 거절됐거나 동일 제어가 이미 수행돼 반복 제안을 억제해야 할 때

## 5. 선택지와 장단점

### 옵션 A. 현행처럼 Node를 제거하고 정기 제안만 유지

장점:

- 시스템과 사용자 경험이 가장 단순하다.
- 센서 이벤트마다 발생할 수 있는 LLM 비용과 지연을 피한다.
- 중복 제안, 반복 자동 제어, 메시지 멱등성 문제를 추가하지 않는다.
- 제품 효과가 불분명한 기능에 여러 서비스가 동시에 결합되지 않는다.

단점:

- 업무시간 외 또는 한 시간 집계가 끝나기 전의 즉시 판단이 불가능하다.
- 사용자가 Flow 조건에 AI 판단을 결합할 수 없다.
- AI 서비스의 suggestion event listener와 Rabbit queue가 사용되지 않는 코드로 남는다.

적합한 조건:

- AI 제안의 핵심 가치가 정기적인 운영 진단에 있을 때
- 즉시 상황 판단의 실제 사용자 시나리오와 성공 지표가 아직 없을 때

### 옵션 B. 기존 이벤트 계약을 그대로 복원

장점:

- 구현 범위가 상대적으로 작다.
- AI 서버의 기존 listener와 event-triggered method를 바로 사용할 수 있다.
- 조건 충족 직후 제안을 생성하는 데모를 빠르게 만들 수 있다.

단점:

- 단일 `metricKey/value` 외에는 정기 경로와 차이가 작다.
- 다중 metric Flow에서 어떤 값을 대표값으로 보낼지 모호하다.
- Schedule trigger에서는 `metricKey/value`가 없어 의미 없는 프롬프트가 만들어질 수 있다.
- cooldown과 event id가 없어 LLM 호출·제안·자동 제어가 중복될 수 있다.
- `deviceId`와 Engine의 `sensorId` 용어가 일치하지 않는다.
- `flowId/nodeId`가 없어 원인 추적과 노드 단위 정책 적용이 어렵다.
- 빠른 복원이 현재의 낮은 실용성을 고착시킬 가능성이 크다.

권장 여부: **권장하지 않음**.

### 옵션 C. 상황 인지형 AI Decision Action으로 재설계

장점:

- 정기 제안과 다른 독립적인 제품 역할을 갖는다.
- 다중 신호, 최근 추세, 현재 장치 상태, 이전 조치를 함께 고려할 수 있다.
- 호출 억제·멱등성·감사 정보를 계약에 포함할 수 있다.
- 단순 Rule과 AI 적용 범위를 명확히 나눌 수 있다.
- 제안 수락률과 환경 개선율로 부가 가치를 측정할 수 있다.

단점:

- Engine, AI, Core, Front의 동시 변경이 필요하다.
- 최근 5~15분 window data와 액추에이터 상태를 제공할 조회·집계 계약이 필요하다.
- LLM latency와 실패를 Action 실행 모델에 포함해야 한다.
- 정책 점수, cooldown, 만료, 중복 처리 등 운영 상태가 증가한다.
- 충분한 실측 데이터 없이 설계하면 복잡도만 커질 수 있다.

권장 여부: 실시간 AI 판단의 제품 가치가 검증될 때 선택하는 **권장 재도입 방향**.

### 옵션 D. Rule이 결정하고 AI는 설명만 생성

장점:

- 실제 제어 결정은 deterministic rule에 남아 안전성이 높다.
- AI는 사용자에게 원인과 권장 행동을 이해하기 쉽게 설명하는 데 집중한다.
- 잘못된 액추에이터 선택 위험을 줄인다.

단점:

- AI가 의사결정을 개선한다는 가치는 제한된다.
- 설명 생성만으로 LLM 비용을 정당화하기 어려울 수 있다.
- Alert 메시지 template이나 규칙 기반 설명으로 대체 가능한 경우가 많다.

적합한 조건:

- 사용자 신뢰와 설명 가능성이 중요하지만 AI 자동 제어는 허용하지 않을 때
- 초기 검증 단계에서 AI의 판단권을 최소화하려 할 때

## 6. 권장 재설계

### 6.1 권장 처리 흐름

```text
Telemetry 수신
  → Rule Engine이 명시적 조건·지속 시간 평가
  → node/location cooldown 및 event 중복 확인
  → 실시간 TriggerSnapshot 발행
  → AI가 최근 window·날씨·장치 상태·이전 조치로 context 보강
  → 정책 점수로 AI 호출 필요 여부 사전 판정
  → 허용된 결정과 명령만 LLM에 제시
  → NO_ACTION / SUGGEST_ACTION / ESCALATE 구조화 응답
  → SUGGESTION 모드는 승인 대기
  → AI_DIRECT 모드는 Core가 명령을 재검증한 뒤 실행
  → 결과와 사용한 context snapshot 저장
```

### 6.2 Engine의 책임

- Flow 조건을 deterministic하게 평가한다.
- Flow·Node·Trigger 식별자와 발화 이유를 만든다.
- 동일 node/location의 cooldown을 적용한다.
- 발행마다 고유 event id를 만들고 재시도 시 동일 id를 유지한다.
- AI의 판단이나 프롬프트 조립을 담당하지 않는다.

### 6.3 AI 서비스의 책임

- 현재 이벤트를 최근 window data, 날씨, 장치 상태, 최근 조치와 결합한다.
- 이미 실행된 조치나 최근 동일 제안을 확인해 중복을 억제한다.
- 허용된 액추에이터와 명령만 LLM 선택지로 제공한다.
- 구조화 응답을 검증하고 제안·에스컬레이션·자동 실행을 수행한다.
- 판단에 사용한 context와 결과를 감사 가능하게 저장한다.

### 6.4 Core의 책임

- location의 그룹 소속과 `SUGGESTION/AI_DIRECT` 모드를 소유한다.
- 액추에이터 상태와 지원 명령의 source of truth가 된다.
- AI 명령을 서버 측에서 다시 검증한다.
- 실행 결과와 실행 주체를 기록하고, 필요하다면 실제 장치 acknowledgement를 제공한다.

### 6.5 Front의 책임

- 일반 사용자에게는 단순 Rule을 우선 노출한다.
- AI Decision Action은 고급 기능으로 분리하고 적용 조건과 비용·지연을 설명한다.
- 제안 이유, 사용한 주요 context, 만료 시각, 제안된 동작을 표시한다.
- 대기 제안의 수락·거절과 이미 만료된 제안 처리를 제공한다.

## 7. 제안 계약

다음은 방향을 설명하기 위한 후보이며 확정 API가 아니다.

### 7.1 Node configuration 후보

```json
{
  "decisionPolicy": "COMFORT_OPTIMIZATION",
  "cooldownSeconds": 1800,
  "contextWindowMinutes": 15,
  "expiresInMinutes": 15,
  "allowedDecisions": ["NO_ACTION", "SUGGEST_ACTION", "ESCALATE"]
}
```

고려사항:

- 자유로운 prompt 문자열을 사용자가 입력하도록 두지 않는다.
- `decisionPolicy`는 서버가 소유한 versioned template을 선택한다.
- 정책별 입력·허용 결정·액추에이터 범위를 명시한다.
- `cooldownSeconds`와 `expiresInMinutes`에는 서비스 수준 최소·최대값을 둔다.

### 7.2 Trigger event 후보

```json
{
  "eventId": "uuid",
  "flowId": 100,
  "nodeId": 104,
  "groupId": 5,
  "locationId": 42,
  "sensorId": 10,
  "triggerType": "TELEMETRY",
  "triggeredAt": "2026-08-31T13:30:00+09:00",
  "reason": {
    "expression": "temperature > 28",
    "matchedMetrics": {
      "temperature": 30.2,
      "humidity": 68.0,
      "co2": 920.0
    },
    "durationSeconds": 600
  },
  "policy": {
    "name": "COMFORT_OPTIMIZATION",
    "version": 1,
    "contextWindowMinutes": 15,
    "expiresInMinutes": 15
  }
}
```

필수 계약:

- `eventId`를 AI 저장소에서 unique하게 처리한다.
- `sensorId`와 `deviceId` 중 하나로 용어를 통일한다.
- Schedule trigger를 허용한다면 `reason`과 metric이 없는 별도 schema 또는 명시적 nullable 규칙을 둔다.
- event가 오래됐으면 LLM 호출 전에 만료 처리한다.

### 7.3 AI 결과 후보

```json
{
  "decision": "SUGGEST_ACTION",
  "urgency": "MEDIUM",
  "reason": "실외 공기 상태가 양호해 냉방보다 환기가 효율적입니다.",
  "actions": [
    {
      "actuatorType": "AIRCON",
      "command": "POWER_STATUS",
      "commandValue": "OFF"
    },
    {
      "actuatorType": "VENTILATION_FAN",
      "command": "POWER_STATUS",
      "commandValue": "ON"
    }
  ],
  "expiresAt": "2026-08-31T13:45:00+09:00"
}
```

LLM 출력은 반드시 다음 검증을 통과해야 한다.

- enum과 schema 검증
- 해당 location에 실제로 존재하는 actuator type 검증
- Core가 허용한 command/value 검증
- 만료 시각 검증
- 정책이 허용한 decision 검증
- 자동 실행 전 location mode 재조회

## 8. 정책 점수와 AI 호출 사전 판정

“이벤트에 가중치를 더 준다”는 동작을 프롬프트 문장으로만 구현하지 않는다. 긴급도와 호출 필요 여부는 deterministic한 신호를 계산해 판단한다.

후보 신호:

- 기준 이탈 정도
- 기준 이탈 지속 시간
- 최근 5~15분 변화율
- 여러 metric의 동시 이상
- 이전 제어 후 개선되지 않은 정도
- 최근 동일 제안의 수락·거절 여부
- 장치가 이미 실행 중인지 여부

예시 분기:

| 점수·상태 | 처리 |
|---|---|
| 낮음 | 무시하거나 정기 분석에 포함 |
| 중간 | AI 제안 후보로 전달 |
| 높음 | 즉시 Alert, 안전 규칙이 명확하면 deterministic 제어 |
| 최근 동일 처리 존재 | cooldown 또는 중복으로 억제 |

구체적인 가중치 숫자는 초기 설계에서 임의로 확정하지 않는다. 실제 이벤트와 사용자 반응 데이터를 수집한 뒤 조정한다.

## 9. 운영 안전장치

재도입 전 다음 항목을 필수 조건으로 둔다.

| 항목 | 필요 이유 |
|---|---|
| node/location cooldown | 센서 패킷마다 반복 호출되는 것을 방지 |
| event id 멱등성 | Rabbit redelivery와 소비 재시도 시 중복 저장·실행 방지 |
| stale event 만료 | 과거 상황에 늦게 반응하는 제어 방지 |
| suggestion 만료 | 이미 변한 상황의 제안을 뒤늦게 수락하는 문제 방지 |
| 시간당 호출 상한 | 장애·설정 오류 시 비용 폭주 방지 |
| DLQ 또는 명시적 실패 정책 | 반복 실패 이벤트의 격리와 조사 |
| Core 재검증 | LLM 출력 또는 오래된 액추에이터 정보로 인한 잘못된 명령 차단 |
| 감사 context 저장 | 왜 그런 결정을 했는지 사후 확인 |
| AI_DIRECT kill switch | 전역 또는 location별 자동 실행 즉시 중지 |
| 관측 지표 | 호출량, 지연, 실패, 수락률, 중복 억제, 실행 결과 측정 |

## 10. 단계적 검증 계획

### 단계 0. 보류 유지

- Engine에는 AI Suggestion Node를 노출하지 않는다.
- AI event listener와 queue를 유지할지 제거할지 AI 서비스 소유자가 결정한다.
- 정기 제안의 효과와 사용 현황부터 측정한다.

### 단계 1. 단일 시나리오 PoC

후보 시나리오:

> 온도 28°C 이상이 10분 지속될 때 최근 15분 추세, 날씨, 현재 액추에이터 상태를 이용해 냉방·환기·무조치 중 하나를 제안한다. 같은 위치에는 30분 동안 재제안하지 않는다.

제약:

- 한 개 location 또는 내부 테스트 그룹으로 제한
- `SUGGESTION` 모드만 허용
- 자동 실행 금지
- 하나의 policy version만 사용
- 사람이 결과와 사용 context를 검토

### 단계 2. 제한된 사용자 제안

- opt-in 그룹에만 제공
- 수락·거절과 거절 이유 수집
- 제안 만료와 중복 억제 적용
- LLM 호출 예산과 지연 상한 설정

### 단계 3. 제한된 AI_DIRECT

- 안전성이 검증된 액추에이터·명령만 허용
- command별 rate limit과 rollback 또는 반대 명령 정책 정의
- Core 실행 결과 확인이 가능한 경우에만 자동 실행 성공으로 기록
- kill switch와 운영 runbook 준비

## 11. 성공·중단 기준

### 11.1 성공 지표 후보

- 제안 수락률
- 거절 및 무시 비율
- 동일 상황 중복 제안 비율
- 제안 후 15~30분 내 쾌적도 개선율
- 기존 deterministic Rule보다 추가로 해결한 상황 수
- 이벤트부터 제안 생성까지의 지연
- location당 일·월 LLM 호출 비용
- AI 제어 실패·롤백·수동 개입 건수

### 11.2 재개 조건

다음이 모두 준비되기 전에는 전체 기능 개발을 시작하지 않는다.

1. 정기 제안과 구분되는 실시간 사용자 시나리오가 하나 이상 승인됨
2. AI가 선택해야 하는 실제 복수 대안이 정의됨
3. 필요한 실시간·window·장치 context의 소유 서비스와 API가 정해짐
4. cooldown, 멱등성, 만료, 호출 상한 정책이 정해짐
5. 성공·중단 지표와 PoC 대상이 정해짐
6. `SUGGESTION`과 `AI_DIRECT`의 허용 범위가 정해짐

### 11.3 중단 기준 후보

- 제안 수락률과 환경 개선율이 사전 합의 기준보다 낮음
- 단순 Rule과 비교해 유의미한 추가 해결 사례가 없음
- 비용 또는 지연이 제품 허용 범위를 지속적으로 초과
- 중복 제안 또는 잘못된 자동 제어를 안전장치로 억제하지 못함
- 판단 이유를 운영자와 사용자에게 충분히 설명할 수 없음

## 12. 서비스별 예상 변경 범위

| 서비스 | 변경 범위 |
|---|---|
| Rule Engine | NodeType, params, executor, cooldown state, event model/publisher, activation validation, tests |
| AI | event contract, context enrichment, policy scoring, idempotency, expiration, structured decision validation, metrics |
| Core | 현재 actuator state·지원 command 조회 계약, 명령 재검증, 실행 결과·ack 계약 검토 |
| Front | 고급 Action 선택, policy 설정, 제안 근거·만료 표시, 승인 UX |
| Gateway | 외부 suggestion API routing 유지, 필요 시 신규 조회 API routing과 인증 경계 검토 |
| Infra | Rabbit delivery/DLQ, 호출량·비용·지연 관측, AI_DIRECT kill switch 운영 |

## 13. 추가로 정리할 현행 불일치

기능을 재개하지 않더라도 다음은 별도로 정리할 가치가 있다.

- AI 서비스의 사용되지 않는 suggestion event listener와 queue를 유지할지 제거할지 결정
- AI suggestion 목록 조회 API의 group membership 검증 여부 재확인
- Swagger와 주석 중 “수락 시 상태만 변경”처럼 실제 Core 실행과 다른 설명 수정
- Front와 AI 양쪽의 관리자 권한 검증 책임을 하나의 계약으로 명확화
- 정기 제안 자체의 수락률·효과·비용을 측정할 지표 추가

## 14. 최종 권고

단기적으로는 옵션 A를 유지한다. Rule Engine의 AI Suggestion Node는 복원하지 않고, 정기 제안 기능의 실제 사용성과 효과를 먼저 측정한다.

실시간 판단의 제품 가치가 확인되면 옵션 C를 PoC 범위로 시작한다. 이때 AI는 단순 Threshold를 대신하지 않으며, 현재값 한 개에 가중치를 주는 방식도 사용하지 않는다. Rule Engine이 발화 조건과 억제 정책을 책임지고, AI는 최근 추세·날씨·장치 상태·이전 조치를 결합해 제한된 결정만 반환하도록 한다.

기존 이벤트 DTO와 publisher를 그대로 되살리는 옵션 B는 빠르지만 현재의 실용성 문제와 운영 위험을 그대로 복원하므로 선택하지 않는다.
