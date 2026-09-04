package com.fluxcore.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApprovalActionRequest(
        @NotBlank String operatorId,
        @NotBlank String actionRequestId,
        @NotNull Long expectedVersion,
        String comment) {
    public ApprovalActionRequest(String operatorId, String actionRequestId, String comment) {
        this(operatorId, actionRequestId, 0L, comment);
    }
}
