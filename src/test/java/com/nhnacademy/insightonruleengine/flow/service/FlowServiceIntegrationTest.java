package com.nhnacademy.insightonruleengine.flow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.dto.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FlowService.class)
class FlowServiceIntegrationTest {

    @Autowired
    private FlowService flowService;

    @Autowired
    private FlowRepository flowRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Flow 수정은 기존 Flow를 보관하고 새 ID의 INACTIVE Flow를 저장한다")
    void updateCreatesNewFlowId() {
        Flow currentFlow = flowRepository.saveAndFlush(
                new Flow(1L, 10L, "온도 경고 v1", null, FlowStatus.ACTIVE)
        );
        Long currentFlowId = currentFlow.getId();

        FlowResponse response = flowService.update(
                1L,
                currentFlowId,
                new FlowUpdateRequest(" 온도 경고 v2 ", "수정 설명")
        );
        entityManager.flush();
        entityManager.clear();

        Flow archivedFlow = flowRepository.findById(currentFlowId).orElseThrow();
        Flow updatedFlow = flowRepository.findById(response.flowId()).orElseThrow();

        assertFalse(currentFlowId.equals(response.flowId()));
        assertEquals(FlowStatus.ARCHIVED, archivedFlow.getStatus());
        assertEquals(FlowStatus.INACTIVE, updatedFlow.getStatus());
        assertEquals(10L, updatedFlow.getLocationId());
        assertEquals("온도 경고 v2", updatedFlow.getName());
    }

    @Test
    @DisplayName("Flow 복구는 새 행 없이 기존 ID를 INACTIVE로 전환한다")
    void restoreKeepsExistingFlowId() {
        Flow archivedFlow = flowRepository.saveAndFlush(
                new Flow(1L, 10L, "온도 경고", null, FlowStatus.ARCHIVED)
        );
        Long archivedFlowId = archivedFlow.getId();
        long beforeCount = flowRepository.count();

        FlowResponse response = flowService.restore(1L, archivedFlowId);
        entityManager.flush();
        entityManager.clear();

        Flow restoredFlow = flowRepository.findById(archivedFlowId).orElseThrow();

        assertEquals(beforeCount, flowRepository.count());
        assertEquals(archivedFlowId, response.flowId());
        assertEquals(FlowStatus.INACTIVE, restoredFlow.getStatus());
    }

    @Test
    @DisplayName("Flow 수정 검증 실패 시 기존 Flow 상태를 유지한다")
    void failedUpdateKeepsCurrentFlow() {
        Flow currentFlow = flowRepository.save(
                new Flow(1L, 10L, "온도 경고 v1", null, FlowStatus.ACTIVE)
        );
        flowRepository.saveAndFlush(
                new Flow(1L, 10L, "온도 경고 v2", null, FlowStatus.ARCHIVED)
        );
        Long currentFlowId = currentFlow.getId();

        assertThrows(
                DuplicateFlowNameException.class,
                () -> flowService.update(
                        1L,
                        currentFlowId,
                        new FlowUpdateRequest("온도 경고 v2", null)
                )
        );
        entityManager.flush();
        entityManager.clear();

        Flow unchangedFlow = flowRepository.findById(currentFlowId).orElseThrow();

        assertEquals(FlowStatus.ACTIVE, unchangedFlow.getStatus());
        assertEquals(2L, flowRepository.count());
    }
}
