package com.fluxcore.approval.controller;

import com.fluxcore.approval.dto.ApprovalActionRequest;
import com.fluxcore.approval.dto.ApprovalActionResponse;
import com.fluxcore.approval.dto.ApprovalAddSignRequest;
import com.fluxcore.approval.dto.ApprovalTransferRequest;
import com.fluxcore.approval.service.ApprovalActionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalActionController {
    private final ApprovalActionService approvalActionService;

    public ApprovalActionController(ApprovalActionService approvalActionService) {
        this.approvalActionService = approvalActionService;
    }

    @PostMapping("/{approvalInstanceId}/withdraw")
    public ApprovalActionResponse withdraw(@PathVariable long approvalInstanceId,
                                           @Valid @RequestBody ApprovalActionRequest request) {
        return approvalActionService.withdraw(approvalInstanceId, request);
    }

    @PostMapping("/{approvalInstanceId}/tasks/{taskId}/reject")
    public ApprovalActionResponse reject(@PathVariable long approvalInstanceId,
                                         @PathVariable long taskId,
                                         @Valid @RequestBody ApprovalActionRequest request) {
        return approvalActionService.reject(approvalInstanceId, taskId, request);
    }

    @PostMapping("/{approvalInstanceId}/tasks/{taskId}/approve")
    public ApprovalActionResponse approve(@PathVariable long approvalInstanceId,
                                          @PathVariable long taskId,
                                          @Valid @RequestBody ApprovalActionRequest request) {
        return approvalActionService.approve(approvalInstanceId, taskId, request);
    }

    @PostMapping("/{approvalInstanceId}/tasks/{taskId}/transfer")
    public ApprovalActionResponse transfer(@PathVariable long approvalInstanceId,
                                           @PathVariable long taskId,
                                           @Valid @RequestBody ApprovalTransferRequest request) {
        return approvalActionService.transfer(approvalInstanceId, taskId, request);
    }

    @PostMapping("/{approvalInstanceId}/tasks/{taskId}/add-sign")
    public ApprovalActionResponse addSign(@PathVariable long approvalInstanceId,
                                          @PathVariable long taskId,
                                          @Valid @RequestBody ApprovalAddSignRequest request) {
        return approvalActionService.addSign(approvalInstanceId, taskId, request);
    }
}
