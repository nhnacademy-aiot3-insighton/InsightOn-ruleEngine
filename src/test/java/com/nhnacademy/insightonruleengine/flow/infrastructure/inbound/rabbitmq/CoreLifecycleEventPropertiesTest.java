package com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq.CoreLifecycleEventProperties.EventBindingProperties;
import com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq.CoreLifecycleEventProperties.RetryProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoreLifecycleEventPropertiesTest {

    @Test
    @DisplayName("활성 설정은 공유 Exchange, 이벤트별 Queue와 공통 재시도 값을 검증합니다")
    void enabledConfigurationTest() {
        assertDoesNotThrow(validProperties()::validateEnabledConfiguration);
    }

    @Test
    @DisplayName("비활성 설정은 RabbitMQ 세부 값을 요구하지 않습니다")
    void disabledConfigurationTest() {
        CoreLifecycleEventProperties properties = new CoreLifecycleEventProperties(
                false,
                null,
                null,
                null,
                null
        );

        assertDoesNotThrow(properties::validateEnabledConfiguration);
    }

    @Test
    @DisplayName("활성 상태에서는 공유 Exchange와 두 이벤트 설정이 모두 필요합니다")
    void requiredConfigurationTest() {
        CoreLifecycleEventProperties valid = validProperties();
        CoreLifecycleEventProperties missingExchange = properties(
                " ",
                valid.retry(),
                valid.groupDeleted(),
                valid.locationDeleted()
        );
        CoreLifecycleEventProperties missingGroupDeleted = properties(
                valid.exchange(),
                valid.retry(),
                null,
                valid.locationDeleted()
        );
        CoreLifecycleEventProperties missingLocationDeleted = properties(
                valid.exchange(),
                valid.retry(),
                valid.groupDeleted(),
                null
        );

        assertThrows(
                IllegalArgumentException.class,
                missingExchange::validateEnabledConfiguration
        );
        assertThrows(
                IllegalArgumentException.class,
                missingGroupDeleted::validateEnabledConfiguration
        );
        assertThrows(
                IllegalArgumentException.class,
                missingLocationDeleted::validateEnabledConfiguration
        );
    }

    @Test
    @DisplayName("각 이벤트에는 routing key, Queue, DLX와 DLQ 이름이 모두 필요합니다")
    void eventBindingValidationTest() {
        CoreLifecycleEventProperties valid = validProperties();

        assertInvalidBinding(new EventBindingProperties(null, "queue", "dlx", "dlq", "dlq"));
        assertInvalidBinding(new EventBindingProperties("event.deleted", null, "dlx", "dlq", "dlq"));
        assertInvalidBinding(new EventBindingProperties("event.deleted", "queue", null, "dlq", "dlq"));
        assertInvalidBinding(new EventBindingProperties("event.deleted", "queue", "dlx", null, "dlq"));
        assertInvalidBinding(new EventBindingProperties("event.deleted", "queue", "dlx", "dlq", null));

        assertDoesNotThrow(valid::validateEnabledConfiguration);
    }

    @Test
    @DisplayName("공통 재시도 정책은 횟수, 양수 간격, 배수와 최대 간격을 검증합니다")
    void retryValidationTest() {
        assertInvalidRetry(new RetryProperties(0, Duration.ofSeconds(1), 2, Duration.ofSeconds(10)));
        assertInvalidRetry(new RetryProperties(3, null, 2, Duration.ofSeconds(10)));
        assertInvalidRetry(new RetryProperties(3, Duration.ZERO, 2, Duration.ofSeconds(10)));
        assertInvalidRetry(new RetryProperties(3, Duration.ofSeconds(1), 0.5, Duration.ofSeconds(10)));
        assertInvalidRetry(new RetryProperties(3, Duration.ofSeconds(2), 2, Duration.ofSeconds(1)));
    }

    private CoreLifecycleEventProperties validProperties() {
        return properties(
                "insighton.core-events",
                new RetryProperties(3, Duration.ofSeconds(1), 2, Duration.ofSeconds(10)),
                binding("group.deleted", "rule-engine.group-deleted"),
                binding("location.deleted", "rule-engine.location-deleted")
        );
    }

    private CoreLifecycleEventProperties properties(
            String exchange,
            RetryProperties retry,
            EventBindingProperties groupDeleted,
            EventBindingProperties locationDeleted
    ) {
        return new CoreLifecycleEventProperties(true, exchange, retry, groupDeleted, locationDeleted);
    }

    private EventBindingProperties binding(String routingKey, String queuePrefix) {
        return new EventBindingProperties(
                routingKey,
                queuePrefix + ".queue",
                queuePrefix + ".dlx",
                queuePrefix + ".dlq",
                queuePrefix + ".dlq"
        );
    }

    private void assertInvalidBinding(EventBindingProperties invalidBinding) {
        CoreLifecycleEventProperties valid = validProperties();
        CoreLifecycleEventProperties properties = properties(
                valid.exchange(),
                valid.retry(),
                invalidBinding,
                valid.locationDeleted()
        );

        assertThrows(IllegalArgumentException.class, properties::validateEnabledConfiguration);
    }

    private void assertInvalidRetry(RetryProperties retry) {
        CoreLifecycleEventProperties valid = validProperties();
        CoreLifecycleEventProperties properties = properties(
                valid.exchange(),
                retry,
                valid.groupDeleted(),
                valid.locationDeleted()
        );

        assertThrows(IllegalStateException.class, properties::validateEnabledConfiguration);
    }
}
