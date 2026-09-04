package com.fluxcore.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApprovalAddSignRequest(
        @NotBlank String operatorId,
        @NotBlank String actionRequestId,
        @NotNull Long expectedVersion,
        @NotBlank String additionalAssigneeId,
        String comment) {
    public ApprovalAddSignRequest(String operatorId, String actionRequestId, String additionalAssigneeId, String comment) {
        this(operatorId, actionRequestId, 0L, additionalAssigneeId, comment);
    }
}
