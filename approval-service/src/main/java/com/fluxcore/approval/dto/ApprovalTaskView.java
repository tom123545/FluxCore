package com.fluxcore.approval.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApprovalTaskView {
    private Long id;
    private Long nodeInstanceId;
    private Long sourceTaskId;
    private Long nodeId;
    private String nodeName;
    private String assigneeId;
    private String status;
    private String action;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime actedAt;
}
