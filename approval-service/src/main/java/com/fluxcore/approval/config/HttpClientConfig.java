package com.fluxcore.approval.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {
    @Bean
    RestClient businessRestClient(
            RestClient.Builder builder,
            @Value("${business.service.url:http://localhost:8082}") String businessServiceUrl) {
        return builder.baseUrl(businessServiceUrl).build();
    }
}
