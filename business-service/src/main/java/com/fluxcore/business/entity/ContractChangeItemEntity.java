package com.fluxcore.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("contract_change_item")
public class ContractChangeItemEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long contractChangeId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getContractChangeId() { return contractChangeId; }
    public void setContractChangeId(Long value) { contractChangeId = value; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String value) { fieldName = value; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String value) { oldValue = value; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String value) { newValue = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
