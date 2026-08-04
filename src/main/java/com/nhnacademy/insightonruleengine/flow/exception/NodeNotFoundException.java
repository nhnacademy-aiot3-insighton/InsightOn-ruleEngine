package com.nhnacademy.insightonruleengine.flow.exception;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;

public class NodeNotFoundException extends EngineException {
    public NodeNotFoundException(Long nodeId) {
        super(
                ErrorCode.NODE_NOT_FOUND,
                "노드를 찾을 수 없습니다. nodeId=%d".formatted(nodeId)
        );
    }
}
