package com.nhnacademy.insightonruleengine.flow.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Link;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NodeLinkConstraintTest {

    @Autowired
    private FlowRepository flowRepository;

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Action 전용 fan-out을 위해 한 출력 Port가 서로 다른 Target으로 연결되는 것을 허용한다.
     */
    @Test
    @DisplayName("같은 Flow의 Source Node와 Port에서 서로 다른 Target으로 fan-out할 수 있다")
    void allowsActionFanOutLinksInSameFlow() {
        Flow flow = flowRepository.saveAndFlush(
                new Flow(1L, 10L, "제약 테스트", null, FlowStatus.INACTIVE)
        );
        Node source = nodeRepository.save(
                new Node(flow.getId(), NodeType.SENSOR, objectMapper.createObjectNode())
        );
        Node firstTarget = nodeRepository.save(
                new Node(flow.getId(), NodeType.ALERT, objectMapper.createObjectNode())
        );
        Node secondTarget = nodeRepository.save(
                new Node(flow.getId(), NodeType.ALERT, objectMapper.createObjectNode())
        );
        nodeRepository.flush();

        linkRepository.saveAndFlush(
                new Link(flow.getId(), source.getId(), "out", firstTarget.getId(), "in")
        );
        linkRepository.saveAndFlush(
                new Link(flow.getId(), source.getId(), "out", secondTarget.getId(), "in")
        );

        assertEquals(2, linkRepository.findByFlowId(flow.getId()).size());
    }

    @Test
    @DisplayName("출발·도착 Node와 Port가 모두 같은 링크는 중복 저장할 수 없다")
    void rejectsExactDuplicateLinkInSameFlow() {
        Flow flow = flowRepository.saveAndFlush(
                new Flow(1L, 10L, "중복 링크 테스트", null, FlowStatus.INACTIVE)
        );
        Node source = nodeRepository.save(
                new Node(flow.getId(), NodeType.SENSOR, objectMapper.createObjectNode())
        );
        Node target = nodeRepository.save(
                new Node(flow.getId(), NodeType.ALERT, objectMapper.createObjectNode())
        );
        nodeRepository.flush();

        linkRepository.saveAndFlush(
                new Link(flow.getId(), source.getId(), "out", target.getId(), "in")
        );

        Link duplicateLink = new Link(flow.getId(), source.getId(), "out", target.getId(), "in");
        assertThrows(
                DataIntegrityViolationException.class,
                () -> linkRepository.saveAndFlush(duplicateLink)
        );
    }

    /**
     * 애플리케이션 검증과 별개로 정확히 같은 링크에 대한 DB 최종 방어선이 존재하는지 확인한다.
     */
    @Test
    @DisplayName("PostgreSQL에 전체 Link 경로 UNIQUE 제약이 생성된다")
    void createsLinkPathUniqueConstraint() {
        Integer constraintCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from pg_constraint
                        where conname = 'uk_links_flow_source_port_target'
                          and contype = 'u'
                        """,
                Integer.class
        );

        assertEquals(1, constraintCount);
    }
}
