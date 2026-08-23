package com.nhnacademy.insightonruleengine.runner.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class ActiveFlowRedisRepositoryTest {

    private static final Long GROUP_ID = 1L;
    private static final Long FLOW_ID = 2L;
    private static final String KEY = "active-flow:1:2";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RedisKeyFactory redisKeyFactory;

    private ActiveFlowRedisRepository repository;

    // 모든 테스트가 Redis와 직렬화 경계를 같은 대역으로 검증하도록 준비합니다.
    @BeforeEach
    void setUp() {
        repository = new ActiveFlowRedisRepository(redisTemplate, objectMapper, redisKeyFactory);
    }

    // JPA Entity가 아닌 불변 실행 모델의 JSON만 저장되는지 확인해줍니다.
    @Test
    @DisplayName("ACTIVE FlowDefinition을 계약된 Key에 JSON으로 저장한다")
    void saveActiveFlowTest() throws JsonProcessingException {
        FlowDefinition definition = definition(FLOW_ID, GROUP_ID, FlowStatus.ACTIVE);
        when(redisKeyFactory.activeFlow(GROUP_ID, FLOW_ID)).thenReturn(KEY);
        when(objectMapper.writeValueAsString(definition)).thenReturn("{\"flowId\":2}");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        repository.save(definition);

        verify(valueOperations).set(KEY, "{\"flowId\":2}");
    }

    // 실행할 수 없는 입력이 Redis에 저장되기 전에 거부되는지 검증합니다.
    @Test
    @DisplayName("null 또는 ACTIVE가 아닌 FlowDefinition은 저장하지 않는다")
    void invalidActiveFlowTest() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
        FlowDefinition inactiveDef = definition(FLOW_ID, GROUP_ID, FlowStatus.INACTIVE);
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save(inactiveDef)
        );
        FlowDefinition archivedDef = definition(FLOW_ID, GROUP_ID, FlowStatus.ARCHIVED);
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save(archivedDef)
        );
        verifyNoInteractions(redisTemplate, objectMapper, redisKeyFactory);
    }

    // 직렬화 실패가 Redis 연결 실패와 구분되는 예외로 변환되는지 검증합니다.
    @Test
    @DisplayName("FlowDefinition JSON 직렬화 실패를 Runtime 데이터 예외로 변환한다")
    void serializationFailureTest() throws JsonProcessingException {
        FlowDefinition definition = definition(FLOW_ID, GROUP_ID, FlowStatus.ACTIVE);
        when(redisKeyFactory.activeFlow(GROUP_ID, FLOW_ID)).thenReturn(KEY);
        when(objectMapper.writeValueAsString(definition))
                .thenThrow(new JsonProcessingException("직렬화 실패") {
                });

        InvalidActiveFlowDataException exception = assertThrows(
                InvalidActiveFlowDataException.class,
                () -> repository.save(definition)
        );

        assertTrue(exception.getCause() instanceof JsonProcessingException);
        verify(redisTemplate).opsForValue();
        verifyNoMoreInteractions(redisTemplate);
        verifyNoInteractions(valueOperations);
    }

    // 저장된 JSON이 요청한 Key와 일치하는 실행 모델로 복원되는지 확인해줍니다.
    @Test
    @DisplayName("Redis JSON을 요청한 ACTIVE FlowDefinition으로 조회한다")
    void findActiveFlowTest() throws JsonProcessingException {
        FlowDefinition definition = definition(FLOW_ID, GROUP_ID, FlowStatus.ACTIVE);
        when(redisKeyFactory.activeFlow(GROUP_ID, FLOW_ID)).thenReturn(KEY);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("{\"flowId\":2}");
        when(objectMapper.readValue("{\"flowId\":2}", FlowDefinition.class)).thenReturn(definition);

        assertEquals(definition, repository.getActiveFlow(GROUP_ID, FLOW_ID).orElseThrow());
    }

    // Redis MISS가 오류가 아닌 정상적인 빈 조회 결과로 처리되는지 검증합니다.
    @Test
    @DisplayName("Active Flow Key가 없거나 값이 비어 있으면 빈 Optional을 반환한다")
    void activeFlowMissTest() {
        when(redisKeyFactory.activeFlow(GROUP_ID, FLOW_ID)).thenReturn(KEY);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(null, "");

        assertTrue(repository.getActiveFlow(GROUP_ID, FLOW_ID).isEmpty());
        assertTrue(repository.getActiveFlow(GROUP_ID, FLOW_ID).isEmpty());
        verifyNoInteractions(objectMapper);
    }

    // 깨진 JSON과 Key 내부 ID 불일치를 같은 저장 데이터 오류 계약입니다.
    @Test
    @DisplayName("깨진 JSON 또는 Key와 다른 FlowDefinition을 Runtime 데이터 오류로 반환한다")
    void invalidStoredActiveFlowTest() throws JsonProcessingException {
        FlowDefinition mismatchedDefinition = definition(99L, GROUP_ID, FlowStatus.ACTIVE);
        when(redisKeyFactory.activeFlow(GROUP_ID, FLOW_ID)).thenReturn(KEY);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("broken", "mismatched");
        when(objectMapper.readValue("broken", FlowDefinition.class))
                .thenThrow(new JsonProcessingException("역직렬화 실패") {
                });
        when(objectMapper.readValue("mismatched", FlowDefinition.class)).thenReturn(mismatchedDefinition);

        assertThrows(
                InvalidActiveFlowDataException.class,
                () -> repository.getActiveFlow(GROUP_ID, FLOW_ID)
        );
        assertThrows(
                InvalidActiveFlowDataException.class,
                () -> repository.getActiveFlow(GROUP_ID, FLOW_ID)
        );
    }

    // Redis JSON에 groupId가 없어도 NullPointerException 대신 저장 데이터 오류로 처리되는지 검증합니다.
    @Test
    @DisplayName("groupId가 누락된 Redis FlowDefinition을 Runtime 데이터 오류로 반환한다")
    void missingGroupIdTest() throws JsonProcessingException {
        FlowDefinition definition = definition(FLOW_ID, null, FlowStatus.ACTIVE);
        when(redisKeyFactory.activeFlow(GROUP_ID, FLOW_ID)).thenReturn(KEY);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("missing-group-id");
        when(objectMapper.readValue("missing-group-id", FlowDefinition.class)).thenReturn(definition);

        assertThrows(
                InvalidActiveFlowDataException.class,
                () -> repository.getActiveFlow(GROUP_ID, FLOW_ID)
        );
    }

    // Redis JSON에 flowId가 없어도 NullPointerException 대신 저장 데이터 오류로 처리되는지 검증합니다.
    @Test
    @DisplayName("flowId가 누락된 Redis FlowDefinition을 Runtime 데이터 오류로 반환한다")
    void missingFlowIdTest() throws JsonProcessingException {
        FlowDefinition definition = definition(null, GROUP_ID, FlowStatus.ACTIVE);
        when(redisKeyFactory.activeFlow(GROUP_ID, FLOW_ID)).thenReturn(KEY);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("missing-flow-id");
        when(objectMapper.readValue("missing-flow-id", FlowDefinition.class)).thenReturn(definition);

        assertThrows(
                InvalidActiveFlowDataException.class,
                () -> repository.getActiveFlow(GROUP_ID, FLOW_ID)
        );
    }

    // 존재 확인과 삭제가 JSON 전체 조회 없이 정확한 Key만 사용하는지 검증합니다.
    @Test
    @DisplayName("Active Flow 존재 확인과 삭제는 정확한 Key를 사용한다")
    void existsAndDeleteTest() {
        when(redisKeyFactory.activeFlow(GROUP_ID, FLOW_ID)).thenReturn(KEY);
        when(redisTemplate.hasKey(KEY)).thenReturn(true, false);

        assertTrue(repository.exists(GROUP_ID, FLOW_ID));
        assertFalse(repository.exists(GROUP_ID, FLOW_ID));
        repository.delete(GROUP_ID, FLOW_ID);

        verify(redisTemplate).delete(KEY);
    }

    // 각 테스트가 필요한 상태와 ID만 바꿔 같은 실행 모델 형태를 사용하도록 생성합니다.
    private FlowDefinition definition(Long flowId, Long groupId, FlowStatus status) {
        return new FlowDefinition(
                flowId,
                groupId,
                3L,
                "온도 경고",
                "30도 이상 경고",
                status,
                OffsetDateTime.parse("2026-08-11T00:00:00Z"),
                List.of(),
                List.of()
        );
    }
}
