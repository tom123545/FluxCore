package com.fluxcore.gateway;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class GatewayProxyController {
    private final RestClient restClient;
    private final String approvalUrl;
    private final String businessUrl;
    private final String gatewayToken;

    public GatewayProxyController(RestClient.Builder builder,
                                  @Value("${fluxcore.gateway.approval-url:http://127.0.0.1:8081}") String approvalUrl,
                                  @Value("${fluxcore.gateway.business-url:http://127.0.0.1:8082}") String businessUrl,
                                  @Value("${fluxcore.gateway.token:fluxcore-gateway-dev-token}") String gatewayToken) {
        this.restClient = builder.build();
        this.approvalUrl = approvalUrl;
        this.businessUrl = businessUrl;
        this.gatewayToken = gatewayToken;
    }

    @RequestMapping(value = {"/api/business/**", "/api/approvals/**", "/api/tasks/**"},
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        String baseUrl = request.getRequestURI().startsWith("/api/business/")
                ? businessUrl : approvalUrl;
        String target = UriComponentsBuilder.fromUriString(baseUrl)
                .path(request.getRequestURI())
                .query(request.getQueryString())
                .build(true).toUriString();
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        byte[] body = request.getInputStream().readAllBytes();

        return restClient.method(method).uri(target).headers(headers -> copyHeaders(request, headers))
                .header("X-Gateway-Token", gatewayToken)
                .body(body)
                .exchange((outgoingRequest, response) -> {
                    byte[] responseBody = response.getBody().readAllBytes();
                    HttpHeaders responseHeaders = new HttpHeaders();
                    response.getHeaders().forEach((name, values) -> {
                        if (!name.equalsIgnoreCase("Transfer-Encoding")) {
                            responseHeaders.put(name, values);
                        }
                    });
                    return ResponseEntity.status(response.getStatusCode())
                            .headers(responseHeaders).body(responseBody);
                });
    }

    private void copyHeaders(HttpServletRequest request, HttpHeaders headers) {
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (name.equalsIgnoreCase("Host") || name.equalsIgnoreCase("Content-Length")
                    || name.equalsIgnoreCase("X-Gateway-Token")) continue;
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) headers.add(name, values.nextElement());
        }
    }
}
