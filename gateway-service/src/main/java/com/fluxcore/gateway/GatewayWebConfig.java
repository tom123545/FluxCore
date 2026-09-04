package com.fluxcore.gateway;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GatewayWebConfig implements WebMvcConfigurer {
    private final GatewayAccessInterceptor interceptor;

    public GatewayWebConfig(GatewayAccessInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/business/**", "/api/approvals/**", "/api/tasks/**");
    }
}
