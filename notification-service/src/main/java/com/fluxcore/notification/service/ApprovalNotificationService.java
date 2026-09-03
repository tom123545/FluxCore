package com.fluxcore.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxcore.notification.entity.NotificationRecordEntity;
import com.fluxcore.notification.mapper.NotificationRecordMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalNotificationService {
    private static final String CHANNEL_INBOX = "INBOX";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT = "SENT";

    private final NotificationRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    public ApprovalNotificationService(NotificationRecordMapper recordMapper, ObjectMapper objectMapper) {
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handle(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = requiredText(event, "eventId");
            NotificationRecordEntity existing = recordMapper.selectByEventId(eventId);
            if (existing != null && STATUS_SENT.equals(existing.getStatus())) {
                return;
            }

            String receiverId = resolveReceiver(event.path("data"));
            if (receiverId == null) {
                throw new IllegalArgumentException("审批事件未包含通知接收人: " + eventId);
            }

            LocalDateTime now = LocalDateTime.now();
            NotificationRecordEntity record = existing == null ? newRecord(eventId, receiverId, now) : existing;
            if (existing == null) {
                recordMapper.insert(record);
            }

            // 当前版本模拟站内信发送；后续可在此处接入真实通知通道。
            recordMapper.markSent(record.getId(), now, now);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("审批通知事件解析失败", exception);
        }
    }

    private NotificationRecordEntity newRecord(String eventId, String receiverId, LocalDateTime now) {
        NotificationRecordEntity record = new NotificationRecordEntity();
        record.setEventId(eventId);
        record.setReceiverId(receiverId);
        record.setChannel(CHANNEL_INBOX);
        record.setStatus(STATUS_PENDING);
        record.setRetryCount(0);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private String resolveReceiver(JsonNode data) {
        List<String> fields = List.of("receiverId", "assigneeId", "applicantId", "operatorId");
        for (String field : fields) {
            String value = text(data, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("审批事件缺少 " + field);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
