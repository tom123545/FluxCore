# 通用审批服务平台技术方案与开发现状

## 1. 文档目的

本文档把需求说明书中的业务要求与当前仓库代码对齐，回答两个问题：

1. 当前项目已经做到什么程度，哪些能力只是数据库或设计预留；
2. 现有接口为什么这样划分，以及后续审批接口应如何沿用同一套设计。

本文档中的“已实现”只表示在当前代码中能够找到对应实现；需求图片是产品要求，不是需要执行的代码指令。

## 2. 需求范围解读

需求说明书的核心交付不是一个采购系统，而是一个可以被多种业务复用的审批中台。验收重点可以归纳为：

| 需求方向 | 目标 |
|---|---|
| 审批引擎 | 一个统一入口，根据 `businessType` 启动对应流程 |
| 标准动作 | 通过、驳回、转审、加签、撤回 |
| 流程能力 | 串行、并行、条件分支 |
| 数据一致性 | 快照、幂等、分布式锁、乐观锁、并发冲突 |
| 待办中心 | 生成、查询、处理并推进下一节点 |
| 多业务接入 | 至少采购和合同两种业务，新增类型不复制审批服务 |
| 异步通知 | 通知失败不回滚审批主流程，并支持重试 |
| 工程形态 | 可运行的多服务/API 优先项目，保留架构和设计取舍说明 |

其中，采购三级串行和合同两级串行已经完成本机 E2E 验证；并行、条件分支和更复杂的会签仍属于后续扩展。

## 3. 当前总体结论

当前项目已经完成采购/合同的本地闭环：创建、提交、通过、驳回、撤回、转审、加签、查询、Outbox 和通知消费均已落地，并通过本机 MySQL/Redis/RabbitMQ 完成真实 E2E。并行、条件分支和完整会签仍是后续扩展。

按需求完成度估算：

| 能力 | 当前状态 | 说明 |
|---|---|---|
| 多模块工程和基础设施 | 已完成 | 四个 Spring Boot 服务、本机 MySQL、Redis、RabbitMQ 连接配置已建立 |
| 采购申请创建 | 已完成 | 主申请、采购主表、采购明细、扩展表事务写入 |
| 合同变更创建 | 已完成 | 主申请、合同变更主表、明细、扩展表事务写入 |
| 统一业务数据读取 | 已完成首版 | 通过内部接口按业务类型拼装快照数据 |
| 提交审批 | 已完成首版 | 校验业务、匹配已发布流程、创建审批实例和首个待办 |
| 提交幂等 | 已实现 | Redis 提交锁 + 数据库唯一约束 + 重复结果返回 |
| 提交快照和提交历史 | 已完成 | 提交时写入不可变快照和动作记录 |
| 审批通过/驳回/撤回 | 已完成 | 对应 Controller、DTO 和 Service 已落地 |
| 节点完整流转 | 已完成首版 | 串行流程会读取 `approval_transition` 并推进下一节点 |
| 并行、条件、会签、或签 | 部分完成 | 当前支持 `SINGLE` 和 `OR`，`AND`、并行和条件分支仍待扩展 |
| 待办/已办查询 | 已完成 | 已按审批人查询 `todo` 和 `done` |
| 转审、加签、委托 | 转审/加签已完成 | 委托明确不在本期范围 |
| 审批动作并发控制 | 已完成 | 已接入动作锁、条件更新和 `lock_version` |
| Outbox 发布和通知消费 | 已完成 | 已有发布器、RabbitMQ Listener 和通知去重 |
| 网关路由、鉴权 | 部分完成 | 已可转发主要业务和审批路径，认证仍是基础版 |
| 自动化验证 | 已完成 | 62 个测试通过，真实本地 E2E 已完成 |

因此，当前更准确的项目阶段是：**核心审批闭环已完成并可本地演示，后续主要是生产加固和扩展能力**。

## 4. 当前代码中的接口清单

### 4.1 对外业务接口

| 方法 | 路径 | 所属服务 | 用途 | 当前状态 |
|---|---|---|---|---|
| `POST` | `/api/business/applications/purchase` | business-service | 创建采购申请草稿 | 已实现 |
| `POST` | `/api/business/applications/contract` | business-service | 创建合同变更申请草稿 | 已实现 |
| `POST` | `/api/approvals` | approval-service | 提交任意业务类型的审批 | 已实现 |
| `GET` | `/api/approvals/{approvalInstanceId}` | approval-service | 查询审批实例详情 | 已实现 |
| `GET` | `/api/approvals/{approvalInstanceId}/history` | approval-service | 查询审批历史 | 已实现 |
| `GET` | `/api/approvals/{approvalInstanceId}/snapshots` | approval-service | 查询审批快照 | 已实现 |
| `POST` | `/api/approvals/{approvalInstanceId}/withdraw` | approval-service | 申请人撤回审批 | 已实现 |
| `POST` | `/api/approvals/{approvalInstanceId}/tasks/{taskId}/approve` | approval-service | 审批通过 | 已实现 |
| `POST` | `/api/approvals/{approvalInstanceId}/tasks/{taskId}/reject` | approval-service | 审批驳回 | 已实现 |
| `POST` | `/api/approvals/{approvalInstanceId}/tasks/{taskId}/transfer` | approval-service | 转审 | 已实现 |
| `POST` | `/api/approvals/{approvalInstanceId}/tasks/{taskId}/add-sign` | approval-service | 加签 | 已实现 |

创建接口使用不同路径，是因为采购和合同的业务字段、校验规则和明细模型不同；提交接口保持统一，是因为提交之后的流程执行、任务、动作、快照和事件都是审批域的通用能力。

### 4.2 服务内部接口

| 方法 | 路径 | 调用方 | 用途 | 当前状态 |
|---|---|---|---|---|
| `GET` | `/api/internal/business-data/{businessType}/{businessId}` | approval-service | 获取业务详情，生成审批快照 | 已实现 |
| `POST` | `/api/internal/applications/{applicationId}/submit` | approval-service | 将业务申请从 `DRAFT` 改为 `SUBMITTED` | 已实现 |
| `POST` | `/api/internal/applications/{applicationId}/withdraw` | approval-service | 将业务申请改为 `WITHDRAWN` | 已实现 |
| `POST` | `/api/internal/applications/{applicationId}/reject` | approval-service | 将业务申请改为 `REJECTED` | 已实现 |
| `POST` | `/api/internal/applications/{applicationId}/approve` | approval-service | 将业务申请改为 `APPROVED` | 已实现 |

内部接口与对外接口分开，避免审批服务直接访问采购表、合同表，也避免业务服务掌握审批状态机。当前部署是一库多表，但服务边界仍通过接口维护。

### 4.3 健康检查接口

| 服务 | 路径 |
|---|---|
| gateway-service | `/api/gateway/ping` |
| approval-service | `/api/approval/ping` |
| business-service | `/api/business/ping` |
| notification-service | `/api/notification/ping` |

这些接口只用于启动检查，不属于业务验收链路。

## 5. 已实现链路与接口设计原因

### 5.1 创建业务申请：为什么拆在 business-service

`business-service` 的 `POST /api/business/applications/purchase` 和 `/contract` 负责创建草稿。实现位于 `BusinessApplicationService`，在一个本地事务中写入统一申请主表和对应业务表。

采用该划分有三个原因：

1. 采购金额、明细和合同变更字段属于业务域，不应进入通用审批核心；
2. 业务服务可以在创建阶段执行业务校验和金额计算，审批服务只消费已形成的业务数据；
3. 新增费用、用印、付款等业务时，只需增加业务模型和适配逻辑，不复制审批引擎。

申请主表用 `business_type + business_id` 表示通用业务引用，用 `(business_type, idempotency_key)` 防止客户端重试产生重复草稿。采购明细金额由数量乘单价计算，避免完全信任调用方传入的明细金额。

当前限制：创建接口返回草稿，但没有通用的“修改草稿、查询申请、取消申请”对外接口；合同和采购的领域校验也仍是首版基础校验。

### 5.2 业务数据读取：为什么使用内部 HTTP API

审批服务通过 `BusinessDataClient` 调用，并由 `HttpClientConfig` 统一注入 `X-Internal-Token`：

```text
GET /api/internal/business-data/{businessType}/{businessId}
```

返回值包含统一元数据和业务数据 JSON：

```json
{
  "applicationId": 1,
  "applicationNo": "APP-...",
  "businessType": "PURCHASE",
  "businessId": "PUR-...",
  "title": "办公用品采购",
  "applicantId": "U1001",
  "status": "DRAFT",
  "data": {
    "totalAmount": 1280.00,
    "formData": {
      "costCenter": "CC-1001"
    },
    "remark": "采购审批备注",
    "items": []
  }
}
```

其中 `data.formData` 和 `data.remark` 来自 `application_ext`。审批服务会将整个响应序列化到 `approval_snapshot.data_json`，因此提交和后续关键审批动作的快照都包含申请扩展字段及备注。

这不是为了追求复杂的微服务通信，而是为了落实“审批服务不直接 Join 业务表”的边界。后续可以把 `BusinessDataClient` 抽象为 `BusinessDataProvider`，按 `businessType` 注册不同适配器；审批状态机不需要感知采购或合同字段。

### 5.3 提交审批：为什么使用一个统一接口

`POST /api/approvals` 接收：

```json
{
  "businessType": "PURCHASE",
  "businessId": "PUR-...",
  "applicantId": "U1001",
  "submitRequestId": "SUBMIT-20260902-001",
  "applicationId": 1
}
```

`businessType` 和 `businessId` 是通用审批聚合的业务定位；`applicationId` 是可选的交叉校验字段；`submitRequestId` 是一次提交意图的幂等键。

提交处理顺序如下：

```text
获取提交锁
  -> 根据 submitRequestId 检查重复
  -> 读取业务数据并校验业务标识、申请人、申请状态
  -> 按 businessType 找最新已发布流程
  -> 找到首个审批节点
  -> 创建审批实例、节点实例、首个任务
  -> 写入 SUBMIT 快照、动作历史、Outbox 事件
  -> 调用业务服务把申请标记为 SUBMITTED
  -> 释放提交锁
```

这个顺序的设计原因是：

- 先查幂等键，可以让网络重试直接返回原审批实例；
- 通过业务服务读取数据，可以在审批开始前确认申请确实存在且仍是草稿；
- 流程按 `businessType` 和已发布版本匹配，流程变化不会写死在 Controller；
- 审批实例绑定 `processId`，未来发布新版本不会改变已经启动的实例；
- 首节点、待办、快照、历史和事件放在审批本地事务内，保证审批库内部不会只落一半；
- 业务状态更新仍通过内部 API 完成，因为两个服务之间没有本地分布式事务。

当前实现已经会在通过时读取 `approval_transition` 推进到下一节点，末级通过则结束实例并同步业务状态。

### 5.4 提交幂等：为什么不是只依赖 Redis

提交使用 Redis key：

```text
approval:submit:{businessType}:{businessId}
```

Redis `SETNX + TTL` 用于拦截短时间内的重复执行，释放锁时通过 Lua 脚本校验 token，避免误删其他请求持有的锁。

但 Redis 锁会过期、服务可能重启或网络可能重试，所以最终约束仍在 MySQL：

- `application`：业务类型 + 幂等键唯一；
- `approval_instance`：申请唯一、审批提交键唯一；
- `approval_action`：审批实例 + 动作请求键唯一。

因此“锁负责减少重复执行，数据库负责最终正确性”。重复提交返回原实例，并在响应中将 `duplicate` 设为 `true`，便于调用方区分首次创建和幂等重放。

### 5.5 快照与历史：为什么不能直接查业务当前表

提交时，审批服务把内部业务响应序列化为 JSON，写入 `approval_snapshot`，同时计算 SHA-256 `dataHash`。`approval_action.snapshotId` 指向该快照。

这样设计是因为审批过程中业务数据可能继续变化。历史页面必须展示“当时审批人看到的版本”，而不是业务表当前值。后续每一级审批动作都应重新调用业务数据接口并新增快照，旧快照不更新。

提交、通过、驳回、撤回、转审和加签都会写入快照，历史与快照查询接口也已可直接使用。

### 5.6 Outbox：为什么先写表再异步发消息

提交事务会新增 `approval_outbox_event`，事件中包含 `eventId`、审批实例、业务标识、任务和审批人信息。审批主数据与事件在同一个审批数据库事务中写入。

采用 Outbox 的原因是审批状态不能依赖 RabbitMQ 可用性：

- RabbitMQ 暂时不可用时，审批仍可以提交成功；
- 后台发布器可以重试未发布事件；
- 消费端使用 `eventId` 去重，允许至少一次投递；
- 通知失败不会回滚已经完成的审批事务。

当前已经具备 `@Scheduled` 发布器、RabbitMQ Exchange/Queue 配置、通知消费者和重试机制；主流程不依赖 RabbitMQ 可用性。

## 6. 数据模型为什么这样拆分

| 表 | 设计职责 | 当前代码使用情况 |
|---|---|---|
| `approval_process` | 流程定义、业务类型、版本、发布状态 | 已用于匹配已发布流程 |
| `approval_node` | 流程模板节点和审批人规则 | 已用于选择首节点 |
| `approval_transition` | 节点之间的连线、条件、优先级 | 已被运行时读取并用于节点推进 |
| `approval_instance` | 一次真实审批及其状态 | 已创建 |
| `approval_node_instance` | 一次审批实际执行到的节点 | 首节点已创建 |
| `approval_task` | 具体审批人的待办/已办 | 首个待办已创建 |
| `approval_action` | 追加式动作历史 | 提交动作已创建 |
| `approval_snapshot` | 审批时的不可变业务数据 | 提交快照已创建 |
| `approval_outbox_event` | 待投递领域事件 | 提交事件已创建 |

流程模板与运行实例分开，是为了让流程版本可发布、可追溯；节点实例与节点模板分开，是为了记录本次审批实际走过的路径；任务单独建表，是为了支持一个节点对应多个审批人以及待办/已办查询。

需要特别注意：当前 Java 代码已经有 `ApprovalTransitionEntity` 和 `ApprovalTransitionMapper`，但只实现了串行和或签的运行时路径；条件和并行仍需继续扩展。

## 7. 后续接口方案

以下接口均已实现，路径以当前代码为准。

### 7.1 审批实例和历史查询

```text
GET /api/approvals/{approvalInstanceId}
GET /api/approvals/{approvalInstanceId}/history
GET /api/approvals/{approvalInstanceId}/snapshots
```

查询详情返回审批状态、当前节点和业务标识；历史按 `approval_action.created_at` 排序；历史展示的业务内容只能来自 `approval_snapshot.data_json`，不能回查业务当前表。

### 7.2 待办和已办查询

```text
GET /api/tasks/todo?assigneeId=U2001
GET /api/tasks/done?assigneeId=U2001
```

查询条件以 `assignee_id + status + created_at` 索引为基础。响应中应包含任务、节点、审批实例摘要和最新快照摘要，避免前端为一条待办发起大量串行请求。

### 7.3 标准审批动作

```text
POST /api/approvals/{approvalInstanceId}/withdraw
POST /api/approvals/{approvalInstanceId}/tasks/{taskId}/approve
POST /api/approvals/{approvalInstanceId}/tasks/{taskId}/reject
POST /api/approvals/{approvalInstanceId}/tasks/{taskId}/transfer
POST /api/approvals/{approvalInstanceId}/tasks/{taskId}/add-sign
```

统一动作请求至少包含：

```json
{
  "operatorId": "U2001",
  "actionRequestId": "ACTION-20260902-001",
  "comment": "审批意见",
  "expectedVersion": 0
}
```

所有动作应进入同一个动作服务和状态机，不应在每个 Controller 中分别实现状态修改。处理逻辑建议为：校验任务归属和权限、获取 `approval:action:{approvalInstanceId}` 锁、校验实例版本和任务状态、写动作/快照、按节点模式计算节点结果、推进 transition、更新待办、递增 `lock_version`、写 Outbox。

### 7.4 流程定义管理

```text
POST /api/processes
POST /api/processes/{processCode}/publish
GET /api/processes?businessType=PURCHASE&status=PUBLISHED
```

当前代码侧重运行时执行，流程发布接口仍可作为后续能力继续补充。流程配置已经能被运行时读取并驱动串行推进。

## 8. 审批状态机设计

### 8.1 实例状态

```text
IN_PROGRESS -> APPROVED
IN_PROGRESS -> REJECTED
IN_PROGRESS -> WITHDRAWN
```

只有 `IN_PROGRESS` 可以执行审批动作；`APPROVED`、`REJECTED`、`WITHDRAWN` 是终态，重复操作应返回明确的冲突错误。

### 8.2 串行节点

单签节点完成后读取当前节点的 transition，创建下一个节点实例和对应任务；到达结束节点后把审批实例改为 `APPROVED`。

### 8.3 会签和或签

同一个节点实例下创建多个任务：

- `ALL`：所有任务通过后节点完成；
- `OR`：任一任务通过后节点完成，其余待办取消；
- `SINGLE`：通常只解析出一个实际审批人。

节点完成规则应由状态机统一实现，而不是让调用方决定是否推进下一个节点。

### 8.4 并行和条件分支

一个节点可以有多个满足条件的后继节点。条件按 `priority` 排序求值；并行节点完成前，汇聚节点不能激活。第一版可以先落地串行、会签和或签，再实现并行汇聚和条件表达式，但表模型不应被重新设计成按业务类型复制流程。

## 9. 一致性和异常处理方案

### 9.1 提交阶段

提交锁解决短时重复提交；业务数据、审批实例、首节点、首任务、快照、提交动作和 Outbox 在审批本地事务内提交。业务服务的 `markSubmitted` 是跨服务调用，不能假设与审批库组成分布式事务，生产实现应补充以下之一：

- 业务服务提供带幂等键的状态变更接口，并由审批侧记录补偿状态；
- 使用可靠消息/事务消息异步完成业务状态变更；
- 在明确的状态机协议下支持失败重试和对账。

### 9.2 审批动作阶段

动作锁用于减少同一审批实例的并发操作；数据库使用 `lock_version` 条件更新作为最终控制。例如：

```sql
UPDATE approval_instance
SET lock_version = lock_version + 1, status = ?, updated_at = NOW(3)
WHERE id = ? AND status = 'IN_PROGRESS' AND lock_version = ?
```

更新行数为 0 时返回 `APPROVAL_CONFLICT`，不能继续写任务或事件。

### 9.3 幂等响应

首次动作写入 `approval_action`，同时保存完整请求规范化后的 `request_hash`；相同 `actionRequestId` 再次到达时，仅当摘要一致才返回首次动作结果，不重复推进节点、不重复生成快照、不重复发布业务效果。摘要不一致时返回 `409 ACTION_REQUEST_ID_REUSED`。

### 9.4 错误码建议

| 错误码 | HTTP | 场景 |
|---|---:|---|
| `SUBMIT_IN_PROGRESS` | 409 | 提交锁冲突 |
| `REQUEST_ID_REUSED` | 409 | 幂等键被其他业务使用 |
| `APPLICATION_NOT_DRAFT` | 422 | 非草稿申请重复提交 |
| `PROCESS_NOT_FOUND` | 422 | 没有已发布流程 |
| `TASK_NOT_ASSIGNED` | 403 | 操作人不是任务处理人 |
| `APPROVAL_CONFLICT` | 409 | 版本或任务状态冲突 |
| `INVALID_ACTION` | 422 | 当前状态不允许该动作 |
| `BUSINESS_DATA_NOT_FOUND` | 404 | 业务数据不存在 |

## 10. 下一阶段实施顺序

建议按以下顺序补齐需求，避免先做页面而缺少审批核心：

1. 增加 transition 实体、Mapper 和流程加载器，完成串行状态机；
2. 增加 approve/reject/withdraw 动作接口，接入动作锁、权限校验、乐观锁和动作幂等；
3. 在每次关键动作创建快照、动作记录和对应 Outbox 事件；
4. 增加待办/已办、实例详情、历史和快照查询；
5. 增加转审、加签及会签/或签规则；
6. 增加并行汇聚和条件分支，验证采购与合同两条不同流程；
7. 实现 Outbox 发布器、RabbitMQ 消费者、通知记录去重和重试；
8. 补充两个业务类型的端到端测试、并发测试、快照测试和干净环境启动验证；
9. 最后接入 gateway 路由、鉴权和 Swagger/OpenAPI 展示。

## 11. 代码依据

当前结论主要依据以下文件：

- `business-service/src/main/java/com/fluxcore/business/controller/ApplicationController.java`
- `business-service/src/main/java/com/fluxcore/business/controller/BusinessDataController.java`
- `business-service/src/main/java/com/fluxcore/business/controller/InternalApplicationController.java`
- `business-service/src/main/java/com/fluxcore/business/service/BusinessApplicationService.java`
- `approval-service/src/main/java/com/fluxcore/approval/controller/ApprovalController.java`
- `approval-service/src/main/java/com/fluxcore/approval/service/ApprovalSubmitService.java`
- `approval-service/src/main/java/com/fluxcore/approval/service/BusinessDataClient.java`
- `approval-service/src/main/java/com/fluxcore/approval/service/RedisLockService.java`
- `approval-service/src/main/resources/db/schema.sql`
- `approval-service/src/main/resources/db/data.sql`
- `approval-service/src/test/java/com/fluxcore/approval/service/ApprovalSubmitServiceTest.java`
- `docs/IMPLEMENTATION_PLAN.md`

