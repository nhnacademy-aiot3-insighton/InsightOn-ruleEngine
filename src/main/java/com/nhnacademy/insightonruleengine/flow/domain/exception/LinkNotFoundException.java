package com.nhnacademy.insightonruleengine.flow.domain.exception;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;

public class LinkNotFoundException extends EngineException {
    public LinkNotFoundException(Long sourceNodeId, String sourcePort) {
        super(
                ErrorCode.LINK_NOT_FOUND,
                "링크를 찾을 수 없습니다. sourceNodeId=%d, sourcePort=%s".formatted(sourceNodeId, sourcePort)
        );
    }
}
