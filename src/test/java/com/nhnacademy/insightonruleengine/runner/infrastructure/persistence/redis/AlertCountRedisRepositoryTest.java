package com.nhnacademy.insightonruleengine.runner.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.runner.redis.RedisKeyFactory;
import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class AlertCountRedisRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisKeyFactory redisKeyFactory;

    private AlertCountRedisRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AlertCountRedisRepository(redisTemplate, redisKeyFactory);
    }

    @Test
    @DisplayName("수집한 Action Node Id의 Count와 Cooldown key만 삭제합니다.")
    void deleteCountAndCooldownTest() {
        when(redisKeyFactory.count(10L, 100L)).thenReturn("count:10:100");
        when(redisKeyFactory.cooldown(10L, 100L)).thenReturn("cooldown:10:100");
        when(redisKeyFactory.count(10L, 200L)).thenReturn("count:10:200");
        when(redisKeyFactory.cooldown(10L, 200L)).thenReturn("cooldown:10:200");

        repository.deleteStates(10L, Set.of(100L, 200L));

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.captor();
        verify(redisTemplate).delete(captor.capture());

        assertThat(captor.getValue())
                .containsExactlyInAnyOrder(
                        "count:10:100",
                        "cooldown:10:100",
                        "count:10:200",
                        "cooldown:10:200"
                );
    }

    @Test
    @DisplayName("정리할 Action Node가 없으면 Redis를 호출하지 않습니다.")
    void emptyNodeTest() {
        repository.deleteStates(10L, Set.of());
        verify(redisTemplate, never()).delete(ArgumentMatchers.anyCollection());
    }
}
