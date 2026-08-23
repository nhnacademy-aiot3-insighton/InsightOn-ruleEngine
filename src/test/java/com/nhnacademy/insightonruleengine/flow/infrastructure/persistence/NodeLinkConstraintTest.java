package com.nhnacademy.insightonruleengine.node.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Link;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.infrastructure.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.NodeRepository;
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
     * 한 출력 Port가 둘 이상의 Target으로 연결되는 저장 오류를 막는다.
     */
    @Test
    @DisplayName("같은 Flow의 Source Node와 Port를 중복 저장할 수 없다")
    void rejectsDuplicateSourceNodePortInSameFlow() {
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

        Link duplicateLink = new Link(flow.getId(), source.getId(), "out", secondTarget.getId(), "in");
        assertThrows(
                DataIntegrityViolationException.class,
                () -> linkRepository.saveAndFlush(duplicateLink)
        );
    }

    /**
     * 애플리케이션 검증과 별개로 DB의 최종 방어선이 존재하는지 확인한다.
     */
    @Test
    @DisplayName("PostgreSQL에 Source Node와 Port UNIQUE 제약이 생성된다")
    void createsSourceNodePortUniqueConstraint() {
        Integer constraintCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from pg_constraint
                        where conname = 'uk_links_flow_source_port'
                          and contype = 'u'
                        """,
                Integer.class
        );

        assertEquals(1, constraintCount);
    }
}
