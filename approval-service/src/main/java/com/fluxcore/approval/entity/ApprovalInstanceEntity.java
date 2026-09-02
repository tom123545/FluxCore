package com.fluxcore.approval.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("approval_instance")
public class ApprovalInstanceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String approvalNo;
    private String businessType;
    private String businessId;
    private String applicantId;
    private String submitRequestId;
    private String status;
    private Long applicationId;
    private Long processId;
    private Long currentNodeId;
    private Long lockVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
