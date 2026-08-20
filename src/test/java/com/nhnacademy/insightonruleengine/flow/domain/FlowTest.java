package com.nhnacademy.insightonruleengine.flow.domain;


import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowStatusTransitionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowTest {

    @Test
    @DisplayName("Flow 생성시 필요한 값 null 체크")
    void nullCheckTest() {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new Flow(null, 1L, "테스트", "테스트", FlowStatus.ACTIVE));
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new Flow(1L, null, "테스트", "테스트", FlowStatus.ACTIVE));
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new Flow(1L, 1L, null, "테스트", FlowStatus.ACTIVE));
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new Flow(1L, 1L, "테스트", "테스트", null));
    }

    @Test
    @DisplayName("플로우 이름 검증")
    void flowNameTest() {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new Flow(1L, 1L, "", "테스트", FlowStatus.ACTIVE));
        String longName = "가".repeat(101);
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new Flow(1L, 1L, longName, "테스트", FlowStatus.ACTIVE));
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

    // 이미 보관된 Flow에 대한 중복 요청이 성공으로 숨겨지지 않는지 확인합니다.
    @Test
    @DisplayName("이미 보관된 Flow는 다시 보관할 수 없다")
    void rejectArchivedFlowArchiveTest() {
        Flow flow = new Flow(1L, 1L, "테스트", null, FlowStatus.ARCHIVED);

        assertThrows(InvalidFlowStatusTransitionException.class, flow::archive);

        Assertions.assertEquals(FlowStatus.ARCHIVED, flow.getStatus());
    }

    @Test
    @DisplayName("ERROR Flow는 수정을 위해 보관할 수 있다")
    void errorFlowCanBeArchived() {
        Flow flow = new Flow(1L, 1L, "테스트", null, FlowStatus.ERROR);

        flow.archive();

        Assertions.assertEquals(FlowStatus.ARCHIVED, flow.getStatus());
    }

    @Test
    @DisplayName("보관된 Flow를 INACTIVE 상태로 복구한다")
    void restoreFlowAsInactive() {
        Flow flow = new Flow(1L, 1L, "테스트", "테스트", FlowStatus.ARCHIVED);

        flow.restore();

        Assertions.assertEquals(FlowStatus.INACTIVE, flow.getStatus());
    }

    @Test
    @DisplayName("같은 상태나 ARCHIVED 상태로 일반 변경할 수 없다")
    void rejectInvalidActivationStatus() {
        Flow flow = new Flow(1L, 1L, "테스트", null, FlowStatus.ACTIVE);

        assertThrows(
                InvalidFlowStatusTransitionException.class,
                () -> flow.changeActivationStatus(FlowStatus.ACTIVE));
        assertThrows(
                InvalidFlowStatusTransitionException.class,
                () -> flow.changeActivationStatus(FlowStatus.ARCHIVED));

        Assertions.assertEquals(FlowStatus.ACTIVE, flow.getStatus());
    }

    @Test
    @DisplayName("ARCHIVED가 아닌 Flow는 복구할 수 없다")
    void rejectNonArchivedRestore() {
        Flow flow = new Flow(1L, 1L, "테스트", null, FlowStatus.INACTIVE);

        assertThrows(InvalidFlowStatusTransitionException.class, flow::restore);

        Assertions.assertEquals(FlowStatus.INACTIVE, flow.getStatus());
    }
}
