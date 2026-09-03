package com.fluxcore.approval.service;

import com.fluxcore.approval.dto.ApprovalTaskQueryItem;
import com.fluxcore.approval.mapper.ApprovalTaskMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ApprovalTaskQueryService {
    private final ApprovalTaskMapper taskMapper;

    public ApprovalTaskQueryService(ApprovalTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    public List<ApprovalTaskQueryItem> findTodo(String assigneeId) {
        return taskMapper.selectTodoByAssignee(requireAssigneeId(assigneeId));
    }

    public List<ApprovalTaskQueryItem> findDone(String assigneeId) {
        return taskMapper.selectDoneByAssignee(requireAssigneeId(assigneeId));
    }

    private String requireAssigneeId(String assigneeId) {
        if (assigneeId == null || assigneeId.isBlank()) {
            throw new ApprovalQueryException("ASSIGNEE_ID_REQUIRED", "assigneeId 不能为空", HttpStatus.BAD_REQUEST);
        }
        return assigneeId.trim();
    }
}
