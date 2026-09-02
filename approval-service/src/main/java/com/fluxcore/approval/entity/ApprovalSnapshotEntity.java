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
@TableName("approval_snapshot")
public class ApprovalSnapshotEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long approvalInstanceId;
    private Long nodeInstanceId;
    private Integer snapshotNo;
    private String snapshotType;
    private String businessType;
    private String businessId;
    private String dataJson;
    private String dataHash;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
