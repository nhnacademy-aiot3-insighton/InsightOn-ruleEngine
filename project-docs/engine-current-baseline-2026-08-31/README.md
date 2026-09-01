# InsightOn Rule Engine 현행 기준 문서

## 1. 문서 목적

이 디렉터리는 2026-08-31 현재 통합 코드에서 실제로 동작하는 Rule Engine을 기준으로 다시 작성한 프로젝트 산출물이다. 기존 `docs/` 문서는 초기 설계와 과거 검토 이력을 보존하기 위해 수정하지 않았다. 저장소의 `.gitignore`가 `/docs/*`를 제외하므로, 새 산출물이 변경 목록과 후속 커밋에서 누락되지 않도록 추적 가능한 `project-docs/` 아래에 분리했다.

이 문서는 다음 질문에 답한다.

- 제품이 현재 제공하는 기능과 제공하지 않는 기능은 무엇인가?
- Flow는 어떤 규칙으로 저장·활성화·실행되는가?
- Core, AI, Front, RabbitMQ, Redis, PostgreSQL과 어떤 계약을 맺는가?
- 다중 인스턴스 환경에서 라우팅·스케줄·장애 복구는 어떻게 동작하는가?
- 초기 기획에서 무엇이 달라졌고, 무엇을 추가 검증하거나 결정해야 하는가?

## 2. 기준선

| 항목 | 값 |
|---|---|
| 기준 일자 | 2026-08-31 (Asia/Seoul) |
| 코드 기준 | `dev` |
| 기준 커밋 | `eed36d5ce687078e75a0f84527aed460daf2ddf3` |
| 기준 커밋 설명 | `Merge pull request #120 ... feature/filter-execution` |
| 문서 작업 브랜치 | `docs/engine-current-baseline` |
| 기술 스택 | Java 21, Spring Boot 3.5.16, PostgreSQL, Redis, RabbitMQ, OpenFeign |

기준 커밋에는 다음 변경이 함께 통합돼 있다.

- `feature/filter-execution`: `TIME_WINDOW`, `TIMER` 실행 기능
- `refact/remove-ai-suggestion`: 미사용 AI 제안 발행 경로 제거
- `refact/engine-stability`: Flow 캐시·오류 분류·로컬 fallback 안정성 보완
- `feature/schedule-execution`: Schedule 실행과 Redis 중복 선점
- `refact/logging-policy`: 실행 로그 레벨과 반복 실패 억제 정책

외부 계약은 2026-08-31 로컬 `dev` 또는 현재 배포 저장소의 다음 revision과 교차 확인했다.

| 저장소 | 검증 revision |
|---|---|
| InsightOn-core | `8fe84d032ed86e9190d82371f0edff8f22177b19` |
| InsightOn-ai | `524b20223a1be03cab51d42fdd6c0f9a92adb652` |
| InsightOn-front | `ccb8540e0c484b5a4209846a99db1a4fd22e671d` |
| InsightOn-gateway | `830c050ec598993a169383861942468588cfd4ec` |
| insighton-k8s-manifests | `b7af34bae79b74e8c51e3dc2c7bca2f0999081b7` |

## 3. 사실의 우선순위

문서와 구현이 충돌할 경우 다음 순서로 현행을 판정한다.

1. 기준 커밋의 운영 코드와 설정
2. 기준 커밋의 자동화 테스트
3. 이 디렉터리의 현행 문서
4. 기존 `docs/`의 초기 기획·과거 검토 자료

기존 문서의 요구사항 번호나 설명을 현행 근거로 재사용하지 않았다. 새 요구사항 ID는 이 문서 세트 안에서만 유효하다.

## 4. 상태 표기

| 상태 | 의미 |
|---|---|
| `구현` | 기준 코드에 동작 경로가 있고 자동화 테스트 또는 직접 근거가 있음 |
| `부분 구현` | 핵심 경로는 있으나 제품 요구를 완전히 충족하지 않거나 운영 제약이 남음 |
| `외부 연동 필요` | 엔진 구현은 있으나 다른 서비스·프론트·운영 환경의 조치가 필요함 |
| `미구현` | 타입·설정 또는 기획만 있고 실행 경로가 없음 |
| `보류` | 의도적으로 후속 단계로 미룬 항목 |
| `결정 필요` | 팀 정책이나 책임 주체의 합의가 선행돼야 함 |
| `제거` | 초기 또는 과거 구현에 있었지만 현행 범위에서 삭제됨 |

## 5. 산출물 목록

| 문서 | 용도 |
|---|---|
| [01-project-plan.md](01-project-plan.md) | 프로젝트 기획서, 범위, 사용자 시나리오, 단계별 계획, 위험 |
| [02-requirements-specification.md](02-requirements-specification.md) | 업무·기능·비기능 요구사항과 인수 조건 |
| [03-requirements-traceability.md](03-requirements-traceability.md) | 요구사항과 코드·테스트·인터페이스 간 추적표 |
| [04-system-architecture.md](04-system-architecture.md) | 시스템 문맥, 컴포넌트, 주요 실행·장애 시퀀스 |
| [05-detailed-design.md](05-detailed-design.md) | Flow 상태, 그래프 규칙, 노드별 설정과 실행 알고리즘 |
| [06-interface-specification.md](06-interface-specification.md) | REST, HTTP, RabbitMQ 계약과 오류·전달 정책 |
| [07-data-and-cache-design.md](07-data-and-cache-design.md) | PostgreSQL 논리 모델, Redis 키, 정합성·만료 정책 |
| [08-security-and-operations.md](08-security-and-operations.md) | 인증 경계, 설정, 배포, 관측, 장애 대응 절차 |
| [09-test-plan-and-results.md](09-test-plan-and-results.md) | 테스트 전략, 현행 검증 결과, 미검증 범위, 출시 점검표 |
| [10-change-and-decision-log.md](10-change-and-decision-log.md) | 초기 기획 대비 변경점과 확정·보류 의사결정 |
| [11-open-issues-and-validation.md](11-open-issues-and-validation.md) | 미구현, 검증 필요, 확정 필요 항목과 우선순위 |
| [12-glossary.md](12-glossary.md) | 프로젝트 용어와 상태·전달 의미 |
| [13-ai-suggestion-node-deferred-proposal.md](13-ai-suggestion-node-deferred-proposal.md) | AI Suggestion Node 보류 근거, 재설계 선택지와 장단점, 재개 조건 |
| [14-composite-threshold-condition-analysis.md](14-composite-threshold-condition-analysis.md) | 복합 임계조건의 실제 지원 범위, cross-sensor 제약, Front·Engine 불일치와 개선안 |

## 6. 현행 범위 한눈에 보기

현재 엔진은 다음 두 실행 경로를 제공한다.

- 텔레메트리 실행: Core가 발행한 센서 패킷을 location 기준으로 분산 수신하고, `SENSOR` 또는 `LOCATION` Trigger Flow를 실행한다.
- 시간 실행: 모든 엔진 인스턴스가 ACTIVE Schedule을 로컬 등록하되 Redis 선점에 성공한 한 인스턴스만 `SCHEDULE → ACTUATOR_CONTROL` Flow를 실행한다.

현재 실행 가능한 NodeType은 `SENSOR`, `LOCATION`, `SCHEDULE`, `THRESHOLD`, `TIME_WINDOW`, `TIMER`, `ACTUATOR_CONTROL`, `ALERT`다. `EXTERNAL_NOTIFICATION`은 설정 타입만 있고 실행기가 없어 ACTIVE 전환이 거부된다.

## 7. 문서 유지 규칙

- 기능이 변경되면 요구사항, 상세 설계, 인터페이스, 추적표를 함께 갱신한다.
- 정책이 확정되지 않은 내용은 현행 요구사항에 섞지 않고 `11-open-issues-and-validation.md`에서 관리한다.
- 외부 서비스 계약을 바꾸면 생산자와 소비자 양쪽 저장소를 함께 검증한다.
- 코드가 없는 목표는 `구현`으로 표시하지 않는다.
- 운영 수치가 실측되지 않았다면 처리량·지연 SLO를 임의로 확정하지 않는다.
