package com.fluxcore.approval.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxcore.approval.dto.ApprovalHistoryItem;
import com.fluxcore.approval.dto.ApprovalHistoryRecord;
import com.fluxcore.approval.dto.ApprovalSnapshotResponse;
import com.fluxcore.approval.entity.ApprovalInstanceEntity;
import com.fluxcore.approval.entity.ApprovalSnapshotEntity;
import com.fluxcore.approval.mapper.ApprovalActionMapper;
import com.fluxcore.approval.mapper.ApprovalInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalSnapshotMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalHistoryQueryServiceTest {
    private static final long INSTANCE_ID = 20001L;

    @Mock private ApprovalInstanceMapper instanceMapper;
    @Mock private ApprovalActionMapper actionMapper;
    @Mock private ApprovalSnapshotMapper snapshotMapper;

    private ApprovalHistoryQueryService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalHistoryQueryService(new ObjectMapper(), instanceMapper, actionMapper, snapshotMapper);
    }

    @Test
    void getHistory_shouldReturnActionsWithTheirImmutableSnapshotData() {
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance());
        ApprovalHistoryRecord record = new ApprovalHistoryRecord();
        record.setActionId(50001L);
        record.setNodeInstanceId(40001L);
        record.setTaskId(30001L);
        record.setNodeName("部门负责人审批");
        record.setOperatorId("U1001");
        record.setActionType("SUBMIT");
        record.setFromStatus("DRAFT");
        record.setToStatus("IN_PROGRESS");
        record.setComment("提交审批");
        record.setActionRequestId("SUBMIT-001");
        record.setSnapshotId(60001L);
        record.setSnapshotNo(1);
        record.setSnapshotType("SUBMIT");
        record.setSnapshotDataJson("{\"amount\":1280}");
        record.setSnapshotDataHash("a".repeat(64));
        record.setActionCreatedAt(LocalDateTime.of(2026, 9, 3, 10, 0));
        record.setSnapshotCreatedAt(LocalDateTime.of(2026, 9, 3, 10, 0));
        when(actionMapper.selectHistoryByInstanceId(INSTANCE_ID)).thenReturn(List.of(record));

        List<ApprovalHistoryItem> result = service.getHistory(INSTANCE_ID);

        assertEquals(1, result.size());
        ApprovalHistoryItem item = result.getFirst();
        assertEquals("部门负责人审批", item.nodeName());
        assertEquals("SUBMIT", item.actionType());
        assertEquals(1280, item.snapshotData().get("amount").asInt());
        assertEquals("a".repeat(64), item.snapshotDataHash());
        verify(actionMapper).selectHistoryByInstanceId(INSTANCE_ID);
    }

    @Test
    void getSnapshots_shouldReturnSnapshotsInSnapshotNumberOrder() {
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance());
        ApprovalSnapshotEntity first = snapshot(60001L, 1, "SUBMIT", "{\"amount\":1280}");
        ApprovalSnapshotEntity second = snapshot(60002L, 2, "WITHDRAW", "{\"amount\":1500}");
        when(snapshotMapper.selectByInstanceId(INSTANCE_ID)).thenReturn(List.of(first, second));

        List<ApprovalSnapshotResponse> result = service.getSnapshots(INSTANCE_ID);

        assertEquals(2, result.size());
        assertEquals(1, result.getFirst().snapshotNo());
        assertEquals("SUBMIT", result.getFirst().snapshotType());
        assertEquals(1500, result.get(1).data().get("amount").asInt());
        verify(snapshotMapper).selectByInstanceId(INSTANCE_ID);
    }

    @Test
    void getHistory_whenInstanceDoesNotExist_shouldReturnNotFound() {
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(null);

        ApprovalQueryException exception = assertThrows(ApprovalQueryException.class,
                () -> service.getHistory(INSTANCE_ID));

        assertEquals("APPROVAL_NOT_FOUND", exception.getCode());
        assertEquals(404, exception.getStatus().value());
        verify(actionMapper, never()).selectHistoryByInstanceId(INSTANCE_ID);
        verify(snapshotMapper, never()).selectByInstanceId(INSTANCE_ID);
    }

    @Test
    void getSnapshots_whenInstanceDoesNotExist_shouldReturnNotFound() {
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(null);

        ApprovalQueryException exception = assertThrows(ApprovalQueryException.class,
                () -> service.getSnapshots(INSTANCE_ID));

        assertTrue(exception.getMessage().contains("20001"));
        verify(snapshotMapper, never()).selectByInstanceId(INSTANCE_ID);
    }

    private ApprovalInstanceEntity instance() {
        ApprovalInstanceEntity instance = new ApprovalInstanceEntity();
        instance.setId(INSTANCE_ID);
        return instance;
    }

    private ApprovalSnapshotEntity snapshot(long id, int number, String type, String data) {
        ApprovalSnapshotEntity snapshot = new ApprovalSnapshotEntity();
        snapshot.setId(id);
        snapshot.setApprovalInstanceId(INSTANCE_ID);
        snapshot.setSnapshotNo(number);
        snapshot.setSnapshotType(type);
        snapshot.setBusinessType("PURCHASE");
        snapshot.setBusinessId("PUR-001");
        snapshot.setDataJson(data);
        snapshot.setDataHash("b".repeat(64));
        snapshot.setCreatedBy("U1001");
        return snapshot;
    }
}
