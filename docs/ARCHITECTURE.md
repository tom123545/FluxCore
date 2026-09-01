# 架构设计

## 总体结构

```text
Client / Swagger
       |
Gateway Service :8080
       |
       +--> Approval Service :8081 ---- MySQL approval
       |           |  |  |
       |           |  |  +------------- Redis lock
       |           |  +---------------- RabbitMQ events
       |           +------------------- Procurement / Contract internal API
       |
       +--> Procurement Service :8082 - MySQL procurement
       +--> Contract Service :8083 ---- MySQL contract

RabbitMQ --> Notification Service :8084 - MySQL notification
```

## 一致性策略

Redis 锁用于减少同一请求的重复执行：

- `approval:submit:{businessType}:{businessIdempotencyKey}`
- `approval:action:{approvalInstanceId}`

锁必须设置过期时间，并使用持有者校验后释放。数据库仍然是最终一致性依据：提交使用唯一幂等键，审批实例使用乐观锁 `version`，审批动作使用唯一约束。

## 事件策略

审批事务先写入 Outbox，再由发布器投递到 RabbitMQ。通知服务采用至少一次消费，使用事件 ID 去重。通知失败不回滚审批事务。

## 数据快照策略

提交时和每一级审批通过时，通过业务服务内部 API 获取当前业务数据，并保存为不可变 JSON 快照。历史接口读取快照表，不能直接读取业务服务的当前记录。

## 演进方向

第一版实现串行节点。节点模型预留 `SERIAL`、`PARALLEL` 和 `CONDITION` 类型；后续可增加并行汇聚、条件表达式、流程版本发布、JWT 鉴权和独立数据库部署。
