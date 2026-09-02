CREATE TABLE IF NOT EXISTS approval_process (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '流程主键',
    process_code VARCHAR(64) NOT NULL COMMENT '流程编码',
    business_type VARCHAR(64) NOT NULL COMMENT '业务类型',
    process_name VARCHAR(128) NOT NULL COMMENT '流程名称',
    version_no INT UNSIGNED NOT NULL COMMENT '流程版本号',
    status VARCHAR(32) NOT NULL COMMENT '流程状态：DRAFT/PUBLISHED/RETIRED',
    definition_json JSON NOT NULL COMMENT '完整流程定义快照',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人 ID',
    published_at DATETIME(3) NULL COMMENT '发布时间',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    UNIQUE KEY uk_process_code (process_code),
    UNIQUE KEY uk_process_business_version (business_type, version_no),
    KEY idx_process_published (business_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程定义表';

CREATE TABLE IF NOT EXISTS approval_node (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '节点主键',
    process_id BIGINT UNSIGNED NOT NULL COMMENT '所属流程 ID',
    node_code VARCHAR(64) NOT NULL COMMENT '节点编码',
    node_name VARCHAR(128) NOT NULL COMMENT '节点名称',
    node_type VARCHAR(32) NOT NULL COMMENT '节点类型：START/APPROVAL/END/CONDITION',
    approval_mode VARCHAR(32) NULL COMMENT '审批模式：SINGLE/AND/OR',
    approver_type VARCHAR(32) NULL COMMENT '审批人类型：USER/ROLE/DEPARTMENT_MANAGER',
    approver_value VARCHAR(128) NULL COMMENT '审批人规则值',
    sequence_no INT NOT NULL COMMENT '串行流程顺序',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    UNIQUE KEY uk_process_node (process_id, node_code),
    KEY idx_process_node_sequence (process_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程节点表';

CREATE TABLE IF NOT EXISTS approval_transition (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '流转关系主键',
    process_id BIGINT UNSIGNED NOT NULL COMMENT '所属流程 ID',
    from_node_id BIGINT UNSIGNED NOT NULL COMMENT '起始节点 ID',
    to_node_id BIGINT UNSIGNED NOT NULL COMMENT '目标节点 ID',
    condition_json JSON NULL COMMENT '条件分支规则，为空表示默认流转',
    priority INT NOT NULL DEFAULT 0 COMMENT '规则优先级，数字越小越优先',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_transition_route (process_id, from_node_id, to_node_id, priority),
    KEY idx_transition_from (process_id, from_node_id, priority),
    KEY idx_transition_to (process_id, to_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批节点流转表';

CREATE TABLE IF NOT EXISTS approval_instance (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '审批实例主键',
    approval_no VARCHAR(64) NOT NULL COMMENT '对外审批单号',
    application_id BIGINT UNSIGNED NOT NULL COMMENT '统一申请 ID',
    business_type VARCHAR(64) NOT NULL COMMENT '业务类型',
    business_id VARCHAR(128) NOT NULL COMMENT '业务单据 ID',
    applicant_id VARCHAR(64) NOT NULL COMMENT '申请人 ID',
    process_id BIGINT UNSIGNED NOT NULL COMMENT '绑定的流程版本 ID',
    submit_request_id VARCHAR(128) NOT NULL COMMENT '提交审批请求幂等键',
    status VARCHAR(32) NOT NULL COMMENT '审批状态：IN_PROGRESS/APPROVED/REJECTED/WITHDRAWN',
    current_node_id BIGINT UNSIGNED NULL COMMENT '当前活动节点 ID',
    lock_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '审批动作乐观锁版本',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    completed_at DATETIME(3) NULL COMMENT '完成时间',
    UNIQUE KEY uk_approval_no (approval_no),
    UNIQUE KEY uk_approval_application (application_id),
    UNIQUE KEY uk_approval_submit_request (application_id, submit_request_id),
    KEY idx_approval_business (business_type, business_id),
    KEY idx_approval_status_node (status, current_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批实例主表';

CREATE TABLE IF NOT EXISTS approval_node_instance (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '节点执行实例主键',
    approval_instance_id BIGINT UNSIGNED NOT NULL COMMENT '所属审批实例 ID',
    node_id BIGINT UNSIGNED NOT NULL COMMENT '对应配置节点 ID',
    status VARCHAR(32) NOT NULL COMMENT '节点状态：ACTIVE/COMPLETED/REJECTED/CANCELLED',
    started_at DATETIME(3) NOT NULL COMMENT '节点激活时间',
    completed_at DATETIME(3) NULL COMMENT '节点完成时间',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    KEY idx_node_instance_approval (approval_instance_id),
    KEY idx_node_instance_status (approval_instance_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批节点执行实例表';

CREATE TABLE IF NOT EXISTS approval_task (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '审批任务主键',
    approval_instance_id BIGINT UNSIGNED NOT NULL COMMENT '所属审批实例 ID',
    node_instance_id BIGINT UNSIGNED NOT NULL COMMENT '所属节点实例 ID',
    assignee_id VARCHAR(64) NOT NULL COMMENT '实际审批人 ID',
    status VARCHAR(32) NOT NULL COMMENT '任务状态：PENDING/APPROVED/REJECTED/TRANSFERRED/CANCELLED',
    action VARCHAR(32) NULL COMMENT '实际执行动作',
    comment VARCHAR(2000) NULL COMMENT '审批意见',
    source_task_id BIGINT UNSIGNED NULL COMMENT '转审或加签来源任务 ID',
    created_at DATETIME(3) NOT NULL COMMENT '待办生成时间',
    acted_at DATETIME(3) NULL COMMENT '任务处理时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    KEY idx_task_todo (assignee_id, status, created_at),
    KEY idx_task_instance (approval_instance_id, status),
    KEY idx_task_node_instance (node_instance_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批任务和待办表';

CREATE TABLE IF NOT EXISTS approval_action (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '审批动作主键',
    approval_instance_id BIGINT UNSIGNED NOT NULL COMMENT '所属审批实例 ID',
    node_instance_id BIGINT UNSIGNED NULL COMMENT '对应节点实例 ID',
    task_id BIGINT UNSIGNED NULL COMMENT '对应审批任务 ID',
    operator_id VARCHAR(64) NOT NULL COMMENT '实际操作人 ID',
    action_type VARCHAR(32) NOT NULL COMMENT '动作类型：SUBMIT/APPROVE/REJECT/WITHDRAW/TRANSFER/ADD_SIGN',
    from_status VARCHAR(32) NOT NULL COMMENT '操作前审批实例状态',
    to_status VARCHAR(32) NOT NULL COMMENT '操作后审批实例状态',
    comment VARCHAR(2000) NULL COMMENT '审批意见或操作说明',
    snapshot_id BIGINT UNSIGNED NULL COMMENT '本动作关联的快照 ID',
    action_request_id VARCHAR(128) NOT NULL COMMENT '审批动作请求幂等键',
    created_at DATETIME(3) NOT NULL COMMENT '动作发生时间',
    UNIQUE KEY uk_action_request (approval_instance_id, action_request_id),
    KEY idx_action_instance_time (approval_instance_id, created_at),
    KEY idx_action_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批动作历史表';

CREATE TABLE IF NOT EXISTS approval_snapshot (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '快照主键',
    approval_instance_id BIGINT UNSIGNED NOT NULL COMMENT '所属审批实例 ID',
    node_instance_id BIGINT UNSIGNED NULL COMMENT '对应节点实例 ID，提交快照为空',
    snapshot_no INT UNSIGNED NOT NULL COMMENT '同一实例内快照序号',
    snapshot_type VARCHAR(32) NOT NULL COMMENT '快照类型：SUBMIT/APPROVE/REJECT/WITHDRAW',
    business_type VARCHAR(64) NOT NULL COMMENT '业务类型',
    business_id VARCHAR(128) NOT NULL COMMENT '业务单据 ID',
    data_json JSON NOT NULL COMMENT '审批时的完整业务数据',
    data_hash CHAR(64) NOT NULL COMMENT '快照内容 SHA-256 摘要',
    created_by VARCHAR(64) NOT NULL COMMENT '触发快照的用户 ID',
    created_at DATETIME(3) NOT NULL COMMENT '快照创建时间',
    UNIQUE KEY uk_snapshot_version (approval_instance_id, snapshot_no),
    KEY idx_snapshot_node (node_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批不可变数据快照表';

CREATE TABLE IF NOT EXISTS approval_outbox_event (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT 'Outbox 事件主键',
    event_id VARCHAR(64) NOT NULL COMMENT '事件唯一 ID',
    aggregate_type VARCHAR(64) NOT NULL COMMENT '聚合类型',
    aggregate_id VARCHAR(64) NOT NULL COMMENT '聚合对象 ID',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    payload_json JSON NOT NULL COMMENT '事件消息体',
    status VARCHAR(32) NOT NULL COMMENT '发布状态：NEW/PUBLISHED/FAILED',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_at DATETIME(3) NULL COMMENT '下次重试时间',
    created_at DATETIME(3) NOT NULL COMMENT '事件创建时间',
    published_at DATETIME(3) NULL COMMENT '投递成功时间',
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbox 可靠事件表';
