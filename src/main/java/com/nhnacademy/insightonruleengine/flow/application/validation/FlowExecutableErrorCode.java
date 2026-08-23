package com.nhnacademy.insightonruleengine.flow.application.validation;

import com.nhnacademy.insightonruleengine.flow.domain.FlowValidationErrorReason;

public enum FlowExecutableErrorCode implements FlowValidationErrorReason {

    UNSUPPORTED_NODE_EXECUTOR,
    INVALID_NODE_CONFIGURATION,
    INVALID_THRESHOLD_EXPRESSION
}
