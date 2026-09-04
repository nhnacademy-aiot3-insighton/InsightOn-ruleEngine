-- 주의: 이 스크립트는 운영 DB의 모든 Flow, Node, Link를 영구 삭제합니다.
-- TIMER 및 ALERT 내부 횟수/쿨다운을 제거하고 EVENT_GATE로 통합한 Backend 배포 직전에 1회 실행합니다.
-- Flyway를 사용하지 않고 ddl-auto=validate를 사용하므로 애플리케이션보다 DB를 먼저 전환해야 합니다.
--
-- 이전 수동 작업이 오류로 중단돼 현재 세션이 "current transaction is aborted" 상태일 수 있으므로
-- 첫 ROLLBACK으로 그 상태를 해제합니다. 진행 중인 트랜잭션이 없으면 PostgreSQL 경고만 발생합니다.

ROLLBACK;

BEGIN;

-- Redis에 남은 과거 runtime key와 새 Flow ID가 충돌하지 않도록 identity는 재시작하지 않습니다.
TRUNCATE TABLE engine.links, engine.nodes, engine.flows;

ALTER TABLE engine.nodes
    DROP CONSTRAINT IF EXISTS nodes_node_type_check;

ALTER TABLE engine.nodes
    ADD CONSTRAINT nodes_node_type_check
        CHECK (node_type IN (
            'SENSOR',
            'LOCATION',
            'SCHEDULE',
            'THRESHOLD',
            'TIME_WINDOW',
            'EVENT_GATE',
            'ACTUATOR_CONTROL',
            'ALERT'
        ));

-- 같은 출력 포트에서 여러 Action으로 분기할 수 있게 하되 완전히 같은 Link의 중복은 막습니다.
ALTER TABLE engine.links
    DROP CONSTRAINT IF EXISTS uk_links_flow_source_port;

ALTER TABLE engine.links
    DROP CONSTRAINT IF EXISTS uk_links_flow_source_port_target;

DROP INDEX IF EXISTS engine.uk_links_flow_source_port;
DROP INDEX IF EXISTS engine.uk_links_flow_source_port_target;

ALTER TABLE engine.links
    ADD CONSTRAINT uk_links_flow_source_port_target
        UNIQUE (flow_id, source_node_id, source_port, target_node_id, target_port);

-- 운영 DB마다 기존 객체가 UNIQUE 제약 또는 인덱스일 수 있어 두 형태를 모두 멱등하게 정리합니다.
ALTER TABLE engine.flows
    DROP CONSTRAINT IF EXISTS uk_flows_group_location_name;

DROP INDEX IF EXISTS engine.uk_flows_group_location_name;

CREATE UNIQUE INDEX uk_flows_group_location_name
    ON engine.flows (group_id, location_id, name)
    WHERE status <> 'ARCHIVED';

COMMIT;
