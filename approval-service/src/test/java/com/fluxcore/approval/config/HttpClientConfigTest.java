package com.fluxcore.approval.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fluxcore.approval.dto.BusinessDataResponse;
import com.fluxcore.approval.service.BusinessDataClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpClientConfigTest {
    private static final String BUSINESS_SERVICE_URL = "http://business-service.test";
    private static final String INTERNAL_TOKEN = "approval-service-internal-token";

    @Test
    void businessRestClient_shouldAttachInternalTokenToEveryInternalBusinessRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BusinessDataClient client = new BusinessDataClient(new HttpClientConfig()
                .businessRestClient(builder, BUSINESS_SERVICE_URL, INTERNAL_TOKEN));

        server.expect(requestTo(BUSINESS_SERVICE_URL + "/api/internal/business-data/PURCHASE/PUR-001"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andRespond(withSuccess("""
                        {
                          "applicationId": 1001,
                          "applicationNo": "APP-1001",
                          "businessType": "PURCHASE",
                          "businessId": "PUR-001",
                          "title": "办公用品采购",
                          "applicantId": "U1001",
                          "status": "DRAFT",
                          "data": {"amount": 1280}
                        }
                        """, MediaType.APPLICATION_JSON));
        expectAuthenticatedPost(server, "/api/internal/applications/1001/submit");
        expectAuthenticatedPost(server, "/api/internal/applications/1001/withdraw");
        expectAuthenticatedPost(server, "/api/internal/applications/1001/reject");
        expectAuthenticatedPost(server, "/api/internal/applications/1001/approve");

        BusinessDataResponse response = client.get("PURCHASE", "PUR-001");
        client.markSubmitted(1001L);
        client.markWithdrawn(1001L);
        client.markRejected(1001L);
        client.markApproved(1001L);

        assertEquals(1001L, response.applicationId());
        assertEquals("PURCHASE", response.businessType());
        server.verify();
    }

    private void expectAuthenticatedPost(MockRestServiceServer server, String path) {
        server.expect(requestTo(BUSINESS_SERVICE_URL + path))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andRespond(withNoContent());
    }
}
