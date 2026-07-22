package com.nhnacademy.insightonruleengine.flow.exception;

public class DuplicateFlowNameException extends RuntimeException {
    public DuplicateFlowNameException(Long groupId, Long locationId, String name) {
        super("플로우 이름이 이미 존재합니다: groupId=%d, locationId=%d, name=%s"
                .formatted(groupId, locationId, name));
    }
}
