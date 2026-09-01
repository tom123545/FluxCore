package com.fluxcore.procurement;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProcurementHealthController {
    @GetMapping("/api/procurement/ping")
    public Map<String, String> ping() {
        return Map.of("service", "procurement-service", "status", "UP");
    }
}
