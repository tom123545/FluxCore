# FluxCore 数据库设计技术方案

## 1. 文档目的

本文档是 FluxCore 当前“一库多表”方案的数据库设计基线，供开发、面试讲解和后续模型接手使用。

本方案解决以下问题：

- 采购和合同使用同一套通用审批引擎；
- 新增审批流程只增加流程配置，不复制审批服务；
- 申请可以先保存为草稿，再提交审批；
- 审批历史保存操作时的数据快照；
- Redis 锁用于减少重复请求，MySQL 唯一约束和乐观锁负责最终一致性；
- 采购、合同和审批数据放在一个 MySQL 数据库中，但通过表职责和服务代码边界隔离。

## 2. 技术与边界

```text
MySQL 8
数据库：fluxcore

business-service：application_*、procurement_*、contract_*
approval-service：approval_*（幂等字段直接放在申请/审批运行表）
notification-service：notification_*
```

说明：这是一个 MySQL 实例、一个逻辑数据库、多张表。服务不能因为共用数据库就直接操作其他服务的表，也不建立跨服务物理外键。`approval-service` 通过 `business-service` 的内部接口读取业务详情，并把读取结果保存到快照表。

## 3. 总体关系

```text
application                         统一申请主单
    ├── application_ext             申请扩展数据
    ├── procurement_request         采购业务数据（可选）
    │     └── procurement_item
    ├── contract_change_request     合同业务数据（可选）
    │     └── contract_change_item
    └── approval_instance            提交后创建的审批实例
          └── approval_node_instance
                └── approval_task
          ├── approval_action
          ├── approval_snapshot
          └── approval_outbox_event

approval_process
    ├── approval_node
    └── approval_transition
```

核心关系：

- 一条 `application` 表示一个采购或合同申请；
- 一条申请最多对应一个当前审批实例，`approval_instance.application_id` 唯一；
- 一个审批实例可以经过多个节点；
- 一个节点可以生成一个或多个审批任务；
- 每次提交或关键审批动作新增一条快照，不更新旧快照；
- 流程配置和审批运行数据分开，审批实例绑定创建时的流程版本。

## 4. 字段公共约定

| 约定 | 设计 |
|---|---|
| 主键 | `BIGINT UNSIGNED AUTO_INCREMENT` |
| 时间 | `DATETIME(3)`，保存毫秒，统一使用 Asia/Shanghai 展示 |
| 金额 | `DECIMAL(18,2)`，禁止使用浮点数 |
| 状态 | `VARCHAR(32)`，由 Java 枚举约束可选值 |
| JSON | MySQL `JSON`，保存表单、快照和事件载荷 |
| 外部 ID | `VARCHAR(64/128)`，不在审批库维护用户或组织主数据 |
| 删除策略 | 审批、任务、动作、快照只做状态终结，不物理删除 |

## 5. 申请和业务数据表

### 5.1 `application`：申请主表

由 `business-service` 负责维护，采购和合同共用。它只保存通用申请字段，不保存采购明细或合同变更字段。

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 申请主键 |
| `application_no` | VARCHAR(64) | UNIQUE, NOT NULL | 对外展示的申请单号 |
| `business_type` | VARCHAR(64) | NOT NULL | 业务类型，如 `PURCHASE`、`CONTRACT_CHANGE` |
| `business_id` | VARCHAR(128) | NOT NULL | 业务单据 ID，关联对应业务模块 |
| `title` | VARCHAR(255) | NOT NULL | 申请标题 |
| `applicant_id` | VARCHAR(64) | NOT NULL | 申请人外部用户 ID |
| `status` | VARCHAR(32) | NOT NULL | `DRAFT`、`SUBMITTED`、`CANCELLED` |
| `idempotency_key` | VARCHAR(128) | NOT NULL | 创建申请请求幂等键 |
| `version` | BIGINT UNSIGNED | NOT NULL | 草稿修改的乐观锁版本，初始为 0 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 最后修改时间 |
| `submitted_at` | DATETIME(3) | NULL | 提交时间，草稿时为空 |

关键索引：

```sql
UNIQUE KEY uk_application_business (business_type, business_id),
UNIQUE KEY uk_application_idempotency (business_type, idempotency_key),
KEY idx_application_applicant_status (applicant_id, status)
```

### 5.2 `application_ext`：申请扩展表

保存表单中不适合放入主表的通用字段。正式业务字段仍应进入采购/合同业务表，不能把所有业务都无约束地塞进 JSON。

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 扩展记录主键 |
| `application_id` | BIGINT UNSIGNED | UNIQUE, NOT NULL | 对应申请 ID，不建立跨服务 FK |
| `form_data` | JSON | NOT NULL | 通用表单扩展内容 |
| `remark` | VARCHAR(1000) | NULL | 申请备注 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 修改时间 |

### 5.3 `procurement_request`：采购申请表

由 `business-service` 的采购模块负责维护。

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 采购申请主键 |
| `application_id` | BIGINT UNSIGNED | UNIQUE, NOT NULL | 对应统一申请主单 |
| `request_no` | VARCHAR(64) | UNIQUE, NOT NULL | 采购单号 |
| `applicant_id` | VARCHAR(64) | NOT NULL | 申请人 ID |
| `department_code` | VARCHAR(64) | NOT NULL | 申请部门编码 |
| `total_amount` | DECIMAL(18,2) | NOT NULL | 采购总金额 |
| `currency` | CHAR(3) | NOT NULL | 币种，如 `CNY` |
| `status` | VARCHAR(32) | NOT NULL | `DRAFT`、`SUBMITTED`、`APPROVED`、`REJECTED` |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 修改时间 |

### 5.4 `procurement_item`：采购明细表

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 明细主键 |
| `procurement_id` | BIGINT UNSIGNED | NOT NULL | 所属采购申请 ID |
| `item_name` | VARCHAR(255) | NOT NULL | 采购项目名称 |
| `quantity` | DECIMAL(18,2) | NOT NULL | 数量 |
| `unit_price` | DECIMAL(18,2) | NOT NULL | 含税单价 |
| `amount` | DECIMAL(18,2) | NOT NULL | 明细金额 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 修改时间 |

### 5.5 `contract_change_request`：合同变更申请表

由 `business-service` 的合同模块负责维护。

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 合同变更主键 |
| `application_id` | BIGINT UNSIGNED | UNIQUE, NOT NULL | 对应统一申请主单 |
| `change_no` | VARCHAR(64) | UNIQUE, NOT NULL | 合同变更单号 |
| `contract_no` | VARCHAR(64) | NOT NULL | 原合同编号 |
| `applicant_id` | VARCHAR(64) | NOT NULL | 申请人 ID |
| `change_reason` | VARCHAR(1000) | NOT NULL | 变更原因 |
| `change_amount` | DECIMAL(18,2) | NOT NULL | 变更金额 |
| `currency` | CHAR(3) | NOT NULL | 币种 |
| `status` | VARCHAR(32) | NOT NULL | `DRAFT`、`SUBMITTED`、`APPROVED`、`REJECTED` |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 修改时间 |

### 5.6 `contract_change_item`：合同变更明细表

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 变更明细主键 |
| `contract_change_id` | BIGINT UNSIGNED | NOT NULL | 所属合同变更 ID |
| `field_name` | VARCHAR(128) | NOT NULL | 变更字段名称 |
| `old_value` | TEXT | NULL | 变更前值 |
| `new_value` | TEXT | NOT NULL | 变更后值 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 修改时间 |

## 6. 流程配置表

### 6.1 `approval_process`：审批流程定义表

由 `approval-service` 维护。同一个审批引擎可以有采购、合同和未来其他业务的多条流程定义。

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 流程主键 |
| `process_code` | VARCHAR(64) | UNIQUE, NOT NULL | 流程编码 |
| `business_type` | VARCHAR(64) | NOT NULL | 匹配申请的业务类型 |
| `process_name` | VARCHAR(128) | NOT NULL | 流程名称 |
| `version_no` | INT UNSIGNED | NOT NULL | 流程版本号 |
| `status` | VARCHAR(32) | NOT NULL | `DRAFT`、`PUBLISHED`、`RETIRED` |
| `definition_json` | JSON | NOT NULL | 可选的完整流程定义快照 |
| `created_by` | VARCHAR(64) | NOT NULL | 创建人 |
| `published_at` | DATETIME(3) | NULL | 发布时间 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 修改时间 |

约束：

```sql
UNIQUE KEY uk_process_business_version (business_type, version_no),
KEY idx_process_published (business_type, status)
```

已经启动的审批实例保存 `process_id`，后续发布新版本不会改变旧实例的执行规则。

### 6.2 `approval_node`：流程节点表

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 节点主键 |
| `process_id` | BIGINT UNSIGNED | NOT NULL | 所属流程定义 |
| `node_code` | VARCHAR(64) | NOT NULL | 节点编码 |
| `node_name` | VARCHAR(128) | NOT NULL | 节点名称 |
| `node_type` | VARCHAR(32) | NOT NULL | `START`、`APPROVAL`、`END`、`CONDITION` |
| `approval_mode` | VARCHAR(32) | NULL | `SINGLE`、`AND`、`OR` |
| `approver_type` | VARCHAR(32) | NULL | `USER`、`ROLE`、`DEPARTMENT_MANAGER` |
| `approver_value` | VARCHAR(128) | NULL | 用户 ID、角色编码或解析规则 |
| `sequence_no` | INT | NOT NULL | 串行流程的顺序 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 修改时间 |

约束：`UNIQUE(process_id, node_code)`。

### 6.3 `approval_transition`：节点流转表

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 流转关系主键 |
| `process_id` | BIGINT UNSIGNED | NOT NULL | 所属流程 |
| `from_node_id` | BIGINT UNSIGNED | NOT NULL | 起始节点 |
| `to_node_id` | BIGINT UNSIGNED | NOT NULL | 目标节点 |
| `condition_json` | JSON | NULL | 条件分支规则，空表示默认流转 |
| `priority` | INT | NOT NULL | 规则优先级，数字越小越优先 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |

## 7. 审批运行表

### 7.1 `approval_instance`：审批实例主表

申请提交后由 `approval-service` 创建。

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 审批实例主键 |
| `approval_no` | VARCHAR(64) | UNIQUE, NOT NULL | 对外审批单号 |
| `application_id` | BIGINT UNSIGNED | UNIQUE, NOT NULL | 对应申请主单 |
| `business_type` | VARCHAR(64) | NOT NULL | 冗余保存，便于查询和路由 |
| `business_id` | VARCHAR(128) | NOT NULL | 业务单据 ID |
| `applicant_id` | VARCHAR(64) | NOT NULL | 申请人 ID |
| `process_id` | BIGINT UNSIGNED | NOT NULL | 创建时绑定的流程版本 |
| `submit_request_id` | VARCHAR(128) | NOT NULL | 提交审批请求幂等键 |
| `status` | VARCHAR(32) | NOT NULL | `IN_PROGRESS`、`APPROVED`、`REJECTED`、`WITHDRAWN` |
| `current_node_id` | BIGINT UNSIGNED | NULL | 当前活动节点 |
| `lock_version` | BIGINT UNSIGNED | NOT NULL | 审批动作乐观锁版本 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 最后更新时间 |
| `completed_at` | DATETIME(3) | NULL | 完成时间 |

关键索引：

```sql
UNIQUE KEY uk_approval_application (application_id),
UNIQUE KEY uk_approval_submit_request (application_id, submit_request_id),
KEY idx_approval_business (business_type, business_id),
KEY idx_approval_status_node (status, current_node_id)
```

### 7.2 `approval_node_instance`：节点执行实例表

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 节点执行实例主键 |
| `approval_instance_id` | BIGINT UNSIGNED | NOT NULL | 所属审批实例 |
| `node_id` | BIGINT UNSIGNED | NOT NULL | 对应配置节点 |
| `status` | VARCHAR(32) | NOT NULL | `ACTIVE`、`COMPLETED`、`REJECTED`、`CANCELLED` |
| `started_at` | DATETIME(3) | NOT NULL | 节点激活时间 |
| `completed_at` | DATETIME(3) | NULL | 节点完成时间 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |

### 7.3 `approval_task`：审批任务/待办表

待办和已办共用此表，不物理删除任务。

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 任务主键 |
| `approval_instance_id` | BIGINT UNSIGNED | NOT NULL | 所属审批实例 |
| `node_instance_id` | BIGINT UNSIGNED | NOT NULL | 所属节点实例 |
| `assignee_id` | VARCHAR(64) | NOT NULL | 实际生成的审批人 ID |
| `status` | VARCHAR(32) | NOT NULL | `PENDING`、`APPROVED`、`REJECTED`、`TRANSFERRED`、`CANCELLED` |
| `action` | VARCHAR(32) | NULL | 实际动作 |
| `comment` | VARCHAR(2000) | NULL | 审批意见 |
| `source_task_id` | BIGINT UNSIGNED | NULL | 转审/加签来源任务 |
| `created_at` | DATETIME(3) | NOT NULL | 待办生成时间 |
| `acted_at` | DATETIME(3) | NULL | 处理时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |

关键索引：

```sql
KEY idx_task_todo (assignee_id, status, created_at),
KEY idx_task_instance (approval_instance_id, status)
```

### 7.4 `approval_action`：审批动作历史表

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 动作主键 |
| `approval_instance_id` | BIGINT UNSIGNED | NOT NULL | 所属审批实例 |
| `node_instance_id` | BIGINT UNSIGNED | NULL | 对应节点实例 |
| `task_id` | BIGINT UNSIGNED | NULL | 对应审批任务 |
| `operator_id` | VARCHAR(64) | NOT NULL | 操作人 |
| `action_type` | VARCHAR(32) | NOT NULL | `SUBMIT`、`APPROVE`、`REJECT`、`WITHDRAW`、`TRANSFER`、`ADD_SIGN` |
| `from_status` | VARCHAR(32) | NOT NULL | 操作前实例状态 |
| `to_status` | VARCHAR(32) | NOT NULL | 操作后实例状态 |
| `comment` | VARCHAR(2000) | NULL | 审批意见 |
| `snapshot_id` | BIGINT UNSIGNED | NULL | 本动作产生的快照 ID |
| `action_request_id` | VARCHAR(128) | NOT NULL | 审批动作请求幂等键，不同动作请求必须唯一 |
| `request_hash` | CHAR(64) | NULL | 完整动作请求规范化后的 SHA-256 摘要 |
| `created_at` | DATETIME(3) | NOT NULL | 动作发生时间 |

约束：`UNIQUE(approval_instance_id, action_request_id)`。

### 7.5 `approval_snapshot`：审批数据快照表

快照只新增不修改，是历史查询的事实来源。

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 快照主键 |
| `approval_instance_id` | BIGINT UNSIGNED | NOT NULL | 所属审批实例 |
| `node_instance_id` | BIGINT UNSIGNED | NULL | 对应节点，提交快照可为空 |
| `snapshot_no` | INT UNSIGNED | NOT NULL | 同一实例内的快照序号 |
| `snapshot_type` | VARCHAR(32) | NOT NULL | `SUBMIT`、`APPROVE`、`REJECT`、`WITHDRAW`、`TRANSFER`、`ADD_SIGN` |
| `business_type` | VARCHAR(64) | NOT NULL | 业务类型 |
| `business_id` | VARCHAR(128) | NOT NULL | 业务单据 ID |
| `data_json` | JSON | NOT NULL | 当时的完整业务数据 |
| `data_hash` | CHAR(64) | NOT NULL | 快照内容 SHA-256 摘要 |
| `created_by` | VARCHAR(64) | NOT NULL | 触发快照的用户 |
| `created_at` | DATETIME(3) | NOT NULL | 快照时间 |

约束：`UNIQUE(approval_instance_id, snapshot_no)`。

### 7.6 `approval_outbox_event`：可靠事件表

审批状态和 Outbox 事件在同一个事务中写入，后台发布器再投递 RabbitMQ。

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | Outbox 主键 |
| `event_id` | VARCHAR(64) | UNIQUE, NOT NULL | 事件唯一 ID，消费端去重 |
| `aggregate_type` | VARCHAR(64) | NOT NULL | 聚合类型，如 `APPROVAL_INSTANCE` |
| `aggregate_id` | VARCHAR(64) | NOT NULL | 聚合 ID |
| `event_type` | VARCHAR(64) | NOT NULL | `APPROVAL_SUBMITTED`、`APPROVAL_APPROVED` 等 |
| `payload_json` | JSON | NOT NULL | 事件消息体 |
| `status` | VARCHAR(32) | NOT NULL | `NEW`、`PUBLISHED`、`FAILED` |
| `retry_count` | INT UNSIGNED | NOT NULL | 已重试次数 |
| `next_retry_at` | DATETIME(3) | NULL | 下次重试时间 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `published_at` | DATETIME(3) | NULL | 投递成功时间 |

## 8. 幂等字段设计

本项目不建立独立的 `idempotency_record` 表。幂等信息直接保存在产生资源的业务表和审批运行表中：

| 表 | 字段 | 用途 |
|---|---|---|
| `application` | `idempotency_key` | 防止同一个创建申请请求生成两条申请 |
| `approval_instance` | `submit_request_id` | 防止同一个提交请求生成两个审批实例 |
| `approval_action` | `action_request_id` | 防止同一个审批动作重复写入历史或推进流程 |

重复请求处理规则：

1. 先获取 Redis 锁，减少同一请求并发执行；
2. 查询对应业务表或审批运行表中的幂等字段；
3. 如果请求已成功，直接返回已有资源；
4. 如果规范化后的请求摘要与同一个幂等键不一致，返回 `409 Conflict`；
5. Redis 锁失效时，由数据库唯一索引阻止重复数据写入。

动作摘要包含动作类型、操作人、任务 ID、`expectedVersion`、转审/加签目标和审批意见。
历史记录缺少摘要时不能证明请求完全相同，按请求键复用冲突处理。

这种方案字段少、链路直观，适合当前面试项目；代价是不同类型请求的幂等处理逻辑分布在各自表和服务中。未来如果需要统一保存任意接口的原始响应，再考虑独立幂等表。

## 9. 通知记录表

### `notification_record`：通知投递记录表

由 `notification-service` 维护，用于记录 RabbitMQ 消费和通知重试。

| 字段 | 类型 | 约束 | 注释 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK | 通知记录主键 |
| `event_id` | VARCHAR(64) | UNIQUE, NOT NULL | 对应 Outbox 事件 ID |
| `receiver_id` | VARCHAR(64) | NOT NULL | 接收人 |
| `channel` | VARCHAR(32) | NOT NULL | `INBOX`、`EMAIL` |
| `status` | VARCHAR(32) | NOT NULL | `PENDING`、`SENT`、`FAILED` |
| `retry_count` | INT UNSIGNED | NOT NULL | 重试次数 |
| `error_message` | VARCHAR(1000) | NULL | 失败原因 |
| `sent_at` | DATETIME(3) | NULL | 发送成功时间 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |

## 10. 典型配置数据示例

### 10.1 采购流程

```text
approval_process
id | process_code       | business_type | version_no | status
1  | PURCHASE_STANDARD   | PURCHASE      | 1          | PUBLISHED
```

```text
approval_node
id  | process_id | node_code          | node_name       | node_type | approval_mode       | approver_type
101 | 1          | DEPT_MANAGER       | 部门负责人审批  | APPROVAL  | SINGLE              | DEPARTMENT_MANAGER
102 | 1          | FINANCE_MANAGER    | 财务负责人审批  | APPROVAL  | SINGLE              | ROLE
103 | 1          | GENERAL_MANAGER    | 总经理审批      | APPROVAL  | SINGLE              | ROLE
```

### 10.2 合同流程

```text
approval_process
id | process_code       | business_type    | version_no | status
2  | CONTRACT_STANDARD   | CONTRACT_CHANGE  | 1          | PUBLISHED
```

```text
approval_node
id  | process_id | node_code          | node_name       | node_type | approval_mode | approver_type
201 | 2          | DEPT_MANAGER       | 部门负责人审批  | APPROVAL  | SINGLE        | DEPARTMENT_MANAGER
202 | 2          | LEGAL_MANAGER      | 法务负责人审批  | APPROVAL  | SINGLE        | ROLE
```

采购和合同虽然匹配不同的 `process_id`，但使用同一个审批实例表、任务表、动作表和状态机代码。

## 11. 采购申请完整数据示例

### 11.1 申请和业务数据

```text
application
id     application_no  business_type  business_id  title          applicant_id  status
10001  APP-20260902-001 PURCHASE       PUR-001      研发服务器采购  U1001         SUBMITTED
```

```text
procurement_request
id    application_id  request_no  department_code  total_amount  currency  status
5001  10001           PUR-001     RD               15000.00       CNY       SUBMITTED
```

```text
procurement_item
id    procurement_id  item_name  quantity  unit_price  amount
5101  5001            服务器      2.00      7500.00     15000.00
```

### 11.2 审批实例和首个待办

```text
approval_instance
id     approval_no    application_id  business_type  business_id  process_id  status       current_node_id  lock_version
20001  APR-20260902-001 10001         PURCHASE       PUR-001      1           IN_PROGRESS  101               0
```

```text
approval_task
id     approval_instance_id  node_instance_id  assignee_id  status   created_at
30001  20001                 40001             U2001        PENDING  2026-09-02 10:00:00.000
```

### 11.3 提交快照

```json
{
  "snapshotNo": 1,
  "snapshotType": "SUBMIT",
  "businessType": "PURCHASE",
  "businessId": "PUR-001",
  "data": {
    "title": "研发服务器采购",
    "departmentCode": "RD",
    "totalAmount": 15000.00,
    "currency": "CNY",
    "items": [
      {"itemName": "服务器", "quantity": 2, "unitPrice": 7500.00, "amount": 15000.00}
    ]
  }
}
```

### 11.4 审批过程中业务数据发生变化

假设申请补充材料后，采购金额从 15,000 修改为 18,000：

```text
approval_snapshot
id    approval_instance_id  snapshot_no  snapshot_type  data_json.amount  created_at
60001 20001                 1             SUBMIT         15000.00           10:00
60002 20001                 2             APPROVE        18000.00           11:30
```

历史页面查看第一个节点时读取 `60001`，显示 15,000；查看第二个节点时读取 `60002`，显示 18,000，不会被当前业务表覆盖。

## 12. 合同申请数据示例

```text
application
id     application_no  business_type    business_id  title            applicant_id  status
10002  APP-20260902-002 CONTRACT_CHANGE  CCHG-001     供应商合同变更    U1001         SUBMITTED
```

```text
contract_change_request
id    application_id  change_no  contract_no  change_reason  change_amount  currency  status
7001  10002           CCHG-001   HT-2026-001  增加服务范围     30000.00       CNY       SUBMITTED
```

```text
approval_instance
id     approval_no      application_id  business_type    business_id  process_id  status       current_node_id
20002  APR-20260902-002 10002           CONTRACT_CHANGE  CCHG-001     2           IN_PROGRESS  201
```

注意：`20001` 和 `20002` 进入的是不同配置流程，但审批代码没有复制。

## 13. 提交事务设计

申请提交时，`approval-service` 在一个 MySQL 事务中完成：

```text
1. Redis SET NX 获取 approval:submit:{businessType}:{businessId}
2. 查询 `approval_instance` 的 `submit_request_id`，并校验申请状态
3. 查询已发布 approval_process
4. 创建 approval_instance
5. 创建 approval_node_instance
6. 创建 approval_task
7. 创建 SUBMIT approval_action
8. 创建 approval_snapshot
9. 创建 approval_outbox_event
10. 提交事务
11. 释放 Redis 锁
```

重复请求的保护层：

```text
Redis 分布式锁       防止短时间并发执行
业务表幂等字段唯一约束 防止重复请求创建第二个资源
审批实例唯一约束     防止一个 application 创建多个审批实例
状态和乐观锁         防止已提交申请被重复处理
```

## 14. 建表和迁移建议

第一版建议使用 Flyway 或版本化 SQL：

```text
V1__create_application_tables.sql
V2__create_approval_process_tables.sql
V3__create_approval_runtime_tables.sql
V4__create_idempotency_and_outbox_tables.sql
V5__insert_demo_processes.sql
```

禁止使用 `ddl-auto: create` 覆盖已有数据。开发阶段可以使用 `update`，交付和测试环境应使用版本化迁移。

## 15. 设计取舍

### 选择一库多表

便于本地开发、调试和面试演示；通过服务代码边界和表职责保持解耦。生产环境可以将 `approval_*`、`procurement_*` 等迁移到独立数据库，而不改变领域模型。

### 申请主表和业务表分离

`application` 负责统一申请生命周期，采购和合同表负责业务字段，避免审批主表出现大量无意义的空字段。

### 业务类型不创建新审批服务

`business_type` 只负责选择流程配置和业务数据适配器。新增费用审批时增加业务模块和流程配置，不复制 `approval-service`。

### Redis 不作为唯一可靠性组件

Redis 锁可能过期、重启或网络异常，所以最终结果必须由 MySQL 的唯一约束、条件更新、幂等记录和乐观锁保证。
