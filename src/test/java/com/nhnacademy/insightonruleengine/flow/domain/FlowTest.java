package com.nhnacademy.insightonruleengine.flow.domain;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowTest {

    @Test
    @DisplayName("Flow 생성시 필요한 값 null 체크")
    void nullCheckTest() {
        Assertions.assertThrows(NullPointerException.class, () ->
                new Flow(null, 1L, "테스트", "테스트", FlowStatus.ACTIVE));
        Assertions.assertThrows(NullPointerException.class, () ->
                new Flow(1L, null, "테스트", "테스트", FlowStatus.ACTIVE));
        Assertions.assertThrows(NullPointerException.class, () ->
                new Flow(1L, 1L, null, "테스트", FlowStatus.ACTIVE));
        Assertions.assertThrows(NullPointerException.class, () ->
                new Flow(1L, 1L, "테스트", "테스트", null));
    }

    @Test
    @DisplayName("플로우 이름 검증")
    void flowNameTest() {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new Flow(1L, 1L, "", "테스트", FlowStatus.ACTIVE));
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new Flow(1L, 1L,
                        "테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트"
                                + "테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트"
                                + "테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트"
                                + "테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트"
                                + "테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트테스트"
                        , "테스트", FlowStatus.ACTIVE));
        Flow testFlow = new Flow(1L, 1L, " 테스트 ", "테스트", FlowStatus.ACTIVE);
        Assertions.assertEquals("테스트", testFlow.getName());
    }

    @Test
    @DisplayName("휴지통 검증")
    void archiveTest() {
        Flow testFlow = new Flow(1L, 1L, "테스트", "테스트", FlowStatus.ACTIVE);
        testFlow.archive();
        Assertions.assertEquals(FlowStatus.ARCHIVED, testFlow.getStatus());
        Assertions.assertEquals(1L, testFlow.getGroupId());
        Assertions.assertEquals(1L, testFlow.getLocationId());
        Assertions.assertEquals("테스트", testFlow.getName());
        Assertions.assertEquals("테스트", testFlow.getDescription());
    }

}