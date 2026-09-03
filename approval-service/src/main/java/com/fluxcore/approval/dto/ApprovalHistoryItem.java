package com.fluxcore.approval.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record ApprovalHistoryItem(
        Long actionId,
        Long nodeInstanceId,
        Long taskId,
        String nodeName,
        String operatorId,
        String actionType,
        String fromStatus,
        String toStatus,
        String comment,
        String actionRequestId,
        Long snapshotId,
        Integer snapshotNo,
        String snapshotType,
        JsonNode snapshotData,
        String snapshotDataHash,
        LocalDateTime actionCreatedAt,
        LocalDateTime snapshotCreatedAt) {
}
