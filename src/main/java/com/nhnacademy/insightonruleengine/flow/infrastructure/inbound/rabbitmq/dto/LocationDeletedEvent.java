package com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq.dto;

public record LocationDeletedEvent(Long locationId) {

    public void validate() {
        if (locationId == null || locationId <= 0L) {
            throw new IllegalArgumentException("locationId는 양수여야 합니다.");
        }
    }
}
