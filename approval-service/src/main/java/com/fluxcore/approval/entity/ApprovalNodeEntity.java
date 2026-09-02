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
@TableName("approval_node")
public class ApprovalNodeEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long processId;
    private String nodeCode;
    private String nodeName;
    private String nodeType;
    private String approvalMode;
    private String approverType;
    private String approverValue;
    private Integer sequenceNo;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
