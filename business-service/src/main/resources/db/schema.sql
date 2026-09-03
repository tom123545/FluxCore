CREATE TABLE IF NOT EXISTS application (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '申请主键',
    application_no VARCHAR(64) NOT NULL COMMENT '申请单号',
    business_type VARCHAR(64) NOT NULL COMMENT '业务类型：PURCHASE/CONTRACT_CHANGE',
    business_id VARCHAR(128) NOT NULL COMMENT '业务单据 ID',
    title VARCHAR(255) NOT NULL COMMENT '申请标题',
    applicant_id VARCHAR(64) NOT NULL COMMENT '申请人 ID',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '创建申请请求幂等键',
    status VARCHAR(32) NOT NULL COMMENT 'DRAFT/SUBMITTED/APPROVED/REJECTED/WITHDRAWN',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    submitted_at DATETIME(3) NULL COMMENT '提交时间，草稿时为空',
    UNIQUE KEY uk_application_no (application_no),
    UNIQUE KEY uk_application_business (business_type, business_id),
    UNIQUE KEY uk_application_idempotency (business_type, idempotency_key),
    KEY idx_application_applicant_status (applicant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一申请主表';

CREATE TABLE IF NOT EXISTS application_ext (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '扩展主键',
    application_id BIGINT UNSIGNED NOT NULL COMMENT '申请 ID',
    form_data JSON NOT NULL COMMENT '通用表单扩展数据',
    remark VARCHAR(1000) NULL COMMENT '申请备注',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    UNIQUE KEY uk_application_ext_application (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申请扩展表';

CREATE TABLE IF NOT EXISTS procurement_request (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '采购申请主键',
    application_id BIGINT UNSIGNED NOT NULL COMMENT '统一申请 ID',
    request_no VARCHAR(64) NOT NULL COMMENT '采购单号',
    applicant_id VARCHAR(64) NOT NULL COMMENT '申请人 ID',
    department_code VARCHAR(64) NOT NULL COMMENT '申请部门',
    total_amount DECIMAL(18,2) NOT NULL COMMENT '采购总金额',
    currency CHAR(3) NOT NULL COMMENT '币种',
    status VARCHAR(32) NOT NULL COMMENT '采购业务状态：DRAFT/SUBMITTED/APPROVED/REJECTED/WITHDRAWN',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    UNIQUE KEY uk_procurement_application (application_id),
    UNIQUE KEY uk_procurement_request_no (request_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购申请表';

CREATE TABLE IF NOT EXISTS procurement_item (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '采购明细主键',
    procurement_id BIGINT UNSIGNED NOT NULL COMMENT '采购申请 ID',
    item_name VARCHAR(255) NOT NULL COMMENT '采购项目名称',
    quantity DECIMAL(18,2) NOT NULL COMMENT '数量',
    unit_price DECIMAL(18,2) NOT NULL COMMENT '单价',
    amount DECIMAL(18,2) NOT NULL COMMENT '明细金额',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    KEY idx_procurement_item_request (procurement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购明细表';

CREATE TABLE IF NOT EXISTS contract_change_request (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '合同变更主键',
    application_id BIGINT UNSIGNED NOT NULL COMMENT '统一申请 ID',
    change_no VARCHAR(64) NOT NULL COMMENT '变更单号',
    contract_no VARCHAR(64) NOT NULL COMMENT '原合同编号',
    applicant_id VARCHAR(64) NOT NULL COMMENT '申请人 ID',
    change_reason VARCHAR(1000) NOT NULL COMMENT '变更原因',
    change_amount DECIMAL(18,2) NOT NULL COMMENT '变更金额',
    currency CHAR(3) NOT NULL COMMENT '币种',
    status VARCHAR(32) NOT NULL COMMENT '合同业务状态：DRAFT/SUBMITTED/APPROVED/REJECTED/WITHDRAWN',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    UNIQUE KEY uk_contract_application (application_id),
    UNIQUE KEY uk_contract_change_no (change_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同变更申请表';

CREATE TABLE IF NOT EXISTS contract_change_item (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '合同变更明细主键',
    contract_change_id BIGINT UNSIGNED NOT NULL COMMENT '合同变更 ID',
    field_name VARCHAR(128) NOT NULL COMMENT '变更字段',
    old_value TEXT NULL COMMENT '变更前值',
    new_value TEXT NOT NULL COMMENT '变更后值',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    KEY idx_contract_item_request (contract_change_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同变更明细表';
