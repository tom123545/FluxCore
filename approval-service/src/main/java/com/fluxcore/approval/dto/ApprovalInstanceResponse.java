package com.fluxcore.approval.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ApprovalInstanceResponse(
        Long approvalInstanceId,
        String approvalNo,
        Long applicationId,
        String businessType,
        String businessId,
        String applicantId,
        String title,
        Long processId,
        String status,
        Long currentNodeId,
        String currentNodeName,
        Long lockVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        List<ApprovalTaskResponse> tasks) {
}
