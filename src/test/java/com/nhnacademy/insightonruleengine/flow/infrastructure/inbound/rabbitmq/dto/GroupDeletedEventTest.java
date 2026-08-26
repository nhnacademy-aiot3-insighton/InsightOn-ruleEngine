package com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq.dto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupDeletedEventTest {

    @Test
    @DisplayName("Core의 groupId와 locationIds 그룹 삭제 이벤트를 허용합니다")
    void validEventTest() {
        GroupDeletedEvent event = new GroupDeletedEvent(10L, List.of(100L, 200L));

        assertDoesNotThrow(event::validate);
    }

    @Test
    @DisplayName("그룹 ID 또는 장소 ID 목록이 계약과 다르면 거부합니다")
    void invalidContractTest() {
        GroupDeletedEvent invalidGroupId = new GroupDeletedEvent(0L, List.of());
        GroupDeletedEvent missingLocations = new GroupDeletedEvent(10L, null);
        GroupDeletedEvent invalidLocationId = new GroupDeletedEvent(10L, List.of(0L));

        assertThrows(IllegalArgumentException.class, invalidGroupId::validate);
        assertThrows(IllegalArgumentException.class, missingLocations::validate);
        assertThrows(IllegalArgumentException.class, invalidLocationId::validate);
    }
}
