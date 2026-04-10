# Forge 当前状态

## 目的

这份文档是当前仓库的代码与业务状态总览。

后续如果需要了解：

- 当前系统是什么
- 当前流程怎么跑
- 当前代码边界和主链状态
- 当前应该先看哪些文档

都先看这里，不再去多份设计稿里拼“现状”。

## 项目定位

`Forge` 是一个面向研发流程的编程 agent 内核。它的目标不是单轮代码生成，而是把一条完整研发链做成可追踪、可审阅、可打回、可恢复的工作流：

- `ANALYSIS`
- `PRD`
- `DESIGN`
- `IMPLEMENTATION`
- `CODE_REVIEW`
- `TEST`

## 业务现状

### 当前主流程

当前对外仍然是六阶段工作流：

1. `ANALYSIS`
2. `PRD`
3. `DESIGN`
4. `IMPLEMENTATION`
5. `CODE_REVIEW`
6. `TEST`

阶段名没有变，但内部已经不是固定状态机直推。

### 当前流程控制

当前流程由四层共同完成：

- `FlowController`
  - 决定流程下一步怎么走
- 业务门面
  - 例如 `ImplementationExecutor`、`StageReviewer`、`TestExecutor`、`SupervisorAgent`
- 共享内部循环
  - `AgentTurnLoop`
- 确定性 gate / 工具结果
  - 用于约束流程是否可继续推进

### 当前事件日志

`events.log` 现在以中文事件为主，并统一记录关键流转：

- 阶段进入 / 产物生成 / 评审 / 监督决策
- implementation 子任务、文件写入、生成尝试、失败原因
- diagnosis 触发、人工批准、致命失败
- generation / review 成功与失败事件会显式标出：
  - `输入token=估算值/实际值`
  - `输出token`
  - `上下文token(num_ctx)`
  - `预留token`
  - `可用输出token`
  - `请求输出token`
  - `生效输出token`
  - `结束原因(done_reason)`

当前预算规则：

- `reserveTokens = clamp(num_ctx * reserveRatio, minimumReserveTokens, maximumReserveTokens)`
- `availableOutput = num_ctx - promptTokens - reserveTokens`
- `safeOutputCeiling = clamp(availableOutput * safeOutputRatio, minimumOutputTokens, availableOutput)`
- `effectiveOutput = min(requestedOutput, safeOutputCeiling)`
- 文档整稿、implementation planning、precise-html、precise-code、inline patch 这类大生成任务默认按动态预算比例申请输出，不再先写死一个固定小 `num_predict`
- implementation / repair / html patch / review / diagnosis / supervisor / testcase planning 主链已去掉固定 `num_predict` 调用点 cap，`requested/effective` 都由动态预算链统一裁剪

事件格式优先保证两件事：

- 人直接看日志就能跟踪流程
- 关键字段仍然稳定保留，方便后续程序化分析

### 当前 gate 方式

当前关键产物统一按两层处理：

- 确定性 gate
  - schema、结构、coverage、parse、apply、typecheck、test evidence
- 语义 gate
  - reviewer / supervisor 只处理结构化语义

### 当前 reviewer / tester / supervisor 状态

- `Reviewer`
  - 已切到结构化 review 语义主路径
  - 不再依赖 prose regex 猜阻塞语义
  - implementation review 会显式消费 `repair_brief.md` 和 `repair_alignment.md`
- `Tester`
  - 当前以确定性执行层为主
  - `TestToolSelector / TestRunner / TestArtifactRenderer` 已成为主链
  - `runtime snapshot / test execution / test report` 已形成可回注的结构化证据链
- `Supervisor`
  - 已明显收口为升级仲裁层
  - 不再充当常驻总导演

## 代码现状

### 当前整体状态

截至 `2026-04-11`：

- 代码重构阶段：`100%`
- 仓库级单测：已通过
  - `mvn -q clean test`
- 正式黄金路径集成验收：最近一次稳定通过为 `v102`
- `AGENTS` 合规收口主线：continuation、planning 约束、patch 主链入口和 quality rules 严格加载均已通过单元测试；黄金路径集成尚未用这版代码重跑

这意味着底层重构主线已经完成正式业务验收；当前剩余工作主要是把最新这版合规收口结果拿到黄金路径验证。

### 当前编辑主链

文件编辑主链已经明显向 `Codex` 思路对齐：

- `patch-first`
- `tool-result-first`
- `budget-first`
- `compact-first`

当前主链能力：

- 代码文件优先走 patch 主链，而不是 `whole-file`
- provider 调用前会先做上下文压缩与预算裁剪
- patch apply / local verify / test evidence 已收成结构化结果
- 宿主 HTML、内联脚本、内联样式已经进入显式嵌入适配层
- diagnosis / repair 已能直接消费最新测试产物，不再只靠 review 摘要
- `repair-before-regenerate` 已在黄金路径中真实生效：
  - `INVALID_PATCH_JSON` 会先进入 `deterministic-json-repair`
  - 本地修复失败后再进入 `model-json-repair`
  - `EDIT_UNIT_SCOPE_VIOLATION` 会触发单元重切或 focused patch 收窄，而不是直接整轮重生成
- `token budget` 分层预算已在黄金路径中真实生效：
  - 日志会输出 `fixed / retrieved / output-reserve / material-budget`
  - 文档阶段、实现阶段、测试阶段都按分层预算记录 telemetry

当前仍待收口的点：

- implementation artifact / task package / 执行链的 `editScope` 一致性已明显改善，但复杂 companion script 接管场景仍需继续用黄金路径验证
- companion script 接管后的宿主 HTML 正规化已进入主链，但仍要继续盯 `HOST_HTML_PATCH` 与 companion script 的长期一致性
- repair 链已经进入 `JSON + syntax + strict body + validate` 主路径；`precise-html` 宿主宽协议现在只打一枪，repair 后仍失败会直接收窄到 `focused-html-region`
- `precise-code` 当前会在执行前把受限多符号父单元直接拆成 leaf unit，不再让 parent unit 先执行、再靠 scope failure 拆分补救
- `IMPLEMENTATION` 未完成不再经过 fake review/supervisor continuation；当前剩余验证重点已回到真实 patch/repair 行为，而不是阶段流转语义本身
- parser 主链已删除 TSX heuristic symbol fallback；invalid parse 不再产出不稳定符号
- validation / testcase planning 已切到 deterministic primary path；模型失败时不再切另一套 fallback 语义主路径
- quality rules 加载已切成“资源默认规则 + 项目规则覆盖”的严格模式，不再支持运行时临时覆写

### 当前 agent / model 关系

当前代码里要区分三层：

- `FlowController`
  - 决定流程怎么走
- 业务门面
  - 例如 `StageReviewer / SupervisorAgent / TestExecutor / ImplementationPlanner`
- `ModelRole`
  - 表示一次模型调用的任务语义和预算策略

所以现在不是“一 个 agent 对应一个 model role”，而是：

- agent 负责业务门面和编排
- `ModelRole` 负责这次模型调用的语义分类

### 当前已完成的最后一轮收口

最近一轮继续完成的关键收口包括：

- `ValidationStrategyPlanner -> ValidationCapabilityCandidateBuilder / ValidationPlanningPromptBuilder / ValidationPlanSanitizer`
- `GeneratedContentGate -> GeneratedTreeSitterValidator / GeneratedJavaScriptContentValidator / GeneratedHtmlContentValidator`
- `FileEditRuntimeFactory -> PatchRuntimeBuilder / FileRoutingRuntimeBuilder`
- `PlaywrightCaseExecutor -> PlaywrightCaseRunSupport / PlaywrightRuntimeSnapshotSupport / PlaywrightSupport`

当前几个典型门面体量：

- `ValidationStrategyPlanner`: `49` 行
- `GeneratedContentGate`: `116` 行
- `FileEditRuntimeFactory`: `54` 行
- `PlaywrightCaseExecutor`: `26` 行
- `FileEditCoordinator`: `281` 行

## 文档分层

### 当前有效

- `docs/current-state.md`
  - 当前代码与业务状态总览
- `docs/workflow-rules.md`
  - 当前工作流、gate、阶段规则
- `docs/engineering-agreements.md`
  - 当前工程约定与协作约定
- `docs/redesign-roadmap.md`
  - 后续演进路线图
- `docs/editing-strategy.md`
  - 当前编辑策略原则

### 当前参考

- `docs/prompts-reference.md`
  - prompt 参考和模板说明

### 已清理的旧文档

旧的设计稿、阶段性实施表和历史复盘文档已经从仓库删除，不再保留多份并行现状描述。

如果需要补充背景，应该把必要内容直接收进：

- `docs/active-work-items.md`
- `docs/current-state.md`
- `docs/workflow-rules.md`
- `docs/redesign-roadmap.md`
- `docs/editing-strategy.md`

## 下一步

当前执行清单见：

- [active-work-items.md](/home/linus/workspace/forge/docs/active-work-items.md)

当前新主线已经切到质量约束层，正式设计见：

- [quality-rules-architecture.md](/home/linus/workspace/forge/docs/quality-rules-architecture.md)

当前最新状态是：

- continuation/replanning 已切到原生 state 约束，planner 会继承已有文件与 patch progress，不再把已有 `index.html` 错误降级成 `SKELETON/full rewrite`
- `precise-html` 和 `precise-code` 的主入口稳定性已完成单元测试收口
- 主链兼容层与旧入口转发已删除，测试已改成真实 owner 装配
- 仓库级单测已通过；黄金路径集成尚未用这版代码重跑

后续继续推进时，先重跑黄金路径，再根据结果更新 `active-work-items.md` 与本文档。
