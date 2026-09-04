package com.fluxcore.business.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final InternalAuthInterceptor internalAuthInterceptor;
    private final GatewayAccessInterceptor gatewayAccessInterceptor;

    public WebMvcConfig(InternalAuthInterceptor internalAuthInterceptor, GatewayAccessInterceptor gatewayAccessInterceptor) {
        this.internalAuthInterceptor = internalAuthInterceptor;
        this.gatewayAccessInterceptor = gatewayAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalAuthInterceptor).addPathPatterns("/api/internal/**");
        registry.addInterceptor(gatewayAccessInterceptor).addPathPatterns("/api/business/**");
    }
}
