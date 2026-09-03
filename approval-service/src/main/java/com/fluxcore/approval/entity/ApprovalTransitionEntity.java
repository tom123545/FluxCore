package com.fluxcore.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("approval_transition")
public class ApprovalTransitionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long processId;
    private Long fromNodeId;
    private Long toNodeId;
    private String conditionJson;
    private Integer priority;
    private LocalDateTime createdAt;
}
