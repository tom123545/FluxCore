package com.fluxcore.approval.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApprovalHistoryRecord {
    private Long actionId;
    private Long nodeInstanceId;
    private Long taskId;
    private String nodeName;
    private String operatorId;
    private String actionType;
    private String fromStatus;
    private String toStatus;
    private String comment;
    private String actionRequestId;
    private Long snapshotId;
    private Integer snapshotNo;
    private String snapshotType;
    private String snapshotDataJson;
    private String snapshotDataHash;
    private LocalDateTime actionCreatedAt;
    private LocalDateTime snapshotCreatedAt;
}
