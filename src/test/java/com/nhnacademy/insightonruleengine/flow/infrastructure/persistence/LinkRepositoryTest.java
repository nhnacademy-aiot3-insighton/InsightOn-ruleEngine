package com.nhnacademy.insightonruleengine.node.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhnacademy.insightonruleengine.flow.domain.Link;
import com.nhnacademy.insightonruleengine.flow.infrastructure.LinkRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LinkRepositoryTest {

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Link를 저장하고 ID로 조회한다")
    void saveAndFindById() {
        Link savedLink = linkRepository.saveAndFlush(new Link(1L, 10L, "out", 20L, "in"));
        entityManager.clear();

        Link foundLink = linkRepository.findById(savedLink.getId()).orElseThrow();

        assertEquals(1L, foundLink.getFlowId());
        assertEquals(10L, foundLink.getSourceNodeId());
        assertEquals("out", foundLink.getSourcePort());
        assertEquals(20L, foundLink.getTargetNodeId());
        assertEquals("in", foundLink.getTargetPort());
    }

    @Test
    @DisplayName("flowId가 일치하는 Link만 조회한다")
    void findByFlowId() {
        Link firstLink = linkRepository.save(new Link(1L, 10L, "out", 20L, "in"));
        Link secondLink = linkRepository.save(new Link(1L, 20L, "true", 30L, "in"));
        Link otherFlowLink = linkRepository.save(new Link(2L, 40L, "out", 50L, "in"));
        linkRepository.flush();
        entityManager.clear();

        List<Link> links = linkRepository.findByFlowId(1L);
        List<Link> otherFlowLinks = linkRepository.findByFlowId(2L);

        assertEquals(2, links.size());
        List<Long> linkIds = links.stream().map(Link::getId).toList();
        assertTrue(linkIds.containsAll(List.of(firstLink.getId(), secondLink.getId())));
        assertEquals(1, otherFlowLinks.size());
        assertEquals(otherFlowLink.getId(), otherFlowLinks.getFirst().getId());
    }

    @Test
    @DisplayName("flowId가 일치하는 Link만 삭제한다")
    void deleteByFlowId() {
        linkRepository.save(new Link(1L, 10L, "out", 20L, "in"));
        linkRepository.save(new Link(1L, 20L, "true", 30L, "in"));
        Link otherFlowLink = linkRepository.save(new Link(2L, 40L, "out", 50L, "in"));
        linkRepository.flush();
        entityManager.clear();

        linkRepository.deleteByFlowId(1L);
        entityManager.flush();
        entityManager.clear();

        assertEquals(0, linkRepository.findByFlowId(1L).size());
        assertEquals(otherFlowLink.getId(), linkRepository.findByFlowId(2L).getFirst().getId());
    }
}
