package com.fluxcore.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("approval_task")
public class ApprovalTaskEntity {
    @TableId(type=IdType.AUTO) private Long id; private Long approvalInstanceId,nodeInstanceId,sourceTaskId; private String assigneeId,status,action,comment; private LocalDateTime createdAt,actedAt,updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getApprovalInstanceId(){return approvalInstanceId;} public void setApprovalInstanceId(Long v){approvalInstanceId=v;} public Long getNodeInstanceId(){return nodeInstanceId;} public void setNodeInstanceId(Long v){nodeInstanceId=v;} public Long getSourceTaskId(){return sourceTaskId;} public void setSourceTaskId(Long v){sourceTaskId=v;} public String getAssigneeId(){return assigneeId;} public void setAssigneeId(String v){assigneeId=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getAction(){return action;} public void setAction(String v){action=v;} public String getComment(){return comment;} public void setComment(String v){comment=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getActedAt(){return actedAt;} public void setActedAt(LocalDateTime v){actedAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
