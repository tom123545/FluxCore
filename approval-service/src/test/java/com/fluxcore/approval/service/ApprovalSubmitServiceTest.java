package com.fluxcore.approval.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalSubmitServiceTest {
    private static final SubmitApprovalRequest REQUEST = new SubmitApprovalRequest(
            "PURCHASE", "PUR-TEST-001", "U1001", "SUBMIT-TEST-001", 10001L);

    @Mock private RedisLockService redisLockService;
    @Mock private BusinessDataClient businessDataClient;
    @Mock private ApprovalProcessMapper processMapper;
    @Mock private ApprovalInstanceMapper instanceMapper;
    @Mock private ApprovalNodeInstanceMapper nodeInstanceMapper;
    @Mock private ApprovalTaskMapper taskMapper;
    @Mock private ApprovalActionMapper actionMapper;
    @Mock private ApprovalSnapshotMapper snapshotMapper;
    @Mock private ApprovalOutboxEventMapper outboxMapper;

    @InjectMocks private ApprovalSubmitService service;

    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(redisLockService.tryLock("approval:submit:PURCHASE:PUR-TEST-001", java.time.Duration.ofSeconds(30)))
                .thenReturn("test-lock-token");
    }

    @Test
    void submit_shouldCreateApprovalRuntimeDataAndMarkBusinessSubmitted() throws Exception {
        ObjectNode businessPayload = objectMapper.createObjectNode()
                .put("totalAmount", 1280.00)
                .put("remark", "采购审批备注");
        businessPayload.set("formData", objectMapper.createObjectNode().put("costCenter", "CC-1001"));
        BusinessDataResponse businessData = new BusinessDataResponse(
                10001L, "APP-TEST-001", "PURCHASE", "PUR-TEST-001", "办公用品采购", "U1001", "DRAFT",
                businessPayload);
        ApprovalProcessEntity process = new ApprovalProcessEntity();
        process.setId(10L);
        ApprovalNodeEntity firstNode = new ApprovalNodeEntity();
        firstNode.setId(101L);
        firstNode.setProcessId(10L);
        firstNode.setNodeType("APPROVAL");
        firstNode.setApproverValue("U2001");

        when(instanceMapper.findBySubmitRequestId(REQUEST.submitRequestId())).thenReturn(Optional.empty());
        when(businessDataClient.get(REQUEST.businessType(), REQUEST.businessId())).thenReturn(businessData);
        when(instanceMapper.findByApplicationId(10001L)).thenReturn(Optional.empty());
        when(processMapper.findPublished("PURCHASE")).thenReturn(Optional.of(process));
        when(processMapper.findFirstApprovalNode(10L)).thenReturn(Optional.of(firstNode));
        doAnswer(invocation -> {
            ((ApprovalInstanceEntity) invocation.getArgument(0)).setId(20001L);
            return 1;
        }).when(instanceMapper).insert(any(ApprovalInstanceEntity.class));
        doAnswer(invocation -> {
            ((ApprovalNodeInstanceEntity) invocation.getArgument(0)).setId(21001L);
            return 1;
        }).when(nodeInstanceMapper).insert(any(ApprovalNodeInstanceEntity.class));
        doAnswer(invocation -> {
            ((ApprovalTaskEntity) invocation.getArgument(0)).setId(30001L);
            return 1;
        }).when(taskMapper).insert(any(ApprovalTaskEntity.class));
        doAnswer(invocation -> {
            ((ApprovalSnapshotEntity) invocation.getArgument(0)).setId(40001L);
            return 1;
        }).when(snapshotMapper).insert(any(ApprovalSnapshotEntity.class));
        when(actionMapper.insert(any(ApprovalActionEntity.class))).thenReturn(1);
        when(outboxMapper.insert(any(ApprovalOutboxEventEntity.class))).thenReturn(1);

        SubmitApprovalResponse response = service.submit(REQUEST);

        assertEquals(20001L, response.approvalInstanceId());
        assertEquals("IN_PROGRESS", response.status());
        assertEquals(101L, response.currentNodeId());
        assertEquals(30001L, response.firstTaskId());
        assertFalse(response.duplicate());

        ArgumentCaptor<ApprovalInstanceEntity> instanceCaptor = ArgumentCaptor.forClass(ApprovalInstanceEntity.class);
        verify(instanceMapper).insert(instanceCaptor.capture());
        assertEquals(10001L, instanceCaptor.getValue().getApplicationId());
        assertEquals("SUBMIT-TEST-001", instanceCaptor.getValue().getSubmitRequestId());
        assertEquals("IN_PROGRESS", instanceCaptor.getValue().getStatus());

        ArgumentCaptor<ApprovalTaskEntity> taskCaptor = ArgumentCaptor.forClass(ApprovalTaskEntity.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertEquals("U2001", taskCaptor.getValue().getAssigneeId());
        assertEquals("PENDING", taskCaptor.getValue().getStatus());

        ArgumentCaptor<ApprovalSnapshotEntity> snapshotCaptor = ArgumentCaptor.forClass(ApprovalSnapshotEntity.class);
        verify(snapshotMapper).insert(snapshotCaptor.capture());
        assertEquals("SUBMIT", snapshotCaptor.getValue().getSnapshotType());
        assertEquals(64, snapshotCaptor.getValue().getDataHash().length());
        assertEquals("CC-1001", objectMapper.readTree(snapshotCaptor.getValue().getDataJson())
                .path("data").path("formData").path("costCenter").asText());
        assertEquals("采购审批备注", objectMapper.readTree(snapshotCaptor.getValue().getDataJson())
                .path("data").path("remark").asText());

        ArgumentCaptor<ApprovalActionEntity> actionCaptor = ArgumentCaptor.forClass(ApprovalActionEntity.class);
        verify(actionMapper).insert(actionCaptor.capture());
        assertEquals("SUBMIT", actionCaptor.getValue().getActionType());
        assertEquals(40001L, actionCaptor.getValue().getSnapshotId());

        ArgumentCaptor<ApprovalOutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(ApprovalOutboxEventEntity.class);
        verify(outboxMapper).insert(outboxCaptor.capture());
        assertEquals("APPROVAL_SUBMITTED", outboxCaptor.getValue().getEventType());
        assertTrue(outboxCaptor.getValue().getPayloadJson()
                .contains("\"recipientIds\":[\"U2001\"]"));
        verify(businessDataClient).markSubmitted(10001L);
        verify(redisLockService).unlock("approval:submit:PURCHASE:PUR-TEST-001", "test-lock-token");
    }

    @Test
    void submit_withSameSubmitRequestId_shouldReturnExistingApprovalAndNotCreateAgain() {
        ApprovalInstanceEntity existing = new ApprovalInstanceEntity();
        existing.setId(20001L);
        existing.setApplicationId(10001L);
        existing.setBusinessType("PURCHASE");
        existing.setBusinessId("PUR-TEST-001");
        existing.setStatus("IN_PROGRESS");
        existing.setCurrentNodeId(101L);
        when(instanceMapper.findBySubmitRequestId(REQUEST.submitRequestId())).thenReturn(Optional.of(existing));

        SubmitApprovalResponse response = service.submit(REQUEST);

        assertTrue(response.duplicate());
        assertEquals(20001L, response.approvalInstanceId());
        assertEquals("IN_PROGRESS", response.status());
        verify(businessDataClient, never()).get(any(), any());
        verify(instanceMapper, never()).insert(any(ApprovalInstanceEntity.class));
        verify(nodeInstanceMapper, never()).insert(any(ApprovalNodeInstanceEntity.class));
        verify(taskMapper, never()).insert(any(ApprovalTaskEntity.class));
        verify(snapshotMapper, never()).insert(any(ApprovalSnapshotEntity.class));
        verify(actionMapper, never()).insert(any(ApprovalActionEntity.class));
        verify(outboxMapper, never()).insert(any(ApprovalOutboxEventEntity.class));
        verify(redisLockService).unlock("approval:submit:PURCHASE:PUR-TEST-001", "test-lock-token");
    }

    @Test
    void submit_whenLockIsUnavailable_shouldReturnConflict() {
        when(redisLockService.tryLock("approval:submit:PURCHASE:PUR-TEST-001", java.time.Duration.ofSeconds(30)))
                .thenReturn(null);

        ApprovalSubmitException exception = assertThrows(ApprovalSubmitException.class, () -> service.submit(REQUEST));

        assertEquals("SUBMIT_IN_PROGRESS", exception.getCode());
        assertEquals(409, exception.getStatus().value());
    }
}
