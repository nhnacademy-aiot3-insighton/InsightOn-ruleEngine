package com.nhnacademy.insightonruleengine.flow.exception;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;

public class DuplicateFlowNameException extends EngineException {

    public DuplicateFlowNameException(Long groupId, Long locationId, String name) {
        super(
                ErrorCode.FLOW_DUPLICATE_NAME,
                "플로우 이름이 이미 존재합니다: groupId=%d, locationId=%d, name=%s"
                        .formatted(groupId, locationId, name)
        );
    }
}
