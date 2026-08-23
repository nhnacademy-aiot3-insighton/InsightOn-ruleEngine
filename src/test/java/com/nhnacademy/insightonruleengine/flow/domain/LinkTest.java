package com.nhnacademy.insightonruleengine.flow.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.nhnacademy.insightonruleengine.flow.domain.Link;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LinkTest {

    @Test
    @DisplayName("Link 생성 시 입력값을 보존한다")
    void createLink() {
        Link link = new Link(1L, 10L, "out", 20L, "in");

        assertEquals(1L, link.getFlowId());
        assertEquals(10L, link.getSourceNodeId());
        assertEquals("out", link.getSourcePort());
        assertEquals(20L, link.getTargetNodeId());
        assertEquals("in", link.getTargetPort());
    }

    @Test
    @DisplayName("저장 전 Link는 서로 같은 객체로 취급하지 않는다")
    void transientLinkEquality() {
        Link source = new Link(1L, 10L, "out", 20L, "in");
        Link target = new Link(1L, 10L, "out", 20L, "in");

        assertNotEquals(source, target);
    }

    @Test
    @DisplayName("Link equals는 식별자가 있을 때 같은 ID를 기준으로 판단한다")
    void persistedLinkEquality() {
        Link source = new Link(1L, 10L, "out", 20L, "in");
        Link target = new Link(2L, 30L, "true", 40L, "in");
        ReflectionTestUtils.setField(source, "id", 10L);
        ReflectionTestUtils.setField(target, "id", 10L);

        assertEquals(source, target);
        assertEquals(source.hashCode(), target.hashCode());
    }
}
