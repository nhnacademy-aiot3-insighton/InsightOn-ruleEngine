package com.nhnacademy.insightonruleengine.runner.infrastructure.inbound.rabbitmq;

import com.nhnacademy.insightonruleengine.config.TelemetryRoutingProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

//16개 고정 Telemetry Queue 중 정상 소유 8개 큐와 상대 장애 시 인계받을 8개 큐의
//Spring AMQP Listener Container 생명주기(시작/중지/인계/반환)를 관리합니다.
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rule-engine.telemetry-routing", name = "enabled", havingValue = "true")
public class TelemetryListenerContainerManager {

    private final ConnectionFactory connectionFactory;
    private final TelemetryRoutingProperties routingProperties;
    private final TelemetryMessageConsumer messageConsumer;
    private final AmqpAdmin amqpAdmin;

    private DirectMessageListenerContainer normalContainer;
    private DirectMessageListenerContainer takeoverContainer;

    public TelemetryListenerContainerManager(
            ConnectionFactory connectionFactory,
            TelemetryRoutingProperties routingProperties,
            TelemetryMessageConsumer messageConsumer,
            AmqpAdmin amqpAdmin
    ) {
        this.connectionFactory = connectionFactory;
        this.routingProperties = routingProperties;
        this.messageConsumer = messageConsumer;
        this.amqpAdmin = amqpAdmin;
        initContainers();
    }

    private void initContainers() {
        if (!routingProperties.enabled()) {
            return;
        }

        List<String> ownedQueues = routingProperties.ownedQueueNames();
        if (!ownedQueues.isEmpty()) {
            normalContainer = createContainer(ownedQueues, "Normal-Telemetry-Listener");
        }

        List<String> peerQueues = routingProperties.peerQueueNames();
        if (!peerQueues.isEmpty()) {
            takeoverContainer = createContainer(peerQueues, "Takeover-Telemetry-Listener");
        }
    }

    private DirectMessageListenerContainer createContainer(List<String> queueNames, String listenerName) {
        DirectMessageListenerContainer container =
                new DirectMessageListenerContainer(connectionFactory);
        container.setQueueNames(queueNames.toArray(new String[0]));
        container.setConsumersPerQueue(1);
        container.setMessageListener(messageConsumer);
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setAutoStartup(false);
        container.setBeanName(listenerName);
        return container;
    }

    @PostConstruct
    public void startNormal() {
        if (normalContainer != null && !normalContainer.isRunning()) {
            amqpAdmin.initialize();
            log.info("Telemetry 기본 수신기를 시작합니다. queues={}",
                    routingProperties.ownedQueueNames());
            normalContainer.start();
        }
    }

    public synchronized void stopNormal() {
        if (normalContainer != null && normalContainer.isRunning()) {
            log.info("Telemetry 기본 수신기를 중지합니다.");
            normalContainer.stop();
        }
    }

    public synchronized void takeover() {
        if (takeoverContainer != null && !takeoverContainer.isRunning()) {
            log.warn("상대 엔진의 Telemetry 큐 인계를 시작합니다. queues={}",
                    routingProperties.peerQueueNames());
            takeoverContainer.start();
        }
    }

    public synchronized void handback() {
        if (takeoverContainer != null && takeoverContainer.isRunning()) {
            log.info("상대 엔진의 Telemetry 큐를 반환했습니다.");
            takeoverContainer.stop();
        }
    }

    public boolean isNormalRunning() {
        return normalContainer != null && normalContainer.isRunning();
    }

    public boolean isTakingOver() {
        return takeoverContainer != null && takeoverContainer.isRunning();
    }

    @PreDestroy
    public synchronized void stopAll() {
        if (normalContainer != null && normalContainer.isRunning()) {
            normalContainer.stop();
        }
        if (takeoverContainer != null && takeoverContainer.isRunning()) {
            takeoverContainer.stop();
        }
    }
}
