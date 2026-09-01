# FluxCore 通用审批服务平台

这是一个面向面试展示的通用审批中台项目，目标是演示配置化审批流程、数据快照、待办中心、分布式锁、幂等、并发控制和异步通知。

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

当前只完成工程骨架和基础设施配置，业务功能按 `docs/IMPLEMENTATION_PLAN.md` 逐阶段实现。

验证骨架：

```powershell
.\scripts\verify-framework.ps1
```

## 模块

- `gateway-service`：统一入口
- `approval-service`：审批核心
- `procurement-service`：采购示例业务
- `contract-service`：合同示例业务
- `notification-service`：异步通知

## 文档

- [实施计划](docs/IMPLEMENTATION_PLAN.md)
- [架构设计](docs/ARCHITECTURE.md)
