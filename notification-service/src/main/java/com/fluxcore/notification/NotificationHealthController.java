package com.fluxcore.notification;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationHealthController {
    @GetMapping("/api/notification/ping")
    public Map<String, String> ping() {
        return Map.of("service", "notification-service", "status", "UP");
    }
}
