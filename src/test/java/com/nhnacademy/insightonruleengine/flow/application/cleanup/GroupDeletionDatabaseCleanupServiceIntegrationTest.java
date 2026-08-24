package com.nhnacademy.insightonruleengine.flow.application.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Link;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.NodeRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(GroupDeletionDatabaseCleanupService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GroupDeletionDatabaseCleanupServiceIntegrationTest {

    private static final long TARGET_GROUP_ID = 7001L;
    private static final long OTHER_GROUP_ID = 7002L;

    @Autowired
    private GroupDeletionDatabaseCleanupService cleanupService;
    @Autowired
    private FlowRepository flowRepository;
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private LinkRepository linkRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUpDatabase() {
        jdbcTemplate.execute("drop trigger if exists reject_group_flow_delete_trigger on flows");
        jdbcTemplate.execute("drop function if exists reject_group_flow_delete()");
        cleanupService.deleteByGroupId(TARGET_GROUP_ID);
        cleanupService.deleteByGroupId(OTHER_GROUP_ID);
    }

    @Test
    @DisplayName("ACTIVE, INACTIVE, ARCHIVED Flow의 Link, Node, Flow를 그룹 단위로 삭제합니다")
    void deleteAllStatusesByGroupTest() {
        Flow active = saveConfiguration(TARGET_GROUP_ID, 10L, "active", FlowStatus.ACTIVE);
        Flow inactive = saveConfiguration(TARGET_GROUP_ID, 20L, "inactive", FlowStatus.INACTIVE);
        Flow archived = saveConfiguration(TARGET_GROUP_ID, 30L, "archived", FlowStatus.ARCHIVED);
        Flow other = saveConfiguration(OTHER_GROUP_ID, 40L, "other", FlowStatus.ACTIVE);

        cleanupService.deleteByGroupId(TARGET_GROUP_ID);

        assertEquals(List.of(), flowRepository.findAllByGroupId(TARGET_GROUP_ID));
        assertEquals(0, nodeRepository.findByFlowId(active.getId()).size());
        assertEquals(0, nodeRepository.findByFlowId(inactive.getId()).size());
        assertEquals(0, nodeRepository.findByFlowId(archived.getId()).size());
        assertEquals(0, linkRepository.findByFlowId(active.getId()).size());
        assertEquals(1, flowRepository.findAllByGroupId(OTHER_GROUP_ID).size());
        assertEquals(2, nodeRepository.findByFlowId(other.getId()).size());
        assertEquals(1, linkRepository.findByFlowId(other.getId()).size());
    }

    @Test
    @DisplayName("같은 locationId에 속한 모든 그룹의 Link, Node, Flow만 삭제합니다")
    void deleteByLocationTest() {
        Flow first = saveConfiguration(TARGET_GROUP_ID, 50L, "first-location", FlowStatus.ACTIVE);
        Flow second = saveConfiguration(OTHER_GROUP_ID, 50L, "second-location", FlowStatus.ARCHIVED);
        Flow untouched = saveConfiguration(TARGET_GROUP_ID, 60L, "untouched", FlowStatus.INACTIVE);

        cleanupService.deleteByLocationId(50L);

        assertEquals(List.of(), flowRepository.findAllByLocationId(50L));
        assertEquals(0, nodeRepository.findByFlowId(first.getId()).size());
        assertEquals(0, nodeRepository.findByFlowId(second.getId()).size());
        assertEquals(0, linkRepository.findByFlowId(first.getId()).size());
        assertEquals(0, linkRepository.findByFlowId(second.getId()).size());
        assertEquals(1, flowRepository.findAllByLocationId(60L).size());
        assertEquals(2, nodeRepository.findByFlowId(untouched.getId()).size());
        assertEquals(1, linkRepository.findByFlowId(untouched.getId()).size());
    }

    @Test
    @DisplayName("Flow 삭제 단계가 실패하면 앞선 Link와 Node 삭제도 함께 롤백합니다")
    void rollbackAllDatabaseDeletesTest() {
        Flow flow = saveConfiguration(TARGET_GROUP_ID, 10L, "rollback", FlowStatus.ACTIVE);
        jdbcTemplate.execute("""
                create or replace function reject_group_flow_delete() returns trigger as $$
                begin
                    if old.group_id = 7001 then
                        raise exception 'forced flow delete failure';
                    end if;
                    return old;
                end;
                $$ language plpgsql
                """);
        //위 SQL에서 테스트 실행 중 동적으로 생성하는 PostgreSQL 함수입니다.
        //noinspection SqlResolve
        jdbcTemplate.execute("""
                create trigger reject_group_flow_delete_trigger
                before delete on flows
                for each row execute function reject_group_flow_delete()
                """);

        assertThrows(RuntimeException.class, () -> cleanupService.deleteByGroupId(TARGET_GROUP_ID));

        assertEquals(1, flowRepository.findAllByGroupId(TARGET_GROUP_ID).size());
        assertEquals(2, nodeRepository.findByFlowId(flow.getId()).size());
        assertEquals(1, linkRepository.findByFlowId(flow.getId()).size());
    }

    private Flow saveConfiguration(Long groupId, Long locationId, String name, FlowStatus status) {
        Flow flow = flowRepository.saveAndFlush(new Flow(groupId, locationId, name, null, status));
        Node source = nodeRepository.save(
                new Node(flow.getId(), NodeType.LOCATION, JsonNodeFactory.instance.objectNode())
        );
        Node target = nodeRepository.save(
                new Node(flow.getId(), NodeType.ALERT, JsonNodeFactory.instance.objectNode())
        );
        nodeRepository.flush();
        linkRepository.saveAndFlush(new Link(flow.getId(), source.getId(), "out", target.getId(), "in"));
        return flow;
    }
}
