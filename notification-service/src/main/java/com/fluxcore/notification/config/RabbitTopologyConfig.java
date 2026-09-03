package com.fluxcore.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {
    @Bean
    TopicExchange approvalEventExchange(
            @Value("${fluxcore.messaging.approval.exchange:fluxcore.approval.events}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    Queue approvalNotificationQueue(
            @Value("${fluxcore.messaging.approval.queue:fluxcore.approval.notifications}") String queue) {
        return new Queue(queue, true);
    }

    @Bean
    Binding approvalNotificationBinding(
            TopicExchange approvalEventExchange,
            Queue approvalNotificationQueue,
            @Value("${fluxcore.messaging.approval.routing-key:approval.event}") String routingKey) {
        return BindingBuilder.bind(approvalNotificationQueue).to(approvalEventExchange).with(routingKey);
    }
}
