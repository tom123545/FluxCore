package com.fluxcore.approval.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record ApprovalSnapshotResponse(
        Long id,
        Long approvalInstanceId,
        Long nodeInstanceId,
        Integer snapshotNo,
        String snapshotType,
        String businessType,
        String businessId,
        JsonNode data,
        String dataHash,
        String createdBy,
        LocalDateTime createdAt) {
}
