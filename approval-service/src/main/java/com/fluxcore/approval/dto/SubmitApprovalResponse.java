package com.fluxcore.approval.dto;

public record SubmitApprovalResponse(
        Long approvalInstanceId,
        String approvalNo,
        Long applicationId,
        String businessType,
        String businessId,
        String status,
        Long currentNodeId,
        Long firstTaskId,
        boolean duplicate) {
}
