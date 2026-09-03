package com.fluxcore.approval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.fluxcore.approval.mapper.ApprovalActionMapper;
import com.fluxcore.approval.mapper.ApprovalInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalNodeInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalOutboxEventMapper;
import com.fluxcore.approval.mapper.ApprovalProcessMapper;
import com.fluxcore.approval.mapper.ApprovalSnapshotMapper;
import com.fluxcore.approval.mapper.ApprovalTaskMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalSubmitService {
    private static final String DRAFT = "DRAFT";
    private static final String SUBMITTED = "SUBMITTED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final Duration LOCK_LEASE = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final RedisLockService redisLockService;
    private final BusinessDataClient businessDataClient;
    private final ApprovalProcessMapper processMapper;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalNodeInstanceMapper nodeInstanceMapper;
    private final ApprovalTaskMapper taskMapper;
    private final ApprovalActionMapper actionMapper;
    private final ApprovalSnapshotMapper snapshotMapper;
    private final ApprovalOutboxEventMapper outboxMapper;

    public ApprovalSubmitService(ObjectMapper objectMapper, RedisLockService redisLockService,
                                 BusinessDataClient businessDataClient, ApprovalProcessMapper processMapper,
                                 ApprovalInstanceMapper instanceMapper, ApprovalNodeInstanceMapper nodeInstanceMapper,
                                 ApprovalTaskMapper taskMapper, ApprovalActionMapper actionMapper,
                                 ApprovalSnapshotMapper snapshotMapper, ApprovalOutboxEventMapper outboxMapper) {
        this.objectMapper = objectMapper;
        this.redisLockService = redisLockService;
        this.businessDataClient = businessDataClient;
        this.processMapper = processMapper;
        this.instanceMapper = instanceMapper;
        this.nodeInstanceMapper = nodeInstanceMapper;
        this.taskMapper = taskMapper;
        this.actionMapper = actionMapper;
        this.snapshotMapper = snapshotMapper;
        this.outboxMapper = outboxMapper;
    }

    @Transactional
    public SubmitApprovalResponse submit(SubmitApprovalRequest request) {
        String lockKey = "approval:submit:" + request.businessType() + ":" + request.businessId();
        String lockToken = redisLockService.tryLock(lockKey, LOCK_LEASE);
        if (lockToken == null) {
            throw new ApprovalSubmitException("SUBMIT_IN_PROGRESS", "该业务申请正在提交，请稍后重试", HttpStatus.CONFLICT);
        }
        try {
            ApprovalInstanceEntity requestDuplicate = instanceMapper.findBySubmitRequestId(request.submitRequestId()).orElse(null);
            if (requestDuplicate != null) {
                validateExisting(request, requestDuplicate);
                return toResponse(requestDuplicate, null, true);
            }

            BusinessDataResponse businessData = businessDataClient.get(request.businessType(), request.businessId());
            validateBusinessData(request, businessData);

            ApprovalInstanceEntity existing = instanceMapper.findByApplicationId(businessData.applicationId()).orElse(null);
            if (existing != null) {
                validateExisting(request, existing);
                return toResponse(existing, null, true);
            }
            if (!DRAFT.equals(businessData.status())) {
                throw new ApprovalSubmitException("APPLICATION_NOT_DRAFT", "只有草稿申请可以提交审批", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            ApprovalProcessEntity process = processMapper.findPublished(request.businessType())
                    .orElseThrow(() -> new ApprovalSubmitException("PROCESS_NOT_FOUND", "没有找到已发布的审批流程: " + request.businessType(), HttpStatus.UNPROCESSABLE_ENTITY));
            ApprovalNodeEntity firstNode = processMapper.findFirstApprovalNode(process.getId())
                    .orElseThrow(() -> new ApprovalSubmitException("APPROVAL_NODE_NOT_FOUND", "审批流程没有可执行的审批节点", HttpStatus.UNPROCESSABLE_ENTITY));
            List<String> firstApprovers = resolveApprovers(firstNode);

            ApprovalInstanceEntity instance = new ApprovalInstanceEntity();
            instance.setApprovalNo("APR-" + shortUuid());
            instance.setApplicationId(businessData.applicationId());
            instance.setBusinessType(request.businessType());
            instance.setBusinessId(request.businessId());
            instance.setApplicantId(request.applicantId());
            instance.setProcessId(process.getId());
            instance.setSubmitRequestId(request.submitRequestId());
            instance.setStatus(IN_PROGRESS);
            instance.setCurrentNodeId(firstNode.getId());
            instance.setLockVersion(0L);
            try {
                instanceMapper.insert(instance);
            } catch (DuplicateKeyException duplicateKeyException) {
                ApprovalInstanceEntity duplicated = instanceMapper.findByApplicationId(businessData.applicationId()).orElseThrow(() -> duplicateKeyException);
                return toResponse(duplicated, null, true);
            }

            ApprovalNodeInstanceEntity nodeInstance = new ApprovalNodeInstanceEntity();
            nodeInstance.setApprovalInstanceId(instance.getId());
            nodeInstance.setNodeId(firstNode.getId());
            nodeInstance.setStatus("ACTIVE");
            nodeInstance.setStartedAt(LocalDateTime.now());
            nodeInstanceMapper.insert(nodeInstance);

            ApprovalTaskEntity task = null;
            for (String approver : firstApprovers) {
                ApprovalTaskEntity candidate = new ApprovalTaskEntity();
                candidate.setApprovalInstanceId(instance.getId());
                candidate.setNodeInstanceId(nodeInstance.getId());
                candidate.setAssigneeId(approver);
                candidate.setStatus("PENDING");
                taskMapper.insert(candidate);
                if (task == null) task = candidate;
            }

            String snapshotJson = writeJson(businessData);
            ApprovalSnapshotEntity snapshot = new ApprovalSnapshotEntity();
            snapshot.setApprovalInstanceId(instance.getId());
            snapshot.setNodeInstanceId(null);
            snapshot.setSnapshotNo(1);
            snapshot.setSnapshotType("SUBMIT");
            snapshot.setBusinessType(request.businessType());
            snapshot.setBusinessId(request.businessId());
            snapshot.setDataJson(snapshotJson);
            snapshot.setDataHash(sha256(snapshotJson));
            snapshot.setCreatedBy(request.applicantId());
            snapshotMapper.insert(snapshot);

            ApprovalActionEntity action = new ApprovalActionEntity();
            action.setApprovalInstanceId(instance.getId());
            action.setNodeInstanceId(nodeInstance.getId());
            action.setTaskId(task.getId());
            action.setOperatorId(request.applicantId());
            action.setActionType("SUBMIT");
            action.setActionRequestId(request.submitRequestId());
            action.setFromStatus(DRAFT);
            action.setToStatus(IN_PROGRESS);
            action.setComment("提交审批");
            action.setSnapshotId(snapshot.getId());
            actionMapper.insert(action);

            ApprovalOutboxEventEntity outbox = new ApprovalOutboxEventEntity();
            outbox.setEventId(UUID.randomUUID().toString());
            outbox.setAggregateType("APPROVAL_INSTANCE");
            outbox.setAggregateId(String.valueOf(instance.getId()));
            outbox.setEventType("APPROVAL_SUBMITTED");
            outbox.setPayloadJson(writeJson(new SubmitEvent(instance.getId(), instance.getApprovalNo(), request.businessType(), request.businessId(), task.getId(), firstNode.getApproverValue())));
            outbox.setStatus("NEW");
            outbox.setRetryCount(0);
            outboxMapper.insert(outbox);

            // 两个服务之间不存在本地分布式事务；审批本地事务成功前更新不会发生，失败会回滚本地审批数据。
            businessDataClient.markSubmitted(businessData.applicationId());
            return toResponse(instance, task.getId(), false);
        } finally {
            redisLockService.unlock(lockKey, lockToken);
        }
    }

    private void validateBusinessData(SubmitApprovalRequest request, BusinessDataResponse data) {
        if (data == null || data.applicationId() == null) throw new ApprovalSubmitException("BUSINESS_DATA_NOT_FOUND", "业务申请不存在", HttpStatus.NOT_FOUND);
        if (!request.businessType().equals(data.businessType()) || !request.businessId().equals(data.businessId())) {
            throw new ApprovalSubmitException("BUSINESS_DATA_MISMATCH", "请求业务标识与业务数据不一致", HttpStatus.BAD_REQUEST);
        }
        if (request.applicationId() != null && !request.applicationId().equals(data.applicationId())) {
            throw new ApprovalSubmitException("APPLICATION_ID_MISMATCH", "applicationId 与业务数据不一致", HttpStatus.BAD_REQUEST);
        }
        if (!request.applicantId().equals(data.applicantId())) {
            throw new ApprovalSubmitException("APPLICANT_MISMATCH", "申请人与业务申请人不一致", HttpStatus.BAD_REQUEST);
        }
    }

    private List<String> resolveApprovers(ApprovalNodeEntity node) {
        String mode = node.getApprovalMode() == null || node.getApprovalMode().isBlank()
                ? "SINGLE" : node.getApprovalMode().trim().toUpperCase(Locale.ROOT);
        List<String> approvers = node.getApproverValue() == null ? List.of() : java.util.Arrays.stream(node.getApproverValue().split("[,，]"))
                .map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
        if (approvers.isEmpty()) {
            throw new ApprovalSubmitException("APPROVER_NOT_CONFIGURED", "审批节点未配置审批人", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!("SINGLE".equals(mode) || "OR".equals(mode))) {
            throw new ApprovalSubmitException("APPROVAL_MODE_UNSUPPORTED", "不支持的审批模式: " + mode, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if ("SINGLE".equals(mode) && approvers.size() != 1) {
            throw new ApprovalSubmitException("APPROVER_CONFIG_INVALID", "SINGLE 节点只能配置一个审批人", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return approvers;
    }

    private void validateExisting(SubmitApprovalRequest request, ApprovalInstanceEntity instance) {
        if (!request.businessType().equals(instance.getBusinessType()) || !request.businessId().equals(instance.getBusinessId())) {
            throw new ApprovalSubmitException("REQUEST_ID_REUSED", "submitRequestId 已用于其他业务申请", HttpStatus.CONFLICT);
        }
        if (request.applicationId() != null && !request.applicationId().equals(instance.getApplicationId())) {
            throw new ApprovalSubmitException("REQUEST_ID_REUSED", "submitRequestId 已用于其他申请", HttpStatus.CONFLICT);
        }
    }

    private SubmitApprovalResponse toResponse(ApprovalInstanceEntity instance, Long taskId, boolean duplicate) {
        return new SubmitApprovalResponse(instance.getId(), instance.getApprovalNo(), instance.getApplicationId(), instance.getBusinessType(),
                instance.getBusinessId(), instance.getStatus(), instance.getCurrentNodeId(), taskId, duplicate);
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("审批数据序列化失败", exception); }
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 不可用", exception); }
    }

    private String shortUuid() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(); }
    private record SubmitEvent(Long approvalInstanceId, String approvalNo, String businessType, String businessId, Long taskId, String assigneeId) {}
}
