package com.fluxcore.approval.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApprovalTaskQueryItem {
    private Long taskId;
    private Long approvalInstanceId;
    private Long nodeInstanceId;
    private Long sourceTaskId;
    private String assigneeId;
    private String taskStatus;
    private String action;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime actedAt;
    private LocalDateTime updatedAt;
    private String approvalNo;
    private Long applicationId;
    private String businessType;
    private String businessId;
    private String title;
    private String applicantId;
    private String approvalStatus;
    private Long currentNodeId;
    private String nodeName;
    private Integer snapshotNo;
    private String snapshotType;
    private String snapshotDataHash;
}
