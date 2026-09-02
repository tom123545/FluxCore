package com.fluxcore.approval.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitApprovalRequest(
        @NotBlank String businessType,
        @NotBlank String businessId,
        @NotBlank String applicantId,
        @NotBlank String submitRequestId,
        Long applicationId) {
}
