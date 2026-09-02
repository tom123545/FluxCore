package com.fluxcore.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("contract_change_request")
public class ContractChangeRequestEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long applicationId;
    private String changeNo;
    private String contractNo;
    private String applicantId;
    private String changeReason;
    private BigDecimal changeAmount;
    private String currency;
    private String status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long value) { applicationId = value; }
    public String getChangeNo() { return changeNo; }
    public void setChangeNo(String value) { changeNo = value; }
    public String getContractNo() { return contractNo; }
    public void setContractNo(String value) { contractNo = value; }
    public String getApplicantId() { return applicantId; }
    public void setApplicantId(String value) { applicantId = value; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String value) { changeReason = value; }
    public BigDecimal getChangeAmount() { return changeAmount; }
    public void setChangeAmount(BigDecimal value) { changeAmount = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
