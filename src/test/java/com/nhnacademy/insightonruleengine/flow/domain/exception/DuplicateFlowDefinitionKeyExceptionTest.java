package com.nhnacademy.insightonruleengine.flow.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;
import com.nhnacademy.insightonruleengine.flow.domain.exception.DuplicateFlowDefinitionKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DuplicateFlowDefinitionKeyExceptionTest {

    @Test
    @DisplayName("중복 Node ID는 잘못된 실행 Definition 오류와 식별값을 제공한다")
    void duplicateNodeIdTest() {
        DuplicateFlowDefinitionKeyException exception =
                new DuplicateFlowDefinitionKeyException(10L);

        assertEquals(ErrorCode.FLOW_INVALID_DEFINITION, exception.getErrorCode());
        assertEquals(
                "FlowDefinition에 중복된 Node ID가 있습니다: nodeId=10",
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("중복 Link Key는 잘못된 실행 Definition 오류와 Source 위치를 제공한다")
    void duplicateLinkKeyTest() {
        DuplicateFlowDefinitionKeyException exception =
                new DuplicateFlowDefinitionKeyException(10L, "true");

        assertEquals(ErrorCode.FLOW_INVALID_DEFINITION, exception.getErrorCode());
        assertEquals(
                "FlowDefinition에 중복된 Link Key가 있습니다: sourceNodeId=10, sourcePort=true",
                exception.getMessage()
        );
    }
}
