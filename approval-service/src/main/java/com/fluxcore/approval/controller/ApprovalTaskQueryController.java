package com.fluxcore.approval.controller;

import com.fluxcore.approval.dto.ApprovalTaskQueryItem;
import com.fluxcore.approval.service.ApprovalTaskQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class ApprovalTaskQueryController {
    private final ApprovalTaskQueryService taskQueryService;

    public ApprovalTaskQueryController(ApprovalTaskQueryService taskQueryService) {
        this.taskQueryService = taskQueryService;
    }

    @GetMapping("/todo")
    public List<ApprovalTaskQueryItem> todo(@RequestParam("assigneeId") String assigneeId) {
        return taskQueryService.findTodo(assigneeId);
    }

    @GetMapping("/done")
    public List<ApprovalTaskQueryItem> done(@RequestParam("assigneeId") String assigneeId) {
        return taskQueryService.findDone(assigneeId);
    }
}
