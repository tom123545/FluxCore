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
import com.fluxcore.approval.dto.ApprovalAddSignRequest;
import com.fluxcore.approval.dto.ApprovalTransferRequest;
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
        lenient().when(instanceMapper.touchWithVersion(anyLong(), anyLong())).thenReturn(1);
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
        ArgumentCaptor<ApprovalOutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(ApprovalOutboxEventEntity.class);
        verify(outboxMapper).insert(outboxCaptor.capture());
        assertEquals("APPROVAL_NODE_APPROVED", outboxCaptor.getValue().getEventType());
        assertTrue(outboxCaptor.getValue().getPayloadJson()
                .contains("\"recipientIds\":[\"U2002\",\"U2003\"]"));
        verify(redisLockService).unlock("approval:action:20001", "action-lock-token");
    }

    @Test
    void approve_onFinalNode_shouldApproveInstanceAndBusinessApplication() {
        ApprovalInstanceEntity instance = instance("IN_PROGRESS", 103L, 2L);
        ApprovalTaskEntity task = task(30001L, 40001L, "U2001", "PENDING");
        ApprovalNodeInstanceEntity activeNode = nodeInstance(40001L, 103L, "ACTIVE");
        ApprovalTransitionEntity transition = transition(103L, 104L);
        BusinessDataResponse data = businessData("SUBMITTED");
        ApprovalActionRequest request = new ApprovalActionRequest("U2001", "ACTION-FINAL-END", 2L, "同意");

        when(instanceMapper.selectById(20001L)).thenReturn(instance);
        when(taskMapper.selectById(30001L)).thenReturn(task);
        when(nodeInstanceMapper.selectActiveByInstanceId(20001L)).thenReturn(activeNode);
        when(nodeMapper.selectById(103L)).thenReturn(node(103L, "U2001", "SINGLE"));
        when(transitionMapper.findDefaultNext(10L, 103L)).thenReturn(java.util.Optional.of(transition));
        when(nodeMapper.selectById(104L)).thenReturn(endNode(104L));
        when(taskMapper.countPendingByNodeInstanceId(40001L)).thenReturn(1);
        when(businessDataClient.get("PURCHASE", "PUR-001")).thenReturn(data);
        doAnswer(invocation -> {
            ((ApprovalSnapshotEntity) invocation.getArgument(0)).setId(50001L);
            return 1;
        }).when(snapshotMapper).insert(any(ApprovalSnapshotEntity.class));
        when(instanceMapper.updateStatusWithVersion(20001L, "IN_PROGRESS", "APPROVED", 2L)).thenReturn(1);
        when(actionMapper.insert(any(ApprovalActionEntity.class))).thenReturn(1);
        when(outboxMapper.insert(any(ApprovalOutboxEventEntity.class))).thenReturn(1);

        ApprovalActionResponse response = service.approve(20001L, 30001L, request);

        assertEquals("APPROVED", response.status());
        assertTrue(response.currentNodeId() == null);
        verify(businessDataClient).markApproved(10001L);
        verify(instanceMapper).updateStatusWithVersion(20001L, "IN_PROGRESS", "APPROVED", 2L);
        verify(nodeInstanceMapper, never()).insert(any(ApprovalNodeInstanceEntity.class));
        verify(taskMapper, never()).insert(any(ApprovalTaskEntity.class));
        ArgumentCaptor<ApprovalOutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(ApprovalOutboxEventEntity.class);
        verify(outboxMapper).insert(outboxCaptor.capture());
        assertEquals("APPROVAL_APPROVED", outboxCaptor.getValue().getEventType());
        assertTrue(outboxCaptor.getValue().getPayloadJson()
                .contains("\"recipientIds\":[\"U1001\"]"));
    }

    @Test
    void approve_onCompletedNodeWithoutTransition_shouldReturnConfigurationError() {
        ApprovalInstanceEntity instance = instance("IN_PROGRESS", 103L, 2L);
        ApprovalTaskEntity task = task(30001L, 40001L, "U2001", "PENDING");
        ApprovalNodeInstanceEntity activeNode = nodeInstance(40001L, 103L, "ACTIVE");
        ApprovalActionRequest request = new ApprovalActionRequest("U2001", "ACTION-MISSING-ROUTE", 2L, "同意");

        when(instanceMapper.selectById(20001L)).thenReturn(instance);
        when(taskMapper.selectById(30001L)).thenReturn(task);
        when(nodeInstanceMapper.selectActiveByInstanceId(20001L)).thenReturn(activeNode);
        when(nodeMapper.selectById(103L)).thenReturn(node(103L, "U2001", "SINGLE"));
        when(transitionMapper.findDefaultNext(10L, 103L)).thenReturn(java.util.Optional.empty());
        when(taskMapper.countPendingByNodeInstanceId(40001L)).thenReturn(1);

        ApprovalActionException exception = assertThrows(ApprovalActionException.class,
                () -> service.approve(20001L, 30001L, request));

        assertEquals("NEXT_NODE_NOT_CONFIGURED", exception.getCode());
        assertEquals(422, exception.getStatus().value());
        verify(taskMapper, never()).updatePendingToApproved(anyLong(), anyLong(), any(), any());
        verify(instanceMapper, never()).updateStatusWithVersion(anyLong(), any(), any(), anyLong());
        verify(businessDataClient, never()).get(any(), any());
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
        assertEquals(ApprovalActionRequestFingerprint.reject(30001L, request),
                actionCaptor.getValue().getRequestHash());

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
        existingAction.setRequestHash(ApprovalActionRequestFingerprint.reject(30001L, request));
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
    void approve_reusingActionRequestIdWithDifferentVersion_shouldReturnConflict() {
        ApprovalActionRequest originalRequest =
                new ApprovalActionRequest("U2001", "ACTION-REUSED-VERSION-001", 0L, "同意");
        ApprovalActionRequest reusedRequest =
                new ApprovalActionRequest("U2001", "ACTION-REUSED-VERSION-001", 1L, "同意");
        ApprovalActionEntity existingAction = new ApprovalActionEntity();
        existingAction.setId(60010L);
        existingAction.setActionType("APPROVE");
        existingAction.setOperatorId("U2001");
        existingAction.setRequestHash(ApprovalActionRequestFingerprint.approve(30001L, originalRequest));
        when(actionMapper.selectByActionRequestId(20001L, reusedRequest.actionRequestId())).thenReturn(existingAction);

        ApprovalActionException exception = assertThrows(ApprovalActionException.class,
                () -> service.approve(20001L, 30001L, reusedRequest));

        assertEquals("ACTION_REQUEST_ID_REUSED", exception.getCode());
        assertEquals(409, exception.getStatus().value());
        verify(instanceMapper, never()).selectById(anyLong());
        verify(taskMapper, never()).updatePendingToApproved(anyLong(), anyLong(), any(), any());
        verify(actionMapper, never()).insert(any(ApprovalActionEntity.class));
    }

    @Test
    void transfer_reusingActionRequestIdWithDifferentTarget_shouldReturnConflict() {
        ApprovalTransferRequest originalRequest =
                new ApprovalTransferRequest("U2001", "ACTION-REUSED-TRANSFER-001", 0L, "U2005", "转审");
        ApprovalTransferRequest reusedRequest =
                new ApprovalTransferRequest("U2001", "ACTION-REUSED-TRANSFER-001", 0L, "U2006", "转审");
        ApprovalActionEntity existingAction = new ApprovalActionEntity();
        existingAction.setId(60011L);
        existingAction.setActionType("TRANSFER");
        existingAction.setOperatorId("U2001");
        existingAction.setRequestHash(ApprovalActionRequestFingerprint.transfer(30001L, originalRequest));
        when(actionMapper.selectByActionRequestId(20001L, reusedRequest.actionRequestId())).thenReturn(existingAction);

        ApprovalActionException exception = assertThrows(ApprovalActionException.class,
                () -> service.transfer(20001L, 30001L, reusedRequest));

        assertEquals("ACTION_REQUEST_ID_REUSED", exception.getCode());
        assertEquals(409, exception.getStatus().value());
        verify(instanceMapper, never()).selectById(anyLong());
        verify(taskMapper, never()).transferPendingTask(anyLong(), anyLong(), any(), any());
        verify(actionMapper, never()).insert(any(ApprovalActionEntity.class));
    }

    @Test
    void addSign_reusingActionRequestIdWithDifferentTarget_shouldReturnConflict() {
        ApprovalAddSignRequest originalRequest =
                new ApprovalAddSignRequest("U2001", "ACTION-REUSED-ADD-SIGN-001", 0L, "U2005", "加签");
        ApprovalAddSignRequest reusedRequest =
                new ApprovalAddSignRequest("U2001", "ACTION-REUSED-ADD-SIGN-001", 0L, "U2006", "加签");
        ApprovalActionEntity existingAction = new ApprovalActionEntity();
        existingAction.setId(60012L);
        existingAction.setActionType("ADD_SIGN");
        existingAction.setOperatorId("U2001");
        existingAction.setRequestHash(ApprovalActionRequestFingerprint.addSign(30001L, originalRequest));
        when(actionMapper.selectByActionRequestId(20001L, reusedRequest.actionRequestId())).thenReturn(existingAction);

        ApprovalActionException exception = assertThrows(ApprovalActionException.class,
                () -> service.addSign(20001L, 30001L, reusedRequest));

        assertEquals("ACTION_REQUEST_ID_REUSED", exception.getCode());
        assertEquals(409, exception.getStatus().value());
        verify(instanceMapper, never()).selectById(anyLong());
        verify(taskMapper, never()).insert(any(ApprovalTaskEntity.class));
        verify(actionMapper, never()).insert(any(ApprovalActionEntity.class));
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
        when(transitionMapper.findDefaultNext(10L, 101L)).thenReturn(java.util.Optional.of(transition(101L, 104L)));
        when(nodeMapper.selectById(104L)).thenReturn(endNode(104L));
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

    @Test
    void approve_afterAddSign_shouldWaitForAddedTask() {
        ApprovalActionRequest request = new ApprovalActionRequest("U2001", "ACTION-ADD-SIGN-APPROVE-001", "同意");
        ApprovalInstanceEntity instance = instance("IN_PROGRESS", 101L, 0L);
        ApprovalTaskEntity task = task(30001L, 40001L, "U2001", "PENDING");
        ApprovalNodeInstanceEntity activeNode = nodeInstance(40001L, 101L, "ACTIVE");

        when(actionMapper.selectByActionRequestId(20001L, request.actionRequestId())).thenReturn(null);
        when(instanceMapper.selectById(20001L)).thenReturn(instance);
        when(taskMapper.selectById(30001L)).thenReturn(task);
        when(nodeInstanceMapper.selectActiveByInstanceId(20001L)).thenReturn(activeNode);
        when(nodeMapper.selectById(101L)).thenReturn(node(101L, "U2001", "SINGLE"));
        when(transitionMapper.findDefaultNext(10L, 101L)).thenReturn(java.util.Optional.empty());
        when(businessDataClient.get("PURCHASE", "PUR-001")).thenReturn(businessData("SUBMITTED"));
        when(taskMapper.updatePendingToApproved(30001L, 20001L, "U2001", "同意")).thenReturn(1);
        when(taskMapper.countPendingByNodeInstanceId(40001L)).thenReturn(2);
        when(instanceMapper.touchWithVersion(20001L, 0L)).thenReturn(1);
        doAnswer(invocation -> {
            ((ApprovalSnapshotEntity) invocation.getArgument(0)).setId(50005L);
            return 1;
        }).when(snapshotMapper).insert(any(ApprovalSnapshotEntity.class));
        when(actionMapper.insert(any(ApprovalActionEntity.class))).thenReturn(1);
        when(outboxMapper.insert(any(ApprovalOutboxEventEntity.class))).thenReturn(1);

        ApprovalActionResponse response = service.approve(20001L, 30001L, request);

        assertEquals("IN_PROGRESS", response.status());
        assertEquals(101L, response.currentNodeId());
        assertEquals(1L, instance.getLockVersion());
        verify(nodeInstanceMapper, never()).markCompleted(anyLong(), anyLong());
        verify(instanceMapper).touchWithVersion(20001L, 0L);
        verify(transitionMapper).findDefaultNext(10L, 101L);
    }

    @Test
    void transfer_shouldReplacePendingTaskAndWriteHistory() {
        ApprovalTransferRequest request = new ApprovalTransferRequest("U2001", "ACTION-TRANSFER-001", "U2005", "转给财务代理人");
        ApprovalInstanceEntity instance = instance("IN_PROGRESS", 101L, 0L);
        ApprovalTaskEntity task = task(30001L, 40001L, "U2001", "PENDING");
        ApprovalNodeInstanceEntity activeNode = nodeInstance(40001L, 101L, "ACTIVE");

        when(actionMapper.selectByActionRequestId(20001L, request.actionRequestId())).thenReturn(null);
        when(instanceMapper.selectById(20001L)).thenReturn(instance);
        when(taskMapper.selectById(30001L)).thenReturn(task);
        when(nodeInstanceMapper.selectActiveByInstanceId(20001L)).thenReturn(activeNode);
        when(nodeMapper.selectById(101L)).thenReturn(node(101L, "U2001", "SINGLE"));
        when(taskMapper.countPendingByNodeAndAssignee(40001L, "U2005")).thenReturn(0);
        when(businessDataClient.get("PURCHASE", "PUR-001")).thenReturn(businessData("SUBMITTED"));
        when(instanceMapper.touchWithVersion(20001L, 0L)).thenReturn(1);
        when(taskMapper.transferPendingTask(30001L, 20001L, "U2001", "转给财务代理人")).thenReturn(1);
        doAnswer(invocation -> {
            ((ApprovalTaskEntity) invocation.getArgument(0)).setId(30002L);
            return 1;
        }).when(taskMapper).insert(any(ApprovalTaskEntity.class));
        doAnswer(invocation -> {
            ((ApprovalSnapshotEntity) invocation.getArgument(0)).setId(50006L);
            return 1;
        }).when(snapshotMapper).insert(any(ApprovalSnapshotEntity.class));
        doAnswer(invocation -> {
            ((ApprovalActionEntity) invocation.getArgument(0)).setId(60006L);
            return 1;
        }).when(actionMapper).insert(any(ApprovalActionEntity.class));
        when(outboxMapper.insert(any(ApprovalOutboxEventEntity.class))).thenReturn(1);

        ApprovalActionResponse response = service.transfer(20001L, 30001L, request);

        assertEquals("IN_PROGRESS", response.status());
        assertEquals("TRANSFER", response.actionType());
        assertEquals(60006L, response.actionId());
        assertEquals(1L, instance.getLockVersion());
        ArgumentCaptor<ApprovalTaskEntity> taskCaptor = ArgumentCaptor.forClass(ApprovalTaskEntity.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertEquals("U2005", taskCaptor.getValue().getAssigneeId());
        assertEquals(30001L, taskCaptor.getValue().getSourceTaskId());
        assertEquals("PENDING", taskCaptor.getValue().getStatus());
        ArgumentCaptor<ApprovalSnapshotEntity> snapshotCaptor = ArgumentCaptor.forClass(ApprovalSnapshotEntity.class);
        verify(snapshotMapper).insert(snapshotCaptor.capture());
        assertEquals("TRANSFER", snapshotCaptor.getValue().getSnapshotType());
        verify(taskMapper).transferPendingTask(30001L, 20001L, "U2001", "转给财务代理人");
        verify(instanceMapper).touchWithVersion(20001L, 0L);
    }

    @Test
    void addSign_shouldCreatePendingTaskAndWriteHistory() {
        ApprovalAddSignRequest request = new ApprovalAddSignRequest("U2001", "ACTION-ADD-SIGN-001", "U2005", "增加财务复核");
        ApprovalInstanceEntity instance = instance("IN_PROGRESS", 101L, 0L);
        ApprovalTaskEntity task = task(30001L, 40001L, "U2001", "PENDING");
        ApprovalNodeInstanceEntity activeNode = nodeInstance(40001L, 101L, "ACTIVE");

        when(actionMapper.selectByActionRequestId(20001L, request.actionRequestId())).thenReturn(null);
        when(instanceMapper.selectById(20001L)).thenReturn(instance);
        when(taskMapper.selectById(30001L)).thenReturn(task);
        when(nodeInstanceMapper.selectActiveByInstanceId(20001L)).thenReturn(activeNode);
        when(nodeMapper.selectById(101L)).thenReturn(node(101L, "U2001", "SINGLE"));
        when(taskMapper.countPendingByNodeAndAssignee(40001L, "U2005")).thenReturn(0);
        when(businessDataClient.get("PURCHASE", "PUR-001")).thenReturn(businessData("SUBMITTED"));
        when(instanceMapper.touchWithVersion(20001L, 0L)).thenReturn(1);
        doAnswer(invocation -> {
            ((ApprovalTaskEntity) invocation.getArgument(0)).setId(30003L);
            return 1;
        }).when(taskMapper).insert(any(ApprovalTaskEntity.class));
        doAnswer(invocation -> {
            ((ApprovalSnapshotEntity) invocation.getArgument(0)).setId(50007L);
            return 1;
        }).when(snapshotMapper).insert(any(ApprovalSnapshotEntity.class));
        doAnswer(invocation -> {
            ((ApprovalActionEntity) invocation.getArgument(0)).setId(60007L);
            return 1;
        }).when(actionMapper).insert(any(ApprovalActionEntity.class));
        when(outboxMapper.insert(any(ApprovalOutboxEventEntity.class))).thenReturn(1);

        ApprovalActionResponse response = service.addSign(20001L, 30001L, request);

        assertEquals("IN_PROGRESS", response.status());
        assertEquals("ADD_SIGN", response.actionType());
        assertEquals(60007L, response.actionId());
        assertEquals(1L, instance.getLockVersion());
        ArgumentCaptor<ApprovalTaskEntity> taskCaptor = ArgumentCaptor.forClass(ApprovalTaskEntity.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertEquals("U2005", taskCaptor.getValue().getAssigneeId());
        assertEquals(30001L, taskCaptor.getValue().getSourceTaskId());
        assertEquals("PENDING", taskCaptor.getValue().getStatus());
        ArgumentCaptor<ApprovalSnapshotEntity> snapshotCaptor = ArgumentCaptor.forClass(ApprovalSnapshotEntity.class);
        verify(snapshotMapper).insert(snapshotCaptor.capture());
        assertEquals("ADD_SIGN", snapshotCaptor.getValue().getSnapshotType());
        verify(instanceMapper).touchWithVersion(20001L, 0L);
    }

    @Test
    void approve_withStaleExpectedVersion_shouldRejectBeforeWritingAggregateRecords() {
        ApprovalActionRequest request = new ApprovalActionRequest("U2001", "ACTION-STALE-001", 1L, "同意");
        ApprovalInstanceEntity instance = instance("IN_PROGRESS", 101L, 0L);

        when(actionMapper.selectByActionRequestId(20001L, request.actionRequestId())).thenReturn(null);
        when(instanceMapper.selectById(20001L)).thenReturn(instance);

        ApprovalActionException exception = assertThrows(ApprovalActionException.class,
                () -> service.approve(20001L, 30001L, request));

        assertEquals("APPROVAL_VERSION_CONFLICT", exception.getCode());
        verify(taskMapper, never()).selectById(anyLong());
        verify(taskMapper, never()).updatePendingToApproved(anyLong(), anyLong(), any(), any());
        verify(taskMapper, never()).insert(any(ApprovalTaskEntity.class));
        verify(snapshotMapper, never()).insert(any(ApprovalSnapshotEntity.class));
        verify(actionMapper, never()).insert(any(ApprovalActionEntity.class));
        verify(outboxMapper, never()).insert(any(ApprovalOutboxEventEntity.class));
    }

    @Test
    void transfer_whenVersionReservationConflicts_shouldNotWriteAggregateRecords() {
        ApprovalTransferRequest request =
                new ApprovalTransferRequest("U2001", "ACTION-TRANSFER-CONFLICT-001", "U2005", "转审");
        ApprovalInstanceEntity instance = instance("IN_PROGRESS", 101L, 0L);
        ApprovalTaskEntity task = task(30001L, 40001L, "U2001", "PENDING");
        ApprovalNodeInstanceEntity activeNode = nodeInstance(40001L, 101L, "ACTIVE");

        when(actionMapper.selectByActionRequestId(20001L, request.actionRequestId())).thenReturn(null);
        when(instanceMapper.selectById(20001L)).thenReturn(instance);
        when(taskMapper.selectById(30001L)).thenReturn(task);
        when(nodeInstanceMapper.selectActiveByInstanceId(20001L)).thenReturn(activeNode);
        when(nodeMapper.selectById(101L)).thenReturn(node(101L, "U2001", "SINGLE"));
        when(taskMapper.countPendingByNodeAndAssignee(40001L, "U2005")).thenReturn(0);
        when(businessDataClient.get("PURCHASE", "PUR-001")).thenReturn(businessData("SUBMITTED"));
        when(instanceMapper.touchWithVersion(20001L, 0L)).thenReturn(0);

        ApprovalActionException exception = assertThrows(ApprovalActionException.class,
                () -> service.transfer(20001L, 30001L, request));

        assertEquals("APPROVAL_VERSION_CONFLICT", exception.getCode());
        verify(taskMapper, never()).transferPendingTask(anyLong(), anyLong(), any(), any());
        verify(taskMapper, never()).insert(any(ApprovalTaskEntity.class));
        verify(snapshotMapper, never()).insert(any(ApprovalSnapshotEntity.class));
        verify(actionMapper, never()).insert(any(ApprovalActionEntity.class));
        verify(outboxMapper, never()).insert(any(ApprovalOutboxEventEntity.class));
    }

    @Test
    void addSign_whenTargetAlreadyHasPendingTask_shouldReturnConflict() {
        ApprovalAddSignRequest request = new ApprovalAddSignRequest("U2001", "ACTION-ADD-SIGN-002", "U2005", null);
        when(actionMapper.selectByActionRequestId(20001L, request.actionRequestId())).thenReturn(null);
        when(instanceMapper.selectById(20001L)).thenReturn(instance("IN_PROGRESS", 101L, 0L));
        when(taskMapper.selectById(30001L)).thenReturn(task(30001L, 40001L, "U2001", "PENDING"));
        when(nodeInstanceMapper.selectActiveByInstanceId(20001L)).thenReturn(nodeInstance(40001L, 101L, "ACTIVE"));
        when(nodeMapper.selectById(101L)).thenReturn(node(101L, "U2001", "SINGLE"));
        when(taskMapper.countPendingByNodeAndAssignee(40001L, "U2005")).thenReturn(1);

        ApprovalActionException exception = assertThrows(ApprovalActionException.class,
                () -> service.addSign(20001L, 30001L, request));

        assertEquals("TARGET_TASK_ALREADY_EXISTS", exception.getCode());
        verify(taskMapper, never()).insert(any(ApprovalTaskEntity.class));
        verify(businessDataClient, never()).get(any(), any());
    }

    private ApprovalInstanceEntity instance(String status, long currentNodeId, long lockVersion) {
        ApprovalInstanceEntity instance = new ApprovalInstanceEntity();
        instance.setId(20001L);
        instance.setApprovalNo("APR-TEST-001");
        instance.setApplicationId(10001L);
        instance.setBusinessType("PURCHASE");
        instance.setBusinessId("PUR-001");
        instance.setProcessId(10L);
        instance.setApplicantId("U1001");
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
        node.setProcessId(10L);
        node.setNodeType("APPROVAL");
        node.setApproverValue(approver);
        node.setApprovalMode(mode);
        return node;
    }

    private ApprovalNodeEntity endNode(long id) {
        ApprovalNodeEntity node = new ApprovalNodeEntity();
        node.setId(id);
        node.setProcessId(10L);
        node.setNodeType("END");
        return node;
    }

    private ApprovalTransitionEntity transition(long fromNodeId, long toNodeId) {
        ApprovalTransitionEntity transition = new ApprovalTransitionEntity();
        transition.setProcessId(10L);
        transition.setFromNodeId(fromNodeId);
        transition.setToNodeId(toNodeId);
        return transition;
    }

    private BusinessDataResponse businessData(String status) {
        return new BusinessDataResponse(10001L, "APP-001", "PURCHASE", "PUR-001", "测试采购",
                "U1001", status, objectMapper.createObjectNode().put("amount", 1280));
    }
}
