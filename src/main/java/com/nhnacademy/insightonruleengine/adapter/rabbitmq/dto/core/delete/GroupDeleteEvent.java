package com.nhnacademy.insightonruleengine.adapter.rabbitmq.dto.core.delete;

import java.util.List;

public record GroupDeleteEvent(
        Long groupId,
        List<Long> locationIds) {
}
