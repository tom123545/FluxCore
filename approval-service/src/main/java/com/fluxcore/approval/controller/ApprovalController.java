package com.fluxcore.approval.controller;

import com.fluxcore.approval.dto.SubmitApprovalRequest;
import com.fluxcore.approval.dto.SubmitApprovalResponse;
import com.fluxcore.approval.service.ApprovalSubmitService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {
    private final ApprovalSubmitService approvalSubmitService;

    public ApprovalController(ApprovalSubmitService approvalSubmitService) {
        this.approvalSubmitService = approvalSubmitService;
    }

    @PostMapping
    public SubmitApprovalResponse submit(@Valid @RequestBody SubmitApprovalRequest request) {
        return approvalSubmitService.submit(request);
    }
}
