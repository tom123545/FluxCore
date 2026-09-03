package com.fluxcore.approval.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxcore.approval.dto.ApprovalActionRequest;
import com.fluxcore.approval.dto.ApprovalActionResponse;
import com.fluxcore.approval.dto.BusinessDataResponse;
import com.fluxcore.approval.entity.ApprovalActionEntity;
import com.fluxcore.approval.entity.ApprovalInstanceEntity;
import com.fluxcore.approval.entity.ApprovalNodeEntity;
import com.fluxcore.approval.entity.ApprovalNodeInstanceEntity;
import com.fluxcore.approval.entity.ApprovalOutboxEventEntity;
import com.fluxcore.approval.entity.ApprovalSnapshotEntity;
import com.fluxcore.approval.entity.ApprovalTaskEntity;
import com.fluxcore.approval.entity.ApprovalTransitionEntity;
import com.fluxcore.approval.mapper.ApprovalActionMapper;
import com.fluxcore.approval.mapper.ApprovalInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalNodeInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalNodeMapper;
import com.fluxcore.approval.mapper.ApprovalOutboxEventMapper;
import com.fluxcore.approval.mapper.ApprovalSnapshotMapper;
import com.fluxcore.approval.mapper.ApprovalTaskMapper;
import com.fluxcore.approval.mapper.ApprovalTransitionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalActionServiceTest {
    private static final ApprovalActionRequest REQUEST =
            new ApprovalActionRequest("U2001", "ACTION-001", "同意");

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
        when(redisLockService.tryLock("approval:action:20001", java.time.Duration.ofSeconds(30)))
                .thenReturn("action-lock-token");
        lenient().when(actionMapper.selectByActionRequestId(20001L, REQUEST.actionRequestId())).thenReturn(null);
        lenient().when(snapshotMapper.selectMaxSnapshotNo(20001L)).thenReturn(1);
        lenient().when(taskMapper.updatePendingToApproved(30001L, 20001L, "U2001", "同意")).thenReturn(1);
        lenient().when(nodeInstanceMapper.markCompleted(40001L, 20001L)).thenReturn(1);
    }

    @Test
    void approve_shouldCreateNextConfiguredTask() {
        ApprovalInstanceEntity instance = instance("IN_PROGRESS", 101L, 0L);
        ApprovalTaskEntity task = task(30001L, 40001L, "U2001", "PENDING");
        ApprovalNodeInstanceEntity activeNode = nodeInstance(40001L, 101L, "ACTIVE");
        ApprovalTransitionEntity transition = new ApprovalTransitionEntity();
        transition.setToNodeId(102L);
        ApprovalNodeEntity nextNode = node(102L, "U2002,U2003", "OR");
        BusinessDataResponse data = businessData("SUBMITTED");

        when(instanceMapper.selectById(20001L)).thenReturn(instance);
        when(taskMapper.selectById(30001L)).thenReturn(task);
        when(nodeInstanceMapper.selectActiveByInstanceId(20001L)).thenReturn(activeNode);
        when(nodeMapper.selectById(101L)).thenReturn(node(101L, "U2001", "SINGLE"));
        when(transitionMapper.findDefaultNext(10L, 101L)).thenReturn(java.util.Optional.of(transition));
        when(nodeMapper.selectById(102L)).thenReturn(nextNode);
        when(businessDataClient.get("PURCHASE", "PUR-001")).thenReturn(data);
        doAnswer(invocation -> {
            ((ApprovalSnapshotEntity) invocation.getArgument(0)).setId(50001L);
            return 1;
        }).when(snapshotMapper).insert(any(ApprovalSnapshotEntity.class));
        doAnswer(invocation -> {
            ((ApprovalNodeInstanceEntity) invocation.getArgument(0)).setId(40002L);
            return 1;
        }).when(nodeInstanceMapper).insert(any(ApprovalNodeInstanceEntity.class));
        doAnswer(invocation -> {
            ((ApprovalTaskEntity) invocation.getArgument(0)).setId(30002L);
            return 1;
        }).when(taskMapper).insert(any(ApprovalTaskEntity.class));
        when(instanceMapper.updateCurrentNodeWithVersion(20001L, 102L, 0L)).thenReturn(1);
        when(actionMapper.insert(any(ApprovalActionEntity.class))).thenReturn(1);
        when(outboxMapper.insert(any(ApprovalOutboxEventEntity.class))).thenReturn(1);

        ApprovalActionResponse response = service.approve(20001L, 30001L, REQUEST);

        assertEquals("IN_PROGRESS", response.status());
        assertEquals(102L, response.currentNodeId());
        assertFalse(response.duplicate());
        verify(taskMapper, times(2)).insert(any(ApprovalTaskEntity.class));
        verify(businessDataClient, never()).markApproved(anyLong());
        verify(redisLockService).unlock("approval:action:20001", "action-lock-token");
    }

    @Test
    void approve_onFinalNode_shouldApproveInstanceAndBusinessApplication() {
        ApprovalInstanceEntity instance = instance("IN_PROGRESS", 103L, 2L);
        ApprovalTaskEntity task = task(30001L, 40001L, "U2001", "PENDING");
        ApprovalNodeInstanceEntity activeNode = nodeInstance(40001L, 103L, "ACTIVE");
        BusinessDataResponse data = businessData("SUBMITTED");

        when(instanceMapper.selectById(20001L)).thenReturn(instance);
        when(taskMapper.selectById(30001L)).thenReturn(task);
        when(nodeInstanceMapper.selectActiveByInstanceId(20001L)).thenReturn(activeNode);
        when(nodeMapper.selectById(103L)).thenReturn(node(103L, "U2001", "SINGLE"));
        when(transitionMapper.findDefaultNext(10L, 103L)).thenReturn(java.util.Optional.empty());
        when(businessDataClient.get("PURCHASE", "PUR-001")).thenReturn(data);
        doAnswer(invocation -> {
            ((ApprovalSnapshotEntity) invocation.getArgument(0)).setId(50001L);
            return 1;
        }).when(snapshotMapper).insert(any(ApprovalSnapshotEntity.class));
        when(instanceMapper.updateStatusWithVersion(20001L, "IN_PROGRESS", "APPROVED", 2L)).thenReturn(1);
        when(actionMapper.insert(any(ApprovalActionEntity.class))).thenReturn(1);
        when(outboxMapper.insert(any(ApprovalOutboxEventEntity.class))).thenReturn(1);

        ApprovalActionResponse response = service.approve(20001L, 30001L, REQUEST);

        assertEquals("APPROVED", response.status());
        assertTrue(response.currentNodeId() == null);
        verify(businessDataClient).markApproved(10001L);
        verify(instanceMapper).updateStatusWithVersion(20001L, "IN_PROGRESS", "APPROVED", 2L);
    }

    @Test
    void reject_shouldRejectTaskNodeAndInstanceCreateHistoryAndMarkBusinessApplication() {
        ApprovalActionRequest request = new ApprovalActionRequest("U2001", "ACTION-REJECT-001", "金额不符");
        ApprovalInstanceEntity instance = instance("IN_PROGRESS", 101L, 0L);
        ApprovalTaskEntity task = task(30001L, 40001L, "U2001", "PENDING");
        ApprovalNodeInstanceEntity activeNode = nodeInstance(40001L, 101L, "ACTIVE");
        BusinessDataResponse data = businessData("SUBMITTED");

        when(actionMapper.selectByActionRequestId(20001L, request.actionRequestId())).thenReturn(null);
        when(instanceMapper.selectById(20001L)).thenReturn(instance);
        when(taskMapper.selectById(30001L)).thenReturn(task);
        when(nodeInstanceMapper.selectActiveByInstanceId(20001L)).thenReturn(activeNode);
        when(businessDataClient.get("PURCHASE", "PUR-001")).thenReturn(data);
        when(taskMapper.updatePendingToRejected(30001L, 20001L, "金额不符")).thenReturn(1);
        when(nodeInstanceMapper.markRejected(40001L, 20001L)).thenReturn(1);
        when(taskMapper.cancelOtherPendingByInstanceId(20001L, 30001L)).thenReturn(0);
        when(instanceMapper.updateStatusWithVersion(20001L, "IN_PROGRESS", "REJECTED", 0L)).thenReturn(1);
        doAnswer(invocation -> {
            ((ApprovalSnapshotEntity) invocation.getArgument(0)).setId(50002L);
            return 1;
        }).when(snapshotMapper).insert(any(ApprovalSnapshotEntity.class));
        doAnswer(invocation -> {
            ((ApprovalActionEntity) invocation.getArgument(0)).setId(60002L);
            return 1;
        }).when(actionMapper).insert(any(ApprovalActionEntity.class));
        when(outboxMapper.insert(any(ApprovalOutboxEventEntity.class))).thenReturn(1);

        ApprovalActionResponse response = service.reject(20001L, 30001L, request);

        assertEquals("REJECTED", response.status());
        assertEquals("REJECT", response.actionType());
        assertEquals(60002L, response.actionId());
        assertFalse(response.duplicate());
        verify(taskMapper).updatePendingToRejected(30001L, 20001L, "金额不符");
        verify(nodeInstanceMapper).markRejected(40001L, 20001L);
        verify(taskMapper).cancelOtherPendingByInstanceId(20001L, 30001L);
        verify(instanceMapper).updateStatusWithVersion(20001L, "IN_PROGRESS", "REJECTED", 0L);
        verify(businessDataClient).markRejected(10001L);

        ArgumentCaptor<ApprovalSnapshotEntity> snapshotCaptor = ArgumentCaptor.forClass(ApprovalSnapshotEntity.class);
        verify(snapshotMapper).insert(snapshotCaptor.capture());
        assertEquals(2, snapshotCaptor.getValue().getSnapshotNo());
        assertEquals("REJECTED", snapshotCaptor.getValue().getSnapshotType());
        assertEquals(64, snapshotCaptor.getValue().getDataHash().length());

        ArgumentCaptor<ApprovalActionEntity> actionCaptor = ArgumentCaptor.forClass(ApprovalActionEntity.class);
        verify(actionMapper).insert(actionCaptor.capture());
        assertEquals(30001L, actionCaptor.getValue().getTaskId());
        assertEquals(50002L, actionCaptor.getValue().getSnapshotId());

        ArgumentCaptor<ApprovalOutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(ApprovalOutboxEventEntity.class);
        verify(outboxMapper).insert(outboxCaptor.capture());
        assertEquals("APPROVAL_REJECTED", outboxCaptor.getValue().getEventType());
        verify(redisLockService).unlock("approval:action:20001", "action-lock-token");
    }

    @Test
    void reject_withSameActionRequestId_shouldReturnExistingAction() {
        ApprovalActionRequest request = new ApprovalActionRequest("U2001", "ACTION-REJECT-001", "金额不符");
        ApprovalActionEntity existingAction = new ApprovalActionEntity();
        existingAction.setId(60002L);
        existingAction.setActionType("REJECT");
        existingAction.setOperatorId("U2001");
        existingAction.setToStatus("REJECTED");
        when(actionMapper.selectByActionRequestId(20001L, request.actionRequestId())).thenReturn(existingAction);
        when(instanceMapper.selectById(20001L)).thenReturn(instance("IN_PROGRESS", 101L, 0L));

        ApprovalActionResponse response = service.reject(20001L, 30001L, request);

        assertTrue(response.duplicate());
        assertEquals("REJECTED", response.status());
        verify(taskMapper, never()).updatePendingToRejected(anyLong(), anyLong(), any());
        verify(businessDataClient, never()).get(any(), any());
        verify(businessDataClient, never()).markRejected(anyLong());
        verify(redisLockService).unlock("approval:action:20001", "action-lock-token");
    }

    @Test
    void reject_whenOperatorIsNotTaskAssignee_shouldReturnForbidden() {
        ApprovalActionRequest request = new ApprovalActionRequest("U2002", "ACTION-REJECT-002", null);
        when(actionMapper.selectByActionRequestId(20001L, request.actionRequestId())).thenReturn(null);
        when(instanceMapper.selectById(20001L)).thenReturn(instance("IN_PROGRESS", 101L, 0L));
        when(taskMapper.selectById(30001L)).thenReturn(task(30001L, 40001L, "U2001", "PENDING"));

        ApprovalSubmitException exception = org.junit.jupiter.api.Assertions.assertThrows(ApprovalSubmitException.class,
                () -> service.reject(20001L, 30001L, request));

        assertEquals("TASK_OPERATOR_FORBIDDEN", exception.getCode());
        assertEquals(403, exception.getStatus().value());
        verify(businessDataClient, never()).get(any(), any());
        verify(redisLockService).unlock("approval:action:20001", "action-lock-token");
    }

    @Test
    void andApproval_shouldBeRejectedAsUnsupported() {
        ApprovalActionRequest request = new ApprovalActionRequest("U2001", "ACTION-AND-001", "同意");
        ApprovalInstanceEntity instance = instance("IN_PROGRESS", 101L, 0L);
        ApprovalTaskEntity task = task(30001L, 40001L, "U2001", "PENDING");
        ApprovalNodeInstanceEntity activeNode = nodeInstance(40001L, 101L, "ACTIVE");

        when(actionMapper.selectByActionRequestId(20001L, request.actionRequestId())).thenReturn(null);
        when(instanceMapper.selectById(20001L)).thenReturn(instance);
        when(taskMapper.selectById(30001L)).thenReturn(task);
        when(nodeInstanceMapper.selectActiveByInstanceId(20001L)).thenReturn(activeNode);
        when(nodeMapper.selectById(101L)).thenReturn(node(101L, "U2001,U2002", "AND"));
        ApprovalActionException exception = assertThrows(ApprovalActionException.class,
                () -> service.approve(20001L, 30001L, request));

        assertEquals("APPROVAL_MODE_UNSUPPORTED", exception.getCode());
        verify(taskMapper, never()).updatePendingToApproved(anyLong(), anyLong(), any(), any());
        verify(nodeInstanceMapper, never()).markCompleted(anyLong(), anyLong());
        verify(businessDataClient, never()).get(any(), any());
    }

    @Test
    void orApproval_shouldCompleteNodeAndCancelOtherPendingTasks() {
        ApprovalActionRequest request = new ApprovalActionRequest("U2001", "ACTION-OR-001", "同意");
        ApprovalInstanceEntity instance = instance("IN_PROGRESS", 101L, 0L);
        ApprovalTaskEntity task = task(30001L, 40001L, "U2001", "PENDING");
        ApprovalNodeInstanceEntity activeNode = nodeInstance(40001L, 101L, "ACTIVE");

        when(actionMapper.selectByActionRequestId(20001L, request.actionRequestId())).thenReturn(null);
        when(instanceMapper.selectById(20001L)).thenReturn(instance);
        when(taskMapper.selectById(30001L)).thenReturn(task);
        when(nodeInstanceMapper.selectActiveByInstanceId(20001L)).thenReturn(activeNode);
        when(nodeMapper.selectById(101L)).thenReturn(node(101L, "U2001,U2002", "OR"));
        when(transitionMapper.findDefaultNext(10L, 101L)).thenReturn(java.util.Optional.empty());
        when(businessDataClient.get("PURCHASE", "PUR-001")).thenReturn(businessData("SUBMITTED"));
        when(taskMapper.updatePendingToApproved(30001L, 20001L, "U2001", "同意")).thenReturn(1);
        when(taskMapper.cancelOtherPendingByNodeInstanceId(40001L, 30001L)).thenReturn(1);
        when(nodeInstanceMapper.markCompleted(40001L, 20001L)).thenReturn(1);
        when(instanceMapper.updateStatusWithVersion(20001L, "IN_PROGRESS", "APPROVED", 0L)).thenReturn(1);
        doAnswer(invocation -> {
            ((ApprovalSnapshotEntity) invocation.getArgument(0)).setId(50004L);
            return 1;
        }).when(snapshotMapper).insert(any(ApprovalSnapshotEntity.class));
        when(actionMapper.insert(any(ApprovalActionEntity.class))).thenReturn(1);
        when(outboxMapper.insert(any(ApprovalOutboxEventEntity.class))).thenReturn(1);

        ApprovalActionResponse response = service.approve(20001L, 30001L, request);

        assertEquals("APPROVED", response.status());
        verify(taskMapper).cancelOtherPendingByNodeInstanceId(40001L, 30001L);
        verify(businessDataClient).markApproved(10001L);
    }

    private ApprovalInstanceEntity instance(String status, long currentNodeId, long lockVersion) {
        ApprovalInstanceEntity instance = new ApprovalInstanceEntity();
        instance.setId(20001L);
        instance.setApprovalNo("APR-TEST-001");
        instance.setApplicationId(10001L);
        instance.setBusinessType("PURCHASE");
        instance.setBusinessId("PUR-001");
        instance.setProcessId(10L);
        instance.setStatus(status);
        instance.setCurrentNodeId(currentNodeId);
        instance.setLockVersion(lockVersion);
        return instance;
    }

    private ApprovalTaskEntity task(long id, long nodeInstanceId, String assigneeId, String status) {
        ApprovalTaskEntity task = new ApprovalTaskEntity();
        task.setId(id);
        task.setApprovalInstanceId(20001L);
        task.setNodeInstanceId(nodeInstanceId);
        task.setAssigneeId(assigneeId);
        task.setStatus(status);
        return task;
    }

    private ApprovalNodeInstanceEntity nodeInstance(long id, long nodeId, String status) {
        ApprovalNodeInstanceEntity node = new ApprovalNodeInstanceEntity();
        node.setId(id);
        node.setApprovalInstanceId(20001L);
        node.setNodeId(nodeId);
        node.setStatus(status);
        return node;
    }

    private ApprovalNodeEntity node(long id, String approver) {
        return node(id, approver, "SINGLE");
    }

    private ApprovalNodeEntity node(long id, String approver, String mode) {
        ApprovalNodeEntity node = new ApprovalNodeEntity();
        node.setId(id);
        node.setNodeType("APPROVAL");
        node.setApproverValue(approver);
        node.setApprovalMode(mode);
        return node;
    }

    private BusinessDataResponse businessData(String status) {
        return new BusinessDataResponse(10001L, "APP-001", "PURCHASE", "PUR-001", "测试采购",
                "U1001", status, objectMapper.createObjectNode().put("amount", 1280));
    }
}
