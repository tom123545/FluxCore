package com.fluxcore.approval.controller;

import com.fluxcore.approval.dto.ApiErrorResponse;
import com.fluxcore.approval.service.ApprovalSubmitException;
import com.fluxcore.approval.service.ApprovalActionException;
import com.fluxcore.approval.service.ApprovalQueryException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApprovalExceptionHandler {
    @ExceptionHandler(ApprovalSubmitException.class)
    public ResponseEntity<ApiErrorResponse> handleSubmit(ApprovalSubmitException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(ApprovalActionException.class)
    public ResponseEntity<ApiErrorResponse> handleAction(ApprovalActionException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(ApprovalQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleQuery(ApprovalQueryException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("INVALID_ARGUMENT", "请求参数校验失败"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(404).body(new ApiErrorResponse("BUSINESS_DATA_NOT_FOUND", exception.getMessage()));
    }
}
