package com.nhnacademy.insightonruleengine.runner.messaging;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nhnacademy.insightonruleengine.flow.cleanup.GroupDeletionCleanupService;
import com.nhnacademy.insightonruleengine.runner.dto.LocationDeletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationDeletionListenerTest {

    @Mock
    private GroupDeletionCleanupService cleanupService;

    private LocationDeletionListener listener;

    @BeforeEach
    void setUp() {
        listener = new LocationDeletionListener(cleanupService);
    }

    @Test
    @DisplayName("유효한 장소 삭제 이벤트는 장소 정리 Service에 전달합니다")
    void consumeValidEventTest() {
        listener.consume(new LocationDeletedEvent(10L));

        verify(cleanupService).cleanupLocation(10L);
    }

    @Test
    @DisplayName("잘못된 장소 삭제 이벤트는 정리하지 않고 예외를 전파합니다")
    void rejectInvalidEventTest() {
        assertThrows(IllegalArgumentException.class, () -> listener.consume(new LocationDeletedEvent(0L)));

        verify(cleanupService, never()).cleanupLocation(0L);
    }
}
