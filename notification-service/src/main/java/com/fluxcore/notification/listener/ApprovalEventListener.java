package com.fluxcore.notification.listener;

import com.fluxcore.notification.service.ApprovalNotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ApprovalEventListener {
    private final ApprovalNotificationService notificationService;

    public ApprovalEventListener(ApprovalNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "${fluxcore.messaging.approval.queue:fluxcore.approval.notifications}")
    public void onMessage(String message) {
        notificationService.handle(message);
    }
}
