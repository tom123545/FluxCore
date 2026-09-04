# FluxCore 通用审批服务平台

这是一个面向面试展示的通用审批中台项目，目标是演示配置化审批流程、数据快照、待办中心、分布式锁、幂等、并发控制和异步通知。项目只包含一套通用审批引擎；采购和合同作为不同业务类型共用审批流程能力，不会为每个流程复制新的审批服务。

## 环境要求

- JDK 21
- Maven 3.9+
- 本机 MySQL 8+
- 本机 Redis 7+
- 本机 RabbitMQ 4+
- 本机已启动的 MySQL、Redis、RabbitMQ 服务

## 本地配置

服务默认使用 `local` profile，连接本机的 `127.0.0.1`：

- MySQL：`127.0.0.1:3306/fluxcore`，默认用户 `root`
- Redis：`127.0.0.1:6379`，默认无密码
- RabbitMQ：`127.0.0.1:5672`，默认用户密码 `guest/guest`

如果本机 MySQL root 用户设置了密码，启动服务前设置：

```powershell
$env:LOCAL_DB_PASSWORD = '<本机 MySQL root 密码>'
```

也可以使用 `LOCAL_DB_USERNAME`、`LOCAL_DB_URL`、`LOCAL_REDIS_*` 和 `LOCAL_RABBITMQ_*` 覆盖本地默认值。

## 本地基础设施

请先在本机启动 MySQL 8、Redis 7、RabbitMQ 4，再运行服务和测试。

默认连接地址如下：

- MySQL：`127.0.0.1:3306/fluxcore`
- Redis：`127.0.0.1:6379`
- RabbitMQ：`127.0.0.1:5672`

如需覆盖默认地址，可使用 `LOCAL_DB_URL`、`LOCAL_REDIS_*`、`LOCAL_RABBITMQ_*` 和 `LOCAL_BUSINESS_SERVICE_URL`。

## 当前状态

当前已完成申请创建的基础业务代码和数据库映射改造，业务功能按 `docs/IMPLEMENTATION_PLAN.md` 逐阶段实现。

数据访问层使用 MyBatis-Plus：Mapper 为 `@Mapper` 接口并继承 `BaseMapper`，Entity 使用 `@TableName`、`@TableId` 和驼峰字段映射。

验证骨架：

```powershell
.\scripts\verify-framework.ps1
```

## 模块

- `gateway-service`：统一入口
- `approval-service`：唯一通用审批引擎
- `business-service`：统一承载采购、合同及其他业务申请数据，不负责审批流转
- `notification-service`：异步通知

## 文档

- [后续模型开发交接文档](docs/MODEL_HANDOFF.md)
- [实施计划](docs/IMPLEMENTATION_PLAN.md)
- [架构设计](docs/ARCHITECTURE.md)
- [技术方案](docs/TECHNICAL_DESIGN.md)
- [技术方案与开发现状](docs/TECHNICAL_SOLUTION.md)
- [数据库设计技术方案](docs/DATABASE_DESIGN.md)
