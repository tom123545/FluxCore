package com.fluxcore.business.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class GatewayAccessInterceptor implements HandlerInterceptor {
    private final String expectedToken;

    public GatewayAccessInterceptor(@Value("${fluxcore.gateway.token:fluxcore-gateway-dev-token}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String actualToken = request.getHeader("X-Gateway-Token");
        if (actualToken == null || !MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
                actualToken.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "gateway authentication required");
            return false;
        }
        return true;
    }
}
