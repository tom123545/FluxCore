CREATE TABLE IF NOT EXISTS notification_record (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '通知记录主键',
    event_id VARCHAR(64) NOT NULL COMMENT '对应审批 Outbox 事件 ID',
    receiver_id VARCHAR(64) NOT NULL COMMENT '通知接收人 ID',
    channel VARCHAR(32) NOT NULL COMMENT '通知渠道：INBOX/EMAIL',
    status VARCHAR(32) NOT NULL COMMENT '通知状态：PENDING/SENT/FAILED',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '通知重试次数',
    error_message VARCHAR(1000) NULL COMMENT '最近一次失败原因',
    sent_at DATETIME(3) NULL COMMENT '发送成功时间',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    UNIQUE KEY uk_notification_event (event_id),
    KEY idx_notification_receiver_status (receiver_id, status, created_at),
    KEY idx_notification_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知投递记录表';
