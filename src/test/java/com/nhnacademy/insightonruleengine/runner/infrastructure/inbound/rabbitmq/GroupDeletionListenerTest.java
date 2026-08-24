package com.nhnacademy.insightonruleengine.runner.infrastructure.inbound.rabbitmq;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nhnacademy.insightonruleengine.flow.application.cleanup.GroupDeletionCleanupService;
import com.nhnacademy.insightonruleengine.runner.model.GroupDeletedEvent;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupDeletionListenerTest {

    @Mock
    private GroupDeletionCleanupService cleanupService;

    private GroupDeletionListener listener;

    @BeforeEach
    void setUp() {
        listener = new GroupDeletionListener(cleanupService);
    }

    @Test
    @DisplayName("유효한 그룹 삭제 이벤트는 정리 Service에 전달합니다")
    void consumeValidEventTest() {
        GroupDeletedEvent event = new GroupDeletedEvent(10L, List.of(100L));

        listener.consume(event);

        verify(cleanupService).cleanup(10L, List.of(100L));
    }

    @Test
    @DisplayName("잘못된 이벤트는 정리하지 않고 예외를 전파합니다")
    void rejectInvalidEventTest() {
        GroupDeletedEvent event = new GroupDeletedEvent(0L, List.of());

        assertThrows(IllegalArgumentException.class, () -> listener.consume(event));
        verifyNoInteractions(cleanupService);
    }

}
