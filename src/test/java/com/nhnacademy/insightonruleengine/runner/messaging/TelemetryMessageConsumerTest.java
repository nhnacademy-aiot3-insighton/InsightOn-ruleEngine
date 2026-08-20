package com.nhnacademy.insightonruleengine.runner.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nhnacademy.insightonruleengine.runner.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.dto.TelemetryEventMessage;
import com.nhnacademy.insightonruleengine.runner.redis.FlowRouteRedisRepository;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

@ExtendWith(MockitoExtension.class)
class TelemetryMessageConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private FlowRouteRedisRepository flowRouteRedisRepository;

    @Mock
    private FlowRunner flowRunner;

    @Mock
    private Channel channel;

    private TelemetryMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TelemetryMessageConsumer(objectMapper, flowRouteRedisRepository, flowRunner);
    }

    @Test
    @DisplayName("정상 메시지 수신 및 활성 Flow가 존재하면 FlowRunner를 실행하고 수동 ACK를 수행합니다.")
    void messageReceivedTest() throws Exception {
        OffsetDateTime now = OffsetDateTime.of(2026, 8, 17, 12, 0, 0, 0, ZoneOffset.UTC);
        TelemetryEventMessage eventMessage = new TelemetryEventMessage(
                1L,
                100L,
                "sensor-01",
                Map.of("temperature", 25.0),
                now
        );
        byte[] body = objectMapper.writeValueAsBytes(eventMessage);
        Message message = createMessage(101L, body);

        when(flowRouteRedisRepository.findFlowIds(1L, 100L)).thenReturn(Set.of(10L, 20L));
        consumer.onMessage(message, channel);

        verify(flowRunner).run(any(SensorEvent.class));
        verify(channel).basicAck(101L, false);
    }

    @Test
    @DisplayName("JSON 역직렬화에 실패하면 로그 후 메시지를 ACK 폐기합니다.")
    void jsonFailedTest() throws Exception {
        byte[] brokeJson = "{ invalid json content }".getBytes(StandardCharsets.UTF_8);
        Message message = createMessage(103L, brokeJson);
        consumer.onMessage(message, channel);

        verify(flowRunner, never()).run(any());
        verify(channel).basicAck(103L, false);
    }

    @Test
    @DisplayName("필수 필드가 누락된 메시지는 검증 실패 후 메시지를 ACK 폐기합니다.")
    void fieldMissingTest() throws Exception {
        String missingLocationJson = """
                {
                    "time": "2026-08-17T12:00:00Z",
                    "sensorId": "sensor-01",
                    "groupId": 1,
                    "metrics": { "temperature": 25.0 }
                }
                """;
        Message message = createMessage(104L, missingLocationJson.getBytes(StandardCharsets.UTF_8));
        consumer.onMessage(message, channel);

        verify(flowRunner, never()).run(any());
        verify(channel).basicAck(104L, false);
    }

    @Test
    @DisplayName("해당 그룹 및 장소에 ACTIVE Flow가 없으면 FlowRunner를 실행하지 않고 정상 ACK 처리합니다.")
    void emptyFlowTest() throws Exception {
        OffsetDateTime now = OffsetDateTime.of(2026, 8, 17, 12, 0, 0, 0, ZoneOffset.UTC);
        TelemetryEventMessage eventMessage = new TelemetryEventMessage(
                1L,
                100L,
                "sensor-01",
                Map.of("temperature", 25.0),
                now
        );
        byte[] body = objectMapper.writeValueAsBytes(eventMessage);
        Message message = createMessage(105L, body);
        when(flowRouteRedisRepository.findFlowIds(1L, 100L)).thenReturn(Set.of());
        consumer.onMessage(message, channel);

        verify(flowRunner, never()).run(any());
        verify(channel).basicAck(105L, false);
    }

    @Test
    @DisplayName("FlowRunner 실행 중 예외가 발생해도 로그를 남긴 후 ACK 폐기합니다.")
    void runnerExceptionTest() throws Exception {
        OffsetDateTime now = OffsetDateTime.of(2026, 8, 17, 12, 0, 0, 0, ZoneOffset.UTC);
        TelemetryEventMessage eventMessage = new TelemetryEventMessage(
                1L,
                100L,
                "sensor-01",
                Map.of("temperature", 25.0),
                now
        );
        byte[] body = objectMapper.writeValueAsBytes(eventMessage);
        Message message = createMessage(106L, body);
        when(flowRouteRedisRepository.findFlowIds(1L, 100L)).thenReturn(Set.of(10L));
        doThrow(new RuntimeException("Execution failed")).when(flowRunner).run(any(SensorEvent.class));
        consumer.onMessage(message, channel);
        verify(channel).basicAck(106L, false);
    }

    private Message createMessage(long deliveryTag, byte[] body) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(body, properties);
    }
}
