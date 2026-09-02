package com.fluxcore.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("approval_node")
public class ApprovalNodeEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long processId;
    private String nodeCode, nodeName, nodeType, approvalMode, approverType, approverValue;
    private Integer sequenceNo;
    private LocalDateTime createdAt, updatedAt;
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getProcessId() { return processId; } public void setProcessId(Long v) { processId = v; }
    public String getNodeCode() { return nodeCode; } public void setNodeCode(String v) { nodeCode = v; }
    public String getNodeName() { return nodeName; } public void setNodeName(String v) { nodeName = v; }
    public String getNodeType() { return nodeType; } public void setNodeType(String v) { nodeType = v; }
    public String getApprovalMode() { return approvalMode; } public void setApprovalMode(String v) { approvalMode = v; }
    public String getApproverType() { return approverType; } public void setApproverType(String v) { approverType = v; }
    public String getApproverValue() { return approverValue; } public void setApproverValue(String v) { approverValue = v; }
    public Integer getSequenceNo() { return sequenceNo; } public void setSequenceNo(Integer v) { sequenceNo = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
