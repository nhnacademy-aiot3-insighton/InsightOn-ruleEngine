-- InsightOn Rule Engine의 engine schema에 1회 적용합니다.
-- 현재 애플리케이션은 Flyway 없이 ddl-auto=validate를 사용하므로 배포 전에 별도로 실행해야 합니다.

begin;

alter table engine.links
    add constraint uk_links_flow_source_port_target
        unique (flow_id, source_node_id, source_port, target_node_id, target_port);

alter table engine.links
    drop constraint uk_links_flow_source_port;

commit;
