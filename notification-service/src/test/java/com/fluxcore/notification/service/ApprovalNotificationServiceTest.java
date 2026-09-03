package com.fluxcore.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxcore.notification.entity.NotificationRecordEntity;
import com.fluxcore.notification.mapper.NotificationRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalNotificationServiceTest {
    @Mock private NotificationRecordMapper recordMapper;

    private ApprovalNotificationService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalNotificationService(recordMapper, new ObjectMapper());
    }

    @Test
    void handle_shouldCreateAndCompleteInboxNotification() {
        when(recordMapper.selectByEventId("event-1")).thenReturn(null);
        when(recordMapper.insert(any(NotificationRecordEntity.class))).thenAnswer(invocation -> {
            NotificationRecordEntity record = invocation.getArgument(0);
            record.setId(10L);
            return 1;
        });

        service.handle("""
                {"eventId":"event-1","eventType":"APPROVAL_SUBMITTED",
                 "data":{"assigneeId":"U2001","approvalInstanceId":20001}}
                """);

        ArgumentCaptor<NotificationRecordEntity> captor = ArgumentCaptor.forClass(NotificationRecordEntity.class);
        verify(recordMapper).insert(captor.capture());
        assertEquals("event-1", captor.getValue().getEventId());
        assertEquals("U2001", captor.getValue().getReceiverId());
        assertEquals("INBOX", captor.getValue().getChannel());
        verify(recordMapper).markSent(any(Long.class), any(), any());
    }

    @Test
    void handle_whenEventWasAlreadySent_shouldBeIdempotent() {
        NotificationRecordEntity record = new NotificationRecordEntity();
        record.setId(10L);
        record.setStatus("SENT");
        when(recordMapper.selectByEventId("event-1")).thenReturn(record);

        service.handle("""
                {"eventId":"event-1","eventType":"APPROVAL_SUBMITTED",
                 "data":{"assigneeId":"U2001"}}
                """);

        verify(recordMapper, never()).insert(any(NotificationRecordEntity.class));
        verify(recordMapper, never()).markSent(any(Long.class), any(), any());
    }

    @Test
    void handle_whenEventHasNoReceiver_shouldRejectAndNotPersistNotification() {
        when(recordMapper.selectByEventId("event-2")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.handle("""
                {"eventId":"event-2","eventType":"APPROVAL_APPROVED",
                 "data":{"approvalInstanceId":20001}}
                """));

        verify(recordMapper, never()).insert(any(NotificationRecordEntity.class));
        verify(recordMapper, never()).markSent(any(Long.class), any(), any());
    }

    @Test
    void handle_whenEventIdIsMissing_shouldRejectAsMalformedMessage() {
        assertThrows(IllegalArgumentException.class, () -> service.handle("""
                {"eventType":"APPROVAL_SUBMITTED","data":{"assigneeId":"U2001"}}
                """));

        verify(recordMapper, never()).selectByEventId(any());
    }
}
