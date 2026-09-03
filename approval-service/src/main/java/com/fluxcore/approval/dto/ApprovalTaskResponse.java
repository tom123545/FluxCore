package com.fluxcore.approval.dto;

import java.time.LocalDateTime;

public record ApprovalTaskResponse(
        Long taskId,
        Long nodeInstanceId,
        Long sourceTaskId,
        Long nodeId,
        String nodeName,
        String assigneeId,
        String status,
        String action,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime actedAt) {
}
