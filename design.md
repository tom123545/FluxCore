# FluxCore Design

## 1. Scope

FluxCore 是一个本地运行的通用审批中台，不使用 Docker 作为运行前置。当前默认依赖为本机 MySQL 8、Redis 7、RabbitMQ 4，服务通过 `local` profile 连接 `127.0.0.1`。

## 2. Topology

```text
Client
  -> gateway-service :8080
       -> business-service :8082
       -> approval-service :8081
       -> approval-service task/query routes
  -> notification-service :8084 consumes RabbitMQ events

approval-service -> MySQL / Redis / RabbitMQ
business-service  -> MySQL
notification-service -> MySQL / RabbitMQ
```

职责划分：

- `gateway-service`：统一入口和转发。
- `business-service`：统一承载采购、合同等业务申请数据。
- `approval-service`：唯一审批引擎，负责流程、实例、任务、动作、历史、快照和 Outbox。
- `notification-service`：消费审批事件并记录站内信式通知结果。

### 2.1 为什么这样拆分

- 业务数据和审批规则是两条演进线，拆开后可以独立扩展采购、合同、费用、用印等业务。
- 审批引擎只保留通用能力，避免每新增一种业务就复制一套审批服务。
- `gateway-service` 只负责入口和路由，避免把协议适配和业务逻辑混在一起。
- `notification-service` 独立出来，是为了让异步通知不影响审批主流程。

## 3. Core Flow

### 3.1 创建申请

业务申请先在 `business-service` 创建草稿，落 `application`、业务主表、明细表和扩展表。

### 3.2 提交审批

1. `approval-service` 通过内部 API 读取业务数据。
2. 获取提交锁，校验幂等键。
3. 匹配已发布流程，创建审批实例、节点实例和首个待办。
4. 写入提交快照、提交动作和 Outbox 事件。
5. 调用 `business-service` 将申请改为 `SUBMITTED`。

### 3.3 审批动作

审批动作统一走 `approve / reject / withdraw / transfer / add-sign`。

- `approve`：推进下一节点或结束实例。
- `reject`：终结当前节点和实例。
- `withdraw`：申请人撤回。
- `transfer`：原任务转审。
- `add-sign`：在当前节点追加待办。

当前运行时支持 `SINGLE` 和 `OR`，`AND`、并行分支和条件路由仍是后续扩展。

### 3.4 查询

- 实例详情：当前节点、业务标题、任务状态。
- 待办/已办：按审批人查询。
- 历史：按动作时间返回动作与快照。
- 快照：按快照号返回不可变 JSON。

## 4. Snapshot Strategy

当前采用的是“全量快照”而不是“差异快照”。

原因很直接：

- 审批历史必须能独立还原当时看到的业务状态。
- 全量快照查询简单，不需要再叠加前序差异。
- 审批场景的写入频率通常远低于查询频率，全文存储更稳。
- 差异存储虽然省空间，但重建成本高，也更容易受历史片段缺失影响。

所以现在的策略是：每次提交、通过、驳回、撤回、转审、加签都保存一份完整 JSON 快照，历史和审计都直接读快照表，不回查业务当前表。

## 5. State Machine

审批流转采用实例、节点、任务三层状态机：

- `approval_instance`：`IN_PROGRESS -> APPROVED / REJECTED / WITHDRAWN`
- `approval_node_instance`：`ACTIVE -> COMPLETED / REJECTED / CANCELLED`
- `approval_task`：`PENDING -> APPROVED / REJECTED / TRANSFERRED / CANCELLED`

节点推进由 `approval_transition` 驱动，不把“第几级审批”写死在代码里。当前实现支持：

- `SINGLE`：单人签核，完成后推进下一节点。
- `OR`：或签，任一待办通过后取消其余待办并推进。

`lock_version` 和动作锁共同防止并发重复推进；终态实例不允许再执行动作。未来如果要扩展并行、会签和条件分支，只需要在这套状态机上继续加运行时规则，不必重做整个模型。

## 6. Data Model

### 4.1 Business side

- `application`
- `application_ext`
- `procurement_request`
- `procurement_item`
- `contract_change_request`
- `contract_change_item`

### 4.2 Approval side

- `approval_process`
- `approval_node`
- `approval_transition`
- `approval_instance`
- `approval_node_instance`
- `approval_task`
- `approval_action`
- `approval_snapshot`
- `approval_outbox_event`

### 4.3 Notification side

- `notification_record`
- `notification_failure`

## 7. Consistency

- Redis 锁用于减少重复提交和重复动作。
- MySQL 唯一约束和 `lock_version` 负责最终一致性。
- `actionRequestId`、`submitRequestId` 和业务幂等键用于请求重放。
- Outbox 先落库再异步发布，审批主流程不依赖 RabbitMQ 可用性。
- `X-Internal-Token` 用于服务间内部接口认证。

## 8. Multi-Business Extension

多业务类型的扩展机制以 `business_type` 为核心：

1. `business-service` 先增加新的业务表和内部数据适配器。
2. `approval-service` 通过 `businessType` 匹配对应流程配置。
3. 审批流程只新增流程定义、节点和流转关系，不新增审批服务。
4. 审批历史和快照仍由同一套表和接口承载。

这意味着新增“费用申请”“用印申请”时，通常只需要：

- 增加业务模块和 DTO；
- 增加流程配置；
- 增加必要的内部数据读取适配；
- 不需要新建 `xxx-approval-service`。

## 9. Validation

已完成验证：

- `mvn -q test`
- `mvn -q validate`
- `mvn -q -DskipTests package`
- 本机 MySQL / Redis / RabbitMQ + 4 个服务的真实 E2E

验证样例之一：

- 采购申请 `applicationId=26`
- 审批实例 `approvalInstanceId=22`
- 业务单号 `PUR-F6FF77D6D596`
- 审批单号 `APR-522D6756E581`
- 最终状态 `APPROVED`

## 10. Remaining Limits

- Gateway 认证和对象级授权仍需继续加固。
- `approval-service` 目前未实现 AND、并行汇聚和条件表达式。
- 待办分页、Swagger 示例和一键干净环境启动仍可继续完善。
- approval / notification 的 `ping` 仍是服务本地健康检查，不作为网关公开业务接口。
