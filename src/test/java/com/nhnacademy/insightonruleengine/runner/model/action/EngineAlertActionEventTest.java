package com.nhnacademy.insightonruleengine.runner.model.action;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.Severity;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EngineAlertActionEventTest {

    private static final UUID EVENT_ID = UUID.fromString("315efbba-2553-4d4d-bb67-f4f41a51f63a");

    @Test
    void acceptsValidEvent() {
        assertDoesNotThrow(() -> event(1L, 10L, 100L, "고온 경보", "온도가 기준을 초과했습니다.", Severity.CRITICAL));
    }

    @Test
    void rejectsNullEventId() {
        Map<String, Object> metrics = Map.of("temperature", 31.5);

        assertThrows(IllegalArgumentException.class, () -> new EngineAlertActionEvent(
                null,
                1L,
                10L,
                100L,
                "경보",
                "메시지",
                Severity.CRITICAL,
                metrics
        ));
    }

    @Test
    void serializesEventIdAsUuidString() {
        JsonNode json = new ObjectMapper().valueToTree(
                event(1L, 10L, 100L, "경보", "메시지", Severity.CRITICAL)
        );

        assertEquals(EVENT_ID.toString(), json.get("eventId").asText());
    }

    @Test
    void rejectsInvalidGroupId() {
        assertThrows(IllegalArgumentException.class,
                () -> event(null, 10L, 100L, "경보", "메시지", Severity.CRITICAL));
        assertThrows(IllegalArgumentException.class,
                () -> event(0L, 10L, 100L, "경보", "메시지", Severity.CRITICAL));
    }

    @Test
    void rejectsInvalidLocationId() {
        assertThrows(IllegalArgumentException.class,
                () -> event(1L, null, 100L, "경보", "메시지", Severity.CRITICAL));
        assertThrows(IllegalArgumentException.class,
                () -> event(1L, -1L, 100L, "경보", "메시지", Severity.CRITICAL));
    }

    @Test
    void rejectsInvalidFlowId() {
        assertThrows(IllegalArgumentException.class,
                () -> event(1L, 10L, null, "경보", "메시지", Severity.CRITICAL));
        assertThrows(IllegalArgumentException.class,
                () -> event(1L, 10L, 0L, "경보", "메시지", Severity.CRITICAL));
    }

    @Test
    void rejectsInvalidTitle() {
        String oversizedTitle = "a".repeat(201);

        assertThrows(IllegalArgumentException.class,
                () -> event(1L, 10L, 100L, null, "메시지", Severity.CRITICAL));
        assertThrows(IllegalArgumentException.class,
                () -> event(1L, 10L, 100L, " ", "메시지", Severity.CRITICAL));
        assertThrows(IllegalArgumentException.class,
                () -> event(1L, 10L, 100L, oversizedTitle, "메시지", Severity.CRITICAL));
    }

    @Test
    void rejectsInvalidMessageAndSeverity() {
        assertThrows(IllegalArgumentException.class,
                () -> event(1L, 10L, 100L, "경보", null, Severity.CRITICAL));
        assertThrows(IllegalArgumentException.class,
                () -> event(1L, 10L, 100L, "경보", " ", Severity.CRITICAL));
        assertThrows(IllegalArgumentException.class,
                () -> event(1L, 10L, 100L, "경보", "메시지", null));
    }

    private EngineAlertActionEvent event(
            Long groupId,
            Long locationId,
            Long flowId,
            String title,
            String message,
            Severity severity
    ) {
        return new EngineAlertActionEvent(
                EVENT_ID,
                groupId,
                locationId,
                flowId,
                title,
                message,
                severity,
                Map.of("temperature", 31.5)
        );
    }
}
