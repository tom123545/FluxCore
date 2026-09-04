package com.fluxcore.business.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public class CreateContractApplicationRequest {
    @NotBlank private String applicantId;
    @NotBlank private String title;
    @NotBlank private String contractNo;
    @NotBlank private String changeReason;
    @DecimalMin("0.00") private BigDecimal changeAmount;
    @Size(max = 1000) private String remark;
    @NotBlank private String currency;
    @NotBlank private String idempotencyKey;
    @Valid private List<ContractChangeItemRequest> items;

    public String getApplicantId() { return applicantId; }
    public void setApplicantId(String applicantId) { this.applicantId = applicantId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContractNo() { return contractNo; }
    public void setContractNo(String contractNo) { this.contractNo = contractNo; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public BigDecimal getChangeAmount() { return changeAmount; }
    public void setChangeAmount(BigDecimal changeAmount) { this.changeAmount = changeAmount; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public List<ContractChangeItemRequest> getItems() { return items; }
    public void setItems(List<ContractChangeItemRequest> items) { this.items = items; }
}
