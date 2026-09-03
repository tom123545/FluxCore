package com.fluxcore.approval.config;

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
}
