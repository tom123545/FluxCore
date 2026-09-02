package com.fluxcore.approval.service;

import com.fluxcore.approval.dto.BusinessDataResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class BusinessDataClient {
    private final RestClient restClient;

    public BusinessDataClient(RestClient businessRestClient) {
        this.restClient = businessRestClient;
    }

    public BusinessDataResponse get(String businessType, String businessId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/internal/business-data/{businessType}/{businessId}")
                        .build(businessType, businessId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalArgumentException("业务数据服务返回错误: " + response.getStatusCode());
                })
                .body(BusinessDataResponse.class);
    }

    public void markSubmitted(long applicationId) {
        restClient.post()
                .uri("/api/internal/applications/{applicationId}/submit", applicationId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalStateException("业务申请状态更新失败: " + response.getStatusCode());
                })
                .toBodilessEntity();
    }
}
