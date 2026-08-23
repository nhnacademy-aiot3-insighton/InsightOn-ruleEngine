# Rule Engine 리팩터링·취약점 개선 보고서

- 점검일: 2026-08-20
- 대상 브랜치: `hotfix/code-fix`
- 검증 결과: Maven 전체 테스트 311개 통과(실패 0, 오류 0, 건너뜀 0)
- 범위: 이벤트 수신, Flow 검증·실행, 노드 파라미터, LOCATION 상태, Redis, 데이터소스, 의존성, API 입력 경계

## 1. 제보 항목 판정 및 적용 결과

### 1.1 기존 Actuator `deviceId` 설정 마이그레이션

**문제 분석**

기존 저장 모델은 `deviceId`를 사용했으나 현재 `ActuatorControlParams`는 `actuatorType`, `command`, `commandValue`를 모두 요구한다. 따라서 기존 JSON을 파싱하면 Bean Validation 위반으로 `INVALID_NODE_CONFIGURATION`이 발생한다. 저장된 설정만으로 신규 3개 값을 안전하게 추론할 수 없으므로 임의 기본값 변환은 실제 장치 오작동 위험이 있다.

**수정 방향**

기존 계약과 신규 계약을 동시에 역직렬화하되 두 계약을 섞은 모호한 JSON은 거부한다. 장치 제어 의미를 임의로 바꾸는 데이터 치환 대신 호환 역직렬화를 채택한다.

**채택한 방식**

- `ActuatorControlParams`에 선택적 `deviceId`를 복원했다.
- 기존 계약은 `deviceId`(과 과거 모델의 선택적 `command`)를 허용한다.
- 신규 계약은 `actuatorType`, `command`, `commandValue`가 모두 있을 때만 허용한다.
- 기존·신규 필드를 혼합한 설정은 거부한다.
- 기존 JSON의 configuration 검증, 활성화 검증, 실행 경로 파싱 회귀 테스트를 추가했다.

**남은 제약**

현재 저장소에는 `ACTUATOR_CONTROL`의 실제 `NodeExecutor`와 Core 발행 계약이 없다. 따라서 실제 애플리케이션은 파라미터 호환 여부와 별개로 이 노드를 `UNSUPPORTED_NODE_EXECUTOR`로 활성화 차단한다. 장치 제어 API/메시지 계약과 멱등성 키를 확정한 뒤 executor를 구현해야 한다. 호환 테스트는 executor가 제공된 상태에서 기존 설정이 활성화·실행 경로를 통과함을 검증한다.

### 1.2 무링크 `false` 종료를 FILTER로 제한

**문제 분석**

기존 `FlowRunner`는 노드 종류와 무관하게 `false` 출력에 링크가 없으면 정상 종료했다. 이 때문에 SENSOR가 잘못 `false`를 반환해도 실행기 계약 오류가 숨겨졌다.

**수정 방향**

무링크 `false` 정상 종료는 `NodeType.Category.FILTER`에만 허용하고, 실행 결과 자체도 NodeType의 terminal/port 계약과 대조한다.

**채택한 방식**

- FILTER의 `false`에만 무링크 정상 종료를 허용했다.
- ACTION은 반드시 terminal, TRIGGER/FILTER는 반드시 non-terminal 결과를 반환하도록 런타임 검증을 추가했다.
- non-terminal 출력은 선언된 `PortSchema`에 포함되어야 한다.
- `NodeExecutionResult` 자체에도 terminal/outputPort 불변식을 추가했다.
- SENSOR의 `false` 결과가 Flow 실패로 기록되는 회귀 테스트를 추가했다.

### 1.3 LOCATION metric 정책 일치

**문제 분석**

문서는 location의 모든 센서에서 metric별 최신 값을 사용한다고 선언했지만 구현은 현재 패킷만 검증했다. 다중 인스턴스 환경에서 인메모리 상태를 사용하면 인스턴스별 결과도 달라진다.

**수정 방향**

`groupId + locationId + metric` 범위의 최신 상태를 Redis에 저장하고 event timestamp가 더 최신인 경우에만 갱신한다. 값 갱신과 snapshot 조회는 원자적이어야 한다.

**채택한 방식**

- 현재 패킷 전용 processor를 제거하고 최신 location snapshot processor로 교체했다.
- Redis hash 두 개(값, timestamp)를 사용하고 Lua script 한 번으로 비교·갱신·전체 조회를 원자 처리한다.
- 실행 컨텍스트의 metric과 `event.metrics`를 동일한 최신 snapshot으로 교체해 SpEL의 `#metrics`와 `#event.metrics`가 불일치하지 않게 했다.
- group/location 범위 검증을 모두 수행한다.
- 다른 센서의 metric 병합과 오래된 이벤트의 덮어쓰기 방지를 실제 Redis 통합 테스트로 검증했다.

### 1.4 DBCP2 설정을 HikariCP로 전환

**문제 분석**

지적 자체는 유효하다. 다만 현재 브랜치의 production 설정은 이미 `spring.datasource.hikari.minimum-idle`, `maximum-pool-size`, `connection-test-query`를 사용하고 있으며 DBCP2 설정이나 강제 datasource type은 없다.

**채택한 방식**

추가 변경 없이 현 설정을 유지했다. 전체 테스트 시작 로그에서도 `HikariDataSource`가 실제 사용됨을 확인했다. 공통 설정에는 `spring.jpa.open-in-view=false`를 추가해 요청 종료까지 영속성 컨텍스트가 불필요하게 유지되는 기본 동작을 해제했다.

## 2. 추가로 발견해 적용한 개선

### 2.1 중복 이벤트 DTO 제거

**문제 분석**

`TelemetryEventMessage`는 `SensorEvent`와 같은 데이터를 별도 타입으로 보유하고 변환 중이었다. 변환 메서드는 사용하지 않는 `ObjectMapper` 인자도 받았고, 두 모델의 시간·sensorId 타입 차이로 검증 로직이 중복됐다.

**채택한 방식**

- `TelemetryEventMessage`와 해당 테스트를 제거했다.
- `SensorEvent.timestamp`가 기존 외부 필드 `time`도 `@JsonAlias`로 받도록 했다.
- Jackson의 숫자 문자열→`Long` 변환을 통해 기존 문자열 `sensorId`를 호환한다.
- 소비자는 `SensorEvent`로 직접 역직렬화한다.
- ID 양수, metric 수(최대 256), key 길이(최대 100), null value를 단일 모델에서 검증한다.

### 2.2 Telemetry 수신 안정성 및 과부하 방어

**문제 분석**

모든 예외를 ACK로 폐기해 일시적 실행 장애에서도 데이터가 유실됐다. 메시지 크기 제한이 없어 큰 JSON이 역직렬화 비용과 메모리 사용을 유발할 수 있었고, 별도 `FlowRouteRedisRepository` 선조회는 실제 `FlowRouter`가 사용하는 캐시와 이중화되어 stale route 때문에 정상 이벤트를 조기 폐기할 수 있었다.

**채택한 방식**

- 잘못된 JSON/필수 필드 누락/크기 초과는 poison message로 ACK 폐기한다.
- consumer까지 전파된 실행 예외는 NACK+requeue한다. Flow별 실행 실패를 내부에서 격리하는 현재 `FlowRunner` 정책은 아래 전달 보장 정책과 함께 별도 확정이 필요하다.
- 입력 메시지를 256 KiB로 제한한다.
- 중복되고 stale 가능성이 있는 active-flow 선조회를 제거하고 routing 책임을 `FlowRunner`/`FlowRouter` 한 곳에 둔다.
- 잘못된 JSON마다 전체 stack trace를 WARN에 남기지 않아 로그 증폭을 줄였다.

### 2.3 실행 가용성 방어

- 활성화 검증을 우회했거나 Redis가 손상되어 cycle이 들어와도 무한 루프가 되지 않도록 실행 중 node 재방문을 차단했다.
- Threshold expression 길이를 1,000자로 제한했다.
- 파싱된 SpEL expression을 최대 1,024개 캐시해 이벤트마다 같은 식을 재파싱하지 않는다.
- SpEL 평가는 기존처럼 `SimpleEvaluationContext.forReadOnlyDataBinding()`만 사용한다.
- Flow 입력은 최대 500 nodes/1,000 links, 설명은 최대 2,000자로 제한했다.
- Alert message는 최대 2,000자로 제한했다.
- prototype 테스트 endpoint에도 Bean Validation을 적용했다.

### 2.4 빌드·의존성 보안

**문제 분석**

- `spring-boot-starter-data-redis`가 POM에 중복 선언되어 Maven model warning이 발생했다.
- Spring Boot 3.5.16이 관리하던 Tomcat 10.1.55는 여러 공개 취약점의 영향 범위에 포함된다.
- PostgreSQL JDBC 42.7.11은 channel binding downgrade 취약점의 영향 범위에 포함된다.

**채택한 방식**

- 중복 Redis starter 선언을 제거했다.
- Tomcat을 10.1.57로 고정했다.
- PostgreSQL JDBC를 42.7.12로 고정했다.
- Maven dependency tree에서 실제 해석 버전을 확인했다.

참고한 공식/원문 자료:

- [Apache Tomcat 10 보안 공지](https://tomcat.apache.org/security-10.html)
- [NVD CVE-2026-54291: pgJDBC channel binding downgrade](https://nvd.nist.gov/vuln/detail/CVE-2026-54291)
- [Spring Framework 6.2.19 보안 수정](https://spring.io/blog/2026/06/08/spring-framework-7-0-8-and-6-2-19-available-now/)
- [NVD CVE-2026-54518: Jackson databind](https://nvd.nist.gov/vuln/detail/CVE-2026-54518)

현재 해석된 Spring Framework 6.2.19, Spring Data Commons 3.5.13, Spring Integration 6.5.10, Jackson Databind 2.21.4는 확인한 해당 공지의 수정 버전 이상이다.

## 3. 정책 또는 외부 계약 확인 후 처리할 항목

### 우선순위 높음

1. **사용자 신원 신뢰 경계**  
   API가 `X-User-Id`를 그대로 신원으로 사용한다. 외부 요청이 서비스에 직접 도달하거나 gateway가 헤더를 제거·재발급하지 않으면 다른 사용자 ID를 사칭할 수 있다. JWT subject 검증, gateway 서명 헤더, 또는 mTLS 서비스 신원을 채택하고 애플리케이션이 검증된 principal만 사용하도록 바꿔야 한다.

2. **RabbitMQ 전달 보장 정책 통합**  
   현재 `SensorEventConsumer`는 실시간 우선이라는 이유로 `ackMode=NONE`, 새 telemetry 경로는 manual ACK/requeue를 사용한다. 어떤 consumer가 production 표준인지 확정해야 한다. 재시도를 채택하면 DLQ, 최대 재시도 횟수, event ID 기반 멱등성(특히 actuator/alert)을 함께 구현해야 중복 실행과 poison-message 무한 재큐잉을 막을 수 있다.

3. **Actuator 실제 실행 계약**  
   신규 3필드와 legacy `deviceId` 사이의 의미 변환, Core API/queue, timeout, retry, idempotency key, 실패 보상 정책이 없다. 이를 확정하기 전에는 현재처럼 activation에서 unsupported executor로 차단하는 것이 안전하다.

4. **신규 NodeType의 노출 범위**  
   `SCHEDULE`, `TIME_WINDOW`, `TIMER`, `ACTUATOR_CONTROL`, `EXTERNAL_NOTIFICATION`은 enum과 저장 모델에는 있으나 executor가 없다. 활성화 검증이 현재 안전하게 차단하지만, API/UI에서 구현 완료 전 타입을 숨기거나 executor 구현 로드맵을 명시해야 한다.

### 우선순위 중간

5. **LOCATION freshness/lifecycle**  
   최신 값의 유효 기간, 센서 제거 시 상태 삭제, location 삭제 시 Redis 정리, 동일 timestamp tie-break, 미래 timestamp 허용 범위가 정의되지 않았다. 현재는 event-time 기준으로 최신 값을 무기한 유지하므로 잘못된 미래 timestamp가 들어오면 이후 정상 값이 오래 반영되지 않을 수 있다. ingest-time 사용 여부 또는 허용 clock skew와 TTL을 결정해야 한다.

6. **LOCATION 상태 저장 시점**  
   현재 상태는 활성 LOCATION flow가 이벤트를 실행할 때부터 축적된다. flow 활성화 전의 과거 최신 상태까지 즉시 필요하다면 telemetry ingestion 단계에서 모든 이벤트를 먼저 저장하거나 Core의 현재 상태 조회 API로 초기 snapshot을 채워야 한다.

## 3.1 운영 환경 확인 완료 항목

- production의 `spring.data.redis.database=324`는 실제 운영 Redis 구성과 일치하므로 변경하지 않는다.
- Config Server, Eureka, Zipkin, Core의 HTTP 통신 구간은 신뢰된 내부망이므로 현재 구성을 유지한다. 향후 해당 서비스가 신뢰 경계 밖에 노출될 경우에만 TLS/mTLS 전환을 다시 검토한다.

## 4. 테스트 및 검증

- 전체: `./mvnw test`
- 결과: 311 tests, 0 failures, 0 errors, 0 skipped
- 포함된 실제 통합 검증: PostgreSQL, Redis, RabbitMQ Testcontainers
- 별도 확인: dependency tree에서 Tomcat 10.1.57, PostgreSQL JDBC 42.7.12 적용
- `git diff --check`: whitespace 오류 없음

사용자가 작업 중이던 static/template 삭제와 `.DS_Store` 파일은 이번 수정 범위에서 건드리지 않았다.
