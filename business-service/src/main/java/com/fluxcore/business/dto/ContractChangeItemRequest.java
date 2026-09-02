package com.fluxcore.business.dto;

import jakarta.validation.constraints.NotBlank;

public class ContractChangeItemRequest {
    @NotBlank private String fieldName;
    private String oldValue;
    @NotBlank private String newValue;

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
}
