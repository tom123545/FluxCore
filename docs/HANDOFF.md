# 后续模型接手说明

请先阅读：

1. 根目录 `README.md`
2. `docs/IMPLEMENTATION_PLAN.md`
3. `docs/ARCHITECTURE.md`

当前基线：

- 工程是 Maven 多模块 Spring Boot 项目。
- 已有五个服务目录和启动类，当前业务类尚未实现。
- 基础设施是 MySQL 8、Redis 7、RabbitMQ 4，由根目录 `docker-compose.yml` 管理。
- 用户明确要求使用 Redis 分布式锁防止重复请求执行。
- 不要引入 RocketMQ，继续使用 RabbitMQ。

接手后的第一步：

```powershell
mvn -q validate
mvn -DskipTests package
docker compose up -d
```

如果 Maven 仍不可用，先检查 PATH，不要改换技术栈。构建通过后，按 `docs/IMPLEMENTATION_PLAN.md` 中最早的未完成任务继续：先实现 `approval-service` 的领域模型、数据库迁移和配置化串行审批。

如果 `docker compose up -d` 无法拉取镜像，先检查 Docker Desktop 的 Internet/Proxy 设置；不要修改服务编排或替换中间件。

每次工作结束时更新实施计划中的复选框、当前进度和验收记录。
