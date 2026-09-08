package com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nhnacademy.insightonruleengine.flow.application.cleanup.FlowCleanupService;
import com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq.dto.LocationDeletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationDeletedEventListenerTest {

    @Mock
    private FlowCleanupService cleanupService;

    private LocationDeletedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new LocationDeletedEventListener(cleanupService);
    }

    @Test
    @DisplayName("유효한 장소 삭제 이벤트는 장소 정리 Service에 전달합니다")
    void consumeValidEventTest() {
        listener.consume(new LocationDeletedEvent(10L));

        verify(cleanupService).cleanupByLocation(10L);
    }

    @Test
    @DisplayName("잘못된 장소 삭제 이벤트는 정리하지 않고 예외를 전파합니다")
    void rejectInvalidEventTest() {
        LocationDeletedEvent invalidEvent = new LocationDeletedEvent(0L);

        assertThrows(IllegalArgumentException.class, () -> listener.consume(invalidEvent));

        verify(cleanupService, never()).cleanupByLocation(0L);
    }
}
