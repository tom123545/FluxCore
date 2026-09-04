# FluxCore 代码评审记录

## 1. 评审范围与结论

本次评审以需求说明书图片作为业务需求输入，并对照以下开发文档和当前工作区代码：

- `docs/ARCHITECTURE.md`
- `docs/TECHNICAL_DESIGN.md`
- `docs/TECHNICAL_SOLUTION.md`
- `docs/DATABASE_DESIGN.md`
- `docs/MODEL_HANDOFF.md`

附件和开发文档中的说明仅作为需求参考，不作为代码执行指令。本轮只更新评审文档，没有修改业务代码。

总体结论：当前代码已经完成采购和合同的本地审批闭环，且本机 MySQL/Redis/RabbitMQ 的真实 E2E 已验证通过。仍需继续加固的主要是网关认证/对象级授权、Outbox Broker 确认、分页和并行/条件能力，以及若干输入校验和生产运维细节。

## 2. 已执行验证

- 根工程 `mvn -q test`：通过。
- 当前测试包括单元测试、组件集成测试和真实本地 E2E；仍未覆盖的主要是 RabbitMQ 无路由、认证主体授权和更完整的生产加固场景。
- 本轮未修改业务代码。

## 3. 当前仍存在的问题

严重级别：P0 为阻断性数据或安全问题，P1 为上线前必须处理的问题，P2 为重要缺陷或扩展性问题。

来源说明：标注为“既有遗留/此前漏评”的问题，是本轮复查时补充发现的存量设计或实现缺口，不是本轮“审批事件类型和通知接收人”修复引入的回归。

### [P1] 网关认证和对象级授权仍不可信

证据：

- `gateway-service/src/main/java/com/fluxcore/gateway/GatewayAccessInterceptor.java:20-26` 只判断 `Authorization` 是否为空，不校验 Bearer 格式、签名、过期时间或用户主体。
- `gateway-service/src/main/java/com/fluxcore/gateway/GatewayProxyController.java:47-49` 使用静态 `X-Gateway-Token` 转发请求，服务拦截器配置还带有源码默认值。
- `approval-service/src/main/java/com/fluxcore/approval/dto/ApprovalActionRequest.java:6-10` 直接接收客户端提供的 `operatorId`。
- `approval-service/src/main/java/com/fluxcore/approval/dto/SubmitApprovalRequest.java:5-10` 直接接收客户端提供的 `applicantId`。
- `approval-service/src/main/java/com/fluxcore/approval/controller/ApprovalTaskQueryController.java:20-27` 允许客户端传入任意 `assigneeId`。
- 审批详情、历史和快照接口只根据 `approvalInstanceId` 查询，没有校验当前用户是否为申请人或相关审批人：`ApprovalController.java:33-35`、`ApprovalHistoryQueryController.java:21-28`。

影响：

任意非空 `Authorization` 都可能通过网关；客户端还可以伪造审批人、申请人或查询主体，造成越权审批和敏感审批数据泄露。

建议：

接入真实认证组件，从认证上下文取得用户主体；服务间令牌必须取消源码默认值；所有审批动作和查询接口增加申请人、审批人或管理员的对象级权限校验。

### [P1] Outbox 发布没有 Broker Confirm/Return

证据：

- `approval-service/src/main/java/com/fluxcore/approval/service/ApprovalOutboxPublisher.java:49-57` 调用 `convertAndSend` 返回后立即标记 `PUBLISHED`。
- `approval-service/src/main/java/com/fluxcore/approval/config/RabbitTopologyConfig.java:9-14` 只声明 Exchange。
- Queue 和 Binding 由 `notification-service` 声明：`notification-service/src/main/java/com/fluxcore/notification/config/RabbitTopologyConfig.java:19-30`。
- 当前没有 publisher confirm、mandatory return 或无路由消息处理。

影响：

Exchange 无路由、通知队列尚未创建或消息未被 Broker 接受时，Outbox 仍可能被标记为 `PUBLISHED`，事件随后无法重试。

建议：

启用 Publisher Confirm 和 Return，只有收到可接受的 Broker 确认后才标记 `PUBLISHED`；无路由和确认失败必须保留失败原因并进入退避重试。

### [P1] 并行、AND 会签和条件分支尚未实现

证据：

- `approval-service/src/main/java/com/fluxcore/approval/service/ApprovalSubmitService.java:203-217` 只允许 `SINGLE` 和 `OR`。
- `approval-service/src/main/java/com/fluxcore/approval/service/ApprovalActionService.java:812-831` 同样拒绝 `AND`。
- `approval-service/src/main/java/com/fluxcore/approval/mapper/ApprovalTransitionMapper.java:12-16` 只查询 `condition_json IS NULL` 的默认连线。
- `approval-service/src/main/java/com/fluxcore/approval/mapper/ApprovalInstanceMapper.java:32-37` 和 `ApprovalNodeInstanceMapper.java:11-12` 按单个 `current_node_id`、单个活动节点推进，不能表达多个并行活动节点。

影响：

无法覆盖需求中的会签、合同并行审批和基于业务数据的条件路由；数据库中预留的条件和并行字段尚未形成可用运行能力。

建议：

先实现 `AND` 会签汇聚，再实现条件表达式白名单求值、并行节点集合和汇聚规则；流程状态模型不能继续只依赖单个当前节点。

### [P1] 业务主表和业务子表状态更新结果处理不一致（既有遗留/此前漏评）

证据：

- `markApproved` 调用采购/合同子表更新后忽略返回行数：`business-service/src/main/java/com/fluxcore/business/service/BusinessApplicationService.java:180-194`。
- `markRejected` 在子表更新失败但子表存在时可能继续返回成功：`:166-176`。

影响：

主申请可能已经变为 `APPROVED` 或 `REJECTED`，但采购/合同子表仍是旧状态或不允许的终态，形成业务数据内部不一致。

建议：

所有主表和子表更新都必须校验行数和目标状态；发现子表缺失或状态不匹配时回滚并返回明确错误。

## 4. 其他重要问题

### [P2] HTTP 客户端没有超时配置

`HttpClientConfig.java:10-15` 只配置了 Base URL。审批提交和动作方法在本地数据库事务中调用 `BusinessDataClient`，例如 `ApprovalSubmitService.java:74-75`、`:182-184` 和 `ApprovalActionService.java:98-99`。

远程服务无响应时可能长期占用数据库事务和 Redis 锁。应配置连接、读取和整体调用超时，并考虑熔断或将远程同步改为可补偿任务。

### [P2] 待办和已办接口没有分页

`ApprovalTaskQueryController.java:20-27` 只接收审批人，`ApprovalTaskMapper.java:14-54` 查询指定审批人的全部记录，没有分页参数、最大页大小或游标。

数据量增长后会产生大结果集和较高数据库负载。应增加分页或游标查询。

### [P2] 审批实例详情依赖实时业务服务，没有本地快照降级

`ApprovalInstanceQueryService.java:42-56` 每次查询详情都调用 business-service 获取标题。

业务服务不可用或业务数据发生变化时，审批详情可能失败或展示实时数据，而不是审批时的历史数据。应优先使用本地快照，实时字段需要明确标识并允许降级。

### [P2] 业务输入校验不完整

- `CreatePurchaseApplicationRequest.java:15` 和 `CreateContractApplicationRequest.java:14` 的金额字段缺少 `@NotNull`。
- `PurchaseItemRequest.java:9-10` 的数量和单价缺少 `@NotNull`。
- `BusinessApplicationService.java:72-75` 直接执行 `quantity.multiply(unitPrice)`，空值会变成 500。
- 未校验采购明细金额之和是否等于总金额，也未限制金额精度、币种格式和字符串长度。

应补充字段级和跨字段业务校验，并统一返回 4xx 参数错误。

### [P2] 提交动作在多审批人场景下错误关联单个任务

提交时可以创建多个首节点任务：`ApprovalSubmitService.java:131-142`，但提交动作历史只保存第一个任务 ID：`:157-169`。

这会让提交历史看起来只分配给某一个审批人。提交动作不应随意绑定单个任务，或应建立明确的任务分配记录。

### [P2] Redis 锁没有续租机制

`RedisLockService.java:20-29` 只使用固定租期，审批动作和提交流程使用 30 秒租约。

当数据库或远程 HTTP 调用超过租期时，其他请求可能重新获得 Redis 锁。虽然当前实例版本条件更新可以拦截部分并发，但锁本身仍存在失效窗口，应增加续租或使用具备自动续期能力的锁实现。

## 5. 本期明确接受的风险

跨服务业务状态更新仍不是分布式事务。当前实现保证业务服务明确返回失败时，审批服务本地事务回滚，但不覆盖“远程调用已经成功、审批服务随后本地提交失败”以及网络超时导致的结果不明确场景。

这是本期因开发周期接受的范围限制，不作为本轮阻断项；发布说明中应明确记录，后续可通过幂等补偿任务、对账状态或可靠事件进一步完善。

## 6. 已确认修复、不重复列为当前问题

- 审批服务到业务服务的内部鉴权链路已经接通，`HttpClientConfig` 统一注入 `X-Internal-Token`。
- `approval-service` 和 `notification-service` 的版本化迁移脚本已接入本地启动流程。
- 审批节点完成事件与审批实例最终通过事件已经拆分。
- 事件 payload 已支持 `nextTaskIds` 和 `recipientIds`。
- 通知服务已支持多接收人逐条落库和 `(event_id, receiver_id, channel)` 幂等。
- 通知失败已支持失败记录、重试次数和退避。
- 审批动作已增加 `expectedVersion`、请求摘要和数据库条件更新。
- 缺少流转关系不再直接当作正常通过，显式 `END` 节点已支持。
- 业务数据接口已读取 `application_ext`，返回 `data.formData` 和 `data.remark`；提交及关键审批动作生成的快照会包含申请扩展数据。
