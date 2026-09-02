package com.fluxcore.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("approval_instance")
public class ApprovalInstanceEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String approvalNo, businessType, businessId, applicantId, submitRequestId, status;
    private Long applicationId, processId, currentNodeId, lockVersion;
    private LocalDateTime createdAt, updatedAt, completedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getApprovalNo(){return approvalNo;} public void setApprovalNo(String v){approvalNo=v;}
    public Long getApplicationId(){return applicationId;} public void setApplicationId(Long v){applicationId=v;} public String getBusinessType(){return businessType;} public void setBusinessType(String v){businessType=v;}
    public String getBusinessId(){return businessId;} public void setBusinessId(String v){businessId=v;} public String getApplicantId(){return applicantId;} public void setApplicantId(String v){applicantId=v;}
    public Long getProcessId(){return processId;} public void setProcessId(Long v){processId=v;} public String getSubmitRequestId(){return submitRequestId;} public void setSubmitRequestId(String v){submitRequestId=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;} public Long getCurrentNodeId(){return currentNodeId;} public void setCurrentNodeId(Long v){currentNodeId=v;}
    public Long getLockVersion(){return lockVersion;} public void setLockVersion(Long v){lockVersion=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;} public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime v){completedAt=v;}
}
