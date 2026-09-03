# FluxCore 代码评审记录

## 1. 评审范围与结论

本次评审以需求说明书图片作为业务需求输入，以 `docs/ARCHITECTURE.md`、`docs/TECHNICAL_DESIGN.md`、`docs/TECHNICAL_SOLUTION.md`、`docs/MODEL_HANDOFF.md` 和当前工作区代码为对照依据。图片及已有文档中的“后续执行规则”均未被当作需要执行的代码指令。

评审只写入本文档，没有修改业务代码。

总体结论：当前代码已经具备采购/合同申请、提交首节点、串行推进、通过/驳回/撤回、待办/历史查询、转审/加签和 Outbox 基础能力，能够作为原型运行；真实环境中的基础串行链路可行，但还不能认定为满足需求的可交付闭环。主要风险集中在事件通知语义、跨服务一致性、幂等与乐观锁、并行/条件流程缺失，以及网关鉴权缺失。

## 2. 已执行验证

- 根工程 `mvn -q -DskipTests package`：通过，退出码 0。
- 已生成测试报告的 11 个测试类共 50 个测试：失败 0、错误 0、跳过 0。
- 真实环境健康检查：MySQL、Redis、RabbitMQ，以及 business `8082`、approval `8081`、notification `8084`、gateway `8080` 均可连接/启动。
- 真实 HTTP 已通过采购三级串行（实例 8）、合同两级串行（实例 9）、驳回（实例 10）、撤回（实例 11）、转审（实例 12）、加签（实例 13）、幂等重放和并发审批（实例 17）。
- 真实 MySQL/RabbitMQ 核验显示 Outbox 事件可发布并被消费；停止 notification-service 后，实例 18 的提交事件在队列中等待，服务恢复后队列清空并落库。
- 真实测试同时确认了通知接收人错误、网关业务路由缺失、未认证 internal 接口可改业务状态，以及缺少数量的采购请求返回 500 等问题；这些问题不是测试环境假象。

## 3. 问题清单

严重级别：P0 为阻断性数据/安全问题，P1 为上线前必须处理的问题，P2 为重要缺陷或扩展性问题。

### [P1] 审批事件类型和通知接收人不正确，无法覆盖下一节点及多审批人

证据：

- `approval-service/src/main/java/com/fluxcore/approval/service/ApprovalActionService.java:236-243` 在当前节点完成时统一发送 `APPROVAL_APPROVED`，即使仍然创建了下一节点；因此“节点通过”和“审批实例最终通过”被混用了。
- 同一段 `ApproveEvent` 只携带当前任务、当前操作人、实例状态、下一节点 ID 和一个 `nextTaskId`，没有下一节点的全部审批人。
- `notification-service/src/main/java/com/fluxcore/notification/service/ApprovalNotificationService.java:68-76` 依次从事件中取 `receiverId/assigneeId/applicantId/operatorId`。审批通过事件没有前面三个字段时，会把当前操作人当成接收人，而不是下一审批人或申请人。
- `notification-service/src/main/resources/db/schema.sql:3-12` 对 `event_id` 建唯一约束，一条事件只能落一条通知记录，不能表达多接收人或多渠道。

影响：采购一级通过后可能产生“审批已完成”类事件并通知错误的人；会签/或签或转审场景无法可靠通知所有待办人。

真实证据：实例 17 当前节点已推进到下一节点，U2002 存在待办，但 `approval_outbox_event` 的 `APPROVAL_APPROVED` payload 只有 `operatorId=U2001` 和 `nextTaskId=35`；对应 `notification_record` 的收件人仍为 U2001，而不是下一节点审批人 U2002。

建议：拆分节点完成事件与实例终态事件；统一定义事件接收人列表和通知用途；通知记录唯一键改为事件 + 接收人 + 渠道，或建立事件接收人明细表。

### [P1] Outbox 标记已发布的条件不足，存在消息丢失窗口

证据：

- `approval-service/src/main/java/com/fluxcore/approval/service/ApprovalOutboxPublisher.java:45-60` 在 `RabbitTemplate.convertAndSend` 返回后立即执行 `markPublished`。
- `approval-service/src/main/java/com/fluxcore/approval/config/RabbitTopologyConfig.java:9-14` 只声明 Exchange；Queue 和 Binding 在 notification-service 中声明，通知服务未启动或队列尚未创建时消息可能不可路由。
- 当前没有 publisher confirm、mandatory return 或发送后的 broker 确认处理。

影响：发送调用没有同步抛错时，事件可能已被 Broker 丢弃，但 Outbox 已被标记为 `PUBLISHED`，后续不会重试；这不满足“通知失败不回滚主流程且可靠重试”的目标。

真实证据：停止 notification-service 后提交实例 18，Outbox 很快为 `PUBLISHED`，RabbitMQ 队列随后显示 `messages_ready=1`；本次因队列已预先存在，恢复服务后消息最终消费。该结果验证了已有队列的恢复路径，但没有证明无队列/无路由时 `PUBLISHED` 标记安全，仍保留消息丢失风险。

建议：启用 publisher confirms 和 returns，只有收到可接受的 Broker 确认后才标记 `PUBLISHED`；同时处理无路由消息，保留失败原因和退避时间。

### [P1] 跨服务业务状态没有补偿协议，审批库和业务库可能分叉

证据：

- `approval-service/src/main/java/com/fluxcore/approval/service/ApprovalSubmitService.java:167-178` 先写审批本地数据，再通过 HTTP 调用 `markSubmitted`。
- `ApprovalActionService.java` 的终态动作同样在本地状态、快照和 Outbox 操作中调用 `markRejected/markApproved/markWithdrawn`，例如 `:518-529`、`:622-633`。
- `@Transactional` 只覆盖审批服务本地数据库，不能覆盖 business-service 的 HTTP 调用。

影响：远程调用成功而本地事务随后提交失败时，业务单据已经变更但审批实例不存在或状态未落库；远程调用失败时，本地回滚也不能自动处理远程已完成的情况。

建议：为业务状态变更增加带幂等键的补偿任务/对账状态，或通过可靠事件异步更新业务状态；记录“审批状态已完成但业务状态待同步”，由重试器处理，不能只依赖一次 HTTP 调用。

### [P1] `lock_version` 没有覆盖所有审批动作

证据：

- `approval-service/src/main/java/com/fluxcore/approval/service/ApprovalActionService.java:303-343` 的转审和 `:400-435` 的加签没有调用实例版本条件更新。
- 通过动作在 `:185-187` 的未完成节点分支也不更新实例版本；该分支会在 `SINGLE` 节点加签后出现。
- 版本更新 SQL 仅定义在 `approval-service/src/main/java/com/fluxcore/approval/mapper/ApprovalInstanceMapper.java:24-37`。

影响：动作历史已经发生，但审批实例版本不变，客户端无法用版本判断这些动作是否改变了实例；在 Redis 锁失效、重启或多实例竞争时，数据库层缺少完整的最终并发保护。

建议：所有改变审批聚合的动作统一使用“读取版本 + 条件更新 + 版本加一”；动作请求携带并校验 `expectedVersion`，版本冲突时不得继续写任务、快照或事件。

### [P1] 动作幂等只校验动作类型和操作人，没有校验完整请求

证据：

- `ApprovalActionService.java:105-111`、`:264-271`、`:361-368`、`:452-458`、`:545-552` 对重复 `actionRequestId` 只校验动作类型和 `operatorId`。
- 没有比较 `taskId`、转审目标、加签目标、备注等请求字段；`ApprovalActionRequest.java:5-8` 也没有文档中建议的 `expectedVersion`。

影响：同一操作人可使用相同请求 ID 请求另一个任务或另一个转审/加签目标，接口仍返回第一次动作结果，调用方无法发现请求语义被复用。

建议：把动作请求规范化后保存请求摘要，重复请求必须校验任务、目标人、版本等关键字段；摘要不同返回 `409 ACTION_REQUEST_ID_REUSED`。

### [P1] 提交幂等键的数据库约束与代码语义不一致

证据：

- `ApprovalSubmitService.java:81-84` 通过 `findBySubmitRequestId` 将 `submitRequestId` 当作全局唯一语义使用。
- `approval-service/src/main/resources/db/schema.sql:63-65` 实际唯一约束是 `(application_id, submit_request_id)`，不是全局唯一。
- 提交锁 `ApprovalSubmitService.java:75-76` 只按业务类型和业务单据加锁，不能保护两个不同业务单据之间的相同提交键。

影响：两个不同申请并发使用相同 `submitRequestId` 时，数据库允许创建两个实例；之后全局查询可能返回不确定的其中一条，破坏“相同键复用原结果/冲突”的语义。

建议：明确提交键是全局唯一还是申请内唯一，并让锁、查询、数据库唯一索引、错误码保持同一作用域。若按当前代码的全局语义，应增加全局唯一索引或独立的幂等约束方案。

### [P1] 需求要求的并行和条件分支尚未实现

证据：

- 需求对照文档将流程能力定义为“串行、并行、条件分支”：`docs/TECHNICAL_SOLUTION.md:18-24`。
- `ApprovalActionService.java:725-731` 只支持 `SINGLE` 和 `OR`，`AND` 会被拒绝。
- `ApprovalTransitionMapper.java:12-16` 只查询 `condition_json IS NULL` 的默认连线，没有条件求值；运行时以单个 `current_node_id` 推进，不能表达多个 ACTIVE 节点和汇聚判断。

影响：当前不能完成需求建议的合同并行场景，也不能按业务数据选择条件分支；流程表的预留字段尚未形成可用能力。

建议：先补齐 `AND`/会签，再增加条件表达式白名单求值、并行节点实例集合和汇聚规则；状态机和实例查询不能继续假设只有一个当前节点。

### [P1] 缺少流转关系时会被当成正常结束，且显式 END 节点当前不可用

证据：

- `ApprovalActionService.java:144-149` 在找不到默认流转时把 `nextNode` 置为 `null`，没有区分“当前节点确实是末节点”和“流程配置缺少连线”。
- `ApprovalActionService.java:211-220` 因此会直接把审批实例改为 `APPROVED`。
- 同时 `:147-148` 拒绝 `nodeType != APPROVAL` 的下一节点，因此数据库设计中预留的 `END` 节点无法作为显式终点使用。

影响：流程配置遗漏一条 transition 时，审批可能跳过后续节点直接通过；配置采用设计文档中的 `APPROVAL -> END` 形式时，又会被报为下一节点不可执行。

建议：流程发布/加载时校验图结构；只有到达合法 END 节点才结束实例，缺少连线应返回配置错误，不能默认为审批通过。

### [P1] 网关路由和鉴权仍是骨架，内部接口可被直接调用

证据：

- `gateway-service/src/main/java/com/fluxcore/gateway/GatewayHealthController.java:7-12` 只有 ping 接口，没有业务路由和认证过滤器。
- `business-service/src/main/java/com/fluxcore/business/controller/InternalApplicationController.java:9-35` 直接暴露提交、驳回、通过、撤回等内部状态变更接口。
- 审批动作从请求体信任 `operatorId`，例如 `ApprovalActionRequest.java:5-8`；当前没有从认证主体获取操作人并进行权限校验的实现。

影响：绕过网关即可直接修改业务申请状态，或伪造审批人执行动作。该问题在真实部署中属于越权和数据完整性风险。

真实证据：网关对 `/api/business/ping`、`/api/approvals` 等业务路径均返回 404，说明当前没有业务路由；对刚提交的申请直接无认证调用 `POST /api/internal/applications/22/approve` 返回 200，并使业务状态变为 `APPROVED`，但对应审批实例 19 仍为 `IN_PROGRESS`。

建议：内部接口只允许服务间身份访问；网关完成路由、认证和统一错误处理；审批人、申请人从可信认证上下文取得，不能由客户端自由声明。

### [P1] 通知失败没有独立的失败记录和退避机制

证据：

- `ApprovalEventListener.java:15-18` 直接调用通知服务。
- `ApprovalNotificationService.java:49-52` 对异常重新抛出；`notification-service/src/main/resources/application.yml:8-12` 配置为自动确认并默认重新入队。
- `NotificationRecordMapper` 虽定义了 `markFailed`，但当前通知服务没有调用它，也没有基于 `retry_count` 的调度重试。

影响：格式错误或永久失败的消息会被无限重新投递，没有可观测的失败状态和退避，可能持续占用消费者并掩盖后续消息。

建议：区分可重试/不可重试异常；落库 `FAILED`、次数和下次重试时间，采用独立重试任务或死信队列；消息处理成功后再确认。

### [P2] 审批快照没有包含申请扩展数据，不符合“完整业务数据快照”

证据：

- `BusinessApplicationService.java:210-212` 将请求保存到 `application_ext.form_data`。
- `BusinessApplicationService.java:103-116` 组装业务数据时没有读取 `ApplicationExtMapper`，返回的 `BusinessDataResponse` 只包含主表和采购/合同字段。
- 提交和动作快照直接序列化该响应，例如 `ApprovalSubmitService.java:141-150` 和 `ApprovalActionService.java:172-181`。

影响：申请备注和通用表单扩展字段不会出现在审批历史快照中，后续无法还原审批人实际看到的完整申请。

建议：内部业务数据接口明确快照契约，包含 `application_ext.form_data/remark`；按版本测试快照内容和哈希稳定性。

### [P2] 待办/已办接口没有分页，查询会随数据量线性膨胀

证据：

- `ApprovalTaskQueryController.java:20-28` 只接收 `assigneeId`。
- `ApprovalTaskMapper.java:13-54` 返回指定审批人的全部待办或全部非待办记录，没有 `LIMIT/OFFSET` 或分页对象。
- 设计文档已经给出了 `page/pageSize` 形式的接口建议：`docs/TECHNICAL_SOLUTION.md:244-251`。

影响：待办中心数据增长后会产生大结果集、长事务和较高数据库负载。

建议：增加页码/游标分页、最大页大小和总数/下一页信息；为已办查询补充按时间/状态的索引策略。

### [P2] 审批实例详情依赖实时业务服务，没有使用本地快照兜底

证据：

- `approval-service/src/main/java/com/fluxcore/approval/service/ApprovalInstanceQueryService.java:42-56` 每次查询实例详情都调用 business-service，并使用实时返回的标题。
- 该响应没有返回最新快照编号、类型和摘要，且 business-service 不可用时，即使审批本地数据完整，详情也无法正常返回。

影响：历史/终态审批的展示结果可能受当前业务数据变化影响；跨服务故障会降低审批查询可用性。

建议：实例详情的审批事实和标题优先读取本地快照；如需展示实时业务信息，应明确标识为实时字段并允许降级。

### [P2] 申请输入校验不完整，部分非法请求会变成 500 或数据库错误

证据：

- `CreatePurchaseApplicationRequest.java:15-17`、`CreateContractApplicationRequest.java:14-17` 对金额使用 `@DecimalMin` 但没有 `@NotNull`。
- `BusinessApplicationService.java:72-75` 对采购明细直接调用 `quantity.multiply(unitPrice)`；金额为空时会触发运行时异常。
- 当前没有校验采购明细金额之和与 `totalAmount` 一致，也没有统一校验币种格式和金额小数位。

建议：增加非空、精度、范围和跨字段业务校验，并将参数错误映射为明确的 4xx 错误，不让其落成 500。

真实证据：采购创建请求省略明细 `quantity` 时返回 HTTP 500，验证了金额/数量非空校验缺失会暴露为服务器错误。

## 4. 交付前建议验收顺序

1. 先修事件类型/接收人契约、Outbox confirm、通知失败重试和内部接口鉴权。
2. 统一提交/动作幂等作用域，补齐 `expectedVersion` 和所有动作的 `lock_version` 更新。
3. 增加业务状态补偿与对账，验证远程调用成功/失败、本地提交失败等故障组合。
4. 实现并行、条件和会签汇聚，再进行采购串行与合同并行端到端验收。
5. 用真实 MySQL、Redis、RabbitMQ 做双请求并发、锁过期、Broker 不可用、无消费者和重复投递测试。
6. 最后补分页、完整快照字段、输入校验、Swagger 示例和干净环境启动验证。
