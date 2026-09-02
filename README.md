# FluxCore 通用审批服务平台

这是一个面向面试展示的通用审批中台项目，目标是演示配置化审批流程、数据快照、待办中心、分布式锁、幂等、并发控制和异步通知。项目只包含一套通用审批引擎；采购和合同作为不同业务类型共用审批流程能力，不会为每个流程复制新的审批服务。

## 环境要求

- JDK 21
- Maven 3.9+
- Docker Desktop
- Docker Compose

## 启动基础设施

```powershell
docker compose up -d
```

也可以执行：

```powershell
.\scripts\start-infra.ps1
```

基础设施包括 MySQL、Redis 和 RabbitMQ。RabbitMQ 管理台地址为 <http://localhost:15672>，账号密码均为 `fluxcore`。

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

- [实施计划](docs/IMPLEMENTATION_PLAN.md)
- [架构设计](docs/ARCHITECTURE.md)
- [技术方案](docs/TECHNICAL_DESIGN.md)
- [数据库设计技术方案](docs/DATABASE_DESIGN.md)
