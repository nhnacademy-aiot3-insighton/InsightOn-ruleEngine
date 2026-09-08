-- InsightOn Rule Engine의 engine schema에 1회 적용합니다.
-- 현재 애플리케이션은 Flyway 없이 ddl-auto=validate를 사용하므로 배포 전에 별도로 실행해야 합니다.
--
-- AI draft 갱신(내용이 바뀌면 기존 Flow를 archive하고 같은 이름으로 새로 만드는 흐름)을 지원하기
-- 위해, (group_id, location_id, name) 유니크 제약에서 ARCHIVED 상태를 제외한다. archive된 Flow는
-- 더 이상 이름을 점유하지 않으므로, 같은 이름의 새 Flow를 정상적으로 만들 수 있다. ARCHIVED가
-- 아닌 Flow끼리의 이름 충돌은 그대로 막는다.
--
-- 일반 UNIQUE 제약(ADD CONSTRAINT ... UNIQUE)은 조건절(WHERE)을 지원하지 않으므로, 부분 유니크
-- 인덱스(CREATE UNIQUE INDEX ... WHERE)로 대체한다.

begin;

alter table engine.flows
    drop constraint uk_flows_group_location_name;

create unique index uk_flows_group_location_name
    on engine.flows (group_id, location_id, name)
    where status <> 'ARCHIVED';

commit;
