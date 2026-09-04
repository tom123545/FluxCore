package com.fluxcore.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxcore.notification.entity.NotificationRecordEntity;
import com.fluxcore.notification.entity.NotificationFailureEntity;
import com.fluxcore.notification.mapper.NotificationFailureMapper;
import com.fluxcore.notification.mapper.NotificationRecordMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalNotificationService {
    private static final String CHANNEL_INBOX = "INBOX";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";
    private static final Logger log = LoggerFactory.getLogger(ApprovalNotificationService.class);

    private final NotificationRecordMapper recordMapper;
    private final ObjectMapper objectMapper;
    private final NotificationFailureMapper failureMapper;

    @Value("${fluxcore.messaging.approval.retry.max-attempts:5}")
    private int maxAttempts = 5;
    @Value("${fluxcore.messaging.approval.retry.base-delay-seconds:30}")
    private int baseDelaySeconds = 30;
    @Value("${fluxcore.messaging.approval.retry.max-delay-seconds:3600}")
    private int maxDelaySeconds = 3600;
    @Value("${fluxcore.messaging.approval.retry.batch-size:50}")
    private int retryBatchSize = 50;

    public ApprovalNotificationService(NotificationRecordMapper recordMapper, ObjectMapper objectMapper) {
        this(recordMapper, objectMapper, null);
    }

    @Autowired
    public ApprovalNotificationService(NotificationRecordMapper recordMapper, ObjectMapper objectMapper,
                                       NotificationFailureMapper failureMapper) {
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
        this.failureMapper = failureMapper;
    }

    @Transactional
    public void handle(String message) {
        JsonNode event;
        try {
            event = objectMapper.readTree(message);
        } catch (Exception exception) {
            recordMalformedMessage(message, exception);
            return;
        }

        try {
            String eventId = requiredText(event, "eventId");
            requiredText(event, "eventType");
            JsonNode data = event.path("data");
            List<String> receiverIds = resolveReceivers(data);
            if (receiverIds.isEmpty()) {
                if ("TASK_PROCESSED".equals(text(data, "notificationPurpose"))) {
                    return;
                }
                throw new NonRetryableNotificationException("审批事件未包含通知接收人: " + eventId);
            }

            for (String receiverId : receiverIds) {
                handleReceiver(eventId, receiverId, message);
            }
        } catch (NonRetryableNotificationException | IllegalArgumentException exception) {
            recordMalformedMessage(message, exception);
        }
    }

    @Scheduled(fixedDelayString = "${fluxcore.messaging.approval.retry.interval-ms:5000}")
    @Transactional
    public void retryDueNotifications() {
        LocalDateTime now = LocalDateTime.now();
        for (NotificationRecordEntity record : recordMapper.selectReadyForRetry(now, retryBatchSize)) {
            try {
                sendInboxNotification(record.getPayloadJson(), record.getReceiverId());
            } catch (Exception exception) {
                int retryCount = nextRetryCount(record);
                recordMapper.markFailed(record.getId(), retryCount, nextRetryAt(exception, retryCount),
                        errorMessage(exception), LocalDateTime.now());
                log.warn("通知重试失败: recordId={}, eventId={}, retryCount={}", record.getId(),
                        record.getEventId(), retryCount, exception);
                continue;
            }
            recordMapper.markSent(record.getId(), LocalDateTime.now(), LocalDateTime.now());
        }
    }

    private void handleReceiver(String eventId, String receiverId, String message) {
        String channel = CHANNEL_INBOX;
        NotificationRecordEntity existing = recordMapper.selectByEventReceiverAndChannel(eventId, receiverId, channel);
        if (existing != null && STATUS_SENT.equals(existing.getStatus())) {
            return;
        }
        if (existing != null && STATUS_FAILED.equals(existing.getStatus())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        NotificationRecordEntity record = existing == null ? newRecord(eventId, receiverId, message, now) : existing;
        if (existing == null) {
            try {
                recordMapper.insert(record);
            } catch (DuplicateKeyException duplicateKeyException) {
                record = recordMapper.selectByEventReceiverAndChannel(eventId, receiverId, channel);
                if (record == null) {
                    throw duplicateKeyException;
                }
                if (STATUS_SENT.equals(record.getStatus())) {
                    return;
                }
                if (STATUS_FAILED.equals(record.getStatus())) {
                    return;
                }
            }
        }

        try {
            sendInboxNotification(message, receiverId);
        } catch (Exception exception) {
            int retryCount = nextRetryCount(record);
            recordMapper.markFailed(record.getId(), retryCount, nextRetryAt(exception, retryCount),
                    errorMessage(exception), LocalDateTime.now());
            log.warn("通知发送失败: recordId={}, eventId={}, retryCount={}", record.getId(), eventId, retryCount,
                    exception);
            return;
        }
        recordMapper.markSent(record.getId(), now, now);
    }

    private NotificationRecordEntity newRecord(String eventId, String receiverId, String message, LocalDateTime now) {
        NotificationRecordEntity record = new NotificationRecordEntity();
        record.setEventId(eventId);
        record.setReceiverId(receiverId);
        record.setChannel(CHANNEL_INBOX);
        record.setStatus(STATUS_PENDING);
        record.setRetryCount(0);
        record.setPayloadJson(message);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    protected void sendInboxNotification(String message, String receiverId) {
        // 当前版本模拟站内信发送；后续可在此处接入真实通知通道。
    }

    private int nextRetryCount(NotificationRecordEntity record) {
        return (record.getRetryCount() == null ? 0 : record.getRetryCount()) + 1;
    }

    private LocalDateTime retryAt(int retryCount) {
        if (retryCount >= maxAttempts) {
            return null;
        }
        long delay = baseDelaySeconds;
        for (int attempt = 1; attempt < retryCount; attempt++) {
            delay = Math.min(delay * 2, maxDelaySeconds);
        }
        return LocalDateTime.now().plusSeconds(delay);
    }

    private LocalDateTime nextRetryAt(Exception exception, int retryCount) {
        return exception instanceof NonRetryableNotificationException ? null : retryAt(retryCount);
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private void recordMalformedMessage(String message, Exception exception) {
        if (failureMapper == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        NotificationFailureEntity failure = new NotificationFailureEntity();
        failure.setEventId(extractEventId(message));
        failure.setRawMessage(message == null ? "" : message);
        failure.setStatus(STATUS_FAILED);
        failure.setRetryCount(0);
        failure.setErrorMessage(errorMessage(exception));
        failure.setCreatedAt(now);
        failure.setUpdatedAt(now);
        failureMapper.insert(failure);
        log.warn("审批通知消息不可重试，已记录失败: error={}", exception.getMessage());
    }

    private String extractEventId(String message) {
        try {
            return text(objectMapper.readTree(message), "eventId");
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> resolveReceivers(JsonNode data) {
        Set<String> receivers = new LinkedHashSet<>();
        JsonNode recipientIds = data.get("recipientIds");
        if (recipientIds != null && recipientIds.isArray()) {
            recipientIds.forEach(recipient -> {
                if (recipient.isTextual() && !recipient.asText().isBlank()) {
                    receivers.add(recipient.asText().trim());
                }
            });
        }
        if (receivers.isEmpty()) {
            // 兼容尚未发布的旧事件；新事件必须使用 recipientIds。
            List<String> legacyFields = List.of("receiverId", "assigneeId", "applicantId");
            for (String field : legacyFields) {
                String value = text(data, field);
                if (value != null && !value.isBlank()) {
                    receivers.add(value.trim());
                }
            }
        }
        return new ArrayList<>(receivers);
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
