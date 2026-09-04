# FluxCore 测试方案

## 1. 目标与范围

本方案用于验证需求说明书和现有技术文档定义的通用审批平台，覆盖：

- 采购、合同变更两类业务申请的创建与接入；
- 提交、逐级通过、驳回、撤回、转审、加签；
- 串行、或签、会签、并行和条件分支；
- 待办/已办、实例、历史和快照查询；
- 幂等、Redis 锁、数据库乐观锁、事务边界和并发冲突；
- Outbox、RabbitMQ 投递、通知去重、失败重试；
- 网关路由、认证和内部接口访问控制。

本轮已新增测试代码和本文档，但没有修改生产代码。

## 2. 测试策略

采用三层测试结构：

| 层级 | 目标 | 外部依赖 | 当前状态 |
|---|---|---|---|
| 单元测试 | 验证状态机、Service 分支、边界输入、权限、幂等和异常 | Mockito | 已补充并通过 |
| 流程组件集成测试 | 真实组合提交服务、动作服务和状态机，验证完整业务状态变化 | 状态化测试适配器 | 已补充并通过 |
| 环境 E2E/验收测试 | 验证真实 HTTP、MySQL、Redis、RabbitMQ 和多服务边界 | 本机基础设施和四个服务 | 需本机依赖启动后执行 |

组件集成测试不是对真实 SQL、事务隔离或消息 Broker 的替代。真实环境测试作为发布前门禁执行，结果需记录到第 6.4 节。

## 3. 已实现测试清单

### 3.1 单元测试

新增或扩展：

| 测试类 | 用例数 | 覆盖内容 |
|---|---:|---|
| `approval-service/src/test/java/com/fluxcore/approval/config/HttpClientConfigTest.java` | 1 | 业务客户端内部 token 自动注入 |
| `approval-service/src/test/java/com/fluxcore/approval/service/ApprovalBoundaryTest.java` | 8 | 业务数据为空、非草稿、业务标识不匹配、审批人未配置、实例不存在、操作人越权、转审/加签目标非法 |
| `approval-service/src/test/java/com/fluxcore/approval/state/ApprovalStateMachineTest.java` | +1 | null、空白、未知状态和大小写/空白规范化 |
| `notification-service/src/test/java/com/fluxcore/notification/service/ApprovalNotificationServiceTest.java` | +2 | 事件缺少接收人、事件缺少 eventId |

已有单测继续覆盖提交、审批通过、末级完成、或签、驳回、撤回、重复动作、查询和 Outbox 失败重试。

### 3.2 流程组件集成测试

文件：`approval-service/src/test/java/com/fluxcore/approval/integration/ApprovalWorkflowIntegrationTest.java`

已实现 4 个完整流程：

1. 采购三级串行：创建/提交 → 部门负责人通过 → 财务通过 → 总经理通过 → 审批和业务状态 `APPROVED`。
2. 合同变更两级串行：创建/提交 → 部门负责人通过 → 法务通过 → `APPROVED`。
3. 首级驳回：提交 → 首级驳回 → 当前节点和实例 `REJECTED`，任务终结，业务状态同步。
4. 中途撤回：提交 → 首级通过并进入第二节点 → 申请人撤回 → 当前待办和活动节点取消，实例 `WITHDRAWN`。

每条流程同时断言实例、节点实例、任务、动作、快照、Outbox 和版本变化，避免只断言 HTTP 返回值。

## 4. 测试用例矩阵

### 4.1 业务申请和提交

| 编号 | 场景 | 预期 |
|---|---|---|
| UT-SUB-01 | 业务数据不存在 | `BUSINESS_DATA_NOT_FOUND`，不创建审批数据，释放锁 |
| UT-SUB-02 | 申请已提交/已终结 | `APPLICATION_NOT_DRAFT`，不创建实例 |
| UT-SUB-03 | businessType、businessId、applicationId 或申请人不匹配 | 4xx，流程配置不被读取 |
| UT-SUB-04 | 没有已发布流程 | `PROCESS_NOT_FOUND` |
| UT-SUB-05 | 首节点无审批人或模式不支持 | 配置错误，审批实例不落库 |
| UT-SUB-06 | 相同提交幂等键重复请求 | 返回原审批实例，不产生第二组节点/任务/快照/事件 |
| UT-SUB-07 | 不同申请并发使用同一提交幂等键 | 按约定作用域，一个成功，另一个明确冲突 |
| UT-SUB-08 | 业务服务状态更新失败 | 审批本地事务回滚或进入可补偿状态，不产生孤儿状态 |

### 4.2 审批动作和状态机

| 编号 | 场景 | 预期 |
|---|---|---|
| UT-ACT-01 | 非任务审批人操作 | 403，不读取/修改业务数据 |
| UT-ACT-02 | 不存在的实例、任务或非当前节点任务 | 404/409，不写动作和事件 |
| UT-ACT-03 | PENDING 任务重复处理 | 条件更新失败并返回冲突 |
| UT-ACT-04 | 终态实例继续通过/驳回/撤回 | 明确拒绝，不修改任何运行数据 |
| UT-ACT-05 | 相同 actionRequestId、完全相同请求重放 | 返回首次结果，`duplicate=true` |
| UT-ACT-06 | 相同 actionRequestId 但任务/目标/操作语义不同 | 409 `ACTION_REQUEST_ID_REUSED` |
| UT-ACT-07 | 通过当前节点后创建下一节点 | 原任务完成、新节点 ACTIVE、全部下一待办创建、实例版本递增 |
| UT-ACT-08 | 末级通过 | 实例、申请主表和业务子表均为 `APPROVED` |
| UT-ACT-09 | 驳回 | 当前任务/节点驳回，其余待办取消，实例和业务状态驳回 |
| UT-ACT-10 | 撤回 | 申请人可撤回，非申请人禁止，所有活动待办和节点终结 |
| UT-ACT-11 | 转审 | 原任务 `TRANSFERRED`，替代任务 PENDING，来源关系正确 |
| UT-ACT-12 | 加签 | 新增任务 PENDING，当前节点完成规则符合模式 |
| UT-ACT-13 | Redis 锁不可用或租约过期 | 返回冲突；数据库条件更新仍保护最终一致性 |
| UT-ACT-14 | expectedVersion 过期 | 返回版本冲突，不写任务、动作、快照或 Outbox |

### 4.3 流程模式

| 编号 | 场景 | 预期 |
|---|---|---|
| IT-FLOW-01 | SINGLE 串行 | 一个审批人完成后进入唯一下一节点 |
| IT-FLOW-02 | OR/ANY 或签 | 任一任务通过，其他 PENDING 任务取消并推进 |
| IT-FLOW-03 | AND/ALL 会签 | 所有任务通过后才完成节点；任一驳回按规则终止 |
| IT-FLOW-04 | 并行分支 | 多个节点同时 ACTIVE，全部前置完成后才进入汇聚节点 |
| IT-FLOW-05 | 条件分支 | 按快照/业务数据计算条件，按 priority 选择唯一或多个后继 |
| IT-FLOW-06 | 缺少 transition | 返回流程配置错误，不得直接批准 |
| IT-FLOW-07 | 显式 END 节点 | 正确进入 END 后才将实例置为 `APPROVED` |

当前代码只支持 SINGLE 和 OR；IT-FLOW-03～05、IT-FLOW-06～07 应在实现对应能力后加入可执行集成测试，不能用“表结构已预留”代替测试通过。

### 4.4 查询、快照和历史

| 编号 | 场景 | 预期 |
|---|---|---|
| UT-QUERY-01 | 待办查询 | 只返回指定审批人和 PENDING 任务 |
| UT-QUERY-02 | 已办查询 | 返回 APPROVED/REJECTED/TRANSFERRED/CANCELLED 等非 PENDING 任务 |
| UT-QUERY-03 | 历史查询 | 按动作时间稳定排序，审批业务内容来自 snapshot，不回查当前业务表 |
| UT-QUERY-04 | 快照哈希 | `data_hash` 为完整 JSON 的 SHA-256，历史快照不可变 |
| UT-QUERY-05 | 终态实例详情 | 业务服务不可用时仍可用本地快照展示审批事实 |
| UT-QUERY-06 | 大量待办/已办 | 分页或游标有效，页大小有上限，不返回无限结果集 |

### 4.5 Outbox 和通知

| 编号 | 场景 | 预期 |
|---|---|---|
| UT-MSG-01 | Outbox payload 为合法 JSON | 组装统一事件信封并发布，成功标记 PUBLISHED |
| UT-MSG-02 | Outbox payload 损坏 | 标记 FAILED，记录 retry_count/next_retry_at |
| IT-MSG-03 | RabbitMQ 不可用 | 审批主事务成功，Outbox 保留可重试状态 |
| IT-MSG-04 | 通知服务停止 | 审批仍成功；通知服务恢复后消息可消费 |
| IT-MSG-05 | 同一 eventId 重复投递 | 只产生一次通知记录 |
| IT-MSG-06 | 多接收人/多渠道 | 每个接收人和渠道均有独立投递状态 |
| IT-MSG-07 | 永久失败消息 | 进入 FAILED/DLQ，不无限快速重投阻塞队列 |
| IT-MSG-08 | Broker 无路由/无消费者队列 | Publisher confirm/return 能检测并安排重试 |

## 5. 测试数据和不变量

### 5.1 基础数据

| 数据 | 值 |
|---|---|
| 采购申请人 | `U1001` |
| 合同申请人 | `U1002` |
| 部门负责人 | `U2001` |
| 财务负责人 | `U2002` |
| 总经理 | `U2003` |
| 法务负责人 | `U2004` |
| 采购流程 | `PURCHASE`，三级串行 |
| 合同流程 | `CONTRACT_CHANGE`，两级串行 |

测试数据必须使用虚拟 ID，不使用真实业务数据、敏感信息或外部 AI Key。

### 5.2 每次动作后的不变量

1. 一个 `approval_instance` 只对应一个申请，且流程版本不会随新版本发布而改变。
2. PENDING 任务只能由对应 assignee 处理，处理成功后不可回到 PENDING。
3. 活动节点必须与实例 `current_node_id`（串行模式）一致；终态实例没有活动节点。
4. 每个提交/关键动作最多产生一条对应 action、snapshot 和 Outbox 记录；重复请求不产生副作用。
5. snapshot 只新增不更新；历史展示内容与业务当前记录变化无关。
6. 影响审批聚合的每个动作都必须使 `lock_version` 按条件递增。
7. 审批本地事务失败时，不能留下半组任务、动作、快照或事件。
8. 事件发布失败不能回滚已完成审批，但必须保留可观测、可重试的 Outbox 状态。

## 6. 执行方式

### 6.1 当前可执行测试

在项目根目录执行：

```powershell
mvn -q -pl approval-service,notification-service test
mvn -q test
mvn -q -DskipTests package
```

当前环境如果 `mvn` 不在 PATH，可使用 IDEA 自带 Maven：

```powershell
$env:MAVEN_OPTS='-Duser.home=C:\Users\admin -Dmaven.repo.local=C:\Users\admin\.m2\repository'
& 'D:\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn.cmd' -q -pl approval-service,notification-service test
```

2026-09-04 执行结果：62 个测试通过，失败 0，错误 0，跳过 0；根工程 `validate` 和跳过测试打包通过。

### 6.2 真实环境 E2E 前置条件

需要启动：

- MySQL 8，数据库 `fluxcore`；
- Redis 7；
- RabbitMQ 4，用户密码按本机安装配置；
- business-service `8082`；
- approval-service `8081`；
- notification-service `8084`；
- gateway-service `8080`（路由和鉴权完成后）。

建议使用干净数据库启动，避免历史数据、旧队列和旧 Outbox 影响结果。E2E 测试结束后保留数据库核验结果和服务日志。

### 6.3 E2E 主流程

1. 创建采购申请草稿，校验 `application`、`procurement_request`、明细和扩展表各有正确记录。
2. 提交采购申请，校验首个待办、SUBMIT 动作、SUBMIT 快照和 Outbox。
3. 依次以 `U2001`、`U2002`、`U2003` 通过，校验每一级只生成一个下一节点，最终实例/业务均为 `APPROVED`。
4. 查询 history/snapshots，校验四条快照包含提交和每一级审批时的数据及哈希。
5. 创建合同变更，完成两级流程，确认没有复制或调用第二套审批引擎。
6. 创建两条驳回/撤回场景，校验任务、节点、实例和业务状态一致。
7. 使用相同提交键和动作键重试，校验幂等返回；使用相同键但改变任务/目标人，校验返回 409。
8. 并发提交两个相同动作，校验最多一个成功，另一个返回冲突，数据库没有重复动作或重复推进。
9. 停止 notification-service，完成审批动作，确认审批成功且 Outbox 未丢失；恢复通知服务后确认事件最终消费。
10. 检查无权限用户、伪造 operatorId、直接访问 internal API 和绕过 Gateway 的结果。

### 6.4 真实环境执行结果

#### 2026-09-04 执行记录

自动化单元/组件集成测试已执行通过；真实 HTTP E2E 未执行，因为本机端口 `8080/8081/8082/8084` 未监听，且本机 MySQL、Redis、RabbitMQ 运行状态需要在启动服务前确认。后续执行必须使用本机安装的 MySQL、Redis、RabbitMQ，不使用其他基础设施启动入口。

#### 2026-09-03 历史记录

环境：本机 MySQL 8、Redis、RabbitMQ，及 business `8082`、approval `8081`、notification `8084`、gateway `8080`；服务使用 local profile 启动，测试数据均为虚拟数据。

| 场景 | 结果 | 关键证据 |
|---|---|---|
| 服务健康检查 | 通过 | 四个 `/api/*/ping` 均 HTTP 200；MySQL、Redis、RabbitMQ 连接正常 |
| 采购三级串行 | 通过 | 实例 8：U2001 → U2002 → U2003，最终 `APPROVED`，lockVersion=3，任务数 3 |
| 合同两级串行 | 通过 | 实例 9：U2001 → U2004，最终 `APPROVED`，lockVersion=2，重复提交返回 `duplicate=true` |
| 首级驳回 | 通过 | 实例 10：审批实例和业务申请均为 `REJECTED`，历史/快照各 2 条 |
| 中途撤回 | 通过 | 实例 11：申请人撤回后实例/业务均为 `WITHDRAWN`，已通过任务保留、活动待办为 `CANCELLED` |
| 转审 | 通过 | 实例 12：U2001 → U2005，替代任务 `sourceTaskId=23`，后续完成至 `APPROVED` |
| 加签 | 通过 | 实例 13：新增 U2006 待办；原审批人与加签人均通过后流程继续并完成 |
| 幂等 | 部分通过 | 完全相同提交/动作重放返回 200 且 `duplicate=true`；相同键换业务或换操作人返回 409 |
| 并发审批 | 通过 | 实例 17 同一任务双请求：一个 200、一个 409；最终只有 1 个 `APPROVED` 任务，lockVersion=1 |
| Outbox/RabbitMQ/通知消费 | 基础链路通过 | 最近事件均为 `PUBLISHED`、retry=0；通知记录为 `SENT`；停通知服务时实例 18 事件在队列等待，恢复后队列回到 0、消费者为 1 |
| 重复消息去重 | 通过 | 重发已有 eventId 后通知记录仍只有 1 条 |
| 网关业务路由 | 失败 | gateway 对 `/api/business/ping`、`/api/approvals` 等业务路径均返回 404 |
| internal 接口隔离 | 失败 | 无认证直接调用 `/api/internal/applications/22/approve` 返回 200，使业务为 `APPROVED` 而审批实例 19 仍为 `IN_PROGRESS` |
| 真实输入边界 | 失败 | 采购明细缺少 `quantity` 返回 500，未映射为明确 4xx |

结论：真实环境可以运行基础串行审批及主要动作链路，但当前不能通过发布验收。至少应先处理 internal 接口鉴权/网关路由、事件收件人契约、Outbox 发布确认和输入校验问题。

## 7. 覆盖率和质量门禁

当前项目尚未配置 JaCoCo 或独立集成测试生命周期。完成配置后建议采用以下门禁：

- 核心状态机和审批 Service 行覆盖率不低于 80%，分支覆盖率不低于 70%；
- 所有 P1 用例必须有自动化测试，P0/P1 缺陷不得带病发布；
- 单元测试、组件集成测试和真实 E2E 均通过后才标记验收完成；
- 并发、消息重投、服务重启和干净数据库启动至少各执行一次；
- 测试失败必须保留请求参数、实例 ID、动作 ID、事件 ID 和相关服务日志。

## 8. 当前限制和后续补充

本轮组件集成测试为了保证在没有本机中间件运行态的环境中可重复执行，使用状态化 Mapper 和业务客户端测试适配器；它不能发现真实 MySQL SQL、事务隔离、Redis TTL、RabbitMQ 路由和网络超时问题。真实环境测试需要在本机 MySQL、Redis、RabbitMQ 启动后继续执行，并且尚未覆盖 Broker 完全不可用、Redis 租约过期和干净数据库重建。

以下项目仍需真实环境或后续功能完成后补测：

- MySQL/Redis/RabbitMQ 真实 E2E；
- 两个服务之间的失败补偿和对账；
- Redis 锁租约过期、服务重启和多实例竞争；
- AND 会签、并行汇聚、条件分支和显式 END；
- 多接收人通知、Publisher confirm、失败退避和死信队列；
- Gateway 路由、认证、权限和 internal API 隔离；
- 待办/已办分页和大数据量性能；
- 生产代码评审文档中列出的事件语义、快照完整性和动作版本问题。
