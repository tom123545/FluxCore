package com.fluxcore.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxcore.notification.entity.NotificationRecordEntity;
import com.fluxcore.notification.entity.NotificationFailureEntity;
import com.fluxcore.notification.mapper.NotificationFailureMapper;
import com.fluxcore.notification.mapper.NotificationRecordMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalNotificationServiceTest {
    @Mock private NotificationRecordMapper recordMapper;
    @Mock private NotificationFailureMapper failureMapper;

    private ApprovalNotificationService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalNotificationService(recordMapper, new ObjectMapper(), failureMapper);
    }

    @Test
    void handle_shouldCreateAndCompleteInboxNotification() {
        when(recordMapper.selectByEventReceiverAndChannel("event-1", "U2001", "INBOX")).thenReturn(null);
        when(recordMapper.insert(any(NotificationRecordEntity.class))).thenAnswer(invocation -> {
            NotificationRecordEntity record = invocation.getArgument(0);
            record.setId(10L);
            return 1;
        });

        service.handle("""
                {"eventId":"event-1","eventType":"APPROVAL_SUBMITTED",
                 "data":{"recipientIds":["U2001"],"notificationPurpose":"TODO_ASSIGNED",
                          "approvalInstanceId":20001}}
                """);

        ArgumentCaptor<NotificationRecordEntity> captor = ArgumentCaptor.forClass(NotificationRecordEntity.class);
        verify(recordMapper).insert(captor.capture());
        assertEquals("event-1", captor.getValue().getEventId());
        assertEquals("U2001", captor.getValue().getReceiverId());
        assertEquals("INBOX", captor.getValue().getChannel());
        verify(recordMapper).markSent(any(Long.class), any(), any());
    }

    @Test
    void handle_shouldCreateOneNotificationForEachRecipient() {
        when(recordMapper.selectByEventReceiverAndChannel(any(), any(), any())).thenReturn(null);
        when(recordMapper.insert(any(NotificationRecordEntity.class))).thenAnswer(invocation -> {
            NotificationRecordEntity record = invocation.getArgument(0);
            record.setId((long) (10 + record.getReceiverId().hashCode() % 100));
            return 1;
        });

        service.handle("""
                {"eventId":"event-many","eventType":"APPROVAL_NODE_APPROVED",
                 "data":{"recipientIds":["U2001","U2002","U2001"],
                          "notificationPurpose":"TODO_ASSIGNED",
                          "approvalInstanceId":20001}}
                """);

        ArgumentCaptor<NotificationRecordEntity> captor = ArgumentCaptor.forClass(NotificationRecordEntity.class);
        verify(recordMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals(List.of("U2001", "U2002"),
                captor.getAllValues().stream().map(NotificationRecordEntity::getReceiverId).toList());
        verify(recordMapper, org.mockito.Mockito.times(2)).markSent(any(Long.class), any(), any());
    }

    @Test
    void handle_whenTaskOnlyEventHasNoReceiver_shouldIgnoreWithoutFailure() {
        service.handle("""
                {"eventId":"event-task-only","eventType":"APPROVAL_TASK_APPROVED",
                 "data":{"recipientIds":[],"notificationPurpose":"TASK_PROCESSED",
                          "approvalInstanceId":20001}}
                """);

        verify(recordMapper, never()).selectByEventReceiverAndChannel(any(), any(), any());
        verify(recordMapper, never()).insert(any(NotificationRecordEntity.class));
        verify(recordMapper, never()).markSent(any(Long.class), any(), any());
        verify(failureMapper, never()).insert(any(NotificationFailureEntity.class));
    }

    @Test
    void handle_whenEventWasAlreadySent_shouldBeIdempotent() {
        NotificationRecordEntity record = new NotificationRecordEntity();
        record.setId(10L);
        record.setStatus("SENT");
        when(recordMapper.selectByEventReceiverAndChannel("event-1", "U2001", "INBOX")).thenReturn(record);

        service.handle("""
                {"eventId":"event-1","eventType":"APPROVAL_SUBMITTED",
                 "data":{"assigneeId":"U2001"}}
                """);

        verify(recordMapper, never()).insert(any(NotificationRecordEntity.class));
        verify(recordMapper, never()).markSent(any(Long.class), any(), any());
    }

    @Test
    void handle_whenEventHasNoReceiver_shouldRejectAndNotPersistNotification() {
        service.handle("""
                {"eventId":"event-2","eventType":"APPROVAL_APPROVED",
                 "data":{"approvalInstanceId":20001}}
                """);

        verify(recordMapper, never()).insert(any(NotificationRecordEntity.class));
        verify(recordMapper, never()).markSent(any(Long.class), any(), any());
        verify(failureMapper).insert(any(NotificationFailureEntity.class));
    }

    @Test
    void handle_whenEventIdIsMissing_shouldRejectAsMalformedMessage() {
        service.handle("""
                {"eventType":"APPROVAL_SUBMITTED","data":{"recipientIds":["U2001"]}}
                """);

        verify(recordMapper, never()).selectByEventReceiverAndChannel(any(), any(), any());
        verify(failureMapper).insert(any(NotificationFailureEntity.class));
    }

    @Test
    void handle_whenSendingFails_shouldPersistRetryStateWithoutThrowing() {
        ApprovalNotificationService failingService = new ApprovalNotificationService(recordMapper, new ObjectMapper(),
                failureMapper) {
            @Override
            protected void sendInboxNotification(String message, String receiverId) {
                throw new IllegalStateException("notification provider unavailable");
            }
        };
        when(recordMapper.selectByEventReceiverAndChannel("event-3", "U2001", "INBOX")).thenReturn(null);
        when(recordMapper.insert(any(NotificationRecordEntity.class))).thenAnswer(invocation -> {
            NotificationRecordEntity record = invocation.getArgument(0);
            record.setId(11L);
            return 1;
        });

        failingService.handle("""
                {"eventId":"event-3","eventType":"APPROVAL_SUBMITTED",
                 "data":{"assigneeId":"U2001"}}
                """);

        verify(recordMapper).markFailed(any(Long.class), org.mockito.ArgumentMatchers.eq(1),
                any(LocalDateTime.class), org.mockito.ArgumentMatchers.contains("provider unavailable"),
                any(LocalDateTime.class));
    }

    @Test
    void retryDueNotifications_whenRetryFailsAtLimit_shouldKeepTerminalFailedRecord() {
        NotificationRecordEntity record = new NotificationRecordEntity();
        record.setId(12L);
        record.setEventId("event-4");
        record.setReceiverId("U2001");
        record.setPayloadJson("{\"eventId\":\"event-4\"}");
        record.setRetryCount(4);
        when(recordMapper.selectReadyForRetry(any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(50)))
                .thenReturn(List.of(record));
        ApprovalNotificationService failingService = new ApprovalNotificationService(recordMapper, new ObjectMapper(),
                failureMapper) {
            @Override
            protected void sendInboxNotification(String message, String receiverId) {
                throw new IllegalStateException("permanent failure");
            }
        };

        failingService.retryDueNotifications();

        verify(recordMapper).markFailed(any(Long.class), org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.contains("permanent failure"),
                any(LocalDateTime.class));
    }

    @Test
    void handle_whenDeliveryRejectsMessage_shouldPersistTerminalFailure() {
        ApprovalNotificationService failingService = new ApprovalNotificationService(recordMapper, new ObjectMapper(),
                failureMapper) {
            @Override
            protected void sendInboxNotification(String message, String receiverId) {
                throw new NonRetryableNotificationException("unsupported notification payload");
            }
        };
        when(recordMapper.selectByEventReceiverAndChannel("event-5", "U2001", "INBOX")).thenReturn(null);
        when(recordMapper.insert(any(NotificationRecordEntity.class))).thenAnswer(invocation -> {
            NotificationRecordEntity record = invocation.getArgument(0);
            record.setId(13L);
            return 1;
        });

        failingService.handle("""
                {"eventId":"event-5","eventType":"APPROVAL_SUBMITTED",
                 "data":{"assigneeId":"U2001"}}
                """);

        verify(recordMapper).markFailed(any(Long.class), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.contains("unsupported notification payload"),
                any(LocalDateTime.class));
    }
}
