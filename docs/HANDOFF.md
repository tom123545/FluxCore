# 后续模型接手说明

请先阅读：

1. 根目录 `README.md`
2. `docs/IMPLEMENTATION_PLAN.md`
3. `docs/ARCHITECTURE.md`
4. `docs/DATABASE_DESIGN.md`

当前基线：

- 工程是 Maven 多模块 Spring Boot 项目。
- 已有四个应用服务目录和启动类：`gateway-service`、`approval-service`、`business-service`、`notification-service`；当前业务类尚未实现。
- 架构原则已确认：只有一个通用 `approval-service` 审批引擎；采购和合同统一放在 `business-service` 中作为不同业务模块，不得各自复制审批引擎。新增审批流程只增加数据库配置/节点规则，必要时增加业务适配器，不新增服务。
- 基础设施是 MySQL 8、Redis 7、RabbitMQ 4，由根目录 `docker-compose.yml` 管理。
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

接手后的第一步：

```powershell
mvn -q validate
mvn -DskipTests package
docker compose up -d
```

如果 Maven 仍不可用，先检查 PATH，不要改换技术栈。构建通过后，按 `docs/IMPLEMENTATION_PLAN.md` 中最早的未完成任务继续：实现审批通过、驳回、撤回和待办查询，并让 PURCHASE、CONTRACT 共用同一套状态机和 API；业务数据统一由 `business-service` 提供。

如果 `docker compose up -d` 无法拉取镜像，先检查 Docker Desktop 的 Internet/Proxy 设置；不要修改服务编排或替换中间件。

每次工作结束时更新实施计划中的复选框、当前进度和验收记录。
