package com.fluxcore.business.dto;

public record ApplicationResponse(
        Long applicationId,
        String applicationNo,
        String businessType,
        String businessId,
        String title,
        String applicantId,
        String status) {
}
