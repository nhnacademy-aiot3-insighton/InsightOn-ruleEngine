# Rule Engine 프로젝트 기획서

## 1. 프로젝트 개요

InsightOn Rule Engine은 사용자가 장소의 센서 상태나 반복 시간 조건을 Flow로 정의하고, 조건이 충족되면 알림을 생성하거나 액추에이터 동작을 요청하는 서비스다.

엔진의 핵심 책임은 다음 세 가지다.

1. 사용자가 만든 Flow 정의를 검증하고 생명주기를 관리한다.
2. 센서 텔레메트리 또는 예약 시각을 Trigger로 ACTIVE Flow를 실행한다.
3. 실행 결과를 Core 액추에이터 API 또는 AI 알림 이벤트로 전달한다.

엔진은 센서 수집, 실제 장치 제어, 알림 조회 UI를 직접 소유하지 않는다. 해당 책임은 각각 Core·Gateway, Core actuator 도메인, AI·Front에 있다.

## 2. 배경과 해결하려는 문제

센서 데이터는 지속적으로 들어오지만 모든 패킷이 사용자 행동으로 이어지지는 않는다. 사용자는 개발 지식 없이 다음과 같은 자동화를 구성할 수 있어야 한다.

- 특정 센서의 온도가 기준보다 높을 때 알림 생성
- 한 장소에서 들어온 현재 패킷이 조건을 만족할 때 장치 제어
- 지정한 시간대에만 센서 조건을 평가
- 짧은 시간에 같은 이벤트가 반복돼도 일정 간격으로만 동작
- 매일 또는 특정 요일의 특정 시각에 액추에이터 제어

위 항목은 현행 Engine/API가 표현하는 제품 목표다. 2026-08-31 Front `dev`는 Schedule cron·그래프·Actuator 편집 계약이 맞지 않아 시간 기반 Flow를 UI로 완성할 수 없다.

다중 엔진 인스턴스가 같은 Flow를 보더라도 같은 패킷과 같은 예약 시각의 책임이 예측 가능해야 하며, 장애 시 무제한 재처리나 중복 제어가 발생하지 않아야 한다.

## 3. 목표

### 3.1 제품 목표

- 그룹 관리자가 장소 단위 자동화 Flow를 생성·검증·활성화·보관할 수 있게 한다.
- 센서 패킷 기반 Flow와 반복 시간 기반 Flow를 안정적으로 실행한다.
- 액추에이터 제어와 알림 발행을 기존 MSA 계약에 맞춰 전달한다.
- 두 엔진 인스턴스에서 location 단위 텔레메트리 라우팅과 Schedule 중복 억제를 제공한다.
- 오류는 운영자가 원인과 영향 범위를 식별할 만큼 기록하되 정상 패킷마다 INFO 로그를 남기지 않는다.

### 3.2 현재 인수 목표

- 구조적으로 잘못되거나 실행기가 없는 Flow는 ACTIVE가 될 수 없다.
- 동일 `flowId + scheduledAt`은 Redis 선점 기준으로 한 인스턴스만 실행 시도한다.
- 잘못된 텔레메트리 메시지는 재처리 루프 없이 폐기한다.
- Redis Flow 캐시 장애 시 최근 로컬 snapshot 또는 DB route 재구축 경로를 사용한다.
- Core·AI와 현재 사용하는 HTTP/RabbitMQ DTO가 상호 호환된다.
- 전체 자동화 테스트가 실패 없이 통과한다.

처리량, p95 지연, 허용 오류율 같은 운영 SLO는 아직 실측 기준이 없어 본 기획에서 수치로 확정하지 않는다.

## 4. 이해관계자와 역할

| 이해관계자 | 관심사·책임 |
|---|---|
| 일반 그룹 멤버 | Flow 목록·상세 조회 |
| 그룹 관리자 | Flow 생성, 변경, 활성화, 보관, 삭제 |
| Front 팀 | Flow 편집 UI, API 요청 조립, 검증 오류 표시 |
| Core 팀 | 그룹 권한, 텔레메트리 발행, 액추에이터 실행, lifecycle 이벤트 |
| AI 팀 | Alert 이벤트 소비·저장·조회 및 사용자 알림 경험 |
| Engine 팀 | Flow 검증·라우팅·실행·공유 상태·운영 로그 |
| 인프라/운영 | PostgreSQL·Redis·RabbitMQ·2개 인스턴스·관측 환경 |

## 5. 사용자 시나리오

### UC-01 센서 임계치 알림

1. 관리자가 장소와 센서를 선택한다.
2. `SENSOR → THRESHOLD → ALERT` Flow를 저장한다.
3. ACTIVE로 전환한다.
4. 해당 센서의 새 패킷이 들어오고 표현식이 true면 Alert 횟수·cooldown 정책을 평가한다.
5. 통과한 Alert 이벤트는 AI로 발행된다.

### UC-02 장소 패킷 기반 액추에이터 제어

1. 관리자가 `LOCATION → TIME_WINDOW → THRESHOLD → ACTUATOR_CONTROL` Flow를 저장한다.
2. 해당 장소에서 들어오는 각 센서 패킷의 현재 metrics로 조건을 평가한다.
3. true 경로가 액추에이터 Action에 도달하면 Core 내부 API를 호출한다.

`LOCATION`은 장소의 과거 metrics를 집계하지 않는다. 한 실행은 현재 수신 패킷만 사용한다.

### UC-03 주기 억제

1. 관리자가 텔레메트리 Flow에 `TIMER`를 배치한다.
2. 동일 `(nodeId, locationId)`에서 interval 내 최초 이벤트만 true 경로를 통과한다.
3. 나머지는 false 경로로 이동하거나 false Link가 없으면 정상 종료한다.

### UC-04 예약 액추에이터 동작

1. 관리자가 반복 cron과 액추에이터 명령을 설정한다.
2. `SCHEDULE → ACTUATOR_CONTROL` 두 노드 Flow를 ACTIVE로 전환한다.
3. 모든 인스턴스가 cron을 등록한다.
4. 예약 시각에 Redis 선점에 성공한 인스턴스만 Core 액추에이터 API를 호출한다.
5. 엔진이 중단된 동안 놓친 실행은 복구하지 않고 다음 cron부터 실행한다.

### UC-05 Flow 보관과 복구

1. 관리자는 사용하지 않는 ACTIVE 또는 INACTIVE Flow를 ARCHIVED로 보낸다.
2. 보관된 Flow는 실행되지 않지만 상세 조회와 복구가 가능하다.
3. 복구 시 같은 ID로 INACTIVE가 된다.
4. 영구 삭제는 ARCHIVED 상태에서만 가능하다.

## 6. 범위

### 6.1 현재 포함

- Flow CRUD, 활성/비활성, 보관, 복구, 영구 삭제
- Core 기반 그룹 역할 확인
- 그래프 구조·Node 설정·실행 가능성 검증
- `SENSOR`, `LOCATION`, `SCHEDULE` Trigger
- `THRESHOLD`, `TIME_WINDOW`, `TIMER` Filter
- `ACTUATOR_CONTROL`, `ALERT` Action
- Redis 기반 ACTIVE Flow snapshot, Alert/Timer 상태, Schedule 상태·선점, heartbeat
- RabbitMQ consistent-hash 16 queue와 고정 2인스턴스 failover
- Core group/location 삭제에 따른 Flow·Schedule·route cache·Alert 상태 정리. Timer key는 기존 TTL까지 잔존
- 실행 로그 레벨·오류 분류·반복 로그 억제·실패 counter

### 6.2 현재 제외 또는 보류

- 일회성 `executeAt` 예약과 놓친 Schedule 보정 실행
- Schedule에서 Alert 또는 Filter로 연결되는 일반 그래프
- 사용자용 Flow 실행 이력·성공/실패 조회 API
- 실행 실패 이벤트 발행과 사용자 실패 알림
- `EXTERNAL_NOTIFICATION` 실제 전송
- 센서/액추에이터 삭제 시 Flow 자동 무효화 또는 `ERROR` 전환
- 탄력적인 3개 이상 엔진 인스턴스
- Flow 수정 시 동일 이름을 자연스럽게 재사용하는 정책
- Front 구현 변경과 배포 자동화 변경

## 7. 제품 원칙과 확정 정책

- PostgreSQL이 Flow 원본이고 Redis는 실행 snapshot과 분산 상태 저장소다.
- 텔레메트리 처리 실패는 패킷 손실로 처리하며 엔진에서 재시도하지 않는다.
- 오류의 일시성/영구성 분류는 로그·메트릭에 사용하고 자동 재시도 정책으로 확대하지 않는다.
- Schedule은 RabbitMQ로 엔진에 재진입하지 않고 로컬 스케줄러에서 FlowRunner를 직접 호출한다.
- Schedule은 액추에이터 제어 전용이며 Alert와 연결하지 않는다.
- Schedule Redis 장애 시 중복 가능성보다 미실행을 선택하는 fail-closed 정책을 쓴다.
- 정상 패킷 수신·라우팅·Flow 완료는 DEBUG, 복구는 INFO, 일시 장애는 WARN, 영구/내부 오류는 ERROR를 기본으로 한다.

## 8. 주요 의존성과 제약

| 항목 | 전제·제약 |
|---|---|
| PostgreSQL | `engine` schema가 배포 전에 존재해야 함. 앱은 `ddl-auto=validate`만 수행 |
| Redis | Flow snapshot, 분산 Timer/Alert/Schedule, heartbeat에 필수 |
| RabbitMQ | consistent-hash exchange plugin 필요, Core와 topology 명칭 일치 필요 |
| Core | 그룹 권한 API와 액추에이터 내부 API가 가용해야 함 |
| AI | Alert exchange/routing key와 payload를 소비해야 함 |
| 배포 | StatefulSet ordinal 0/1, 정확히 2개 인스턴스 가정 |
| Gateway | 외부가 임의로 만들 수 없는 신뢰된 `X-User-Id` 전달 경계 필요 |
| Front | 현행 5필드 cron 생성과 엔진 6필드 cron 계약 불일치 해소 필요 |

## 9. 단계별 추진안

### 단계 A — 현행 기준선 고정

- 코드·요구사항·인터페이스·데이터·운영·테스트 문서 일치
- 현재 자동화 테스트 통과
- 외부 계약의 생산자/소비자 코드 교차 확인

### 단계 B — 출시 전 필수 정합성

- DB schema migration 소유권과 배포 절차 확정
- Front cron과 Schedule 연결 규칙 수정
- `groupId-locationId` 소유관계 검증 책임 확정
- Gateway 신뢰 헤더와 내부 서비스 접근 제어 검증
- Core 액추에이터 preset을 실제 장치/Front 계약으로 확정

### 단계 C — 운영 안정성 검증

- 실제 두 Pod에서 queue failover·handback 점검
- Redis·DB·RabbitMQ·Core 장애 주입 시험
- Telemetry 처리량과 Schedule 집중 시간대 부하 측정
- Core lifecycle DLQ, 로그, tracing, failure counter 관측 경로 확인. Telemetry에는 DLQ가 없음

### 단계 D — 후속 제품 기능

- 사용자용 실행 이력과 실패 노출
- 센서/액추에이터 삭제 정책과 `ERROR` 상태 결정
- External notification 필요성 재평가
- 3개 이상 인스턴스가 필요해질 때 queue ownership 재설계
- Flow 수정 시 동일 이름 정책 재논의

## 10. 위험 요약

| 위험 | 영향 | 현재 대응 | 후속 |
|---|---|---|---|
| DB schema 자동 생성·migration 부재 | 신규 환경 기동 실패 | Hibernate validate로 조기 실패 | migration 도입·소유자 확정 |
| group-location 소유권 미검증 | 다른 그룹 location에 Action 요청 가능 | 그룹 역할만 검증 | 생성·활성화·Core에서 소유권 재검증 |
| Front cron/그래프 불일치 | Schedule 생성·활성화 실패 | 엔진 검증이 거부 | Front 계약 수정 및 E2E |
| Redis 장애 | Schedule 미실행, 일부 상태 Filter 실패 | Schedule fail-closed, Flow cache fallback | 장애 알림·복구 시험 |
| DB commit 이후 runtime 비원자 갱신 | 비활성 Flow의 일시적 stale 실행 가능 | afterCommit·재조정·TTL | 허용 창 합의, version/무효화 회귀 시험 |
| 고정 2인스턴스 구조 | 수평 확장 제한 | ordinal 0/1 검증 | 확장 필요 시 ownership 재설계 |
| 실행 이력 부재 | 사용자가 제어 실패를 모름 | 운영 로그만 존재 | 제품 기능으로 별도 설계 |
| 액추에이터 규칙 중복 | Core·Engine 값 불일치 가능 | 현재 코드 교차 검증 | 단일 계약 또는 contract test |
| 여러 Flow의 상충 Action | 최종 액추에이터 상태가 비결정적 | packet 내 순차 실행만 존재 | 충돌 정책·priority 필요성 확정 |
| lifecycle 일부만 수신 | 삭제된 Sensor Flow가 남음 | group/location은 정리 | sensor/actuator 정책 확정 |
| Secret/TLS 소유권 미확정 | 배포 실패·내부 데이터 평문 노출 | 외부 secret과 내부망을 전제 | 프로비저닝·회전·전송 암호화 책임 확정 |

상세 위험과 결정 항목은 [11-open-issues-and-validation.md](11-open-issues-and-validation.md)를 따른다.
