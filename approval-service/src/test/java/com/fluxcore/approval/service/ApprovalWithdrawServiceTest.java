package com.fluxcore.approval.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxcore.approval.dto.ApprovalActionRequest;
import com.fluxcore.approval.dto.ApprovalActionResponse;
import com.fluxcore.approval.dto.BusinessDataResponse;
import com.fluxcore.approval.entity.ApprovalActionEntity;
import com.fluxcore.approval.entity.ApprovalInstanceEntity;
import com.fluxcore.approval.entity.ApprovalNodeInstanceEntity;
import com.fluxcore.approval.entity.ApprovalOutboxEventEntity;
import com.fluxcore.approval.entity.ApprovalSnapshotEntity;
import com.fluxcore.approval.mapper.ApprovalActionMapper;
import com.fluxcore.approval.mapper.ApprovalInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalNodeInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalNodeMapper;
import com.fluxcore.approval.mapper.ApprovalOutboxEventMapper;
import com.fluxcore.approval.mapper.ApprovalSnapshotMapper;
import com.fluxcore.approval.mapper.ApprovalTaskMapper;
import com.fluxcore.approval.mapper.ApprovalTransitionMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalWithdrawServiceTest {
    private static final long INSTANCE_ID = 20001L;
    private static final ApprovalActionRequest REQUEST = new ApprovalActionRequest("U1001", "WITHDRAW-001", "撤回申请");

    @Mock private RedisLockService redisLockService;
    @Mock private BusinessDataClient businessDataClient;
    @Mock private ApprovalInstanceMapper instanceMapper;
    @Mock private ApprovalNodeInstanceMapper nodeInstanceMapper;
    @Mock private ApprovalNodeMapper nodeMapper;
    @Mock private ApprovalTransitionMapper transitionMapper;
    @Mock private ApprovalTaskMapper taskMapper;
    @Mock private ApprovalActionMapper actionMapper;
    @Mock private ApprovalSnapshotMapper snapshotMapper;
    @Mock private ApprovalOutboxEventMapper outboxMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ApprovalActionService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalActionService(objectMapper, redisLockService, businessDataClient, instanceMapper,
                nodeInstanceMapper, taskMapper, nodeMapper, transitionMapper, actionMapper, snapshotMapper,
                outboxMapper);
        when(redisLockService.tryLock("approval:action:" + INSTANCE_ID, Duration.ofSeconds(30)))
                .thenReturn("action-lock-token");
    }

    @Test
    void withdraw_shouldCancelPendingTaskAndActiveNodeAndWriteRecords() {
        ApprovalInstanceEntity instance = instance();
        ApprovalNodeInstanceEntity activeNode = nodeInstance();
        BusinessDataResponse data = new BusinessDataResponse(10001L, "APP-001", "PURCHASE", "PUR-001",
                "采购申请", "U1001", "SUBMITTED", objectMapper.createObjectNode().put("amount", 1280));
        when(actionMapper.selectByActionRequestId(INSTANCE_ID, REQUEST.actionRequestId())).thenReturn(null);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);
        when(nodeInstanceMapper.selectActiveByInstanceId(INSTANCE_ID)).thenReturn(activeNode);
        when(businessDataClient.get("PURCHASE", "PUR-001")).thenReturn(data);
        when(taskMapper.cancelPendingByInstanceId(INSTANCE_ID)).thenReturn(1);
        when(nodeInstanceMapper.markCancelled(activeNode.getId())).thenReturn(1);
        when(instanceMapper.updateStatusWithVersion(INSTANCE_ID, "IN_PROGRESS", "WITHDRAWN", 0L)).thenReturn(1);
        when(snapshotMapper.selectMaxSnapshotNo(INSTANCE_ID)).thenReturn(1);
        doAnswer(invocation -> {
            ((ApprovalSnapshotEntity) invocation.getArgument(0)).setId(40002L);
            return 1;
        }).when(snapshotMapper).insert(any(ApprovalSnapshotEntity.class));
        doAnswer(invocation -> {
            ((ApprovalActionEntity) invocation.getArgument(0)).setId(50002L);
            return 1;
        }).when(actionMapper).insert(any(ApprovalActionEntity.class));
        when(outboxMapper.insert(any(ApprovalOutboxEventEntity.class))).thenReturn(1);

        ApprovalActionResponse response = service.withdraw(INSTANCE_ID, REQUEST);

        assertEquals("WITHDRAWN", response.status());
        assertEquals("WITHDRAW", response.actionType());
        assertEquals(50002L, response.actionId());
        assertFalse(response.duplicate());
        verify(taskMapper).cancelPendingByInstanceId(INSTANCE_ID);
        verify(nodeInstanceMapper).markCancelled(activeNode.getId());
        verify(instanceMapper).updateStatusWithVersion(INSTANCE_ID, "IN_PROGRESS", "WITHDRAWN", 0L);
        verify(businessDataClient).markWithdrawn(10001L);

        ArgumentCaptor<ApprovalSnapshotEntity> snapshot = ArgumentCaptor.forClass(ApprovalSnapshotEntity.class);
        verify(snapshotMapper).insert(snapshot.capture());
        assertEquals(2, snapshot.getValue().getSnapshotNo());
        assertEquals("WITHDRAW", snapshot.getValue().getSnapshotType());
        assertEquals(64, snapshot.getValue().getDataHash().length());
    }

    @Test
    void withdraw_withSameActionRequestId_shouldReturnExistingAction() {
        ApprovalActionEntity action = new ApprovalActionEntity();
        action.setId(50002L);
        action.setActionType("WITHDRAW");
        action.setOperatorId("U1001");
        action.setToStatus("WITHDRAWN");
        when(actionMapper.selectByActionRequestId(INSTANCE_ID, REQUEST.actionRequestId())).thenReturn(action);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance());

        ApprovalActionResponse response = service.withdraw(INSTANCE_ID, REQUEST);

        assertTrue(response.duplicate());
        assertEquals(50002L, response.actionId());
        verify(taskMapper, never()).cancelPendingByInstanceId(anyLong());
        verify(businessDataClient, never()).get(any(), any());
        verify(businessDataClient, never()).markWithdrawn(anyLong());
    }

    @Test
    void withdraw_whenOperatorIsNotApplicant_shouldReturnForbidden() {
        when(actionMapper.selectByActionRequestId(INSTANCE_ID, "WITHDRAW-002")).thenReturn(null);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance());

        ApprovalSubmitException exception = assertThrows(ApprovalSubmitException.class,
                () -> service.withdraw(INSTANCE_ID, new ApprovalActionRequest("U2001", "WITHDRAW-002", null)));

        assertEquals("WITHDRAW_OPERATOR_FORBIDDEN", exception.getCode());
        assertEquals(403, exception.getStatus().value());
    }

    @Test
    void withdraw_whenLockIsUnavailable_shouldReturnConflict() {
        when(redisLockService.tryLock("approval:action:" + INSTANCE_ID, Duration.ofSeconds(30))).thenReturn(null);

        ApprovalSubmitException exception = assertThrows(ApprovalSubmitException.class,
                () -> service.withdraw(INSTANCE_ID, REQUEST));

        assertEquals("ACTION_IN_PROGRESS", exception.getCode());
        assertEquals(409, exception.getStatus().value());
    }

    private ApprovalInstanceEntity instance() {
        ApprovalInstanceEntity instance = new ApprovalInstanceEntity();
        instance.setId(INSTANCE_ID);
        instance.setApprovalNo("APR-001");
        instance.setApplicationId(10001L);
        instance.setBusinessType("PURCHASE");
        instance.setBusinessId("PUR-001");
        instance.setApplicantId("U1001");
        instance.setStatus("IN_PROGRESS");
        instance.setLockVersion(0L);
        return instance;
    }

    private ApprovalNodeInstanceEntity nodeInstance() {
        ApprovalNodeInstanceEntity node = new ApprovalNodeInstanceEntity();
        node.setId(21001L);
        node.setApprovalInstanceId(INSTANCE_ID);
        node.setNodeId(101L);
        node.setStatus("ACTIVE");
        return node;
    }
}
