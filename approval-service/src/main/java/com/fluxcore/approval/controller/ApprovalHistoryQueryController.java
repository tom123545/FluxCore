package com.fluxcore.approval.controller;

import com.fluxcore.approval.dto.ApprovalHistoryItem;
import com.fluxcore.approval.dto.ApprovalSnapshotResponse;
import com.fluxcore.approval.service.ApprovalHistoryQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalHistoryQueryController {
    private final ApprovalHistoryQueryService historyQueryService;

    public ApprovalHistoryQueryController(ApprovalHistoryQueryService historyQueryService) {
        this.historyQueryService = historyQueryService;
    }

    @GetMapping("/{approvalInstanceId}/history")
    public List<ApprovalHistoryItem> history(@PathVariable long approvalInstanceId) {
        return historyQueryService.getHistory(approvalInstanceId);
    }

    @GetMapping("/{approvalInstanceId}/snapshots")
    public List<ApprovalSnapshotResponse> snapshots(@PathVariable long approvalInstanceId) {
        return historyQueryService.getSnapshots(approvalInstanceId);
    }
}
