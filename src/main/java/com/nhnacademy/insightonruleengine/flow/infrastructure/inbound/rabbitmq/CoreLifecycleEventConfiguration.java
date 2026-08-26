package com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq;

import com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq.CoreLifecycleEventProperties.EventBindingProperties;
import com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq.CoreLifecycleEventProperties.RetryProperties;
import java.util.ArrayList;
import java.util.List;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        prefix = "rule-engine.core-lifecycle-events",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(CoreLifecycleEventProperties.class)
public class CoreLifecycleEventConfiguration {

    @Bean
    Declarables coreLifecycleEventTopology(CoreLifecycleEventProperties properties) {
        properties.validateEnabledConfiguration();

        TopicExchange coreEventsExchange = new TopicExchange(properties.exchange(), true, false);
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(coreEventsExchange);
        declarables.addAll(eventTopology(coreEventsExchange, properties.groupDeleted()));
        declarables.addAll(eventTopology(coreEventsExchange, properties.locationDeleted()));
        return new Declarables(declarables);
    }

    @Bean(name = "groupDeletedEventListenerContainerFactory")
    SimpleRabbitListenerContainerFactory groupDeletedEventListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            RabbitTemplate rabbitTemplate,
            CoreLifecycleEventProperties properties
    ) {
        return listenerContainerFactory(
                configurer,
                connectionFactory,
                rabbitTemplate,
                properties,
                properties.groupDeleted()
        );
    }

    @Bean(name = "locationDeletedEventListenerContainerFactory")
    SimpleRabbitListenerContainerFactory locationDeletedEventListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            RabbitTemplate rabbitTemplate,
            CoreLifecycleEventProperties properties
    ) {
        return listenerContainerFactory(
                configurer,
                connectionFactory,
                rabbitTemplate,
                properties,
                properties.locationDeleted()
        );
    }

    private List<Declarable> eventTopology(
            TopicExchange coreEventsExchange,
            EventBindingProperties eventProperties
    ) {
        DirectExchange deadLetterExchange = new DirectExchange(
                eventProperties.deadLetterExchange(),
                true,
                false
        );
        Queue queue = QueueBuilder.durable(eventProperties.queue()).build();
        Queue deadLetterQueue = QueueBuilder.durable(eventProperties.deadLetterQueue()).build();
        Binding binding = BindingBuilder.bind(queue)
                .to(coreEventsExchange)
                .with(eventProperties.routingKey());
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(eventProperties.deadLetterRoutingKey());

        return List.of(deadLetterExchange, queue, deadLetterQueue, binding, deadLetterBinding);
    }

    private SimpleRabbitListenerContainerFactory listenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            RabbitTemplate rabbitTemplate,
            CoreLifecycleEventProperties properties,
            EventBindingProperties eventProperties
    ) {
        properties.validateEnabledConfiguration();
        RetryProperties retry = properties.retry();
        RepublishMessageRecoverer recoverer = new RepublishMessageRecoverer(
                rabbitTemplate,
                eventProperties.deadLetterExchange(),
                eventProperties.deadLetterRoutingKey()
        );
        recoverer.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Advice retryAdvice = RetryInterceptorBuilder.stateless()
                .maxAttempts(retry.maxAttempts())
                .backOffOptions(
                        retry.initialInterval().toMillis(),
                        retry.multiplier(),
                        retry.maxInterval().toMillis()
                )
                .recoverer(recoverer)
                .build();

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryAdvice);
        return factory;
    }
}
