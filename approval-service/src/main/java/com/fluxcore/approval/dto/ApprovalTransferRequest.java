package com.fluxcore.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApprovalTransferRequest(
        @NotBlank String operatorId,
        @NotBlank String actionRequestId,
        @NotNull Long expectedVersion,
        @NotBlank String targetAssigneeId,
        String comment) {
    public ApprovalTransferRequest(String operatorId, String actionRequestId, String targetAssigneeId, String comment) {
        this(operatorId, actionRequestId, 0L, targetAssigneeId, comment);
    }
}
