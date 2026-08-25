package com.nhnacademy.insightonruleengine.runner.model.action;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.Severity;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EngineAlertActionEventTest {

    @Test
    void acceptsValidEvent() {
        assertDoesNotThrow(() -> event(1L, 10L, 100L, "고온 경보", "온도가 기준을 초과했습니다.", Severity.CRITICAL));
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
        assertThrows(IllegalArgumentException.class,
                () -> event(1L, 10L, 100L, null, "메시지", Severity.CRITICAL));
        assertThrows(IllegalArgumentException.class,
                () -> event(1L, 10L, 100L, " ", "메시지", Severity.CRITICAL));
        assertThrows(IllegalArgumentException.class,
                () -> event(1L, 10L, 100L, "a".repeat(201), "메시지", Severity.CRITICAL));
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
