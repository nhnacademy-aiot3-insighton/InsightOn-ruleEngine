package com.nhnacademy.insightonruleengine.runner.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class FlowRouteRedisRepositoryTest {

    private static final Long GROUP_ID = 1L;
    private static final Long LOCATION_ID = 2L;
    private static final String KEY = "route:1:2";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private RedisKeyFactory redisKeyFactory;

    private FlowRouteRedisRepository repository;

    // 각 테스트가 같은 Redis Route 경계를 검증하도록 Repository를 준비합니다.
    @BeforeEach
    void setUp() {
        repository = new FlowRouteRedisRepository(redisTemplate, redisKeyFactory);
    }

    // 기존 목록 삭제와 새 목록 저장이 하나의 원자 연산으로 요청되는지 검증합니다.
    @Test
    @DisplayName("Location의 ACTIVE Flow ID 집합을 하나의 Redis Script로 교체한다")
    void replaceRouteTest() {
        when(redisKeyFactory.route(GROUP_ID, LOCATION_ID)).thenReturn(KEY);

        repository.replace(GROUP_ID, LOCATION_ID, Set.of(10L, 20L));

        ArgumentCaptor<Object> flowIdCaptor = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(KEY)),
                flowIdCaptor.capture(),
                flowIdCaptor.capture()
        );
        assertEquals(Set.of("10", "20"), Set.copyOf(flowIdCaptor.getAllValues()));
    }

    // 빈 집합도 기존 Route를 삭제하는 원자 교체로 실행되는지 확인합니다.
    @Test
    @DisplayName("빈 Flow ID 집합으로 교체하면 기존 Route를 제거하는 Script를 실행한다")
    void replaceEmptyRouteTest() {
        when(redisKeyFactory.route(GROUP_ID, LOCATION_ID)).thenReturn(KEY);

        repository.replace(GROUP_ID, LOCATION_ID, Set.of());

        verify(redisTemplate).execute(ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(KEY)));
    }

    // 잘못된 Flow ID가 Redis Route에 기록되기 전에 모두 거부되는지 검증합니다.
    @Test
    @DisplayName("Route의 Flow ID 집합은 null이 아니고 모든 값이 양수여야 한다")
    void invalidFlowIdsTest() {
        assertThrows(IllegalArgumentException.class, () -> repository.replace(GROUP_ID, LOCATION_ID, null));
        Set<Long> zeroSet = Set.of(0L);
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.replace(GROUP_ID, LOCATION_ID, zeroSet)
        );
        Set<Long> negativeSet = Set.of(-1L);
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.replace(GROUP_ID, LOCATION_ID, negativeSet)
        );
        verifyNoInteractions(redisTemplate, redisKeyFactory);
    }

    // Redis Set 문자열이 실행에 사용할 Long ID 집합으로 복원되는지 확인합니다.
    @Test
    @DisplayName("Location Route의 문자열 값을 ACTIVE Flow ID 집합으로 조회한다")
    void findFlowIdsTest() {
        when(redisKeyFactory.route(GROUP_ID, LOCATION_ID)).thenReturn(KEY);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(KEY)).thenReturn(Set.of("10", "20"));

        assertEquals(Set.of(10L, 20L), repository.findFlowIds(GROUP_ID, LOCATION_ID));
    }

    // Route MISS가 실행 오류가 아닌 빈 Flow 목록으로 처리되는지 검증합니다.
    @Test
    @DisplayName("Route Key가 없거나 비어 있으면 빈 집합을 반환한다")
    void routeMissTest() {
        when(redisKeyFactory.route(GROUP_ID, LOCATION_ID)).thenReturn(KEY);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(KEY))
                .thenReturn(null)
                .thenReturn(Set.of());

        assertEquals(Set.of(), repository.findFlowIds(GROUP_ID, LOCATION_ID));
        assertEquals(Set.of(), repository.findFlowIds(GROUP_ID, LOCATION_ID));
    }

    // Redis의 깨진 Route 값을 연결 장애와 구분되는 데이터 예외로 반환하는지 확인합니다.
    @Test
    @DisplayName("숫자가 아니거나 양수가 아닌 Route 값을 Runtime 데이터 오류로 반환한다")
    void invalidStoredFlowIdTest() {
        when(redisKeyFactory.route(GROUP_ID, LOCATION_ID)).thenReturn(KEY);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(KEY))
                .thenReturn(Set.of("broken"))
                .thenReturn(Set.of("0"));

        assertThrows(
                InvalidRouteDataException.class,
                () -> repository.findFlowIds(GROUP_ID, LOCATION_ID)
        );
        assertThrows(
                InvalidRouteDataException.class,
                () -> repository.findFlowIds(GROUP_ID, LOCATION_ID)
        );
    }

    // 존재 확인과 삭제가 Set 전체를 읽지 않고 정확한 Route Key만 사용하는지 검증합니다.
    @Test
    @DisplayName("Route 존재 확인과 삭제는 정확한 Key를 사용한다")
    void existsAndDeleteTest() {
        when(redisKeyFactory.route(GROUP_ID, LOCATION_ID)).thenReturn(KEY);
        when(redisTemplate.hasKey(KEY)).thenReturn(true, false);

        assertTrue(repository.exists(GROUP_ID, LOCATION_ID));
        assertFalse(repository.exists(GROUP_ID, LOCATION_ID));
        repository.delete(GROUP_ID, LOCATION_ID);

        verify(redisTemplate).delete(KEY);
    }
}
