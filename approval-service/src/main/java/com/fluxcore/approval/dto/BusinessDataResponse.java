package com.fluxcore.approval.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record BusinessDataResponse(
        Long applicationId,
        String applicationNo,
        String businessType,
        String businessId,
        String title,
        String applicantId,
        String status,
        JsonNode data) {
}
