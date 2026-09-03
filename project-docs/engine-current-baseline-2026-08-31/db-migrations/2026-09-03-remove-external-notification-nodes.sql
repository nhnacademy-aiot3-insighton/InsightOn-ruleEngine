-- InsightOn Rule Engine의 engine schema에 1회 적용합니다.
-- 현재 애플리케이션은 Flyway 없이 ddl-auto=validate를 사용하므로 배포 전에 별도로 실행해야 합니다.
--
-- NodeType에서 EXTERNAL_NOTIFICATION을 제거했다. Node.nodeType은 @Enumerated(EnumType.STRING)이라
-- nodes.node_type에 이 값이 남아 있으면 해당 Node를 읽는 순간 enum 변환에 실패한다. Flow 상세 조회,
-- FlowDefinitionAssembler의 정의 조립, 그룹·장소 삭제 시 cleanup 경로가 모두 같은 지점에서 막히므로
-- 배포 전에 행을 지운다.
--
-- 이 Node가 든 Flow는 executor가 없어 ACTIVE가 된 적이 없다(활성화 검증이 UNSUPPORTED_NODE_EXECUTOR로
-- 항상 거부했다). 따라서 INACTIVE 또는 ARCHIVED 상태이며, 삭제로 실행 중인 자동화가 끊기지 않는다.
--
-- 해당 Node와 그 Node에 연결된 Link만 지우고 Flow 자체는 남긴다. 남은 Flow는 Action이 없거나 경로가
-- 끊긴 상태일 수 있는데, 이는 사용자가 ACTIVE로 바꾸려 할 때 활성화 검증이 구체적인 오류로 알려준다.
-- Flow를 통째로 지우면 사용자가 만든 나머지 Node 구성까지 사라지므로 그렇게 하지 않는다.
--
-- 적용 전 영향 범위 확인:
--   select f.flow_id, f.group_id, f.location_id, f.status, f.name,
--          count(n.node_id) as external_notification_nodes
--   from engine.flows f
--   join engine.nodes n on n.flow_id = f.flow_id
--   where n.node_type = 'EXTERNAL_NOTIFICATION'
--   group by f.flow_id, f.group_id, f.location_id, f.status, f.name
--   order by f.flow_id;
--
-- 적용 후 검증(0이어야 한다):
--   select count(*) from engine.nodes where node_type = 'EXTERNAL_NOTIFICATION';

begin;

delete from engine.links
where source_node_id in (
    select node_id from engine.nodes where node_type = 'EXTERNAL_NOTIFICATION'
)
   or target_node_id in (
    select node_id from engine.nodes where node_type = 'EXTERNAL_NOTIFICATION'
);

delete from engine.nodes
where node_type = 'EXTERNAL_NOTIFICATION';

commit;
