-- FlowRepositoryTest 전용. Flow.java의 @Table은 (group_id, location_id, name) 유니크 제약을
-- 더 이상 선언하지 않는다(ARCHIVED를 제외해야 하는데 JPA가 부분 유니크 인덱스를 표현할 수 없어서).
-- 실제 제약은 project-docs/engine-current-baseline-2026-08-31/db-migrations/
-- 2026-09-03-flow-name-partial-unique.sql로 운영 DB에 직접 만든다. create-drop으로 만들어지는
-- 이 테스트 스키마에는 그 제약이 없으므로, 여기서 같은 조건의 인덱스를 재현해 실제 DB와 같은
-- 제약 하에서 테스트한다.
create unique index if not exists uk_flows_group_location_name
    on flows (group_id, location_id, name)
    where status <> 'ARCHIVED';
