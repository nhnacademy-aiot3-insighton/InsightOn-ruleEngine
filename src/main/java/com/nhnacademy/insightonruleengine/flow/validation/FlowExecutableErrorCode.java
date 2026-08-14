package com.nhnacademy.insightonruleengine.flow.validation;

public enum FlowExecutableErrorCode implements FlowValidationErrorReason {

    UNSUPPORTED_NODE_EXECUTOR,
    INVALID_NODE_CONFIGURATION,
    INVALID_THRESHOLD_EXPRESSION
}
