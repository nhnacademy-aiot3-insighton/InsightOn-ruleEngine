package com.nhnacademy.insightonruleengine.runner.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nhnacademy.insightonruleengine.runner.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
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
    private FlowRunner flowRunner;

    @Mock
    private Channel channel;

    private TelemetryMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TelemetryMessageConsumer(objectMapper, flowRunner);
    }

    @Test
    @DisplayName("정상 메시지 수신 및 활성 Flow가 존재하면 FlowRunner를 실행하고 수동 ACK를 수행합니다.")
    void messageReceivedTest() throws Exception {
        byte[] body = validBody();
        Message message = createMessage(101L, body);

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
                    "sensorId": "101",
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
    @DisplayName("FlowRunner 실행 중 예외가 발생하면 메시지를 재큐잉합니다.")
    void runnerExceptionTest() throws Exception {
        byte[] body = validBody();
        Message message = createMessage(106L, body);
        doThrow(new RuntimeException("Execution failed")).when(flowRunner).run(any(SensorEvent.class));
        consumer.onMessage(message, channel);
        verify(channel).basicNack(106L, false, true);
        verify(channel, never()).basicAck(106L, false);
    }

    @Test
    @DisplayName("과도하게 큰 메시지는 역직렬화하지 않고 폐기합니다.")
    void oversizedMessageTest() throws Exception {
        Message message = createMessage(107L, new byte[256 * 1024 + 1]);

        consumer.onMessage(message, channel);

        verify(flowRunner, never()).run(any());
        verify(channel).basicAck(107L, false);
    }

    private byte[] validBody() {
        return """
                {
                    "time": "2026-08-17T12:00:00Z",
                    "sensorId": "101",
                    "groupId": 1,
                    "locationId": 100,
                    "metrics": {"temperature": 25.0}
                }
                """.getBytes(StandardCharsets.UTF_8);
    }

    private Message createMessage(long deliveryTag, byte[] body) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(body, properties);
    }
}
