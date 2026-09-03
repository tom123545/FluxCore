package com.fluxcore.approval.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxcore.approval.dto.ApprovalActionRequest;
import com.fluxcore.approval.dto.ApprovalAddSignRequest;
import com.fluxcore.approval.dto.ApprovalTransferRequest;
import com.fluxcore.approval.dto.BusinessDataResponse;
import com.fluxcore.approval.dto.SubmitApprovalRequest;
import com.fluxcore.approval.entity.ApprovalInstanceEntity;
import com.fluxcore.approval.entity.ApprovalNodeEntity;
import com.fluxcore.approval.entity.ApprovalNodeInstanceEntity;
import com.fluxcore.approval.entity.ApprovalProcessEntity;
import com.fluxcore.approval.entity.ApprovalTaskEntity;
import com.fluxcore.approval.mapper.ApprovalActionMapper;
import com.fluxcore.approval.mapper.ApprovalInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalNodeInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalNodeMapper;
import com.fluxcore.approval.mapper.ApprovalOutboxEventMapper;
import com.fluxcore.approval.mapper.ApprovalProcessMapper;
import com.fluxcore.approval.mapper.ApprovalSnapshotMapper;
import com.fluxcore.approval.mapper.ApprovalTaskMapper;
import com.fluxcore.approval.mapper.ApprovalTransitionMapper;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalBoundaryTest {
    private static final SubmitApprovalRequest SUBMIT =
            new SubmitApprovalRequest("PURCHASE", "PUR-001", "U1001", "REQ-001", 100L);

    @Mock private RedisLockService redisLockService;
    @Mock private BusinessDataClient businessDataClient;
    @Mock private ApprovalProcessMapper processMapper;
    @Mock private ApprovalInstanceMapper instanceMapper;
    @Mock private ApprovalNodeInstanceMapper nodeInstanceMapper;
    @Mock private ApprovalNodeMapper nodeMapper;
    @Mock private ApprovalTransitionMapper transitionMapper;
    @Mock private ApprovalTaskMapper taskMapper;
    @Mock private ApprovalActionMapper actionMapper;
    @Mock private ApprovalSnapshotMapper snapshotMapper;
    @Mock private ApprovalOutboxEventMapper outboxMapper;

    private ApprovalSubmitService submitService;
    private ApprovalActionService actionService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        submitService = new ApprovalSubmitService(objectMapper, redisLockService, businessDataClient,
                processMapper, instanceMapper, nodeInstanceMapper, taskMapper, actionMapper,
                snapshotMapper, outboxMapper);
        actionService = new ApprovalActionService(objectMapper, redisLockService, businessDataClient,
                instanceMapper, nodeInstanceMapper, taskMapper, nodeMapper, transitionMapper,
                actionMapper, snapshotMapper, outboxMapper);
        when(redisLockService.tryLock(anyString(), any(Duration.class))).thenReturn("test-token");
    }

    @Test
    void submit_whenBusinessDataIsMissing_shouldReturnNotFoundAndReleaseLock() {
        when(instanceMapper.findBySubmitRequestId("REQ-001")).thenReturn(Optional.empty());
        when(businessDataClient.get("PURCHASE", "PUR-001")).thenReturn(null);

        ApprovalSubmitException exception = assertThrows(ApprovalSubmitException.class,
                () -> submitService.submit(SUBMIT));

        assertEquals("BUSINESS_DATA_NOT_FOUND", exception.getCode());
        verify(redisLockService).unlock("approval:submit:PURCHASE:PUR-001", "test-token");
        verify(processMapper, never()).findPublished(anyString());
    }

    @Test
    void submit_whenApplicationIsNotDraft_shouldRejectBeforeCreatingRuntimeData() {
        when(instanceMapper.findBySubmitRequestId("REQ-001")).thenReturn(Optional.empty());
        when(businessDataClient.get("PURCHASE", "PUR-001"))
                .thenReturn(businessData("SUBMITTED", "PURCHASE", "PUR-001"));

        ApprovalSubmitException exception = assertThrows(ApprovalSubmitException.class,
                () -> submitService.submit(SUBMIT));

        assertEquals("APPLICATION_NOT_DRAFT", exception.getCode());
        verify(instanceMapper, never()).insert(any(ApprovalInstanceEntity.class));
        verify(redisLockService).unlock("approval:submit:PURCHASE:PUR-001", "test-token");
    }

    @Test
    void submit_whenBusinessIdentityDoesNotMatch_shouldRejectWithoutLoadingProcess() {
        when(instanceMapper.findBySubmitRequestId("REQ-001")).thenReturn(Optional.empty());
        when(businessDataClient.get("PURCHASE", "PUR-001"))
                .thenReturn(businessData("DRAFT", "CONTRACT_CHANGE", "CCHG-001"));

        ApprovalSubmitException exception = assertThrows(ApprovalSubmitException.class,
                () -> submitService.submit(SUBMIT));

        assertEquals("BUSINESS_DATA_MISMATCH", exception.getCode());
        verify(processMapper, never()).findPublished(anyString());
    }

    @Test
    void submit_whenFirstNodeHasNoApprover_shouldRejectConfiguration() {
        when(instanceMapper.findBySubmitRequestId("REQ-001")).thenReturn(Optional.empty());
        when(businessDataClient.get("PURCHASE", "PUR-001"))
                .thenReturn(businessData("DRAFT", "PURCHASE", "PUR-001"));
        when(instanceMapper.findByApplicationId(100L)).thenReturn(Optional.empty());
        ApprovalProcessEntity process = new ApprovalProcessEntity();
        process.setId(10L);
        when(processMapper.findPublished("PURCHASE")).thenReturn(Optional.of(process));
        ApprovalNodeEntity firstNode = new ApprovalNodeEntity();
        firstNode.setId(101L);
        firstNode.setNodeType("APPROVAL");
        when(processMapper.findFirstApprovalNode(10L)).thenReturn(Optional.of(firstNode));

        ApprovalSubmitException exception = assertThrows(ApprovalSubmitException.class,
                () -> submitService.submit(SUBMIT));

        assertEquals("APPROVER_NOT_CONFIGURED", exception.getCode());
        verify(instanceMapper, never()).insert(any(ApprovalInstanceEntity.class));
    }

    @Test
    void approve_whenInstanceDoesNotExist_shouldReturnNotFoundAndReleaseLock() {
        ApprovalActionRequest request = new ApprovalActionRequest("U2001", "ACTION-001", null);
        when(actionMapper.selectByActionRequestId(100L, "ACTION-001")).thenReturn(null);
        when(instanceMapper.selectById(100L)).thenReturn(null);

        ApprovalActionException exception = assertThrows(ApprovalActionException.class,
                () -> actionService.approve(100L, 200L, request));

        assertEquals("APPROVAL_NOT_FOUND", exception.getCode());
        verify(redisLockService).unlock("approval:action:100", "test-token");
    }

    @Test
    void approve_whenOperatorIsNotTaskAssignee_shouldForbidWithoutReadingBusinessData() {
        ApprovalActionRequest request = new ApprovalActionRequest("U9999", "ACTION-002", null);
        when(actionMapper.selectByActionRequestId(100L, "ACTION-002")).thenReturn(null);
        when(instanceMapper.selectById(100L)).thenReturn(instance("IN_PROGRESS", 101L));
        when(taskMapper.selectById(200L)).thenReturn(task(200L, 300L, "U2001", "PENDING"));

        ApprovalActionException exception = assertThrows(ApprovalActionException.class,
                () -> actionService.approve(100L, 200L, request));

        assertEquals("TASK_OPERATOR_FORBIDDEN", exception.getCode());
        verify(businessDataClient, never()).get(anyString(), anyString());
    }

    @Test
    void transfer_whenTargetIsBlank_shouldRejectBeforeBusinessRead() {
        ApprovalTransferRequest request = new ApprovalTransferRequest("U2001", "ACTION-003", " ", null);
        prepareAction("ACTION-003");
        when(nodeMapper.selectById(101L)).thenReturn(node(101L, "U2001", "SINGLE"));

        ApprovalActionException exception = assertThrows(ApprovalActionException.class,
                () -> actionService.transfer(100L, 200L, request));

        assertEquals("TARGET_ASSIGNEE_REQUIRED", exception.getCode());
        verify(businessDataClient, never()).get(anyString(), anyString());
    }

    @Test
    void addSign_whenTargetEqualsOperator_shouldRejectBeforeCreatingTask() {
        ApprovalAddSignRequest request = new ApprovalAddSignRequest("U2001", "ACTION-004", "U2001", null);
        prepareAction("ACTION-004");
        when(nodeMapper.selectById(101L)).thenReturn(node(101L, "U2001", "SINGLE"));

        ApprovalActionException exception = assertThrows(ApprovalActionException.class,
                () -> actionService.addSign(100L, 200L, request));

        assertEquals("TARGET_ASSIGNEE_INVALID", exception.getCode());
        verify(taskMapper, never()).insert(any(com.fluxcore.approval.entity.ApprovalTaskEntity.class));
    }

    private void prepareAction(String actionRequestId) {
        when(actionMapper.selectByActionRequestId(100L, actionRequestId)).thenReturn(null);
        when(instanceMapper.selectById(100L)).thenReturn(instance("IN_PROGRESS", 101L));
        when(taskMapper.selectById(200L)).thenReturn(task(200L, 300L, "U2001", "PENDING"));
        ApprovalNodeInstanceEntity active = new ApprovalNodeInstanceEntity();
        active.setId(300L);
        active.setApprovalInstanceId(100L);
        active.setNodeId(101L);
        active.setStatus("ACTIVE");
        when(nodeInstanceMapper.selectActiveByInstanceId(100L)).thenReturn(active);
    }

    private ApprovalInstanceEntity instance(String status, long currentNodeId) {
        ApprovalInstanceEntity instance = new ApprovalInstanceEntity();
        instance.setId(100L);
        instance.setApplicationId(1000L);
        instance.setBusinessType("PURCHASE");
        instance.setBusinessId("PUR-001");
        instance.setProcessId(10L);
        instance.setStatus(status);
        instance.setCurrentNodeId(currentNodeId);
        instance.setLockVersion(0L);
        return instance;
    }

    private ApprovalTaskEntity task(long id, long nodeInstanceId, String assigneeId, String status) {
        ApprovalTaskEntity task = new ApprovalTaskEntity();
        task.setId(id);
        task.setApprovalInstanceId(100L);
        task.setNodeInstanceId(nodeInstanceId);
        task.setAssigneeId(assigneeId);
        task.setStatus(status);
        return task;
    }

    private ApprovalNodeEntity node(long id, String approver, String mode) {
        ApprovalNodeEntity node = new ApprovalNodeEntity();
        node.setId(id);
        node.setNodeType("APPROVAL");
        node.setApproverValue(approver);
        node.setApprovalMode(mode);
        return node;
    }

    private BusinessDataResponse businessData(String status, String type, String businessId) {
        return new BusinessDataResponse(100L, "APP-001", type, businessId, "测试申请", "U1001", status,
                new ObjectMapper().createObjectNode());
    }
}
