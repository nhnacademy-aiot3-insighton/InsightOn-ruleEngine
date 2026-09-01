package com.nhnacademy.insightonruleengine.flow.domain.exception;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;

public class LocationNotFoundException extends EngineException {

    public LocationNotFoundException(Long locationId) {
        super(
                ErrorCode.LOCATION_NOT_FOUND,
                "위치를 찾을 수 없습니다: locationId=%d".formatted(locationId)
        );
    }
}
