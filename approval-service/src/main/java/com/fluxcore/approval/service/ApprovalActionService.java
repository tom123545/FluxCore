package com.fluxcore.approval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxcore.approval.dto.ApprovalActionRequest;
import com.fluxcore.approval.dto.ApprovalActionResponse;
import com.fluxcore.approval.dto.BusinessDataResponse;
import com.fluxcore.approval.entity.ApprovalActionEntity;
import com.fluxcore.approval.entity.ApprovalInstanceEntity;
import com.fluxcore.approval.entity.ApprovalNodeInstanceEntity;
import com.fluxcore.approval.entity.ApprovalOutboxEventEntity;
import com.fluxcore.approval.entity.ApprovalSnapshotEntity;
import com.fluxcore.approval.entity.ApprovalTaskEntity;
import com.fluxcore.approval.entity.ApprovalNodeEntity;
import com.fluxcore.approval.entity.ApprovalTransitionEntity;
import com.fluxcore.approval.mapper.ApprovalActionMapper;
import com.fluxcore.approval.mapper.ApprovalInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalNodeInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalNodeMapper;
import com.fluxcore.approval.mapper.ApprovalOutboxEventMapper;
import com.fluxcore.approval.mapper.ApprovalSnapshotMapper;
import com.fluxcore.approval.mapper.ApprovalTaskMapper;
import com.fluxcore.approval.mapper.ApprovalTransitionMapper;
import com.fluxcore.approval.state.ApprovalStateMachine;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalActionService {
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String WITHDRAWN = "WITHDRAWN";
    private static final String WITHDRAW = "WITHDRAW";
    private static final String REJECTED = "REJECTED";
    private static final String REJECT = "REJECT";
    private static final Duration LOCK_LEASE = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final RedisLockService redisLockService;
    private final BusinessDataClient businessDataClient;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalNodeInstanceMapper nodeInstanceMapper;
    private final ApprovalNodeMapper nodeMapper;
    private final ApprovalTransitionMapper transitionMapper;
    private final ApprovalTaskMapper taskMapper;
    private final ApprovalActionMapper actionMapper;
    private final ApprovalSnapshotMapper snapshotMapper;
    private final ApprovalOutboxEventMapper outboxMapper;
    private final ApprovalStateMachine stateMachine;

    /** Spring 使用的完整构造方法。 */
    @Autowired
    public ApprovalActionService(ObjectMapper objectMapper, RedisLockService redisLockService,
                                 BusinessDataClient businessDataClient, ApprovalInstanceMapper instanceMapper,
                                 ApprovalNodeInstanceMapper nodeInstanceMapper, ApprovalTaskMapper taskMapper,
                                 ApprovalNodeMapper nodeMapper, ApprovalTransitionMapper transitionMapper,
                                 ApprovalActionMapper actionMapper, ApprovalSnapshotMapper snapshotMapper,
                                 ApprovalOutboxEventMapper outboxMapper, ApprovalStateMachine stateMachine) {
        this.objectMapper = objectMapper;
        this.redisLockService = redisLockService;
        this.businessDataClient = businessDataClient;
        this.instanceMapper = instanceMapper;
        this.nodeInstanceMapper = nodeInstanceMapper;
        this.nodeMapper = nodeMapper;
        this.transitionMapper = transitionMapper;
        this.taskMapper = taskMapper;
        this.actionMapper = actionMapper;
        this.snapshotMapper = snapshotMapper;
        this.outboxMapper = outboxMapper;
        this.stateMachine = stateMachine;
    }

    /** 保持单元测试和已有调用方兼容；生产环境由 Spring 注入上面的状态机。 */
    public ApprovalActionService(ObjectMapper objectMapper, RedisLockService redisLockService,
                                 BusinessDataClient businessDataClient, ApprovalInstanceMapper instanceMapper,
                                 ApprovalNodeInstanceMapper nodeInstanceMapper, ApprovalTaskMapper taskMapper,
                                 ApprovalNodeMapper nodeMapper, ApprovalTransitionMapper transitionMapper,
                                 ApprovalActionMapper actionMapper, ApprovalSnapshotMapper snapshotMapper,
                                 ApprovalOutboxEventMapper outboxMapper) {
        this(objectMapper, redisLockService, businessDataClient, instanceMapper, nodeInstanceMapper, taskMapper,
                nodeMapper, transitionMapper, actionMapper, snapshotMapper, outboxMapper,
                new ApprovalStateMachine());
    }

    @Transactional
    public ApprovalActionResponse approve(long approvalInstanceId, long taskId, ApprovalActionRequest request) {
        String lockKey = "approval:action:" + approvalInstanceId;
        String lockToken = redisLockService.tryLock(lockKey, LOCK_LEASE);
        if (lockToken == null) {
            throw new ApprovalActionException("ACTION_IN_PROGRESS", "该审批实例正在处理中，请稍后重试", HttpStatus.CONFLICT);
        }
        try {
            ApprovalActionEntity existingAction = actionMapper.selectByActionRequestId(approvalInstanceId, request.actionRequestId());
            if (existingAction != null) {
                if (!"APPROVE".equals(existingAction.getActionType())
                        || !request.operatorId().equals(existingAction.getOperatorId())) {
                    throw new ApprovalActionException("ACTION_REQUEST_ID_REUSED", "actionRequestId 已用于其他审批动作", HttpStatus.CONFLICT);
                }
                return toResponse(approvalInstanceId, existingAction, true);
            }

            ApprovalInstanceEntity instance = instanceMapper.selectById(approvalInstanceId);
            if (instance == null) {
                throw new ApprovalActionException("APPROVAL_NOT_FOUND", "审批实例不存在: " + approvalInstanceId, HttpStatus.NOT_FOUND);
            }
            ApprovalTaskEntity task = taskMapper.selectById(taskId);
            if (task == null || !Long.valueOf(approvalInstanceId).equals(task.getApprovalInstanceId())) {
                throw new ApprovalActionException("TASK_NOT_FOUND", "审批待办不存在: " + taskId, HttpStatus.NOT_FOUND);
            }
            if (!request.operatorId().equals(task.getAssigneeId())) {
                throw new ApprovalActionException("TASK_OPERATOR_FORBIDDEN", "只有待办审批人可以通过", HttpStatus.FORBIDDEN);
            }
            if (!stateMachine.isInstanceActionable(instance.getStatus())
                    || !stateMachine.isTaskActionable(task.getStatus())) {
                throw new ApprovalActionException("APPROVAL_NOT_ACTIONABLE", "审批实例或待办当前不可通过", HttpStatus.CONFLICT);
            }
            ApprovalNodeInstanceEntity activeNode = nodeInstanceMapper.selectActiveByInstanceId(approvalInstanceId);
            if (activeNode == null || !stateMachine.isNodeActive(activeNode.getStatus())
                    || instance.getCurrentNodeId() == null
                    || !Long.valueOf(task.getNodeInstanceId()).equals(activeNode.getId())
                    || !instance.getCurrentNodeId().equals(activeNode.getNodeId())) {
                throw new ApprovalActionException("ACTIVE_NODE_CHANGED", "审批节点状态已发生变化，请重试", HttpStatus.CONFLICT);
            }
            ApprovalNodeEntity currentNode = nodeMapper.selectById(activeNode.getNodeId());
            if (currentNode == null) {
                throw new ApprovalActionException("CURRENT_NODE_NOT_FOUND", "审批配置节点不存在", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            String approvalMode = approvalMode(currentNode);
            resolveApprovers(currentNode);
            ensureTaskTransition(task.getStatus(), "APPROVED");
            ensureNodeTransition(activeNode.getStatus(), "COMPLETED");

            ApprovalTransitionEntity transition = transitionMapper.findDefaultNext(
                    instance.getProcessId(), instance.getCurrentNodeId()).orElse(null);
            ApprovalNodeEntity nextNode = transition == null ? null : nodeMapper.selectById(transition.getToNodeId());
            if (transition != null && (nextNode == null || !"APPROVAL".equals(nextNode.getNodeType()))) {
                throw new ApprovalActionException("NEXT_NODE_INVALID", "审批流转的下一节点不可执行", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (nextNode != null && (nextNode.getApproverValue() == null || nextNode.getApproverValue().isBlank())) {
                throw new ApprovalActionException("APPROVER_NOT_CONFIGURED", "下一审批节点未配置审批人", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            BusinessDataResponse businessData = businessDataClient.get(instance.getBusinessType(), instance.getBusinessId());
            if (businessData == null || !instance.getApplicationId().equals(businessData.applicationId())) {
                throw new ApprovalActionException("BUSINESS_DATA_NOT_FOUND", "审批对应的业务数据不存在或已变更", HttpStatus.NOT_FOUND);
            }
            if (taskMapper.updatePendingToApproved(taskId, approvalInstanceId, request.operatorId(), request.comment()) != 1) {
                throw new ApprovalActionException("TASK_STATE_CHANGED", "待办状态已发生变化，请重试", HttpStatus.CONFLICT);
            }
            if ("OR".equals(approvalMode)) {
                taskMapper.cancelOtherPendingByNodeInstanceId(activeNode.getId(), taskId);
            }
            boolean nodeCompleted = true;
            if (nodeCompleted && nodeInstanceMapper.markCompleted(activeNode.getId(), approvalInstanceId) != 1) {
                throw new ApprovalActionException("ACTIVE_NODE_CHANGED", "审批节点状态已发生变化，请重试", HttpStatus.CONFLICT);
            }

            String snapshotJson = writeJson(businessData);
            ApprovalSnapshotEntity snapshot = new ApprovalSnapshotEntity();
            snapshot.setApprovalInstanceId(approvalInstanceId);
            snapshot.setNodeInstanceId(activeNode.getId());
            snapshot.setSnapshotNo(snapshotMapper.selectMaxSnapshotNo(approvalInstanceId) + 1);
            snapshot.setSnapshotType("APPROVE");
            snapshot.setBusinessType(instance.getBusinessType());
            snapshot.setBusinessId(instance.getBusinessId());
            snapshot.setDataJson(snapshotJson);
            snapshot.setDataHash(sha256(snapshotJson));
            snapshot.setCreatedBy(request.operatorId());
            snapshotMapper.insert(snapshot);

            Long nextTaskId = null;
            String toStatus = IN_PROGRESS;
            if (!nodeCompleted) {
                toStatus = IN_PROGRESS;
            } else if (nextNode != null) {
                ApprovalNodeInstanceEntity nextNodeInstance = new ApprovalNodeInstanceEntity();
                nextNodeInstance.setApprovalInstanceId(approvalInstanceId);
                nextNodeInstance.setNodeId(nextNode.getId());
                nextNodeInstance.setStatus("ACTIVE");
                nextNodeInstance.setStartedAt(LocalDateTime.now());
                nodeInstanceMapper.insert(nextNodeInstance);

                List<String> nextApprovers = resolveApprovers(nextNode);
                for (String approver : nextApprovers) {
                    ApprovalTaskEntity nextTask = new ApprovalTaskEntity();
                    nextTask.setApprovalInstanceId(approvalInstanceId);
                    nextTask.setNodeInstanceId(nextNodeInstance.getId());
                    nextTask.setAssigneeId(approver);
                    nextTask.setStatus("PENDING");
                    taskMapper.insert(nextTask);
                    if (nextTaskId == null) nextTaskId = nextTask.getId();
                }
                if (instanceMapper.updateCurrentNodeWithVersion(approvalInstanceId, nextNode.getId(), currentLockVersion(instance)) != 1) {
                    throw new ApprovalActionException("APPROVAL_VERSION_CONFLICT", "审批实例版本已变化，请稍后重试", HttpStatus.CONFLICT);
                }
                instance.setCurrentNodeId(nextNode.getId());
            } else {
                toStatus = "APPROVED";
                ensureInstanceTransition(instance.getStatus(), toStatus);
                if (instanceMapper.updateStatusWithVersion(approvalInstanceId, IN_PROGRESS, toStatus,
                        currentLockVersion(instance)) != 1) {
                    throw new ApprovalActionException("APPROVAL_VERSION_CONFLICT", "审批实例版本已变化，请稍后重试", HttpStatus.CONFLICT);
                }
                instance.setStatus(toStatus);
                instance.setCurrentNodeId(null);
                businessDataClient.markApproved(instance.getApplicationId());
            }

            ApprovalActionEntity action = new ApprovalActionEntity();
            action.setApprovalInstanceId(approvalInstanceId);
            action.setNodeInstanceId(activeNode.getId());
            action.setTaskId(taskId);
            action.setOperatorId(request.operatorId());
            action.setActionType("APPROVE");
            action.setActionRequestId(request.actionRequestId());
            action.setFromStatus(IN_PROGRESS);
            action.setToStatus(toStatus);
            action.setComment(request.comment());
            action.setSnapshotId(snapshot.getId());
            actionMapper.insert(action);

            ApprovalOutboxEventEntity outbox = new ApprovalOutboxEventEntity();
            outbox.setEventId(UUID.randomUUID().toString());
            outbox.setAggregateType("APPROVAL_INSTANCE");
            outbox.setAggregateId(String.valueOf(approvalInstanceId));
            outbox.setEventType(nodeCompleted ? "APPROVAL_APPROVED" : "APPROVAL_TASK_APPROVED");
            outbox.setPayloadJson(writeJson(new ApproveEvent(approvalInstanceId, instance.getApprovalNo(),
                    instance.getBusinessType(), instance.getBusinessId(), taskId, request.operatorId(), toStatus,
                    instance.getCurrentNodeId(), nextTaskId)));
            outbox.setStatus("NEW");
            outbox.setRetryCount(0);
            outboxMapper.insert(outbox);

            return new ApprovalActionResponse(approvalInstanceId, instance.getApprovalNo(), instance.getApplicationId(),
                    instance.getStatus(), instance.getCurrentNodeId(), "APPROVE", action.getId(), false);
        } finally {
            redisLockService.unlock(lockKey, lockToken);
        }
    }

    @Transactional
    public ApprovalActionResponse withdraw(long approvalInstanceId, ApprovalActionRequest request) {
        String lockKey = "approval:action:" + approvalInstanceId;
        String lockToken = redisLockService.tryLock(lockKey, LOCK_LEASE);
        if (lockToken == null) {
            throw new ApprovalSubmitException("ACTION_IN_PROGRESS", "该审批实例正在处理中，请稍后重试", HttpStatus.CONFLICT);
        }
        try {
            ApprovalActionEntity existingAction = actionMapper.selectByActionRequestId(approvalInstanceId, request.actionRequestId());
            if (existingAction != null) {
                if (!WITHDRAW.equals(existingAction.getActionType())
                        || !request.operatorId().equals(existingAction.getOperatorId())) {
                    throw new ApprovalSubmitException("ACTION_REQUEST_ID_REUSED", "actionRequestId 已用于其他审批动作", HttpStatus.CONFLICT);
                }
                return toResponse(approvalInstanceId, existingAction, true);
            }

            ApprovalInstanceEntity instance = instanceMapper.selectById(approvalInstanceId);
            if (instance == null) {
                throw new ApprovalSubmitException("APPROVAL_NOT_FOUND", "审批实例不存在: " + approvalInstanceId, HttpStatus.NOT_FOUND);
            }
            if (!request.operatorId().equals(instance.getApplicantId())) {
                throw new ApprovalSubmitException("WITHDRAW_OPERATOR_FORBIDDEN", "只有申请人可以撤回审批", HttpStatus.FORBIDDEN);
            }
            if (!stateMachine.isInstanceActionable(instance.getStatus())) {
                throw new ApprovalSubmitException("APPROVAL_NOT_IN_PROGRESS", "只有进行中的审批可以撤回", HttpStatus.CONFLICT);
            }

            ApprovalNodeInstanceEntity activeNode = nodeInstanceMapper.selectActiveByInstanceId(approvalInstanceId);
            if (activeNode == null || !stateMachine.isNodeActive(activeNode.getStatus())) {
                throw new ApprovalSubmitException("ACTIVE_NODE_NOT_FOUND", "审批实例没有活动节点", HttpStatus.CONFLICT);
            }
            ensureInstanceTransition(instance.getStatus(), WITHDRAWN);
            ensureNodeTransition(activeNode.getStatus(), "CANCELLED");
            ensureTaskTransition("PENDING", "CANCELLED");
            BusinessDataResponse businessData = businessDataClient.get(instance.getBusinessType(), instance.getBusinessId());
            String snapshotJson = writeJson(businessData);

            int cancelledTasks = taskMapper.cancelPendingByInstanceId(approvalInstanceId);
            if (cancelledTasks < 1) {
                throw new ApprovalSubmitException("PENDING_TASK_NOT_FOUND", "审批实例没有可撤回的待办", HttpStatus.CONFLICT);
            }
            if (nodeInstanceMapper.markCancelled(activeNode.getId()) != 1) {
                throw new ApprovalSubmitException("ACTIVE_NODE_CHANGED", "审批节点状态已发生变化，请重试", HttpStatus.CONFLICT);
            }
            if (instanceMapper.updateStatusWithVersion(approvalInstanceId, IN_PROGRESS, WITHDRAWN,
                    currentLockVersion(instance)) != 1) {
                throw new ApprovalSubmitException("APPROVAL_STATE_CHANGED", "审批状态已发生变化，请重试", HttpStatus.CONFLICT);
            }

            ApprovalSnapshotEntity snapshot = new ApprovalSnapshotEntity();
            snapshot.setApprovalInstanceId(approvalInstanceId);
            snapshot.setNodeInstanceId(activeNode.getId());
            snapshot.setSnapshotNo(snapshotMapper.selectMaxSnapshotNo(approvalInstanceId) + 1);
            snapshot.setSnapshotType(WITHDRAW);
            snapshot.setBusinessType(instance.getBusinessType());
            snapshot.setBusinessId(instance.getBusinessId());
            snapshot.setDataJson(snapshotJson);
            snapshot.setDataHash(sha256(snapshotJson));
            snapshot.setCreatedBy(request.operatorId());
            snapshotMapper.insert(snapshot);

            ApprovalActionEntity action = new ApprovalActionEntity();
            action.setApprovalInstanceId(approvalInstanceId);
            action.setNodeInstanceId(activeNode.getId());
            action.setOperatorId(request.operatorId());
            action.setActionType(WITHDRAW);
            action.setActionRequestId(request.actionRequestId());
            action.setFromStatus(IN_PROGRESS);
            action.setToStatus(WITHDRAWN);
            action.setComment(request.comment());
            action.setSnapshotId(snapshot.getId());
            actionMapper.insert(action);

            ApprovalOutboxEventEntity outbox = new ApprovalOutboxEventEntity();
            outbox.setEventId(UUID.randomUUID().toString());
            outbox.setAggregateType("APPROVAL_INSTANCE");
            outbox.setAggregateId(String.valueOf(approvalInstanceId));
            outbox.setEventType("APPROVAL_WITHDRAWN");
            outbox.setPayloadJson(writeJson(new WithdrawEvent(approvalInstanceId, instance.getApprovalNo(),
                    instance.getBusinessType(), instance.getBusinessId(), request.operatorId())));
            outbox.setStatus("NEW");
            outbox.setRetryCount(0);
            outboxMapper.insert(outbox);

            businessDataClient.markWithdrawn(instance.getApplicationId());
            return new ApprovalActionResponse(approvalInstanceId, instance.getApprovalNo(), instance.getApplicationId(),
                    WITHDRAWN, null, WITHDRAW, action.getId(), false);
        } finally {
            redisLockService.unlock(lockKey, lockToken);
        }
    }

    @Transactional
    public ApprovalActionResponse reject(long approvalInstanceId, long taskId, ApprovalActionRequest request) {
        String lockKey = "approval:action:" + approvalInstanceId;
        String lockToken = redisLockService.tryLock(lockKey, LOCK_LEASE);
        if (lockToken == null) {
            throw new ApprovalSubmitException("ACTION_IN_PROGRESS", "该审批实例正在处理中，请稍后重试", HttpStatus.CONFLICT);
        }
        try {
            ApprovalActionEntity existingAction = actionMapper.selectByActionRequestId(approvalInstanceId,
                    request.actionRequestId());
            if (existingAction != null) {
                if (!REJECT.equals(existingAction.getActionType())
                        || !request.operatorId().equals(existingAction.getOperatorId())) {
                    throw new ApprovalSubmitException("ACTION_REQUEST_ID_REUSED", "actionRequestId 已用于其他审批动作", HttpStatus.CONFLICT);
                }
                return toResponse(approvalInstanceId, existingAction, true);
            }

            ApprovalInstanceEntity instance = instanceMapper.selectById(approvalInstanceId);
            if (instance == null) {
                throw new ApprovalSubmitException("APPROVAL_NOT_FOUND", "审批实例不存在: " + approvalInstanceId, HttpStatus.NOT_FOUND);
            }
            if (!stateMachine.isInstanceActionable(instance.getStatus())) {
                throw new ApprovalSubmitException("APPROVAL_NOT_IN_PROGRESS", "只有进行中的审批可以驳回", HttpStatus.CONFLICT);
            }

            ApprovalTaskEntity task = taskMapper.selectById(taskId);
            if (task == null || !Long.valueOf(approvalInstanceId).equals(task.getApprovalInstanceId())) {
                throw new ApprovalSubmitException("TASK_NOT_FOUND", "审批待办不存在: " + taskId, HttpStatus.NOT_FOUND);
            }
            if (!request.operatorId().equals(task.getAssigneeId())) {
                throw new ApprovalSubmitException("TASK_OPERATOR_FORBIDDEN", "只有待办审批人可以驳回", HttpStatus.FORBIDDEN);
            }
            if (!stateMachine.isTaskActionable(task.getStatus())) {
                throw new ApprovalSubmitException("TASK_NOT_PENDING", "该待办已经处理，不能重复驳回", HttpStatus.CONFLICT);
            }

            ApprovalNodeInstanceEntity activeNode = nodeInstanceMapper.selectActiveByInstanceId(approvalInstanceId);
            if (activeNode == null || !stateMachine.isNodeActive(activeNode.getStatus())
                    || !Long.valueOf(task.getNodeInstanceId()).equals(activeNode.getId())) {
                throw new ApprovalSubmitException("ACTIVE_NODE_CHANGED", "审批节点状态已发生变化，请重试", HttpStatus.CONFLICT);
            }
            ensureTaskTransition(task.getStatus(), "REJECTED");
            ensureNodeTransition(activeNode.getStatus(), "REJECTED");
            ensureInstanceTransition(instance.getStatus(), REJECTED);
            BusinessDataResponse businessData = businessDataClient.get(instance.getBusinessType(), instance.getBusinessId());
            String snapshotJson = writeJson(businessData);

            if (taskMapper.updatePendingToRejected(taskId, approvalInstanceId, request.comment()) != 1) {
                throw new ApprovalSubmitException("TASK_STATE_CHANGED", "待办状态已发生变化，请重试", HttpStatus.CONFLICT);
            }
            if (nodeInstanceMapper.markRejected(activeNode.getId(), approvalInstanceId) != 1) {
                throw new ApprovalSubmitException("ACTIVE_NODE_CHANGED", "审批节点状态已发生变化，请重试", HttpStatus.CONFLICT);
            }
            taskMapper.cancelOtherPendingByInstanceId(approvalInstanceId, taskId);
            if (instanceMapper.updateStatusWithVersion(approvalInstanceId, IN_PROGRESS, REJECTED,
                    currentLockVersion(instance)) != 1) {
                throw new ApprovalSubmitException("APPROVAL_STATE_CHANGED", "审批状态已发生变化，请重试", HttpStatus.CONFLICT);
            }

            ApprovalSnapshotEntity snapshot = new ApprovalSnapshotEntity();
            snapshot.setApprovalInstanceId(approvalInstanceId);
            snapshot.setNodeInstanceId(activeNode.getId());
            snapshot.setSnapshotNo(snapshotMapper.selectMaxSnapshotNo(approvalInstanceId) + 1);
            snapshot.setSnapshotType(REJECTED);
            snapshot.setBusinessType(instance.getBusinessType());
            snapshot.setBusinessId(instance.getBusinessId());
            snapshot.setDataJson(snapshotJson);
            snapshot.setDataHash(sha256(snapshotJson));
            snapshot.setCreatedBy(request.operatorId());
            snapshotMapper.insert(snapshot);

            ApprovalActionEntity action = new ApprovalActionEntity();
            action.setApprovalInstanceId(approvalInstanceId);
            action.setNodeInstanceId(activeNode.getId());
            action.setTaskId(taskId);
            action.setOperatorId(request.operatorId());
            action.setActionType(REJECT);
            action.setActionRequestId(request.actionRequestId());
            action.setFromStatus(IN_PROGRESS);
            action.setToStatus(REJECTED);
            action.setComment(request.comment());
            action.setSnapshotId(snapshot.getId());
            actionMapper.insert(action);

            ApprovalOutboxEventEntity outbox = new ApprovalOutboxEventEntity();
            outbox.setEventId(UUID.randomUUID().toString());
            outbox.setAggregateType("APPROVAL_INSTANCE");
            outbox.setAggregateId(String.valueOf(approvalInstanceId));
            outbox.setEventType("APPROVAL_REJECTED");
            outbox.setPayloadJson(writeJson(new RejectEvent(approvalInstanceId, instance.getApprovalNo(),
                    instance.getBusinessType(), instance.getBusinessId(), taskId, request.operatorId(), request.comment())));
            outbox.setStatus("NEW");
            outbox.setRetryCount(0);
            outboxMapper.insert(outbox);

            businessDataClient.markRejected(instance.getApplicationId());
            return new ApprovalActionResponse(approvalInstanceId, instance.getApprovalNo(), instance.getApplicationId(),
                    REJECTED, null, REJECT, action.getId(), false);
        } finally {
            redisLockService.unlock(lockKey, lockToken);
        }
    }

    private long currentLockVersion(ApprovalInstanceEntity instance) {
        return instance.getLockVersion() == null ? 0L : instance.getLockVersion();
    }

    private void ensureInstanceTransition(String from, String to) {
        if (!stateMachine.canTransitionInstance(from, to)) {
            throw new ApprovalActionException("INVALID_INSTANCE_TRANSITION",
                    "审批实例不允许从 " + from + " 转换为 " + to, HttpStatus.CONFLICT);
        }
    }

    private void ensureTaskTransition(String from, String to) {
        if (!stateMachine.canTransitionTask(from, to)) {
            throw new ApprovalActionException("INVALID_TASK_TRANSITION",
                    "审批任务不允许从 " + from + " 转换为 " + to, HttpStatus.CONFLICT);
        }
    }

    private void ensureNodeTransition(String from, String to) {
        if (!stateMachine.canTransitionNode(from, to)) {
            throw new ApprovalActionException("INVALID_NODE_TRANSITION",
                    "审批节点不允许从 " + from + " 转换为 " + to, HttpStatus.CONFLICT);
        }
    }

    private String approvalMode(ApprovalNodeEntity node) {
        String mode = node.getApprovalMode() == null || node.getApprovalMode().isBlank()
                ? "SINGLE" : node.getApprovalMode().trim().toUpperCase(Locale.ROOT);
        if (!("SINGLE".equals(mode) || "OR".equals(mode))) {
            throw new ApprovalActionException("APPROVAL_MODE_UNSUPPORTED", "不支持的审批模式: " + mode, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return mode;
    }

    private List<String> resolveApprovers(ApprovalNodeEntity node) {
        String mode = approvalMode(node);
        List<String> approvers = node.getApproverValue() == null ? List.of() : java.util.Arrays.stream(node.getApproverValue().split("[,，]"))
                .map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
        if (approvers.isEmpty()) {
            throw new ApprovalActionException("APPROVER_NOT_CONFIGURED", "审批节点未配置审批人", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if ("SINGLE".equals(mode) && approvers.size() != 1) {
            throw new ApprovalActionException("APPROVER_CONFIG_INVALID", "SINGLE 节点只能配置一个审批人", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return approvers;
    }

    private ApprovalActionResponse toResponse(long approvalInstanceId, ApprovalActionEntity action, boolean duplicate) {
        ApprovalInstanceEntity instance = instanceMapper.selectById(approvalInstanceId);
        return new ApprovalActionResponse(approvalInstanceId, instance == null ? null : instance.getApprovalNo(),
                instance == null ? null : instance.getApplicationId(),
                action.getToStatus(),
                instance == null ? null : instance.getCurrentNodeId(),
                action.getActionType(), action.getId(), duplicate);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审批数据序列化失败", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private record WithdrawEvent(Long approvalInstanceId, String approvalNo, String businessType,
                                 String businessId, String operatorId) {
    }

    private record ApproveEvent(Long approvalInstanceId, String approvalNo, String businessType,
                                String businessId, Long taskId, String operatorId, String status,
                                Long currentNodeId, Long nextTaskId) {
    }

    private record RejectEvent(Long approvalInstanceId, String approvalNo, String businessType,
                               String businessId, Long taskId, String operatorId, String comment) {
    }
}
