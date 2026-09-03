package com.fluxcore.approval.dto;

import jakarta.validation.constraints.NotBlank;

public record ApprovalTransferRequest(
        @NotBlank String operatorId,
        @NotBlank String actionRequestId,
        @NotBlank String targetAssigneeId,
        String comment) {
}
