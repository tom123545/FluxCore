package com.fluxcore.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("approval_action")
public class ApprovalActionEntity {
    @TableId(type=IdType.AUTO) private Long id; private Long approvalInstanceId,nodeInstanceId,taskId,snapshotId; private String operatorId,actionType,actionRequestId,fromStatus,toStatus,comment; private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getApprovalInstanceId(){return approvalInstanceId;} public void setApprovalInstanceId(Long v){approvalInstanceId=v;} public Long getNodeInstanceId(){return nodeInstanceId;} public void setNodeInstanceId(Long v){nodeInstanceId=v;} public Long getTaskId(){return taskId;} public void setTaskId(Long v){taskId=v;} public Long getSnapshotId(){return snapshotId;} public void setSnapshotId(Long v){snapshotId=v;} public String getOperatorId(){return operatorId;} public void setOperatorId(String v){operatorId=v;} public String getActionType(){return actionType;} public void setActionType(String v){actionType=v;} public String getActionRequestId(){return actionRequestId;} public void setActionRequestId(String v){actionRequestId=v;} public String getFromStatus(){return fromStatus;} public void setFromStatus(String v){fromStatus=v;} public String getToStatus(){return toStatus;} public void setToStatus(String v){toStatus=v;} public String getComment(){return comment;} public void setComment(String v){comment=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
