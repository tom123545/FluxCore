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
@TableName("approval_action")
public class ApprovalActionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long approvalInstanceId;
    private Long nodeInstanceId;
    private Long taskId;
    private Long snapshotId;
    private String operatorId;
    private String actionType;
    private String actionRequestId;
    private String fromStatus;
    private String toStatus;
    private String comment;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
