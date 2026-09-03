package com.fluxcore.approval.service;

import com.fluxcore.approval.dto.ApprovalInstanceResponse;
import com.fluxcore.approval.dto.ApprovalTaskResponse;
import com.fluxcore.approval.dto.ApprovalTaskView;
import com.fluxcore.approval.dto.BusinessDataResponse;
import com.fluxcore.approval.entity.ApprovalInstanceEntity;
import com.fluxcore.approval.entity.ApprovalNodeEntity;
import com.fluxcore.approval.mapper.ApprovalInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalNodeMapper;
import com.fluxcore.approval.mapper.ApprovalTaskMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalInstanceQueryService {
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalNodeMapper nodeMapper;
    private final ApprovalTaskMapper taskMapper;
    private final BusinessDataClient businessDataClient;

    public ApprovalInstanceQueryService(ApprovalInstanceMapper instanceMapper,
                                        ApprovalNodeMapper nodeMapper,
                                        ApprovalTaskMapper taskMapper,
                                        BusinessDataClient businessDataClient) {
        this.instanceMapper = instanceMapper;
        this.nodeMapper = nodeMapper;
        this.taskMapper = taskMapper;
        this.businessDataClient = businessDataClient;
    }

    @Transactional(readOnly = true)
    public ApprovalInstanceResponse get(long approvalInstanceId) {
        ApprovalInstanceEntity instance = instanceMapper.selectById(approvalInstanceId);
        if (instance == null) {
            throw new ApprovalQueryException("APPROVAL_NOT_FOUND",
                    "审批实例不存在: " + approvalInstanceId, HttpStatus.NOT_FOUND);
        }

        BusinessDataResponse businessData = businessDataClient.get(instance.getBusinessType(), instance.getBusinessId());
        ApprovalNodeEntity currentNode = instance.getCurrentNodeId() == null
                ? null : nodeMapper.selectById(instance.getCurrentNodeId());
        List<ApprovalTaskResponse> tasks = taskMapper.selectViewsByApprovalInstanceId(approvalInstanceId).stream()
                .map(this::toTaskResponse)
                .toList();

        return new ApprovalInstanceResponse(
                instance.getId(),
                instance.getApprovalNo(),
                instance.getApplicationId(),
                instance.getBusinessType(),
                instance.getBusinessId(),
                instance.getApplicantId(),
                businessData == null ? null : businessData.title(),
                instance.getProcessId(),
                instance.getStatus(),
                instance.getCurrentNodeId(),
                currentNode == null ? null : currentNode.getNodeName(),
                instance.getLockVersion(),
                instance.getCreatedAt(),
                instance.getUpdatedAt(),
                instance.getCompletedAt(),
                tasks);
    }

    private ApprovalTaskResponse toTaskResponse(ApprovalTaskView task) {
        return new ApprovalTaskResponse(task.getId(), task.getNodeInstanceId(), task.getSourceTaskId(), task.getNodeId(),
                task.getNodeName(), task.getAssigneeId(), task.getStatus(), task.getAction(), task.getComment(),
                task.getCreatedAt(), task.getActedAt());
    }
}
