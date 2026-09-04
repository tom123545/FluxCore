package com.fluxcore.notification.service;

public class NonRetryableNotificationException extends RuntimeException {
    public NonRetryableNotificationException(String message) {
        super(message);
    }

    public NonRetryableNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
