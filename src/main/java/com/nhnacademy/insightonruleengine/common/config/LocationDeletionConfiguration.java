package com.nhnacademy.insightonruleengine.common.config;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
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
@ConditionalOnProperty(prefix = "rule-engine.location-deletion", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LocationDeletionProperties.class)
public class LocationDeletionConfiguration {

    @Bean
    Declarables locationDeletionTopology(LocationDeletionProperties properties) {
        properties.validateEnabledConfiguration();
        TopicExchange coreEventsExchange = new TopicExchange(properties.exchange(), true, false);
        DirectExchange deadLetterExchange = new DirectExchange(properties.deadLetterExchange(), true, false);
        // Rule Engine 전용 Queue다. AI 서비스 Queue와 분리해 같은 이벤트를 각각 받는다.
        Queue queue = QueueBuilder.durable(properties.queue()).build();
        Queue deadLetterQueue = QueueBuilder.durable(properties.deadLetterQueue()).build();
        Binding binding = BindingBuilder.bind(queue)
                .to(coreEventsExchange)
                .with(properties.routingKey());
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(properties.deadLetterRoutingKey());
        return new Declarables(
                coreEventsExchange,
                deadLetterExchange,
                queue,
                deadLetterQueue,
                binding,
                deadLetterBinding
        );
    }

    @Bean(name = "locationDeletionListenerContainerFactory")
    SimpleRabbitListenerContainerFactory locationDeletionListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            RabbitTemplate rabbitTemplate,
            LocationDeletionProperties properties
    ) {
        properties.validateEnabledConfiguration();
        RepublishMessageRecoverer recoverer = new RepublishMessageRecoverer(
                rabbitTemplate,
                properties.deadLetterExchange(),
                properties.deadLetterRoutingKey()
        );
        recoverer.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Advice retryAdvice = RetryInterceptorBuilder.stateless()
                .maxAttempts(properties.maxAttempts())
                .backOffOptions(
                        properties.initialInterval().toMillis(),
                        properties.multiplier(),
                        properties.maxInterval().toMillis()
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
