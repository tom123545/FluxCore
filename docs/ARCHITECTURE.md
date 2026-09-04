# 架构设计

## 核心原则

审批引擎只有一个。采购、合同以及未来新增的费用、用印、付款等业务，都是审批引擎可以处理的业务类型，不为每个审批流程创建新的审批服务。当前项目按本机 MySQL、Redis、RabbitMQ 运行，不依赖 Docker。

新增一种审批流程时，只需要增加流程定义、节点和流转配置；如果业务数据确实独立，再增加业务数据服务。不能复制一套审批引擎。

## 总体结构

```text
Client / Swagger
       |
Gateway Service :8080
       |
       +--> Approval Service :8081 ---- MySQL fluxcore
       |           |  |  |
       |           |  |  +------------- Redis lock
       |           |  +---------------- RabbitMQ events
       |           +------------------- Business internal API
       |
       +--> Business Service :8082 - MySQL fluxcore（采购、合同业务数据）

RabbitMQ --> Notification Service :8084 [通用通知消费者]
```

Business Service 统一承载采购、合同及未来业务申请数据，不包含审批引擎。审批服务通过 Business Service 的接口或适配器读取业务数据，禁止跨服务直接 Join 或直接修改业务表。当前内部调用通过 `X-Internal-Token` 保护。

## 设计说明

### 为什么这样拆分

- 业务类型会持续增加，但审批能力大体共用，所以把审批引擎做成唯一服务最省维护成本。
- 业务数据和审批流程的变更节奏不同，拆开后可以独立发布。
- 通知消费单独拆出，是为了避免消息处理拖慢审批主链路。

### 数据快照策略

当前采用全量 JSON 快照，不采用差异存储。这样做的好处是历史回放简单、审计稳定、实现成本低。审批历史、实例详情和追溯都直接读快照表，不依赖业务表当前值。

### 状态机设计

审批实例、节点实例和任务各自维护状态。审批推进由 `approval_transition` 驱动，当前支持 `SINGLE` 和 `OR`，`AND`、并行与条件分支作为后续扩展点。最终一致性依赖数据库条件更新和 `lock_version`，不是只靠 Redis 锁。

### 多业务扩展

新增业务类型时，只需要在 `business-service` 增加业务数据和内部适配器，再在审批侧增加流程配置。不会为每个业务单独复制审批服务。

## 服务职责

| 服务 | 是否审批引擎 | 职责 |
|---|---|---|
| gateway-service | 否 | 统一入口、鉴权和路由 |
| approval-service | 是，且只有这一套 | 流程定义解析、节点流转、审批动作、待办、历史、快照、锁和事件 |
| business-service | 否 | 采购、合同及未来业务申请数据 |
| notification-service | 否 | 消费通用审批事件，发送/模拟邮件和站内信 |

## 通用审批模型

审批实例通过以下字段关联任意业务：

```text
business_type = PURCHASE / CONTRACT / FUTURE_TYPE
business_id   = 业务单据 ID
```

流程由配置驱动：

```text
workflow_definition  流程定义及版本
workflow_node        节点、审批人规则、节点类型
workflow_transition  节点之间的流转关系和条件
```

采购和合同共用同一个 `ApprovalStateMachine`、同一组审批接口和同一套历史/快照/待办模型，差异只来自业务类型对应的流程配置和业务数据适配器。

## 一致性策略

Redis 锁用于减少同一业务提交和审批动作的重复执行：

- `approval:submit:{businessType}:{businessId}`
- `approval:action:{approvalInstanceId}`

锁必须设置过期时间，并使用持有者校验后释放。数据库仍然是最终一致性依据：申请和审批实例保存幂等字段并建立唯一索引，审批实例使用乐观锁 `lock_version`，任务状态使用条件更新。

## 事件策略

审批事务先写入 Outbox，再由发布器投递到 RabbitMQ。通知服务采用至少一次消费，使用事件 ID 去重。通知失败不回滚审批事务。

## 数据快照策略

提交时和每一级审批通过时，通过业务服务内部 API 获取当前业务数据，并保存为不可变 JSON 快照。历史接口读取快照表，不能直接读取业务服务的当前记录。

## 演进方向

第一版实现串行节点。节点模型预留 `SERIAL`、`PARALLEL` 和 `CONDITION` 类型；后续可增加并行汇聚、条件表达式、流程版本发布、JWT 鉴权和独立数据库部署。新增业务类型不新增审批服务，只增加业务适配器和流程配置。
