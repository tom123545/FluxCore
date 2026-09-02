package com.fluxcore.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("approval_snapshot")
public class ApprovalSnapshotEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long approvalInstanceId;
    private Long nodeInstanceId;
    private Integer snapshotNo;
    private String snapshotType,businessType,businessId,dataJson,dataHash,createdBy;
    private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getApprovalInstanceId(){return approvalInstanceId;} public void setApprovalInstanceId(Long v){approvalInstanceId=v;} public Long getNodeInstanceId(){return nodeInstanceId;} public void setNodeInstanceId(Long v){nodeInstanceId=v;} public Integer getSnapshotNo(){return snapshotNo;} public void setSnapshotNo(Integer v){snapshotNo=v;} public String getSnapshotType(){return snapshotType;} public void setSnapshotType(String v){snapshotType=v;} public String getBusinessType(){return businessType;} public void setBusinessType(String v){businessType=v;} public String getBusinessId(){return businessId;} public void setBusinessId(String v){businessId=v;} public String getDataJson(){return dataJson;} public void setDataJson(String v){dataJson=v;} public String getDataHash(){return dataHash;} public void setDataHash(String v){dataHash=v;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
