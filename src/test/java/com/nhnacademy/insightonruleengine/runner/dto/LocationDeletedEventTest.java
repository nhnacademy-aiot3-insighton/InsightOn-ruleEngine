package com.nhnacademy.insightonruleengine.runner.dto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocationDeletedEventTest {

    @Test
    @DisplayName("Core의 양수 locationId 삭제 이벤트를 허용합니다")
    void validEventTest() {
        assertDoesNotThrow(() -> new LocationDeletedEvent(10L).validate());
    }

    @Test
    @DisplayName("비어 있거나 양수가 아닌 locationId는 거부합니다")
    void invalidEventTest() {
        assertThrows(IllegalArgumentException.class, () -> new LocationDeletedEvent(null).validate());
        assertThrows(IllegalArgumentException.class, () -> new LocationDeletedEvent(0L).validate());
    }
}
