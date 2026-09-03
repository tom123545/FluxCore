package com.fluxcore.approval.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fluxcore.approval.dto.ApprovalInstanceResponse;
import com.fluxcore.approval.dto.ApprovalTaskView;
import com.fluxcore.approval.dto.BusinessDataResponse;
import com.fluxcore.approval.entity.ApprovalInstanceEntity;
import com.fluxcore.approval.entity.ApprovalNodeEntity;
import com.fluxcore.approval.mapper.ApprovalInstanceMapper;
import com.fluxcore.approval.mapper.ApprovalNodeMapper;
import com.fluxcore.approval.mapper.ApprovalTaskMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalInstanceQueryServiceTest {
    private static final long INSTANCE_ID = 20001L;

    @Mock private ApprovalInstanceMapper instanceMapper;
    @Mock private ApprovalNodeMapper nodeMapper;
    @Mock private ApprovalTaskMapper taskMapper;
    @Mock private BusinessDataClient businessDataClient;

    private ApprovalInstanceQueryService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalInstanceQueryService(instanceMapper, nodeMapper, taskMapper, businessDataClient);
    }

    @Test
    void get_shouldReturnInstanceSummaryCurrentNodeAndAllTasks() {
        ApprovalInstanceEntity instance = instance();
        ApprovalNodeEntity currentNode = new ApprovalNodeEntity();
        currentNode.setId(101L);
        currentNode.setNodeName("部门负责人审批");
        ApprovalTaskView task = task(30001L, 40001L, 101L, "部门负责人审批", "U2001", "PENDING");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);
        when(businessDataClient.get("PURCHASE", "PUR-001"))
                .thenReturn(new BusinessDataResponse(10001L, "APP-001", "PURCHASE", "PUR-001",
                        "办公用品采购", "U1001", "SUBMITTED", null));
        when(nodeMapper.selectById(101L)).thenReturn(currentNode);
        when(taskMapper.selectViewsByApprovalInstanceId(INSTANCE_ID)).thenReturn(List.of(task));

        ApprovalInstanceResponse response = service.get(INSTANCE_ID);

        assertEquals(INSTANCE_ID, response.approvalInstanceId());
        assertEquals("APR-001", response.approvalNo());
        assertEquals("办公用品采购", response.title());
        assertEquals("IN_PROGRESS", response.status());
        assertEquals(101L, response.currentNodeId());
        assertEquals("部门负责人审批", response.currentNodeName());
        assertEquals(1, response.tasks().size());
        assertEquals(30001L, response.tasks().getFirst().taskId());
        assertEquals("PENDING", response.tasks().getFirst().status());
        assertEquals("U2001", response.tasks().getFirst().assigneeId());
    }

    @Test
    void get_whenInstanceIsFinished_shouldReturnNullCurrentNodeAndKeepTaskHistory() {
        ApprovalInstanceEntity instance = instance();
        instance.setStatus("REJECTED");
        instance.setCurrentNodeId(null);
        ApprovalTaskView task = task(30001L, 40001L, 101L, "部门负责人审批", "U2001", "REJECTED");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);
        when(businessDataClient.get("PURCHASE", "PUR-001"))
                .thenReturn(new BusinessDataResponse(10001L, "APP-001", "PURCHASE", "PUR-001",
                        "办公用品采购", "U1001", "REJECTED", null));
        when(taskMapper.selectViewsByApprovalInstanceId(INSTANCE_ID)).thenReturn(List.of(task));

        ApprovalInstanceResponse response = service.get(INSTANCE_ID);

        assertEquals("REJECTED", response.status());
        assertNull(response.currentNodeId());
        assertNull(response.currentNodeName());
        assertEquals("REJECTED", response.tasks().getFirst().status());
        verify(nodeMapper, never()).selectById(101L);
    }

    @Test
    void get_whenInstanceDoesNotExist_shouldReturnNotFound() {
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(null);

        ApprovalQueryException exception = assertThrows(ApprovalQueryException.class,
                () -> service.get(INSTANCE_ID));

        assertEquals("APPROVAL_NOT_FOUND", exception.getCode());
        assertEquals(404, exception.getStatus().value());
        verify(businessDataClient, never()).get("PURCHASE", "PUR-001");
        verify(taskMapper, never()).selectViewsByApprovalInstanceId(INSTANCE_ID);
    }

    private ApprovalInstanceEntity instance() {
        ApprovalInstanceEntity instance = new ApprovalInstanceEntity();
        instance.setId(INSTANCE_ID);
        instance.setApprovalNo("APR-001");
        instance.setApplicationId(10001L);
        instance.setBusinessType("PURCHASE");
        instance.setBusinessId("PUR-001");
        instance.setApplicantId("U1001");
        instance.setProcessId(10L);
        instance.setStatus("IN_PROGRESS");
        instance.setCurrentNodeId(101L);
        instance.setLockVersion(1L);
        return instance;
    }

    private ApprovalTaskView task(long id, long nodeInstanceId, long nodeId, String nodeName,
                                  String assigneeId, String status) {
        ApprovalTaskView task = new ApprovalTaskView();
        task.setId(id);
        task.setNodeInstanceId(nodeInstanceId);
        task.setNodeId(nodeId);
        task.setNodeName(nodeName);
        task.setAssigneeId(assigneeId);
        task.setStatus(status);
        return task;
    }
}
