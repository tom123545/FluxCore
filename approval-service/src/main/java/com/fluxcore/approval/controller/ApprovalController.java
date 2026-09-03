package com.fluxcore.approval.controller;

import com.fluxcore.approval.dto.SubmitApprovalRequest;
import com.fluxcore.approval.dto.SubmitApprovalResponse;
import com.fluxcore.approval.dto.ApprovalInstanceResponse;
import com.fluxcore.approval.service.ApprovalInstanceQueryService;
import com.fluxcore.approval.service.ApprovalSubmitService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {
    private final ApprovalSubmitService approvalSubmitService;
    private final ApprovalInstanceQueryService approvalInstanceQueryService;

    public ApprovalController(ApprovalSubmitService approvalSubmitService,
                              ApprovalInstanceQueryService approvalInstanceQueryService) {
        this.approvalSubmitService = approvalSubmitService;
        this.approvalInstanceQueryService = approvalInstanceQueryService;
    }

    @PostMapping
    public SubmitApprovalResponse submit(@Valid @RequestBody SubmitApprovalRequest request) {
        return approvalSubmitService.submit(request);
    }

    @GetMapping("/{approvalInstanceId}")
    public ApprovalInstanceResponse get(@PathVariable long approvalInstanceId) {
        return approvalInstanceQueryService.get(approvalInstanceId);
    }
}
