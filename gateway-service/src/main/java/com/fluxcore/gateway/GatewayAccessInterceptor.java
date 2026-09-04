package com.fluxcore.gateway;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class GatewayAccessInterceptor implements HandlerInterceptor {
    private final String token;

    public GatewayAccessInterceptor(@Value("${fluxcore.gateway.token:fluxcore-gateway-dev-token}") String token) {
        this.token = token;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "authentication required");
            return false;
        }
        return true;
    }
}
