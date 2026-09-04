CREATE TABLE IF NOT EXISTS notification_record (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '通知记录主键',
    event_id VARCHAR(64) NOT NULL COMMENT '对应审批 Outbox 事件 ID',
    receiver_id VARCHAR(64) NOT NULL COMMENT '通知接收人 ID',
    channel VARCHAR(32) NOT NULL COMMENT '通知渠道：INBOX/EMAIL',
    status VARCHAR(32) NOT NULL COMMENT '通知状态：PENDING/SENT/FAILED',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '通知重试次数',
    payload_json TEXT NOT NULL COMMENT '用于独立重试的原始事件消息',
    error_message VARCHAR(1000) NULL COMMENT '最近一次失败原因',
    next_retry_at DATETIME(3) NULL COMMENT '下次重试时间；NULL 表示不再重试',
    sent_at DATETIME(3) NULL COMMENT '发送成功时间',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    UNIQUE KEY uk_notification_event_receiver_channel (event_id, receiver_id, channel),
    KEY idx_notification_receiver_status (receiver_id, status, created_at),
    KEY idx_notification_status (status, updated_at),
    KEY idx_notification_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知投递记录表';

CREATE TABLE IF NOT EXISTS notification_failure (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '失败信封主键',
    event_id VARCHAR(64) NULL COMMENT '可解析时对应审批事件 ID',
    raw_message TEXT NOT NULL COMMENT '原始消息',
    status VARCHAR(32) NOT NULL COMMENT '失败状态：FAILED',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试次数',
    error_message VARCHAR(1000) NOT NULL COMMENT '失败原因',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    KEY idx_notification_failure_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可解析通知消息失败记录';
