# 后续模型接手说明

请先阅读：

1. `docs/MODEL_HANDOFF.md`（当前最完整的实际状态和开发路线）
2. 根目录 `README.md`
3. `docs/IMPLEMENTATION_PLAN.md`
4. `docs/ARCHITECTURE.md`
5. `docs/DATABASE_DESIGN.md`

当前基线：

- 工程是 Maven 多模块 Spring Boot 项目。
- 已有四个应用服务目录和启动类：`gateway-service`、`approval-service`、`business-service`、`notification-service`；申请创建和提交审批首节点代码已经实现。
- 架构原则已确认：只有一个通用 `approval-service` 审批引擎；采购和合同统一放在 `business-service` 中作为不同业务模块，不得各自复制审批引擎。新增审批流程只增加数据库配置/节点规则，必要时增加业务适配器，不新增服务。
- 默认运行配置使用本机 MySQL 8、Redis 7、RabbitMQ 4，并通过各模块的 `application-local.yml` 连接 `127.0.0.1`。
- 用户明确要求使用 Redis 分布式锁防止重复请求执行。
- 幂等方案已确认：不创建独立幂等记录表，直接在 `application`、`approval_instance`、`approval_action` 中保存幂等字段，并使用唯一索引。
- 不要引入 RocketMQ，继续使用 RabbitMQ。
- 数据访问层统一使用 MyBatis-Plus（Spring Boot 3 Starter）；Mapper 使用 `@Mapper`，实体使用 `@TableName`/`@TableId`，不再使用 `JdbcTemplate`。
- 数据库设计以 `docs/DATABASE_DESIGN.md` 为准：一个 `fluxcore` 数据库、多张职责表；采购和合同统一由 `business-service` 维护业务数据。

当前已完成：

- MyBatis-Plus 数据访问层改造，Mapper 使用 `@Mapper` + `BaseMapper`。
- 申请创建和提交审批接口实现。
- 提交时创建审批实例、首节点、首个待办、提交快照、动作历史和 Outbox 事件。
- Redis 提交锁和申请/提交请求幂等校验。
- 完整多模块 Maven `compile` 已通过。
- `ApprovalSubmitServiceTest` 已覆盖正常提交、重复提交和 Redis 锁冲突，`approval-service` Maven `test` 已通过。

接手后的第一步：

```powershell
mvn -q validate
mvn -DskipTests package
```

如果 Maven 仍不可用，先检查 PATH，不要改换技术栈。构建通过后，确认本机 MySQL、Redis、RabbitMQ 已启动，再按 `docs/MODEL_HANDOFF.md` 和 `docs/IMPLEMENTATION_PLAN.md` 中最早的未完成任务继续；业务数据统一由 `business-service` 提供。

每次工作结束时更新实施计划中的复选框、当前进度和验收记录。
