package com.nhnacademy.insightonruleengine.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EngineExceptionTest {

    @Test
    @DisplayName("EngineException은 errorCode null을 허용하지 않는다")
    void rNullErrorCodeTest() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new TestEngineException(null, "test")
        );

        assertEquals("errorCode must not be null", exception.getMessage());
    }

    private static class TestEngineException extends EngineException {

        private TestEngineException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }
    }
}
