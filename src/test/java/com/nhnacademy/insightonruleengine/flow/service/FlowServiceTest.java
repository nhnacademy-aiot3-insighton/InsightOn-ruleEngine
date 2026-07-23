package com.nhnacademy.insightonruleengine.flow.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlowServiceTest {

    @Mock
    FlowRepository flowRepository;

    @InjectMocks
    FlowService flowService;

    @Test
    @DisplayName("INACTIVE 상태 플로우 생성 테스트")
    void createInactiveFlowTest() {
        FlowCreateRequest flowCreateRequest = new FlowCreateRequest(2L, " 온도 알람 ", "온도가 너무 높아요");
        when(flowRepository.existsByGroupIdAndLocationIdAndName(
                1L,
                2L,
                "온도 알람"
        )).thenReturn(false);
        when(flowRepository.save(any(Flow.class))).thenAnswer(flow -> flow.getArgument(0));

        FlowResponse flowResponse = flowService.create(1L, flowCreateRequest);
        verify(flowRepository).save(any(Flow.class));
        Assertions.assertEquals(1L, flowResponse.groupId());
        Assertions.assertEquals(2L, flowResponse.locationId());
        Assertions.assertEquals("온도 알람", flowResponse.name());
        Assertions.assertEquals("온도가 너무 높아요", flowResponse.description());
        Assertions.assertEquals(FlowStatus.INACTIVE, flowResponse.status());

    }

    @Test
    @DisplayName("동일 플로우 이름 중복 테스트")
    void sameFlowNameTest() {
        FlowCreateRequest flowCreateRequest = new FlowCreateRequest(1L, "온도", "온도");
        when(flowRepository.existsByGroupIdAndLocationIdAndName(
                1L,
                1L,
                "온도"
        )).thenReturn(true);
        Assertions.assertThrows(DuplicateFlowNameException.class, () -> flowService.create(
                1L,
                flowCreateRequest
        ));
    }

    @Test
    @DisplayName("ACTIVE ↔ INACTIVE 상태 전환 테스트")
    void changeStatusTest() {
        Flow flow = new Flow(1L, 1L, "기존 Flow", "기존 설명", FlowStatus.ACTIVE);
        when(flowRepository.findById(1L)).thenReturn(Optional.of(flow));
        FlowStatusChangeRequest request = new FlowStatusChangeRequest(FlowStatus.INACTIVE);

        FlowResponse response = flowService.changeActivationStatus(1L, 1L, request);

        Assertions.assertEquals(FlowStatus.INACTIVE, flow.getStatus());
        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());

        FlowStatusChangeRequest activeRequest =
                new FlowStatusChangeRequest(FlowStatus.ACTIVE);

        FlowResponse activeResponse =
                flowService.changeActivationStatus(1L, 1L, activeRequest);

        Assertions.assertEquals(FlowStatus.ACTIVE, flow.getStatus());
        Assertions.assertEquals(FlowStatus.ACTIVE, activeResponse.status());

    }

    @Test
    @DisplayName("Flow 수정 시 기존 Flow를 보관하고 새 Flow를 INACTIVE로 생성한다")
    void updateFlowInactiveTest() {
        Flow currentFlow = new Flow(1L, 1L, "기존 Flow", "기존 설명", FlowStatus.ACTIVE);
        FlowCreateRequest request = new FlowCreateRequest(1L, "수정 Flow", "수정 설명");
        when(flowRepository.findById(1L)).thenReturn(Optional.of(currentFlow));
        when(flowRepository.existsByGroupIdAndLocationIdAndName(
                1L,
                1L,
                "수정 Flow"
        )).thenReturn(false);
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FlowResponse response = flowService.update(1L, 1L, request);

        Assertions.assertEquals(FlowStatus.ARCHIVED, currentFlow.getStatus());
        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());
    }

    @Test
    @DisplayName("INACTIVE Flow를 보관하고 기존 ARCHIVED Flow를 INACTIVE로 복구한다")
    void restoreArchivedFlowInactiveTest() {
        Flow currentFlow = new Flow(1L, 1L, "현재 Flow", null, FlowStatus.INACTIVE);
        Flow archivedFlow = new Flow(1L, 1L, "이전 Flow", null, FlowStatus.ARCHIVED);
        when(flowRepository.findById(1L)).thenReturn(Optional.of(currentFlow));
        when(flowRepository.findById(2L)).thenReturn(Optional.of(archivedFlow));

        FlowResponse response = flowService.restore(1L, 1L, 2L);

        Assertions.assertEquals(FlowStatus.ARCHIVED, currentFlow.getStatus());
        Assertions.assertEquals(FlowStatus.INACTIVE, archivedFlow.getStatus());
        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());
    }
}
