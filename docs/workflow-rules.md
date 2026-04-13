# 工作流规则

> 说明：这份文档只保留当前有效的运行规则。当前代码与业务状态请优先看 `docs/current-state.md`，文档入口请看 `docs/README.md`。

## 目的

这份文档回答 4 件事：

1. 当前流程怎么走
2. 每个阶段产出什么、通过什么
3. 失败时该走 `PATCH`、`REWORK`、`REPAIR` 还是人工介入
4. `IMPLEMENTATION / CODE_REVIEW / TEST` 的职责边界是什么

它不是架构设计稿，也不是类清单。

## 总体流程

当前主流程固定为 6 个阶段：

1. `ANALYSIS`
2. `PRD`
3. `DESIGN`
4. `IMPLEMENTATION`
5. `CODE_REVIEW`
6. `TEST`

流程推进方式不是“硬编码状态机直推”，而是四层协作：

- `FlowController`
  - 决定流程下一步怎么走
- `WorkflowEngine`
  - 负责执行、约束、落盘
- 阶段门面
  - 负责具体产物生成、验证和 review
- gate / tool result
  - 负责提供确定性约束和结构化证据

## 阶段规则

### `ANALYSIS`

职责：

- 明确目标
- 明确约束
- 明确环境与范围
- 识别主要风险与不确定点

通过标准：

- 目标、约束、上下文完整
- 没有明显遗漏的前置条件
- 没有无来源的实现结论

### `PRD`

职责：

- 把分析结果收成需求与能力清单
- 区分绑定要求与可选项
- 形成后续 contract 输入

通过标准：

- 能力定义完整
- 必选与可选边界清楚
- 没有把“建议/可选”错误提升成硬要求

### `DESIGN`

职责：

- 形成实现方案
- 明确交付形态、入口、运行表面、关键约束
- 为 implementation 提供 contract 与 delivery policy 输入

通过标准：

- 设计能支持后续实现拆分
- 关键运行约束明确
- 不引入无来源量化指标或硬实现限定

### `IMPLEMENTATION`

职责：

- 先规划，再按子任务执行
- 按 `contract + delivery policy + working set` 推进
- 输出结构化执行状态与进度

通过标准：

- plan 已完成
- 当前子任务满足自己的 `acceptanceCriteria`
- 整体交付满足阶段级 runnable / architect 检查
- 不是骨架冒充完成，不是 placeholder 冒充完成

### `CODE_REVIEW`

职责：

- 判断实现是否满足当前阶段要求
- 输出结构化 review 语义
- 决定通过、修订、重做、升级
- 如果存在 repair brief / repair alignment，要把它们作为当前轮修复契约显式核对

通过标准：

- 当前阶段要求已满足
- 阻塞性缺口已被识别
- review 结论有结构化依据

### `TEST`

职责：

- 选择合适测试工具
- 执行 testcase 与自检验证
- 汇总结构化测试证据
- 为 diagnosis / repair 输出可直接回注的运行时与测试证据

通过标准：

- required case 不得 `FAILED`
- required case 不得 `BLOCKED`
- 证据足以支持通过结论

## Gate 模型

当前 gate 分 3 层：

### 1. 确定性 gate

用于检查：

- schema / 结构是否合法
- contract coverage 是否完整
- patch / apply / parse / typecheck / test 是否通过
- 证据是否存在

这层不负责猜语义。

补充规则：

- implementation 自检失败时，必须优先消费结构化 `toolResults`
- 工具/协议阻塞不得降级成 reviewer prose 猜测
- `Playwright probe payload invalid / execution failed` 这类 deterministic tooling failure 必须直接走 `REQUEST_HUMAN`

### 可观测性要求

- `events.log` 必须使用中文稳定模板。
- generation / review 的成功与失败事件必须显式写出：
  - `输入token=估算值/实际值`
  - `输出token`
  - `上下文token`
  - `预留token`
  - `可用输出token`
  - `请求输出token`
  - `生效输出token`
  - `结束原因`
- 不允许再靠猜测判断“这次是输入过大还是输出被截断”。
- 大生成任务必须走动态预算：
  - `reserveTokens = clamp(num_ctx * reserveRatio, minimumReserveTokens, maximumReserveTokens)`
- `availableOutput = num_ctx - promptTokens - reserveTokens`
- `safeOutputCeiling = clamp(availableOutput * safeOutputRatio, minimumOutputTokens, availableOutput)`
- `effectiveOutput = min(requestedOutput, safeOutputCeiling)`
- 不允许在实现主链里再写一组绕过预算链的固定小输出上限。

### 2. 语义 gate

由 reviewer / supervisor 消费结构化输入后判断：

- 是否满足阶段目标
- 是否仍有阻塞项
- 是否需要修订或升级

这层不再靠 prose / regex 猜自然语言意图。

### 3. 流程 gate

由 `FlowController` 最终决定：

- 继续下一阶段
- 重试当前阶段
- 回到上游阶段
- 路由到 `diagnosis / repair`
- 请求人工介入
- 结束 run

## 失败路由规则

### `PATCH`

用于：

- 当前阶段方向是对的
- 只是局部实现、局部证据、局部结构还不够
- 不需要重做整轮方案

特点：

- 优先复用已有计划和已完成部分
- 优先局部编辑，不默认整文件重来

### `REWORK`

用于：

- 当前阶段方案本身不成立
- 当前计划结构错误
- 当前设计或实现路径需要重做

特点：

- 允许重做当前阶段主要产物
- 不是局部补丁

### `ROUTE_TO_REPAIR`

用于：

- 连续失败但方向仍不清楚
- 需要先明确根因、优先级和禁止方向

特点：

- repair 产物会反向约束 implementation / review
- diagnosis 会直接读取最新测试产物与 review evidence，而不是只看摘要

### `REQUEST_HUMAN`

用于：

- 高不确定性
- 决策冲突
- 缺关键外部信息
- 自动流程不应继续推进

## Implementation 专项规则

### 1. 先 plan，再执行

`IMPLEMENTATION` 不是直接写代码，而是：

1. 形成 implementation plan
2. 按子任务顺序推进
3. 每个子任务先自检，再 verifier，再阶段 gate

### 2. 子任务按责任域通过

每个子任务必须显式区分：

- `ownedCapabilities`
- `deferredCapabilities`
- `acceptanceCriteria`

通过规则：

- 只按当前责任域判断是否完成
- 留给后续子任务的能力不能提前阻塞当前子任务
- 但最终阶段仍要做整体检查

补充规则：

- 子任务自检失败后，先走确定性 self-check failure routing，再决定是否进入 LLM reviewer
- 如果失败属于工具链阻塞，当前子任务必须停止自动重试，不能从头续跑 implementation attempt
- implementation stage artifact 必须显式声明 continuation mode：
  - `CONTINUE_SUBTASKS`
  - `BLOCK_STAGE`
- `BLOCK_STAGE` 表示当前阶段只能人工介入，不能再伪装成“还有未完成子任务，继续跑下一轮”

### 3. 默认局部编辑，不默认整文件重写

当前编辑主链默认：

- `patch-first`
- `tool-result-first`
- `budget-first`

规则：

- 代码文件默认优先走局部 patch
- `PATCH / INCREMENTAL` 下不允许静默退回 `whole-file`
- 截断时优先拆小单元，不继续重试同一个过大单元

### 4. Implementation 通过条件

进入 `CODE_REVIEW` 前，至少要满足：

- plan 已完成
- 当前阶段要求已实现
- architect / runnable 检查通过
- 没有阻塞性 placeholder / 空实现 / 假完成

## Review 专项规则

### 1. review 不猜 prose 语义

review 高层结论只来自结构化字段，不再靠：

- 中文关键词
- 英文关键词
- prose bullet
- regex 猜测

### 2. review 不替代 gate

review 负责语义判断，不负责替代：

- schema 检查
- patch apply 检查
- parse / test / evidence 检查

### 3. review 不能假通过

只要还有阻塞性缺口，就不能 `APPROVED`。

## Test 专项规则

### 1. Tester 以确定性执行层为主

当前测试主链优先是：

- `TestToolSelector`
- `TestRunner`
- `TestArtifactRenderer`

不是让测试阶段自由决定“这次怎么测”。

### 2. 测试证据必须结构化

测试输出至少要支持：

- testcase 结果
- self-check / validation 结果
- runtime snapshot
- tool results

### 3. required case 是硬 gate

只要 required case：

- `FAILED`
- `BLOCKED`

测试阶段都不能通过。

## Artifact 规则

当前运行时至少要有这些核心产物：

- `run.json`
- `events.log`
- `<stage>.md`
- `<stage>_review.md`
- `<stage>_review_history.md`

`IMPLEMENTATION` 额外维护：

- `implementation.md`
- `worker_results.md`
- `implementation_progress.md`
- `implementation_state.json`

这些实现产物必须从统一运行时快照派生，不允许各写各的真相。

补充约束：

- `implementation_state.json` 是 implementation live control flow 的唯一来源
- `stage progress`、`implementation review intake`、`code review intake` 只能读取 `implementation_state.json` 恢复 live 状态
- `implementation_stage_status.md`、`worker_results.md`、`implementation_diagnostics.md` 只允许作为派生展示物，不允许再参与推进判定

## 文档同步规则

只要代码行为发生变化，必须同步更新：

- `README.md`
- `docs/current-state.md`
- `docs/workflow-rules.md`
- `docs/redesign-roadmap.md`

如果是编辑主链或工程协作规则变更，还要同步：

- `docs/editing-strategy.md`
- `docs/engineering-agreements.md`

## 下一步

这份文档只维护当前规则。

如果要看：

- 当前系统现状：`docs/current-state.md`
- 当前工程约定：`docs/engineering-agreements.md`
- 后续演进方向：`docs/redesign-roadmap.md`
