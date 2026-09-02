package com.fluxcore.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("approval_process")
public class ApprovalProcessEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String processCode, businessType, processName, status, definitionJson, createdBy;
    private Integer versionNo;
    private LocalDateTime publishedAt, createdAt, updatedAt;
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public String getProcessCode() { return processCode; } public void setProcessCode(String v) { processCode = v; }
    public String getBusinessType() { return businessType; } public void setBusinessType(String v) { businessType = v; }
    public String getProcessName() { return processName; } public void setProcessName(String v) { processName = v; }
    public Integer getVersionNo() { return versionNo; } public void setVersionNo(Integer v) { versionNo = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public String getDefinitionJson() { return definitionJson; } public void setDefinitionJson(String v) { definitionJson = v; }
    public String getCreatedBy() { return createdBy; } public void setCreatedBy(String v) { createdBy = v; }
    public LocalDateTime getPublishedAt() { return publishedAt; } public void setPublishedAt(LocalDateTime v) { publishedAt = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
