package com.fluxcore.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("application_ext")
public class ApplicationExtEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long applicationId;
    private String formData;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long value) { applicationId = value; }
    public String getFormData() { return formData; }
    public void setFormData(String value) { formData = value; }
    public String getRemark() { return remark; }
    public void setRemark(String value) { remark = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
