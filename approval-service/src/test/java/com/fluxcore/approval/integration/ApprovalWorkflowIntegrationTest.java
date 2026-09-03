package com.fluxcore.approval.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxcore.approval.dto.ApprovalActionRequest;
import com.fluxcore.approval.dto.ApprovalActionResponse;
import com.fluxcore.approval.dto.BusinessDataResponse;
import com.fluxcore.approval.dto.SubmitApprovalRequest;
import com.fluxcore.approval.dto.SubmitApprovalResponse;
import com.fluxcore.approval.entity.ApprovalActionEntity;
import com.fluxcore.approval.entity.ApprovalInstanceEntity;
import com.fluxcore.approval.entity.ApprovalNodeEntity;
import com.fluxcore.approval.entity.ApprovalNodeInstanceEntity;
import com.fluxcore.approval.entity.ApprovalOutboxEventEntity;
import com.fluxcore.approval.entity.ApprovalProcessEntity;
import com.fluxcore.approval.entity.ApprovalSnapshotEntity;
import com.fluxcore.approval.entity.ApprovalTaskEntity;
import com.fluxcore.approval.entity.ApprovalTransitionEntity;
import com.fluxcore.approval.mapper.ApprovalActionMapper;
import com.fluxcore.approval.mapper.ApprovalInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalNodeInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalNodeMapper;
import com.fluxcore.approval.mapper.ApprovalOutboxEventMapper;
import com.fluxcore.approval.mapper.ApprovalProcessMapper;
import com.fluxcore.approval.mapper.ApprovalSnapshotMapper;
import com.fluxcore.approval.mapper.ApprovalTaskMapper;
import com.fluxcore.approval.mapper.ApprovalTransitionMapper;
import com.fluxcore.approval.service.ApprovalActionService;
import com.fluxcore.approval.service.ApprovalSubmitService;
import com.fluxcore.approval.service.BusinessDataClient;
import com.fluxcore.approval.service.RedisLockService;
import com.fluxcore.approval.state.ApprovalStateMachine;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 流程组件集成测试：组合真实 SubmitService、ActionService 和状态机，
 * 仅把数据库、Redis 及业务 HTTP 边界替换成状态化测试适配器。
 */
class ApprovalWorkflowIntegrationTest {
    @Test
    void purchase_shouldRunThreeLevelSerialApprovalToApproved() {
        Scenario scenario = new Scenario();
        scenario.configure("PURCHASE", "PUR-001", 1001L, 1L,
                List.of("U2001", "U2002", "U2003"), "采购申请");

        SubmitApprovalResponse submitted = scenario.submitService.submit(
                new SubmitApprovalRequest("PURCHASE", "PUR-001", "U1001", "SUBMIT-PUR-001", 1001L));
        assertEquals("IN_PROGRESS", submitted.status());
        assertEquals(101L, submitted.currentNodeId());
        assertNotNull(scenario.pendingTask("U2001"));

        approveCurrent(scenario, submitted.approvalInstanceId(), "U2001", "APPROVE-PUR-1");
        assertEquals(102L, scenario.instance().getCurrentNodeId());
        assertNotNull(scenario.pendingTask("U2002"));

        approveCurrent(scenario, submitted.approvalInstanceId(), "U2002", "APPROVE-PUR-2");
        assertEquals(103L, scenario.instance().getCurrentNodeId());
        assertNotNull(scenario.pendingTask("U2003"));

        ApprovalActionResponse completed = approveCurrent(
                scenario, submitted.approvalInstanceId(), "U2003", "APPROVE-PUR-3");

        assertEquals("APPROVED", completed.status());
        assertNull(completed.currentNodeId());
        assertEquals("APPROVED", scenario.instance().getStatus());
        assertEquals(3L, scenario.instance().getLockVersion());
        assertEquals(3L, scenario.nodeInstances().stream()
                .filter(node -> "COMPLETED".equals(node.getStatus())).count());
        assertEquals(3L, scenario.tasks().stream()
                .filter(task -> "APPROVED".equals(task.getStatus())).count());
        assertEquals(4, scenario.snapshots().size());
        assertEquals(4, scenario.actions().size());
        assertEquals(4, scenario.outboxEvents().size());
        verify(scenario.businessDataClient).markSubmitted(1001L);
        verify(scenario.businessDataClient).markApproved(1001L);
    }

    @Test
    void contract_shouldRunTwoLevelSerialApprovalToApproved() {
        Scenario scenario = new Scenario();
        scenario.configure("CONTRACT_CHANGE", "CCHG-001", 1002L, 2L,
                List.of("U2001", "U2004"), "合同变更");

        SubmitApprovalResponse submitted = scenario.submitService.submit(
                new SubmitApprovalRequest("CONTRACT_CHANGE", "CCHG-001", "U1002", "SUBMIT-CCHG-001", 1002L));
        approveCurrent(scenario, submitted.approvalInstanceId(), "U2001", "APPROVE-CCHG-1");
        ApprovalActionResponse completed = approveCurrent(
                scenario, submitted.approvalInstanceId(), "U2004", "APPROVE-CCHG-2");

        assertEquals("APPROVED", completed.status());
        assertEquals("APPROVED", scenario.instance().getStatus());
        assertEquals(2L, scenario.tasks().stream()
                .filter(task -> "APPROVED".equals(task.getStatus())).count());
        verify(scenario.businessDataClient).markSubmitted(1002L);
        verify(scenario.businessDataClient).markApproved(1002L);
    }

    @Test
    void firstLevelReject_shouldTerminateApprovalAndCancelOtherTasks() {
        Scenario scenario = new Scenario();
        scenario.configure("PURCHASE", "PUR-002", 1003L, 3L,
                List.of("U2001", "U2002"), "采购驳回");

        SubmitApprovalResponse submitted = scenario.submitService.submit(
                new SubmitApprovalRequest("PURCHASE", "PUR-002", "U1003", "SUBMIT-PUR-002", 1003L));
        long taskId = scenario.pendingTask("U2001").getId();
        ApprovalActionResponse rejected = scenario.actionService.reject(
                submitted.approvalInstanceId(), taskId,
                new ApprovalActionRequest("U2001", "REJECT-PUR-1", "金额不符"));

        assertEquals("REJECTED", rejected.status());
        assertEquals("REJECTED", scenario.instance().getStatus());
        assertTrue(scenario.nodeInstances().stream()
                .allMatch(node -> "REJECTED".equals(node.getStatus())));
        assertTrue(scenario.tasks().stream()
                .allMatch(task -> "REJECTED".equals(task.getStatus())));
        assertEquals(2, scenario.snapshots().size());
        assertEquals(2, scenario.actions().size());
        verify(scenario.businessDataClient).markRejected(1003L);
    }

    @Test
    void withdrawAfterFirstApproval_shouldCancelCurrentPendingNode() {
        Scenario scenario = new Scenario();
        scenario.configure("CONTRACT_CHANGE", "CCHG-002", 1004L, 4L,
                List.of("U2001", "U2004"), "合同撤回");

        SubmitApprovalResponse submitted = scenario.submitService.submit(
                new SubmitApprovalRequest("CONTRACT_CHANGE", "CCHG-002", "U1004", "SUBMIT-CCHG-002", 1004L));
        approveCurrent(scenario, submitted.approvalInstanceId(), "U2001", "APPROVE-CCHG-3");

        ApprovalActionResponse withdrawn = scenario.actionService.withdraw(
                submitted.approvalInstanceId(),
                new ApprovalActionRequest("U1004", "WITHDRAW-CCHG-1", "暂缓办理"));

        assertEquals("WITHDRAWN", withdrawn.status());
        assertEquals("WITHDRAWN", scenario.instance().getStatus());
        assertTrue(scenario.tasks().stream().allMatch(task -> "CANCELLED".equals(task.getStatus())
                || "APPROVED".equals(task.getStatus())));
        assertTrue(scenario.nodeInstances().stream()
                .anyMatch(node -> "CANCELLED".equals(node.getStatus())));
        assertEquals(3, scenario.snapshots().size());
        assertEquals(3, scenario.actions().size());
        verify(scenario.businessDataClient).markWithdrawn(1004L);
    }

    private ApprovalActionResponse approveCurrent(Scenario scenario, long approvalId,
                                                   String operatorId, String requestId) {
        ApprovalTaskEntity task = scenario.pendingTask(operatorId);
        assertNotNull(task, "未找到审批人 " + operatorId + " 的待办");
        return scenario.actionService.approve(approvalId, task.getId(),
                new ApprovalActionRequest(operatorId, requestId, "同意"));
    }

    private static final class Scenario {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final RedisLockService redisLockService = mock(RedisLockService.class);
        private final BusinessDataClient businessDataClient = mock(BusinessDataClient.class);
        private final ApprovalProcessMapper processMapper = mock(ApprovalProcessMapper.class);
        private final ApprovalInstanceMapper instanceMapper = mock(ApprovalInstanceMapper.class);
        private final ApprovalNodeInstanceMapper nodeInstanceMapper = mock(ApprovalNodeInstanceMapper.class);
        private final ApprovalNodeMapper nodeMapper = mock(ApprovalNodeMapper.class);
        private final ApprovalTransitionMapper transitionMapper = mock(ApprovalTransitionMapper.class);
        private final ApprovalTaskMapper taskMapper = mock(ApprovalTaskMapper.class);
        private final ApprovalActionMapper actionMapper = mock(ApprovalActionMapper.class);
        private final ApprovalSnapshotMapper snapshotMapper = mock(ApprovalSnapshotMapper.class);
        private final ApprovalOutboxEventMapper outboxMapper = mock(ApprovalOutboxEventMapper.class);

        private final Map<Long, ApprovalInstanceEntity> instanceStore = new HashMap<>();
        private final Map<Long, ApprovalNodeEntity> nodeStore = new HashMap<>();
        private final Map<Long, ApprovalNodeInstanceEntity> nodeInstanceStore = new HashMap<>();
        private final Map<Long, ApprovalTaskEntity> taskStore = new HashMap<>();
        private final Map<Long, ApprovalActionEntity> actionStore = new HashMap<>();
        private final Map<Long, ApprovalSnapshotEntity> snapshotStore = new HashMap<>();
        private final Map<Long, ApprovalOutboxEventEntity> outboxStore = new HashMap<>();
        private final Map<String, ApprovalTransitionEntity> transitionStore = new HashMap<>();
        private final Map<String, BusinessDataResponse> businessDataStore = new HashMap<>();
        private final List<ApprovalNodeEntity> configuredNodes = new ArrayList<>();

        private long nextInstanceId = 10000L;
        private long nextNodeInstanceId = 20000L;
        private long nextTaskId = 30000L;
        private long nextActionId = 40000L;
        private long nextSnapshotId = 50000L;
        private long nextOutboxId = 60000L;
        private ApprovalSubmitService submitService;
        private ApprovalActionService actionService;
        private long approvalInstanceId;

        private Scenario() {
            when(redisLockService.tryLock(anyString(), any(Duration.class))).thenReturn("integration-token");
            doNothing().when(redisLockService).unlock(anyString(), anyString());
            when(businessDataClient.get(anyString(), anyString())).thenAnswer(invocation ->
                    businessDataStore.get(invocation.getArgument(0, String.class) + "/"
                            + invocation.getArgument(1, String.class)));

            when(instanceMapper.findBySubmitRequestId(anyString())).thenAnswer(invocation ->
                    instanceStore.values().stream()
                            .filter(value -> invocation.getArgument(0, String.class).equals(value.getSubmitRequestId()))
                            .findFirst());
            when(instanceMapper.findByApplicationId(anyLong())).thenAnswer(invocation ->
                    instanceStore.values().stream()
                            .filter(value -> invocation.getArgument(0, Long.class).equals(value.getApplicationId()))
                            .findFirst());
            when(instanceMapper.selectById(anyLong())).thenAnswer(invocation ->
                    instanceStore.get(invocation.getArgument(0, Long.class)));
            doAnswer(invocation -> {
                ApprovalInstanceEntity value = invocation.getArgument(0);
                value.setId(++nextInstanceId);
                instanceStore.put(value.getId(), value);
                approvalInstanceId = value.getId();
                return 1;
            }).when(instanceMapper).insert(any(ApprovalInstanceEntity.class));
            when(instanceMapper.updateCurrentNodeWithVersion(anyLong(), anyLong(), anyLong()))
                    .thenAnswer(invocation -> {
                        ApprovalInstanceEntity value = instanceStore.get(invocation.getArgument(0, Long.class));
                        long version = invocation.getArgument(2, Long.class);
                        if (value == null || !"IN_PROGRESS".equals(value.getStatus())
                                || value.getLockVersion() != version) return 0;
                        value.setCurrentNodeId(invocation.getArgument(1, Long.class));
                        value.setLockVersion(version + 1);
                        return 1;
                    });
            when(instanceMapper.updateStatusWithVersion(anyLong(), anyString(), anyString(), anyLong()))
                    .thenAnswer(invocation -> {
                        ApprovalInstanceEntity value = instanceStore.get(invocation.getArgument(0, Long.class));
                        long version = invocation.getArgument(3, Long.class);
                        if (value == null || !invocation.getArgument(1, String.class).equals(value.getStatus())
                                || value.getLockVersion() != version) return 0;
                        value.setStatus(invocation.getArgument(2, String.class));
                        value.setCurrentNodeId(null);
                        value.setCompletedAt(LocalDateTime.now());
                        value.setLockVersion(version + 1);
                        return 1;
                    });

            when(processMapper.findPublished(anyString())).thenAnswer(invocation ->
                    configuredNodes.isEmpty() ? Optional.empty()
                            : Optional.of(processFor(configuredNodes.getFirst().getProcessId())));
            when(processMapper.findFirstApprovalNode(anyLong())).thenAnswer(invocation ->
                    configuredNodes.stream().filter(node -> node.getProcessId().equals(invocation.getArgument(0, Long.class)))
                            .findFirst());
            when(nodeMapper.selectById(anyLong())).thenAnswer(invocation ->
                    nodeStore.get(invocation.getArgument(0, Long.class)));
            when(transitionMapper.findDefaultNext(anyLong(), anyLong())).thenAnswer(invocation ->
                    Optional.ofNullable(transitionStore.get(invocation.getArgument(0, Long.class) + "/"
                            + invocation.getArgument(1, Long.class))));

            when(nodeInstanceMapper.selectActiveByInstanceId(anyLong())).thenAnswer(invocation ->
                    nodeInstanceStore.values().stream()
                            .filter(value -> invocation.getArgument(0, Long.class).equals(value.getApprovalInstanceId())
                                    && "ACTIVE".equals(value.getStatus()))
                            .max(Comparator.comparing(ApprovalNodeInstanceEntity::getId)).orElse(null));
            doAnswer(invocation -> {
                ApprovalNodeInstanceEntity value = invocation.getArgument(0);
                value.setId(++nextNodeInstanceId);
                nodeInstanceStore.put(value.getId(), value);
                return 1;
            }).when(nodeInstanceMapper).insert(any(ApprovalNodeInstanceEntity.class));
            when(nodeInstanceMapper.markCompleted(anyLong(), anyLong())).thenAnswer(invocation ->
                    markNode(invocation.getArgument(0, Long.class), invocation.getArgument(1, Long.class), "COMPLETED"));
            when(nodeInstanceMapper.markRejected(anyLong(), anyLong())).thenAnswer(invocation ->
                    markNode(invocation.getArgument(0, Long.class), invocation.getArgument(1, Long.class), "REJECTED"));
            when(nodeInstanceMapper.markCancelled(anyLong())).thenAnswer(invocation -> {
                ApprovalNodeInstanceEntity value = nodeInstanceStore.get(invocation.getArgument(0, Long.class));
                if (value == null || !"ACTIVE".equals(value.getStatus())) return 0;
                value.setStatus("CANCELLED");
                value.setCompletedAt(LocalDateTime.now());
                return 1;
            });

            when(taskMapper.selectById(anyLong())).thenAnswer(invocation ->
                    taskStore.get(invocation.getArgument(0, Long.class)));
            doAnswer(invocation -> {
                ApprovalTaskEntity value = invocation.getArgument(0);
                value.setId(++nextTaskId);
                taskStore.put(value.getId(), value);
                return 1;
            }).when(taskMapper).insert(any(ApprovalTaskEntity.class));
            when(taskMapper.updatePendingToApproved(anyLong(), anyLong(), anyString(), any()))
                    .thenAnswer(invocation -> updateTask(invocation.getArgument(0, Long.class),
                            invocation.getArgument(1, Long.class), invocation.getArgument(2, String.class), "APPROVED", "APPROVE"));
            when(taskMapper.updatePendingToRejected(anyLong(), anyLong(), any()))
                    .thenAnswer(invocation -> updateTask(invocation.getArgument(0, Long.class),
                            invocation.getArgument(1, Long.class), null, "REJECTED", "REJECT"));
            when(taskMapper.transferPendingTask(anyLong(), anyLong(), anyString(), any()))
                    .thenAnswer(invocation -> updateTask(invocation.getArgument(0, Long.class),
                            invocation.getArgument(1, Long.class), invocation.getArgument(2, String.class), "TRANSFERRED", "TRANSFER"));
            when(taskMapper.countPendingByNodeInstanceId(anyLong())).thenAnswer(invocation -> (int) taskStore.values().stream()
                    .filter(value -> invocation.getArgument(0, Long.class).equals(value.getNodeInstanceId())
                            && "PENDING".equals(value.getStatus())).count());
            when(taskMapper.countPendingByNodeAndAssignee(anyLong(), anyString())).thenAnswer(invocation -> (int) taskStore.values().stream()
                    .filter(value -> invocation.getArgument(0, Long.class).equals(value.getNodeInstanceId())
                            && invocation.getArgument(1, String.class).equals(value.getAssigneeId())
                            && "PENDING".equals(value.getStatus())).count());
            when(taskMapper.cancelOtherPendingByNodeInstanceId(anyLong(), anyLong())).thenAnswer(invocation -> {
                int changed = 0;
                for (ApprovalTaskEntity value : taskStore.values()) {
                    if (invocation.getArgument(0, Long.class).equals(value.getNodeInstanceId())
                            && !invocation.getArgument(1, Long.class).equals(value.getId())
                            && "PENDING".equals(value.getStatus())) {
                        value.setStatus("CANCELLED");
                        changed++;
                    }
                }
                return changed;
            });
            when(taskMapper.cancelOtherPendingByInstanceId(anyLong(), anyLong())).thenAnswer(invocation -> {
                int changed = 0;
                for (ApprovalTaskEntity value : taskStore.values()) {
                    if (invocation.getArgument(0, Long.class).equals(value.getApprovalInstanceId())
                            && !invocation.getArgument(1, Long.class).equals(value.getId())
                            && "PENDING".equals(value.getStatus())) {
                        value.setStatus("CANCELLED");
                        changed++;
                    }
                }
                return changed;
            });
            when(taskMapper.cancelPendingByInstanceId(anyLong())).thenAnswer(invocation -> {
                int changed = 0;
                for (ApprovalTaskEntity value : taskStore.values()) {
                    if (invocation.getArgument(0, Long.class).equals(value.getApprovalInstanceId())
                            && "PENDING".equals(value.getStatus())) {
                        value.setStatus("CANCELLED");
                        value.setAction("WITHDRAW");
                        changed++;
                    }
                }
                return changed;
            });

            when(actionMapper.selectByActionRequestId(anyLong(), anyString())).thenAnswer(invocation -> actionStore.values().stream()
                    .filter(value -> invocation.getArgument(0, Long.class).equals(value.getApprovalInstanceId())
                            && invocation.getArgument(1, String.class).equals(value.getActionRequestId()))
                    .findFirst().orElse(null));
            doAnswer(invocation -> {
                ApprovalActionEntity value = invocation.getArgument(0);
                value.setId(++nextActionId);
                actionStore.put(value.getId(), value);
                return 1;
            }).when(actionMapper).insert(any(ApprovalActionEntity.class));
            when(snapshotMapper.selectMaxSnapshotNo(anyLong())).thenAnswer(invocation -> snapshotStore.values().stream()
                    .filter(value -> invocation.getArgument(0, Long.class).equals(value.getApprovalInstanceId()))
                    .mapToInt(ApprovalSnapshotEntity::getSnapshotNo).max().orElse(0));
            doAnswer(invocation -> {
                ApprovalSnapshotEntity value = invocation.getArgument(0);
                value.setId(++nextSnapshotId);
                snapshotStore.put(value.getId(), value);
                return 1;
            }).when(snapshotMapper).insert(any(ApprovalSnapshotEntity.class));
            doAnswer(invocation -> {
                ApprovalOutboxEventEntity value = invocation.getArgument(0);
                value.setId(++nextOutboxId);
                outboxStore.put(value.getId(), value);
                return 1;
            }).when(outboxMapper).insert(any(ApprovalOutboxEventEntity.class));
        }

        private void configure(String businessType, String businessId, long applicationId, long processId,
                               List<String> approvers, String title) {
            ApprovalProcessEntity process = processFor(processId);
            configuredNodes.clear();
            nodeStore.clear();
            transitionStore.clear();
            for (int i = 0; i < approvers.size(); i++) {
                ApprovalNodeEntity node = new ApprovalNodeEntity();
                node.setId(101L + i);
                node.setProcessId(processId);
                node.setNodeType("APPROVAL");
                node.setNodeName("第" + (i + 1) + "级审批");
                node.setApprovalMode("SINGLE");
                node.setApproverValue(approvers.get(i));
                node.setSequenceNo(i + 1);
                configuredNodes.add(node);
                nodeStore.put(node.getId(), node);
                if (i > 0) {
                    ApprovalTransitionEntity transition = new ApprovalTransitionEntity();
                    transition.setProcessId(processId);
                    transition.setFromNodeId(100L + i);
                    transition.setToNodeId(node.getId());
                    transitionStore.put(processId + "/" + (100L + i), transition);
                }
            }
            when(processMapper.findPublished(businessType)).thenReturn(Optional.of(process));
            when(processMapper.findFirstApprovalNode(processId)).thenReturn(Optional.of(configuredNodes.getFirst()));
            BusinessDataResponse data = new BusinessDataResponse(applicationId, "APP-" + applicationId,
                    businessType, businessId, title, "U" + applicationId, "DRAFT",
                    objectMapper.createObjectNode().put("title", title));
            businessDataStore.put(businessType + "/" + businessId, data);
            submitService = new ApprovalSubmitService(objectMapper, redisLockService, businessDataClient,
                    processMapper, instanceMapper, nodeInstanceMapper, taskMapper, actionMapper,
                    snapshotMapper, outboxMapper);
            actionService = new ApprovalActionService(objectMapper, redisLockService, businessDataClient,
                    instanceMapper, nodeInstanceMapper, taskMapper, nodeMapper, transitionMapper,
                    actionMapper, snapshotMapper, outboxMapper, new ApprovalStateMachine());
        }

        private ApprovalProcessEntity processFor(long processId) {
            ApprovalProcessEntity process = new ApprovalProcessEntity();
            process.setId(processId);
            process.setBusinessType("TEST");
            return process;
        }

        private int markNode(long nodeInstanceId, long approvalId, String status) {
            ApprovalNodeInstanceEntity value = nodeInstanceStore.get(nodeInstanceId);
            if (value == null || !approvalIdEquals(value, approvalId) || !"ACTIVE".equals(value.getStatus())) return 0;
            value.setStatus(status);
            value.setCompletedAt(LocalDateTime.now());
            return 1;
        }

        private boolean approvalIdEquals(ApprovalNodeInstanceEntity value, long approvalId) {
            return Long.valueOf(approvalId).equals(value.getApprovalInstanceId());
        }

        private int updateTask(long taskId, long approvalId, String operatorId, String status, String action) {
            ApprovalTaskEntity value = taskStore.get(taskId);
            if (value == null || !Long.valueOf(approvalId).equals(value.getApprovalInstanceId())
                    || (operatorId != null && !operatorId.equals(value.getAssigneeId()))
                    || !"PENDING".equals(value.getStatus())) return 0;
            value.setStatus(status);
            value.setAction(action);
            value.setActedAt(LocalDateTime.now());
            return 1;
        }

        private ApprovalTaskEntity pendingTask(String assigneeId) {
            return taskStore.values().stream()
                    .filter(task -> assigneeId.equals(task.getAssigneeId()) && "PENDING".equals(task.getStatus()))
                    .findFirst().orElse(null);
        }

        private ApprovalInstanceEntity instance() {
            return instanceStore.get(approvalInstanceId);
        }

        private List<ApprovalNodeInstanceEntity> nodeInstances() {
            return new ArrayList<>(nodeInstanceStore.values());
        }

        private List<ApprovalTaskEntity> tasks() {
            return new ArrayList<>(taskStore.values());
        }

        private List<ApprovalActionEntity> actions() {
            return new ArrayList<>(actionStore.values());
        }

        private List<ApprovalSnapshotEntity> snapshots() {
            return new ArrayList<>(snapshotStore.values());
        }

        private List<ApprovalOutboxEventEntity> outboxEvents() {
            return new ArrayList<>(outboxStore.values());
        }
    }
}
