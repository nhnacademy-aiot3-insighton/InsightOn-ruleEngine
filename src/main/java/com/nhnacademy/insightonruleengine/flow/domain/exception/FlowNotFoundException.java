package com.nhnacademy.insightonruleengine.flow.domain.exception;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;

public class FlowNotFoundException extends EngineException {

    public FlowNotFoundException(Long groupId, Long flowId) {
        super(
                ErrorCode.FLOW_NOT_FOUND,
                "플로우를 찾을 수 없습니다: groupId=%d, flowId=%d".formatted(groupId, flowId)
        );
    }
}
