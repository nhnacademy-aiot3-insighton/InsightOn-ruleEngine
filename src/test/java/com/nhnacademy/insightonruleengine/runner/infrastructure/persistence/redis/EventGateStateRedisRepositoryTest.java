package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class EventGateStateRedisRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RedisKeyFactory redisKeyFactory;

    private EventGateStateRedisRepository repository;

    @BeforeEach
    void setUp() {
        repository = new EventGateStateRedisRepository(redisTemplate, redisKeyFactory);
    }

    @Test
    @DisplayName("EVENT_GATE의 Count와 Cooldown Key를 함께 삭제한다")
    void deleteStates() {
        when(redisKeyFactory.count(10L, 100L)).thenReturn("count:10:100");
        when(redisKeyFactory.cooldown(10L, 100L)).thenReturn("cooldown:10:100");
        when(redisKeyFactory.count(10L, 200L)).thenReturn("count:10:200");
        when(redisKeyFactory.cooldown(10L, 200L)).thenReturn("cooldown:10:200");

        repository.deleteStates(10L, Set.of(100L, 200L));

        ArgumentCaptor<Collection<String>> keys = ArgumentCaptor.captor();
        verify(redisTemplate).delete(keys.capture());
        assertThat(keys.getValue()).containsExactlyInAnyOrder(
                "count:10:100",
                "cooldown:10:100",
                "count:10:200",
                "cooldown:10:200"
        );
    }

    @Test
    @DisplayName("정리할 EVENT_GATE가 없으면 Redis를 호출하지 않는다")
    void skipEmptyStates() {
        repository.deleteStates(10L, Set.of());

        verify(redisTemplate, never()).delete(anyCollection());
    }
}
