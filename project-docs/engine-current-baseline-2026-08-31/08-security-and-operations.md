# 보안·배포·운영 가이드

## 1. 실행 환경

### 1.1 필수 의존성

- Java 21
- PostgreSQL과 기존 `engine` schema
- Redis 단일 또는 non-cluster topology
- RabbitMQ와 `rabbitmq_consistent_hash_exchange` plugin
- InsightOn Core 내부 API
- Alert를 사용할 경우 InsightOn AI consumer
- tracing을 사용할 경우 Zipkin

### 1.2 Profile

| Profile | Telemetry routing | Heartbeat | Core URL | 비고 |
|---|---|---|---|---|
| dev | 기본 false | 기본 false | `http://localhost:8100` | 단일 로컬 개발 기본 |
| prod | 기본 true | 기본 true | `http://insighton-core` | pod ordinal 0/1 필요 |

## 2. 환경 변수와 주요 설정

애플리케이션은 실행 환경에 맞는 `SPRING_PROFILES_ACTIVE=dev|prod` bootstrap 설정이 필요하다. Kubernetes manifest는 `prod`를 주입한다. profile을 지정하지 않으면 profile별 DB·Redis·Rabbit·routing 설정 import가 적용되지 않는다.

### 2.1 필수 환경 변수

| 변수 | Profile | 설명 |
|---|---|---|
| `DB_HOST` | dev/prod | PostgreSQL host, port는 설정상 8000 |
| `DB_PASSWORD` | dev/prod | DB password |
| `REDIS_HOST` | dev/prod | Redis host |
| `REDIS_PASSWORD` | dev/prod | Redis password |
| `RABBITMQ_HOST` | dev/prod | RabbitMQ host |
| `RABBITMQ_PASSWORD` | dev/prod | RabbitMQ password |
| `REDIS_PORT` | dev | dev Redis port |
| `REDIS_DATABASE` | dev | dev logical DB |

고정 username은 DB `aiot3-team3`, RabbitMQ `admin`이다. production Redis port는 6379, DB는 324로 고정돼 있다.

### 2.2 주요 기본값·제약값

| 설정 | 기본값 | 변경 경로·제약 |
|---|---:|---|
| Core connect/read timeout | 2초 / 5초 | 모든 profile에서 환경 변수 지원 |
| Schedule zone | Asia/Seoul | prod 환경 변수 지원, dev 고정 |
| Schedule pool | 8 | dev/prod 환경 변수 지원 |
| Schedule execution key TTL | 10분 | prod 환경 변수 지원, dev 고정 |
| Schedule reconciliation | 60초 | prod 환경 변수 지원, dev 고정 |
| Flow Redis cache TTL | 30분 | dev/prod 환경 변수 지원 |
| Local fallback max age | 1분 | dev/prod 환경 변수 지원 |
| Stale sensor max entries | 10,000 | dev/prod 환경 변수 지원 |
| Stale watermark idle TTL | 24시간 | dev/prod 환경 변수 지원 |
| Heartbeat refresh / TTL | 5초 / 15초 | property는 존재하지만 validator가 이 값만 허용 |
| prod DB pool | pod당 min 2 / max 10 | prod 환경 변수 지원 |
| prod Zipkin sampling | 0.3 | prod 파일 고정 |

저장소의 `.env.example`에는 제거된 예전 Sensor Rabbit 설정이 남아 있으므로 현행 운영 설정의 기준으로 사용하지 않는다.

## 3. Kubernetes 배포 전제

현행 manifest 기준:

- StatefulSet `replicas: 2`
- pod `insighton-ruleengine-0` → engine-a, 짝수 8개 queue
- pod `insighton-ruleengine-1` → engine-b, 홀수 8개 queue
- startup/liveness/readiness probe 사용
- request: 100m CPU, 256Mi memory
- limit: 500m CPU, 512Mi memory
- `automountServiceAccountToken: false`
- 종료 전 5초 preStop, 앱 scheduler 최대 10초 종료 대기

production hostname이 `-0` 또는 `-1`로 끝나지 않으면 engine/peer ID와 queue ownership을 자동 주입하지 못한다. 별도 유효 queue 설정도 없으면 routing configuration 검증으로 기동에 실패할 수 있으므로 StatefulSet ordinal 전제를 유지해야 한다.

현재 manifest image tag는 기준 `dev` 커밋이 아닌 과거 `9714445...` 이미지를 가리킨다. 문서 기준 기능을 운영에서 검증하려면 CI/CD가 최신 image tag로 manifest를 갱신했는지 먼저 확인해야 한다.

manifest가 참조하는 `insighton-ruleengine-secret`과 image pull용 `insighton-ghcr-secret`의 생성 리소스는 현재 manifest 저장소에서 확인되지 않는다. 배포 전 외부 secret 프로비저닝 주체, 필수 key, 회전·폐기 절차를 운영 책임으로 확정해야 한다.

현재 manifest에 명시적으로 없는 항목:

- PodDisruptionBudget
- pod anti-affinity 또는 topology spread
- NetworkPolicy
- container/pod securityContext

두 인스턴스 가용성을 목표로 한다면 동시에 같은 node에서 사라지지 않도록 배치 정책과 PDB를 검토해야 한다.

## 4. 보안 모델

### 4.1 사용자 요청 경계

Gateway는 외부 요청의 기존 `X-User-Id`를 제거하고 JWT subject로 신뢰 헤더를 다시 넣는다. Engine은 이 값을 이용해 Core 그룹 멤버 API를 호출한다.

Engine 권한:

- GET: MEMBER 이상
- POST/PUT/DELETE: MANAGER 이상

필수 운영 조건:

- Engine Service가 외부에서 Gateway를 우회해 직접 접근되지 않아야 한다.
- Gateway의 헤더 제거·재주입 정책을 통합 테스트로 고정해야 한다.
- application prod 설정의 PostgreSQL JDBC, Redis, RabbitMQ, Core HTTP, Zipkin endpoint는 모두 평문 프로토콜이고 애플리케이션 TLS 설정이 없다. 서비스 메시나 인프라 계층 암호화가 없다면 DB·Rabbit·Redis 자격 증명과 telemetry·Flow snapshot·내부 API 데이터가 평문 내부망을 통과한다.
- 내부 전송 암호화와 서비스 인증을 애플리케이션, 서비스 메시, 인프라 중 어느 계층이 소유하는지 확정해야 한다.

### 4.2 높은 우선순위 교차 그룹 위험

현재 Flow 생성은 사용자가 `groupId`의 MANAGER인지 확인하지만 request `locationId`가 그 group에 속하는지 확인하지 않는다. Actuator Action은 저장된 locationId로 Core 내부 API를 호출하고, 특히 Schedule Flow는 telemetry의 group/location route 검증 없이 예약 시각에 직접 실행된다. Core actuator API도 groupId 소유권이나 서비스 자격 증명 없이 `callerService != USER`만 검사한다.

따라서 다른 그룹의 locationId를 아는 관리자가 그 location을 자신의 Flow에 저장해 액추에이터 상태를 바꾸는 경로가 생길 수 있다.

출시 전 권고:

1. Flow 생성·수정 시 Core에서 `groupId-locationId` 소속을 검증한다.
2. Engine actuator request에 groupId를 포함하고 Core가 다시 소유권을 검증한다.
3. Engine identity를 검증할 서비스 인증(mTLS, signed service token 등)을 적용한다.
4. Kubernetes NetworkPolicy로 Core 내부 endpoint 호출 주체를 제한한다.

### 4.3 표현식 안전성

Threshold는 full `StandardEvaluationContext`가 아닌 read-only `SimpleEvaluationContext`를 사용한다. 사용자 expression에 제공되는 변수는 현재 event와 metrics이며, bean/type 접근을 의도적으로 제공하지 않는다.

## 5. 관측성

### 5.1 로그 레벨

| 레벨 | 대상 |
|---|---|
| DEBUG | 정상 telemetry route, Flow 완료, stale drop, 정상 Action 전달, 상세 stack trace |
| INFO | 기동 warm-up, listener 시작·반환, lifecycle 정리 완료, 장애 복구 요약 |
| WARN | 일시 의존성 장애, invalid packet drop, peer takeover 시작 |
| ERROR | 영구 설정/거부/내부 오류, queue 전환 실패 |

반복되는 동일 실패는 최초만 WARN/ERROR로 기록하고 이후 수를 억제한다. 복구 로그에 마지막 오류 종류·message·억제 횟수를 포함한다. Schedule 일부 복구 로그는 마지막 message를 아직 포함하지 않는다.

### 5.2 Metric

```text
rule_engine.execution.failures{
  stage="routing|flow",
  kind="transient_dependency|permanent_configuration|permanent_rejected|internal"
}
```

Micrometer counter는 코드에 있지만 Prometheus registry 의존성과 metrics endpoint 노출이 없다. 현재 이 metric이 외부 모니터링 시스템에 수집된다고 가정하면 안 된다.

### 5.3 Tracing과 로그 수집

- prod Zipkin sampling: 30%
- Actuator health probes 활성화
- 인프라의 Loki/Grafana 로그 수집 대상에 Rule Engine 포함
- 현재 Loki 보존 설정은 168시간
- Engine 전용 dashboard와 alert rule은 확인되지 않음

## 6. 장애 대응 Runbook

### 6.1 한 Engine Pod 장애

예상 동작:

1. peer heartbeat key가 최대 15초 후 만료된다.
2. 생존 인스턴스가 최대 5초 주기의 다음 점검에서 상대 8개 queue listener를 시작한다. 이론상 장애 후 약 20초와 listener 전환 시간이 걸릴 수 있다.
3. 복구된 pod가 heartbeat를 갱신하면 takeover queue를 반환한다.

확인:

- `상대 엔진의 Telemetry 큐 인계를 시작합니다` WARN
- `상대 엔진의 Telemetry 큐를 반환했습니다` INFO
- 각 pod의 normal/takeover listener 상태
- queue별 consumer 수와 backlog

Redis 조회 오류가 원인이면 takeover하지 않는 것이 정상이다.

### 6.2 Redis 장애

영향:

- Flow cache는 최대 1분 local snapshot 또는 DB route rebuild 사용
- Timer와 Redis count/cooldown이 필요한 Alert는 실패. `requiredCount=1`, `cooldown=0` Alert는 Redis를 우회
- Schedule은 fail-closed로 미실행
- heartbeat 상태를 알 수 없어 queue 전환 보류

주의: 로컬 fallback은 1분으로 제한되지만, 이전 Flow 변경의 Redis 저장 실패로 이미 남아 있던 Redis snapshot은 정상 hit로 읽혀 기본 cache TTL까지 stale할 수 있다.

확인:

- Flow cache degraded와 local fallback WARN
- Schedule Redis 최초 ERROR와 복구 INFO
- DB connection pool 급증 여부
- Redis 복구 후 cache/state 정상 접근 로그

놓친 Schedule은 수동 backfill하지 않는 것이 현행 정책이다.

### 6.3 PostgreSQL 장애

영향:

- Flow API 실패
- Schedule 60초 재조정 실패
- Redis hit telemetry는 계속 실행 가능
- Redis miss이고 usable local snapshot도 없으면 route 실패
- 장애 중 새 인스턴스를 기동하면 cache와 Schedule warm-up이 DB를 직접 조회하므로 정상 기동 가용성을 보장하지 않음

확인:

- DB pool/connection 상태
- Schedule reconcile WARN
- `rule_engine.execution.failures`의 dependency/internal 분류

### 6.4 RabbitMQ 장애

Telemetry:

- Core가 발행 실패를 WARN 후 drop한다.
- Engine 재처리 수단이 없다.

Alert:

- RabbitActionPublisher 예외로 해당 Flow가 실패한다.
- count/cooldown은 이미 전이됐을 수 있다.

Lifecycle:

- broker가 복구되면 queue의 미소비 메시지를 처리한다.
- 소비 오류는 3회 후 DLQ로 이동한다.

### 6.5 Core 장애

- 그룹 권한 API 장애: Flow REST가 502
- actuator API 장애: 해당 Flow Action 실패, 자동 retry 없음
- 사용자가 볼 수 있는 실행 실패 이력이 없으므로 운영 로그로만 확인 가능

### 6.6 DLQ 처리

Core lifecycle DLQ 메시지는 다음을 확인한 뒤 재처리한다.

1. payload ID가 유효한가?
2. DB·Redis 의존성이 복구됐는가?
3. 같은 group/location cleanup을 다시 수행해도 안전한가?
4. 원래 exchange/routing key로 republish할지 운영 도구로 직접 cleanup할지 결정했는가?

무조건 반복 republish하지 않는다.

## 7. 배포 전 체크

- 최신 image tag가 기준 커밋을 포함하는지 확인
- `engine` schema와 expected unique/index 존재 확인
- Redis DB 324 사용 가능 여부 확인
- Redis가 cluster mode가 아닌지 확인
- Rabbit consistent-hash plugin과 16 queue topology 확인
- StatefulSet replica가 정확히 2인지 확인
- pod-0/1의 injected engine ID와 queue 목록 확인
- Core membership/actuator endpoint 접근 확인
- AI Alert binding 확인
- Gateway 우회 접근 차단과 `X-User-Id` 정책 확인
- startup/readiness/liveness probe 확인
- lifecycle DLQ와 운영 조회 절차 확인
