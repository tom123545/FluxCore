INSERT INTO approval_process
    (process_code, business_type, process_name, version_no, status, definition_json, created_by, published_at, created_at, updated_at)
VALUES
    ('PURCHASE_STANDARD', 'PURCHASE', '采购标准审批流程', 1, 'PUBLISHED',
     '{"type":"SERIAL","nodes":["DEPT_MANAGER","FINANCE_MANAGER","GENERAL_MANAGER","END"]}',
     'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE process_name = VALUES(process_name), status = VALUES(status), definition_json = VALUES(definition_json), published_at = VALUES(published_at), updated_at = VALUES(updated_at);

INSERT INTO approval_process
    (process_code, business_type, process_name, version_no, status, definition_json, created_by, published_at, created_at, updated_at)
VALUES
    ('CONTRACT_STANDARD', 'CONTRACT_CHANGE', '合同变更标准审批流程', 1, 'PUBLISHED',
     '{"type":"SERIAL","nodes":["DEPT_MANAGER","LEGAL_MANAGER","END"]}',
     'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE process_name = VALUES(process_name), status = VALUES(status), definition_json = VALUES(definition_json), published_at = VALUES(published_at), updated_at = VALUES(updated_at);

INSERT INTO approval_node
    (process_id, node_code, node_name, node_type, approval_mode, approver_type, approver_value, sequence_no, created_at, updated_at)
SELECT id, 'DEPT_MANAGER', '部门负责人审批', 'APPROVAL', 'SINGLE', 'USER', 'U2001', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM approval_process WHERE process_code = 'PURCHASE_STANDARD'
ON DUPLICATE KEY UPDATE node_name = VALUES(node_name), approval_mode = VALUES(approval_mode), approver_type = VALUES(approver_type), approver_value = VALUES(approver_value), sequence_no = VALUES(sequence_no), updated_at = VALUES(updated_at);

INSERT INTO approval_node
    (process_id, node_code, node_name, node_type, approval_mode, approver_type, approver_value, sequence_no, created_at, updated_at)
SELECT id, 'FINANCE_MANAGER', '财务负责人审批', 'APPROVAL', 'SINGLE', 'USER', 'U2002', 2, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM approval_process WHERE process_code = 'PURCHASE_STANDARD'
ON DUPLICATE KEY UPDATE node_name = VALUES(node_name), approval_mode = VALUES(approval_mode), approver_type = VALUES(approver_type), approver_value = VALUES(approver_value), sequence_no = VALUES(sequence_no), updated_at = VALUES(updated_at);

INSERT INTO approval_node
    (process_id, node_code, node_name, node_type, approval_mode, approver_type, approver_value, sequence_no, created_at, updated_at)
SELECT id, 'GENERAL_MANAGER', '总经理审批', 'APPROVAL', 'SINGLE', 'USER', 'U2003', 3, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM approval_process WHERE process_code = 'PURCHASE_STANDARD'
ON DUPLICATE KEY UPDATE node_name = VALUES(node_name), approval_mode = VALUES(approval_mode), approver_type = VALUES(approver_type), approver_value = VALUES(approver_value), sequence_no = VALUES(sequence_no), updated_at = VALUES(updated_at);

INSERT INTO approval_node
    (process_id, node_code, node_name, node_type, approval_mode, approver_type, approver_value, sequence_no, created_at, updated_at)
SELECT id, 'DEPT_MANAGER', '部门负责人审批', 'APPROVAL', 'SINGLE', 'USER', 'U2001', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM approval_process WHERE process_code = 'CONTRACT_STANDARD'
ON DUPLICATE KEY UPDATE node_name = VALUES(node_name), approval_mode = VALUES(approval_mode), approver_type = VALUES(approver_type), approver_value = VALUES(approver_value), sequence_no = VALUES(sequence_no), updated_at = VALUES(updated_at);

INSERT INTO approval_node
    (process_id, node_code, node_name, node_type, approval_mode, approver_type, approver_value, sequence_no, created_at, updated_at)
SELECT id, 'LEGAL_MANAGER', '法务负责人审批', 'APPROVAL', 'SINGLE', 'USER', 'U2004', 2, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM approval_process WHERE process_code = 'CONTRACT_STANDARD'
ON DUPLICATE KEY UPDATE node_name = VALUES(node_name), approval_mode = VALUES(approval_mode), approver_type = VALUES(approver_type), approver_value = VALUES(approver_value), sequence_no = VALUES(sequence_no), updated_at = VALUES(updated_at);

INSERT INTO approval_node
    (process_id, node_code, node_name, node_type, approval_mode, approver_type, approver_value, sequence_no, created_at, updated_at)
SELECT id, 'END', '结束', 'END', NULL, NULL, NULL, 4, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM approval_process WHERE process_code = 'PURCHASE_STANDARD'
ON DUPLICATE KEY UPDATE node_name = VALUES(node_name), node_type = VALUES(node_type), sequence_no = VALUES(sequence_no), updated_at = VALUES(updated_at);

INSERT INTO approval_node
    (process_id, node_code, node_name, node_type, approval_mode, approver_type, approver_value, sequence_no, created_at, updated_at)
SELECT id, 'END', '结束', 'END', NULL, NULL, NULL, 3, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM approval_process WHERE process_code = 'CONTRACT_STANDARD'
ON DUPLICATE KEY UPDATE node_name = VALUES(node_name), node_type = VALUES(node_type), sequence_no = VALUES(sequence_no), updated_at = VALUES(updated_at);

INSERT INTO approval_transition
    (process_id, from_node_id, to_node_id, condition_json, priority, created_at)
SELECT p.id, n1.id, n2.id, NULL, 0, CURRENT_TIMESTAMP(3)
FROM approval_process p
JOIN approval_node n1 ON n1.process_id = p.id AND n1.node_code = 'DEPT_MANAGER'
JOIN approval_node n2 ON n2.process_id = p.id AND n2.node_code = 'FINANCE_MANAGER'
WHERE p.process_code = 'PURCHASE_STANDARD'
ON DUPLICATE KEY UPDATE priority = VALUES(priority);

INSERT INTO approval_transition
    (process_id, from_node_id, to_node_id, condition_json, priority, created_at)
SELECT p.id, n1.id, n2.id, NULL, 0, CURRENT_TIMESTAMP(3)
FROM approval_process p
JOIN approval_node n1 ON n1.process_id = p.id AND n1.node_code = 'FINANCE_MANAGER'
JOIN approval_node n2 ON n2.process_id = p.id AND n2.node_code = 'GENERAL_MANAGER'
WHERE p.process_code = 'PURCHASE_STANDARD'
ON DUPLICATE KEY UPDATE priority = VALUES(priority);

INSERT INTO approval_transition
    (process_id, from_node_id, to_node_id, condition_json, priority, created_at)
SELECT p.id, n1.id, n2.id, NULL, 0, CURRENT_TIMESTAMP(3)
FROM approval_process p
JOIN approval_node n1 ON n1.process_id = p.id AND n1.node_code = 'DEPT_MANAGER'
JOIN approval_node n2 ON n2.process_id = p.id AND n2.node_code = 'LEGAL_MANAGER'
WHERE p.process_code = 'CONTRACT_STANDARD'
ON DUPLICATE KEY UPDATE priority = VALUES(priority);

INSERT INTO approval_transition
    (process_id, from_node_id, to_node_id, condition_json, priority, created_at)
SELECT p.id, n1.id, n2.id, NULL, 0, CURRENT_TIMESTAMP(3)
FROM approval_process p
JOIN approval_node n1 ON n1.process_id = p.id AND n1.node_code = 'GENERAL_MANAGER'
JOIN approval_node n2 ON n2.process_id = p.id AND n2.node_code = 'END'
WHERE p.process_code = 'PURCHASE_STANDARD'
ON DUPLICATE KEY UPDATE priority = VALUES(priority);

INSERT INTO approval_transition
    (process_id, from_node_id, to_node_id, condition_json, priority, created_at)
SELECT p.id, n1.id, n2.id, NULL, 0, CURRENT_TIMESTAMP(3)
FROM approval_process p
JOIN approval_node n1 ON n1.process_id = p.id AND n1.node_code = 'LEGAL_MANAGER'
JOIN approval_node n2 ON n2.process_id = p.id AND n2.node_code = 'END'
WHERE p.process_code = 'CONTRACT_STANDARD'
ON DUPLICATE KEY UPDATE priority = VALUES(priority);
