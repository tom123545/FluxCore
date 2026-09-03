package com.fluxcore.approval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxcore.approval.dto.ApprovalHistoryItem;
import com.fluxcore.approval.dto.ApprovalHistoryRecord;
import com.fluxcore.approval.dto.ApprovalSnapshotResponse;
import com.fluxcore.approval.entity.ApprovalSnapshotEntity;
import com.fluxcore.approval.mapper.ApprovalActionMapper;
import com.fluxcore.approval.mapper.ApprovalInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalSnapshotMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalHistoryQueryService {
    private final ObjectMapper objectMapper;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalActionMapper actionMapper;
    private final ApprovalSnapshotMapper snapshotMapper;

    public ApprovalHistoryQueryService(ObjectMapper objectMapper, ApprovalInstanceMapper instanceMapper,
                                       ApprovalActionMapper actionMapper, ApprovalSnapshotMapper snapshotMapper) {
        this.objectMapper = objectMapper;
        this.instanceMapper = instanceMapper;
        this.actionMapper = actionMapper;
        this.snapshotMapper = snapshotMapper;
    }

    @Transactional(readOnly = true)
    public List<ApprovalHistoryItem> getHistory(long approvalInstanceId) {
        requireInstance(approvalInstanceId);
        return actionMapper.selectHistoryByInstanceId(approvalInstanceId).stream()
                .map(this::toHistoryItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalSnapshotResponse> getSnapshots(long approvalInstanceId) {
        requireInstance(approvalInstanceId);
        return snapshotMapper.selectByInstanceId(approvalInstanceId).stream()
                .map(this::toSnapshotResponse)
                .toList();
    }

    private void requireInstance(long approvalInstanceId) {
        if (instanceMapper.selectById(approvalInstanceId) == null) {
            throw new ApprovalQueryException("APPROVAL_NOT_FOUND", "审批实例不存在: " + approvalInstanceId,
                    HttpStatus.NOT_FOUND);
        }
    }

    private ApprovalHistoryItem toHistoryItem(ApprovalHistoryRecord record) {
        return new ApprovalHistoryItem(record.getActionId(), record.getNodeInstanceId(), record.getTaskId(),
                record.getNodeName(), record.getOperatorId(), record.getActionType(), record.getFromStatus(),
                record.getToStatus(), record.getComment(), record.getActionRequestId(), record.getSnapshotId(),
                record.getSnapshotNo(), record.getSnapshotType(), parseJson(record.getSnapshotDataJson()),
                record.getSnapshotDataHash(), record.getActionCreatedAt(), record.getSnapshotCreatedAt());
    }

    private ApprovalSnapshotResponse toSnapshotResponse(ApprovalSnapshotEntity snapshot) {
        return new ApprovalSnapshotResponse(snapshot.getId(), snapshot.getApprovalInstanceId(),
                snapshot.getNodeInstanceId(), snapshot.getSnapshotNo(), snapshot.getSnapshotType(),
                snapshot.getBusinessType(), snapshot.getBusinessId(), parseJson(snapshot.getDataJson()),
                snapshot.getDataHash(), snapshot.getCreatedBy(), snapshot.getCreatedAt());
    }

    private JsonNode parseJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审批快照 JSON 数据损坏", exception);
        }
    }
}
