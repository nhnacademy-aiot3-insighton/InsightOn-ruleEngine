package com.nhnacademy.insightonruleengine.flow.exception;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;

// FlowDefinitionIndex에서 노드와 링크 인덱스에서 생길 예외사항 모음집
public class DuplicateFlowDefinitionKeyException extends EngineException {

    // 중복 노드 아이디 검증을 위함
    public DuplicateFlowDefinitionKeyException(Long nodeId) {
        super(
                ErrorCode.FLOW_INVALID_DEFINITION,
                "FlowDefinition에 중복된 Node ID가 있습니다: nodeId=%d".formatted(nodeId)
        );
    }

    // 한 sourcePort가 여러곳으로 가는걸 방지하기 위함
    public DuplicateFlowDefinitionKeyException(Long sourceNodeId, String sourcePort) {
        super(
                ErrorCode.FLOW_INVALID_DEFINITION,
                "FlowDefinition에 중복된 Link Key가 있습니다: sourceNodeId=%d, sourcePort=%s"
                        .formatted(sourceNodeId, sourcePort)
        );
    }
}
