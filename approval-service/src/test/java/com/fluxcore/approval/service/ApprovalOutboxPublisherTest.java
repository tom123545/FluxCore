package com.fluxcore.approval.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxcore.approval.entity.ApprovalOutboxEventEntity;
import com.fluxcore.approval.mapper.ApprovalOutboxEventMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class ApprovalOutboxPublisherTest {
    @Mock private ApprovalOutboxEventMapper outboxMapper;
    @Mock private RabbitTemplate rabbitTemplate;

    private ApprovalOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ApprovalOutboxPublisher(outboxMapper, rabbitTemplate, new ObjectMapper(),
                "fluxcore.approval.events", "approval.event", 50, 30);
    }

    @Test
    void publishDueEvents_shouldPublishEnvelopeAndMarkPublished() {
        ApprovalOutboxEventEntity event = event("{\"approvalInstanceId\":20001,\"assigneeId\":\"U2001\"}");
        when(outboxMapper.selectReadyForPublish(any(LocalDateTime.class), anyInt())).thenReturn(List.of(event));

        publisher.publishDueEvents();

        verify(rabbitTemplate).convertAndSend(eq("fluxcore.approval.events"), eq("approval.event"), anyString());
        verify(outboxMapper).markPublished(eq(1L), any(LocalDateTime.class));
        verify(outboxMapper, never()).markFailed(any(Long.class), anyInt(), any(LocalDateTime.class));
    }

    @Test
    void publishDueEvents_whenPayloadIsInvalid_shouldScheduleRetry() {
        ApprovalOutboxEventEntity event = event("not-json");
        when(outboxMapper.selectReadyForPublish(any(LocalDateTime.class), anyInt())).thenReturn(List.of(event));

        publisher.publishDueEvents();

        verify(outboxMapper).markFailed(eq(1L), eq(1), any(LocalDateTime.class));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), anyString());
    }

    private ApprovalOutboxEventEntity event(String payload) {
        ApprovalOutboxEventEntity event = new ApprovalOutboxEventEntity();
        event.setId(1L);
        event.setEventId("event-1");
        event.setAggregateType("APPROVAL_INSTANCE");
        event.setAggregateId("20001");
        event.setEventType("APPROVAL_SUBMITTED");
        event.setPayloadJson(payload);
        event.setStatus("NEW");
        event.setRetryCount(0);
        return event;
    }
}
