package com.fluxcore.approval.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fluxcore.approval.entity.ApprovalOutboxEventEntity;
import com.fluxcore.approval.mapper.ApprovalOutboxEventMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(ApprovalOutboxPublisher.class);

    private final ApprovalOutboxEventMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final String routingKey;
    private final int batchSize;
    private final int retryDelaySeconds;

    public ApprovalOutboxPublisher(ApprovalOutboxEventMapper outboxMapper,
                                   RabbitTemplate rabbitTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${fluxcore.messaging.approval.exchange:fluxcore.approval.events}") String exchange,
                                   @Value("${fluxcore.messaging.approval.routing-key:approval.event}") String routingKey,
                                   @Value("${fluxcore.messaging.approval.batch-size:50}") int batchSize,
                                   @Value("${fluxcore.messaging.approval.retry-delay-seconds:30}") int retryDelaySeconds) {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.batchSize = batchSize;
        this.retryDelaySeconds = retryDelaySeconds;
    }

    @Scheduled(fixedDelayString = "${fluxcore.messaging.approval.publish-interval-ms:1000}")
    @Transactional
    public void publishDueEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<ApprovalOutboxEventEntity> events = outboxMapper.selectReadyForPublish(now, batchSize);
        for (ApprovalOutboxEventEntity event : events) {
            try {
                rabbitTemplate.convertAndSend(exchange, routingKey, toMessage(event));
                outboxMapper.markPublished(event.getId(), LocalDateTime.now());
            } catch (Exception exception) {
                int retryCount = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
                LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(retryDelaySeconds);
                outboxMapper.markFailed(event.getId(), retryCount, nextRetryAt);
                log.warn("发布审批 Outbox 事件失败: eventId={}, retryCount={}", event.getEventId(), retryCount,
                        exception);
            }
        }
    }

    private String toMessage(ApprovalOutboxEventEntity event) throws Exception {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", event.getEventId());
        envelope.put("aggregateType", event.getAggregateType());
        envelope.put("aggregateId", event.getAggregateId());
        envelope.put("eventType", event.getEventType());
        envelope.set("data", objectMapper.readTree(event.getPayloadJson()));
        return objectMapper.writeValueAsString(envelope);
    }
}
