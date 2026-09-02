package com.fluxcore.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("approval_node_instance")
public class ApprovalNodeInstanceEntity {
    @TableId(type=IdType.AUTO) private Long id; private Long approvalInstanceId,nodeId; private String status; private LocalDateTime startedAt,completedAt,createdAt,updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getApprovalInstanceId(){return approvalInstanceId;} public void setApprovalInstanceId(Long v){approvalInstanceId=v;} public Long getNodeId(){return nodeId;} public void setNodeId(Long v){nodeId=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public LocalDateTime getStartedAt(){return startedAt;} public void setStartedAt(LocalDateTime v){startedAt=v;} public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime v){completedAt=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
