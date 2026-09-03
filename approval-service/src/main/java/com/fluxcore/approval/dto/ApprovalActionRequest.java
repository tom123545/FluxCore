package com.fluxcore.approval.dto;

import jakarta.validation.constraints.NotBlank;

public record ApprovalActionRequest(
        @NotBlank String operatorId,
        @NotBlank String actionRequestId,
        String comment) {
}
