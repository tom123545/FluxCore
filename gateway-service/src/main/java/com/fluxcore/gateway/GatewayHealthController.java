package com.fluxcore.gateway;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayHealthController {
    @GetMapping("/api/gateway/ping")
    public Map<String, String> ping() {
        return Map.of("service", "gateway-service", "status", "UP");
    }
}
