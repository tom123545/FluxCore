# FluxCore 审批平台数据库设计技术方案

## 1. 文档说明

本文档描述 FluxCore 通用审批服务平台当前阶段的数据库设计。设计重点是先建立容易理解、可以实现和演示的审批模型，同时为串行审批、或签、会签以及并行节点保留基础能力。

当前方案遵循以下已确认结论：

- 不在审批服务中复制采购、合同等业务表；
- 审批流程节点和节点之间的流转关系配置在数据库中；
- 审批引擎代码负责通用的流程执行，不硬编码某个业务类型的具体审批步骤；
- 使用 `approval_node_instance` 表记录一次审批实际经过的节点；
- 使用 `approval_task` 表记录具体审批人的待办；
- 提交接口使用幂等键，配合 Redis 分布式锁和数据库唯一约束防止重复创建审批实例；
- 申请、审批实例和任务根据生命周期保留 `submitted_at`、`completed_at`、`acted_at` 等关键时间字段；
- Redis 锁用于减少并发重复执行，数据库状态条件和乐观锁用于最终并发控制。

## 2. 数据库和服务边界

项目采用“一库多表”结构，所有服务默认连接同一个 MySQL 数据库：

```text
MySQL: fluxcore
  ├── approval_*       审批服务负责维护
  ├── procurement_*    business-service 中的采购模块负责维护
  ├── contract_*       business-service 中的合同模块负责维护
  └── notification_*   通知服务负责维护
```

| 数据库 | 所属服务 | 保存内容 |
|---|---|---|
| `fluxcore` | `approval-service` | `approval_*` 流程配置、审批实例、任务、历史、快照和 Outbox 事件 |
| `fluxcore` | `business-service` | `procurement_*` 和 `contract_*` 采购、合同等业务数据表；这些表不是审批流程表 |
| `fluxcore` | `notification-service` | `notification_*` 通知消费和发送记录 |

“一库多表”只表示物理数据库统一，不表示服务可以随意访问其他服务的表。每个服务仍然拥有自己的表边界：审批服务通过业务服务接口获取业务数据，禁止直接修改采购表或合同表；服务之间也不在数据库层建立跨服务的物理外键。审批服务中的 `business_id`、`applicant_id`、`assignee_id` 是外部系统 ID，只保存引用，不负责维护用户或业务主数据。

## 3. 总体数据关系

流程配置和审批运行数据分开：

```text
approval_process
    ├── approval_node
    └── approval_transition

approval_instance
    ├── approval_node_instance
    │       └── approval_task
    ├── approval_action
    ├── approval_snapshot
    └── approval_outbox_event
```

含义如下：

| 对象 | 作用 |
|---|---|
| `approval_process` | 定义某种业务使用哪条流程 |
| `approval_node` | 定义流程中有哪些节点以及节点的审批模式 |
| `approval_transition` | 定义节点之间如何流转，可表示分支和并行拆分 |
| `approval_instance` | 一次真实的审批申请 |
| `approval_node_instance` | 该审批实例实际执行到的节点 |
| `approval_task` | 某个具体审批人的待办或已办 |
| `approval_action` | 审批人执行过的操作记录 |
| `approval_snapshot` | 审批时保存的业务数据快照 |
| `approval_outbox_event` | 等待发布到 RabbitMQ 的领域事件 |

## 4. 审批流程配置表

### 4.1 `approval_process`：审批流程表

一条记录代表一条业务审批流程。第一版假设同一种业务类型只有一条启用流程。

| 字段 | 类型 | 约束 | 备注 |
|---|---|---|---|
| `id` | `BIGINT` | PK | 流程主键 |
| `business_type` | `VARCHAR(64)` | NOT NULL | 业务类型，如 `PROCUREMENT`、`CONTRACT_CHANGE` |
| `process_code` | `VARCHAR(64)` | NOT NULL | 流程编码 |
| `process_name` | `VARCHAR(128)` | NOT NULL | 流程名称 |
| `status` | `VARCHAR(32)` | NOT NULL | 流程状态，如 `ACTIVE`、`DISABLED` |
| `created_at` | `DATETIME(3)` | NOT NULL | 创建时间 |
| `updated_at` | `DATETIME(3)` | NOT NULL | 最后更新时间 |

建议建立唯一索引：

```sql
UNIQUE KEY uk_process_business_code (business_type, process_code)
```

示例数据：

| `id` | `business_type` | `process_code` | `process_name` | `status` |
|---:|---|---|---|---|
| 1 | `PROCUREMENT` | `PROCUREMENT_DEFAULT` | 采购申请审批 | `ACTIVE` |
| 2 | `CONTRACT_CHANGE` | `CONTRACT_CHANGE_DEFAULT` | 合同变更审批 | `ACTIVE` |

### 4.2 `approval_node`：审批节点表

一条记录代表流程中的一个节点。节点本身只定义“这个节点是什么、由谁审批、采用什么审批模式”，不直接保存下一个节点。

| 字段 | 类型 | 约束 | 备注 |
|---|---|---|---|
| `id` | `BIGINT` | PK | 节点主键 |
| `process_id` | `BIGINT` | NOT NULL | 所属流程 ID |
| `node_code` | `VARCHAR(64)` | NOT NULL | 节点编码，在同一流程内唯一 |
| `node_name` | `VARCHAR(128)` | NOT NULL | 节点名称 |
| `node_type` | `VARCHAR(32)` | NOT NULL | 节点类型：`START`、`APPROVAL`、`END` |
| `approval_mode` | `VARCHAR(32)` | NULL | `SINGLE` 单签、`ANY` 或签、`ALL` 会签；开始/结束节点为空 |
| `approver_rule` | `JSON` | NULL | 审批人规则，如用户、角色、部门负责人 |
| `sort_no` | `INT` | NOT NULL | 展示顺序，仅用于排序，不作为实际流转依据 |
| `created_at` | `DATETIME(3)` | NOT NULL | 创建时间 |
| `updated_at` | `DATETIME(3)` | NOT NULL | 最后更新时间 |

建议建立唯一索引：

```sql
UNIQUE KEY uk_node_process_code (process_id, node_code)
```

`approver_rule` 示例：

```json
{"type":"ROLE","value":"FINANCE"}
```

```json
{"type":"DEPARTMENT_MANAGER"}
```

数据库中保存审批人规则，系统创建节点实例时解析规则并生成具体任务。已经生成的任务保存具体的 `assignee_id`，之后组织关系变化不会改变历史待办的归属。

### 4.3 `approval_transition`：节点流转表

一条记录代表一条“从当前节点到下一个节点”的连线。它解决一个节点可能有多个后继节点的问题。

| 字段 | 类型 | 约束 | 备注 |
|---|---|---|---|
| `id` | `BIGINT` | PK | 流转关系主键 |
| `process_id` | `BIGINT` | NOT NULL | 所属流程 ID |
| `from_node_id` | `BIGINT` | NOT NULL | 当前节点 ID |
| `to_node_id` | `BIGINT` | NOT NULL | 目标节点 ID |
| `condition_expr` | `JSON` | NULL | 流转条件，无条件流转时为空 |
| `priority` | `INT` | NOT NULL | 多条条件满足时的匹配优先级，数值越小优先级越高 |
| `created_at` | `DATETIME(3)` | NOT NULL | 创建时间 |
| `updated_at` | `DATETIME(3)` | NOT NULL | 最后更新时间 |

无条件串行流转示例：

| 来源节点 | 目标节点 | 条件 |
|---|---|---|
| 开始 | 部门负责人 | 无 |
| 部门负责人 | 财务 | 无 |
| 财务 | 总经理 | 无 |
| 总经理 | 结束 | 无 |

条件流转示例：

```json
{"field":"totalAmount","operator":"GT","value":100000}
```

```json
{"field":"totalAmount","operator":"LTE","value":100000}
```

并行拆分示例：一个节点流向两个节点。

| 来源节点 | 目标节点 |
|---|---|
| 提交 | 财务审批 |
| 提交 | 法务审批 |
| 财务审批 | 总经理审批 |
| 法务审批 | 总经理审批 |

实际是否可以进入总经理节点，需要结合前置节点是否全部完成。第一版可以先实现串行流程，表结构保留这种表达能力。

## 5. 审批运行时表

### 5.1 `approval_instance`：审批实例表

一条记录代表一次真实的审批申请。

| 字段 | 类型 | 约束 | 备注 |
|---|---|---|---|
| `id` | `BIGINT` | PK | 审批实例主键 |
| `approval_no` | `VARCHAR(64)` | UNIQUE | 对外展示的审批单号 |
| `business_type` | `VARCHAR(64)` | NOT NULL | 业务类型 |
| `business_id` | `VARCHAR(128)` | NOT NULL | 业务系统中的单据 ID |
| `process_id` | `BIGINT` | NOT NULL | 本次审批使用的流程 ID |
| `applicant_id` | `VARCHAR(64)` | NOT NULL | 申请人 ID |
| `submit_request_id` | `VARCHAR(128)` | NOT NULL | 提交审批请求的幂等键 |
| `status` | `VARCHAR(32)` | NOT NULL | `PENDING`、`IN_PROGRESS`、`APPROVED`、`REJECTED`、`WITHDRAWN` |
| `lock_version` | `INT` | NOT NULL | 乐观锁版本号，每次状态修改递增 |
| `created_at` | `DATETIME(3)` | NOT NULL | 创建时间 |
| `updated_at` | `DATETIME(3)` | NOT NULL | 最后更新时间 |

提交接口使用 `submit_request_id`。Redis 锁用于减少短时间内的重复提交，数据库唯一约束用于应对锁失效、请求重试和服务重启；重复提交应返回已有申请/审批实例，而不是创建新实例。

建议索引：

```sql
INDEX idx_instance_business (business_type, business_id)
INDEX idx_instance_applicant_status (applicant_id, status)
INDEX idx_instance_process (process_id)
UNIQUE INDEX uk_instance_submit_request (submit_request_id)
```

### 5.2 `approval_node_instance`：节点执行实例表

一条记录代表某次审批实际执行的一个节点。它不能与 `approval_node` 混为一张表：前者是运行数据，后者是流程模板。

| 字段 | 类型 | 约束 | 备注 |
|---|---|---|---|
| `id` | `BIGINT` | PK | 节点执行实例主键 |
| `approval_instance_id` | `BIGINT` | NOT NULL | 所属审批实例 ID |
| `node_id` | `BIGINT` | NOT NULL | 对应的流程节点 ID |
| `status` | `VARCHAR(32)` | NOT NULL | `PENDING`、`ACTIVE`、`COMPLETED`、`REJECTED`、`CANCELLED` |
| `approval_mode` | `VARCHAR(32)` | NOT NULL | 本次执行实际采用的审批模式，避免模板后续修改影响运行数据 |
| `created_at` | `DATETIME(3)` | NOT NULL | 创建时间 |
| `updated_at` | `DATETIME(3)` | NOT NULL | 最后更新时间 |

并行时，同一个审批实例可以同时拥有多条 `ACTIVE` 节点实例，因此不在 `approval_instance` 中保存单一的 `current_node_id`。

### 5.3 `approval_task`：审批任务表

一条记录代表分配给一个具体审批人的任务。待办和已办共用这张表，任务不物理删除。

| 字段 | 类型 | 约束 | 备注 |
|---|---|---|---|
| `id` | `BIGINT` | PK | 任务主键 |
| `approval_instance_id` | `BIGINT` | NOT NULL | 所属审批实例 ID |
| `node_instance_id` | `BIGINT` | NOT NULL | 所属节点执行实例 ID |
| `assignee_type` | `VARCHAR(32)` | NOT NULL | `USER`、`ROLE`、`DEPARTMENT` 等审批人类型 |
| `assignee_id` | `VARCHAR(64)` | NOT NULL | 实际审批人、角色或部门 ID |
| `task_type` | `VARCHAR(32)` | NOT NULL | `NORMAL`、`TRANSFER`、`ADD_SIGN` 等任务类型 |
| `status` | `VARCHAR(32)` | NOT NULL | `PENDING`、`APPROVED`、`REJECTED`、`CANCELLED`、`TRANSFERRED` |
| `created_at` | `DATETIME(3)` | NOT NULL | 创建时间 |
| `updated_at` | `DATETIME(3)` | NOT NULL | 最后更新时间 |

或签和会签的区别不在下一个节点，而在同一个 `node_instance` 下有多个任务时的完成规则：

- `ANY`：任意一个任务审批通过，节点完成，其余待办取消；
- `ALL`：所有任务审批通过，节点完成；
- `SINGLE`：通常只有一个有效任务通过即可完成。

### 5.4 `approval_action`：审批动作表

保存提交、通过、驳回、撤回等历史操作。该表只追加记录，不修改历史动作。

| 字段 | 类型 | 约束 | 备注 |
|---|---|---|---|
| `id` | `BIGINT` | PK | 动作记录主键 |
| `approval_instance_id` | `BIGINT` | NOT NULL | 所属审批实例 ID |
| `node_instance_id` | `BIGINT` | NULL | 所属节点实例 ID，提交/撤回动作可能为空 |
| `task_id` | `BIGINT` | NULL | 所属任务 ID，提交/撤回动作可能为空 |
| `operator_id` | `VARCHAR(64)` | NOT NULL | 实际操作人 ID |
| `action_type` | `VARCHAR(32)` | NOT NULL | `SUBMIT`、`APPROVE`、`REJECT`、`WITHDRAW`、`TRANSFER`、`ADD_SIGN` |
| `action_request_id` | `VARCHAR(128)` | NOT NULL | 本次审批动作请求的幂等键 |
| `request_hash` | `CHAR(64)` | NULL | 完整动作请求规范化后的 SHA-256 摘要 |
| `comment` | `VARCHAR(2000)` | NULL | 审批意见或操作说明 |
| `created_at` | `DATETIME(3)` | NOT NULL | 动作发生时间 |
| `updated_at` | `DATETIME(3)` | NOT NULL | 最后更新时间；历史动作通常不再更新 |

约束：`UNIQUE(approval_instance_id, action_request_id)`。同一个审批实例中，只有完整请求摘要一致时才返回第一次处理结果；摘要不一致返回 `409 ACTION_REQUEST_ID_REUSED`。

### 5.5 `approval_snapshot`：审批数据快照表

保存提交或审批时从业务服务读取的业务数据，保证历史页面展示当时的数据，而不是业务表当前数据。

| 字段 | 类型 | 约束 | 备注 |
|---|---|---|---|
| `id` | `BIGINT` | PK | 快照主键 |
| `approval_instance_id` | `BIGINT` | NOT NULL | 所属审批实例 ID |
| `node_instance_id` | `BIGINT` | NULL | 对应节点实例；提交快照可以为空 |
| `snapshot_type` | `VARCHAR(32)` | NOT NULL | `SUBMIT`、`APPROVE`、`REJECT` 等快照类型 |
| `business_type` | `VARCHAR(64)` | NOT NULL | 业务类型 |
| `business_id` | `VARCHAR(128)` | NOT NULL | 业务单据 ID |
| `data_json` | `JSON` | NOT NULL | 当时的业务数据 |
| `source_service` | `VARCHAR(64)` | NOT NULL | 数据来源模块，如 `business-service/procurement` |
| `created_at` | `DATETIME(3)` | NOT NULL | 快照创建时间 |
| `updated_at` | `DATETIME(3)` | NOT NULL | 最后更新时间；快照通常不再更新 |

### 5.6 `approval_outbox_event`：Outbox 事件表

审批状态和事件在同一个数据库事务中写入。后台发布器读取待发布事件并投递 RabbitMQ。

| 字段 | 类型 | 约束 | 备注 |
|---|---|---|---|
| `id` | `BIGINT` | PK | Outbox 记录主键 |
| `event_id` | `VARCHAR(64)` | UNIQUE | 事件唯一 ID，供消息消费端去重 |
| `aggregate_type` | `VARCHAR(64)` | NOT NULL | 聚合类型，如 `APPROVAL_INSTANCE` |
| `aggregate_id` | `VARCHAR(64)` | NOT NULL | 聚合对象 ID |
| `event_type` | `VARCHAR(64)` | NOT NULL | `APPROVAL_SUBMITTED`、`APPROVAL_APPROVED` 等 |
| `payload_json` | `JSON` | NOT NULL | 事件内容 |
| `status` | `VARCHAR(32)` | NOT NULL | `PENDING`、`PUBLISHED`、`FAILED` |
| `retry_count` | `INT` | NOT NULL | 已重试次数 |
| `created_at` | `DATETIME(3)` | NOT NULL | 事件创建时间 |
| `updated_at` | `DATETIME(3)` | NOT NULL | 最后更新时间 |

## 6. 业务类型统一接入

采购、合同以及未来的费用、用印、付款等，都属于同一个审批平台，只是 `business_type` 不同。审批核心表不按业务类型复制：

| 业务类型 | `business_type` 示例 | `business_id` 示例 | 业务数据来源 |
|---|---|---|---|
| 采购申请 | `PROCUREMENT` | `P202609020001` | 采购业务模块 |
| 合同变更 | `CONTRACT_CHANGE` | `C202609020001` | 合同业务模块 |
| 费用报销 | `EXPENSE` | `E202609020001` | 费用业务模块 |

审批实例统一使用以下两个字段关联任意业务：

| 字段 | 备注 |
|---|---|
| `business_type` | 标识业务类别，并用于匹配对应流程 |
| `business_id` | 标识该类别下的具体业务单据 |

例如，采购和合同都写入同一张 `approval_instance`：

| id | business_type | business_id | process_id | status |
|---:|---|---|---:|---|
| 10001 | `PROCUREMENT` | `P202609020001` | 1 | `IN_PROGRESS` |
| 10002 | `CONTRACT_CHANGE` | `C202609020001` | 2 | `IN_PROGRESS` |

两条记录使用同一套节点、任务、动作、快照和事件表。它们的差异只体现在：

```text
business_type → 匹配不同的 approval_process
business_id   → 调用对应业务模块获取业务数据
```

业务模块可以继续保留自己的业务数据表，例如采购明细和合同变更字段，但这些表属于业务数据，不属于审批流程模型。审批服务不复制这些字段，也不为采购和合同分别设计审批表；它只通过统一的业务适配器读取业务数据并保存到 `approval_snapshot.data_json`。

业务适配器接口可以统一为：

```text
BusinessDataProvider.getSnapshot(businessType, businessId)
```

内部根据 `businessType` 选择数据来源，但审批状态机、节点流转和历史查询代码保持完全一致。

## 7. 示例一：采购串行审批

### 7.1 配置数据

采购流程 `process_id = 1` 的节点：

| 节点 ID | 节点编码 | 节点名称 | 类型 | 模式 |
|---:|---|---|---|---|
| 101 | `START` | 开始 | `START` | NULL |
| 102 | `DEPT_MANAGER` | 部门负责人审批 | `APPROVAL` | `SINGLE` |
| 103 | `FINANCE` | 财务审批 | `APPROVAL` | `SINGLE` |
| 104 | `GENERAL_MANAGER` | 总经理审批 | `APPROVAL` | `SINGLE` |
| 105 | `END` | 结束 | `END` | NULL |

节点流转数据：

| 来源 | 目标 |
|---|---|
| 101 开始 | 102 部门负责人 |
| 102 部门负责人 | 103 财务 |
| 103 财务 | 104 总经理 |
| 104 总经理 | 105 结束 |

### 7.2 提交后的数据

用户提交采购单 `P202609020001`，业务服务返回采购数据后，审批服务写入：

`approval_instance`：

| id | approval_no | business_type | business_id | process_id | status | lock_version |
|---:|---|---|---|---:|---|---:|
| 10001 | `A202609020001` | `PROCUREMENT` | `P202609020001` | 1 | `IN_PROGRESS` | 0 |

`approval_node_instance`：

| id | approval_instance_id | node_id | status | approval_mode |
|---:|---:|---:|---|---|
| 11001 | 10001 | 102 | `ACTIVE` | `SINGLE` |

`approval_task`：

| id | node_instance_id | assignee_type | assignee_id | status |
|---:|---:|---|---|---|
| 12001 | 11001 | `USER` | `manager-001` | `PENDING` |

同时新增一条 `SUBMIT` 动作、一条 `SUBMIT` 快照和一条 `APPROVAL_SUBMITTED` Outbox 事件。

### 7.3 部门负责人通过后的变化

更新原任务：

```text
approval_task(12001).status: PENDING → APPROVED
```

更新节点实例：

```text
approval_node_instance(11001).status: ACTIVE → COMPLETED
```

更新审批实例：

```text
approval_instance(10001).lock_version: 0 → 1
```

然后根据 `approval_transition` 创建财务节点：

| id | approval_instance_id | node_id | status |
|---:|---:|---:|---|
| 11002 | 10001 | 103 | `ACTIVE` |

并创建财务待办：

| id | node_instance_id | assignee_id | status |
|---:|---:|---|---|
| 12002 | 11002 | `finance-001` | `PENDING` |

同时新增 `APPROVE` 动作、审批快照和节点流转事件。

### 7.4 总经理通过后的变化

总经理任务变为 `APPROVED`，总经理节点变为 `COMPLETED`，然后命中 `END` 节点。

审批实例最终变为：

| id | status | lock_version |
|---:|---|---:|
| 10001 | `APPROVED` | 3 |

三个审批节点实例均为 `COMPLETED`，三个审批任务均为 `APPROVED`。历史页面通过 `approval_action` 和 `approval_snapshot` 展示完整过程。

## 8. 示例二：会签

假设采购流程的财务节点配置为：

```text
approval_mode = ALL
approver_rule = ROLE:FINANCE
```

系统解析财务角色得到三名审批人，于同一个节点实例下创建三条任务：

| task_id | node_instance_id | assignee_id | status |
|---:|---:|---|---|
| 22001 | 21001 | `finance-001` | `PENDING` |
| 22002 | 21001 | `finance-002` | `PENDING` |
| 22003 | 21001 | `finance-003` | `PENDING` |

流转过程：

```text
finance-001 通过：任务 22001 → APPROVED，节点仍 ACTIVE
finance-002 通过：任务 22002 → APPROVED，节点仍 ACTIVE
finance-003 通过：任务 22003 → APPROVED，节点 ACTIVE → COMPLETED
```

节点完成后，才根据 `approval_transition` 创建下一个节点。会签不是通过增加多个下一个节点实现，而是同一节点实例下拥有多个任务，并由 `approval_mode = ALL` 决定完成条件。

如果会签中的任意一人驳回，第一版建议：

```text
当前节点 → REJECTED
其他 PENDING 任务 → CANCELLED
审批实例 → REJECTED
```

## 9. 示例三：或签

假设总经理节点配置为：

```text
approval_mode = ANY
approver_rule = ROLE:GENERAL_MANAGER
```

系统创建两条任务：

| task_id | node_instance_id | assignee_id | status |
|---:|---:|---|---|
| 32001 | 31001 | `gm-001` | `PENDING` |
| 32002 | 31001 | `deputy-gm-001` | `PENDING` |

`gm-001` 通过后：

```text
任务 32001：PENDING → APPROVED
任务 32002：PENDING → CANCELLED
节点 31001：ACTIVE → COMPLETED
```

然后流程继续进入下一个节点。或签也不需要配置多个下一个节点。

## 10. 示例四：并行节点

流程配置如下：

```text
提交
 ├── 财务审批
 └── 法务审批
       ↓
     总经理审批
```

提交后创建两个节点实例：

| id | approval_instance_id | node_id | status |
|---:|---:|---:|---|
| 41001 | 10002 | 财务审批 | `ACTIVE` |
| 41002 | 10002 | 法务审批 | `ACTIVE` |

它们分别生成财务和法务任务。此时 `approval_instance` 不保存单一当前节点，而是通过查询所有 `ACTIVE` 的节点实例判断当前执行位置。

当财务完成而法务未完成：

```text
财务节点：ACTIVE → COMPLETED
法务节点：仍为 ACTIVE
总经理节点：不创建
```

当法务也完成后：

```text
法务节点：ACTIVE → COMPLETED
系统确认总经理节点的所有前置节点均已完成
创建总经理节点实例和总经理任务
```

因此，`approval_transition` 表保存“可能的连线”，`approval_node_instance` 表保存“这一次实际激活和完成了哪些节点”。

## 11. 驳回和撤回时的数据变化

### 11.1 驳回

审批人驳回时：

```text
当前 approval_task：PENDING → REJECTED
当前 approval_node_instance：ACTIVE → REJECTED
其他当前 PENDING 任务：→ CANCELLED
approval_instance：IN_PROGRESS → REJECTED
新增 approval_action：REJECT
新增 approval_snapshot：REJECT
新增 Outbox：APPROVAL_REJECTED
```

### 11.2 撤回

申请人撤回时：

```text
当前 PENDING 任务：→ CANCELLED
当前 ACTIVE 节点：→ CANCELLED
approval_instance：IN_PROGRESS → WITHDRAWN
新增 approval_action：WITHDRAW
新增 Outbox：APPROVAL_WITHDRAWN
```

所有历史记录保留，不删除审批实例、节点实例、任务或动作。

## 12. 提交和审批动作的事务及并发控制

### 12.1 提交

提交事务中完成：

```text
创建 approval_instance
创建首个 approval_node_instance
创建 approval_task
创建 SUBMIT approval_action
创建 SUBMIT approval_snapshot
创建 approval_outbox_event
```

Redis 锁示例：

```text
approval:submit:{businessType}:{businessId}
```

提交接口把幂等键直接保存到审批实例表。Redis 锁释放后再次提交同一业务单据时，通过审批实例上的幂等字段和唯一约束返回已有审批实例，不创建第二个实例：

```sql
UNIQUE KEY uk_instance_submit_request (submit_request_id)
```

### 12.2 审批动作

审批动作使用 `action_request_id`，并在 `approval_action` 表上建立实例范围内的唯一约束：

```text
approval:action:{approvalInstanceId}
```

```sql
UNIQUE KEY uk_action_request (approval_instance_id, action_request_id)
```

数据库更新必须同时校验任务仍为待处理状态，并使用 `lock_version`：

```sql
UPDATE approval_instance
SET lock_version = lock_version + 1,
    status = 'IN_PROGRESS'
WHERE id = ?
  AND lock_version = ?;
```

更新行数为 0 时，说明版本已经变化，应返回并发冲突。Redis 锁用于减少冲突，不能替代数据库校验。

## 13. 第一版实现建议

第一版可以按以下顺序实现：

1. 先实现 `approval_process`、`approval_node`、`approval_transition` 三张配置表；
2. 再实现 `approval_instance`、`approval_node_instance`、`approval_task`；
3. 增加 `approval_action` 和 `approval_snapshot`，完成历史查询；
4. 最后增加 `approval_outbox_event` 和 RabbitMQ 发布；
5. 先实现 `SINGLE` 串行审批；
6. 在同一模型上增加 `ANY` 和 `ALL`；
7. 最后实现并行节点的前置节点汇聚判断。

流程节点、审批模式和流转关系属于数据库配置；审批引擎只负责解析配置并执行通用状态流转。这样新增采购、合同或其他业务类型时，主要工作是在统一的 `business-service` 中增加业务模块/数据和流程配置，不需要复制一套审批代码或新建审批服务。详细字段设计和数据示例以 `docs/DATABASE_DESIGN.md` 为准。
