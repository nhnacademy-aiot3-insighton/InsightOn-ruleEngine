package com.nhnacademy.insightonruleengine.flow.dto;

import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;

public record FlowStatusChangeRequest(FlowStatus status) {
}
