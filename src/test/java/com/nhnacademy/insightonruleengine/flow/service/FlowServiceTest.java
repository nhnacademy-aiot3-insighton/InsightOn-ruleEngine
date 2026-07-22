package com.nhnacademy.insightonruleengine.flow.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
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
    @DisplayName("ACTIVE 상태 플로우 생성 테스트")
    void createActiveFlowTest() {
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
        Assertions.assertEquals(FlowStatus.ACTIVE, flowResponse.status());

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
}