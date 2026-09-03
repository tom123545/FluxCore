package com.fluxcore.approval.dto;

import jakarta.validation.constraints.NotBlank;

public record ApprovalAddSignRequest(
        @NotBlank String operatorId,
        @NotBlank String actionRequestId,
        @NotBlank String additionalAssigneeId,
        String comment) {
}
