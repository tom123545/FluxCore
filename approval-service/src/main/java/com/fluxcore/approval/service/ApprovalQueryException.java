package com.fluxcore.approval.service;

import org.springframework.http.HttpStatus;

public class ApprovalQueryException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public ApprovalQueryException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
