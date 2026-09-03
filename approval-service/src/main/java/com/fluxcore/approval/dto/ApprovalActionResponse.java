package com.fluxcore.approval.dto;

public record ApprovalActionResponse(
        Long approvalInstanceId,
        String approvalNo,
        Long applicationId,
        String status,
        Long currentNodeId,
        String actionType,
        Long actionId,
        boolean duplicate) {
}
