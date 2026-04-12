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
  - review artifact 机器协议已收口为 `REVIEW_RESULT` block-only
  - `ReviewResult / REVIEW_RESULT` 已显式携带 `implementationPatchTarget / overrideChanges / revisionRoute / reasonCode`
  - 不再依赖 prose regex 猜阻塞语义，也不再接受 key-value retrofit
  - implementation review 会显式消费 `repair_brief.md` 和 `repair_alignment.md`
  - 如果上一轮 `TEST` 已经给出结构化失败归因，implementation review 现在还会显式消费：
    - `failingCaseIds`
    - `failureCapabilitySurfaces`
    - `requiredCapabilitySurfaces`
  - implementation review 在 reviewer 前会先做一次“上一轮失败目标”的确定性复核；当前失败 case/capability 没有重新通过时，不允许只靠 smoke/self-check 放行
  - implementation gate 已切成 contract-first：
    - 不再因为 `single html / 外提脚本 / embedded dominance` 这类实现形态直接回退 `DESIGN`
    - 只要 `DESIGN` contract 已冻结，`IMPLEMENTATION` 被打回时默认修当前阶段
    - 入口接线、runtime ownership、可启动入口和可见运行表面都由确定性 architect check 驱动
- `Tester`
  - 当前以确定性执行层为主
  - `TestToolSelector / TestRunner / TestArtifactRenderer` 已成为主链
  - `runtime snapshot / test execution / test report` 已形成可回注的结构化证据链
  - `EXPERIENCE_FAILURE_DISPOSITION` 现在会显式记录：
    - `failingCaseIds`
    - `failureCapabilitySurfaces`
    - `requiredCapabilitySurfaces`
  - testcase planner/sanitizer 已增加 capability 级 canonicalization：
    - `PRIMARY_INTERACTION = snapshot -> interaction -> WAIT(policy) -> ASSERT_CHANGED`
    - `TIMED_STATE_PROGRESSION = snapshot -> WAIT(policy) -> ASSERT_CHANGED`
  - 如果 required testcase 仍违反上述 capability 骨架，失败 owner 会先归类成 `TEST_PLAN_DEFECT`，不再误打成 implementation gap
  - `Playwright` probe 已回到单一协议：
    - `--probe` 只返回 `status / probe / errors`
    - testcase 结果专用的 `cases` 字段不再混入 implementation self-check probe 载荷
- `Supervisor`
  - 已明显收口为升级仲裁层
  - 不再充当常驻总导演
  - 文档阶段重复问题现在只允许 `RETRY_STAGE / ROLLBACK_STAGE`，不会再把 `PRD/DESIGN` 误路由到 `IMPLEMENTATION repair`

## 代码现状

### 当前整体状态

截至 `2026-04-12`：

- 代码重构阶段：`100%`
- 仓库级单测：已通过
  - `mvn -q clean test`
  - `mvn -q test`
- 正式黄金路径集成验收：最近一次稳定通过为 `v102`
- `AGENTS` 合规收口主线：continuation、planning 约束、patch 主链入口和 quality rules 严格加载均已通过单元测试；黄金路径集成尚未用这版代码重跑

这意味着底层重构主线已经完成正式业务验收；当前剩余工作主要是把最新这版合规收口结果拿到黄金路径验证。

### 最近一次收口评审结论

最近一次代码改动完成后，已经按约定执行了 `self-test + code review`。这轮 review 的结论是：

- `TEST` 阶段评审不再只看 `REVIEW_RESULT`，还会显式消费 `QUALITY_LEDGER`；必需能力覆盖缺失时不能再被误判为通过
- `uiRuntimeContract` 无效时，测试执行链不再短路成空结果，而会稳定产出 `TEST_CASE_EXECUTION` 的 blocked/skipped 证据
- 测试侧与主链已经切到新的 runtime contract API，不再保留 `parseTestArtifact`、旧版 `renderExecution(...)`、旧版 `sanitize(...)` 这类旧入口双轨
- 本轮 review 中发现的新增坏味道只有一个：`observation-contract-invalid` 曾以裸字符串存在；已在主代码中收成常量并复测通过

当前结论：

- 本轮代码收口在“方案执行 + 自测 + code review”三个维度上已经完成
- 剩余未完成事项是黄金路径集成验证，而不是单测主链本身

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
- `precise-code / inline-script-workset / inline-style-workset` 主链已经统一成 `StructuredDiffPatch`：
  - `expectedSourceHash`
  - `hunks[].sourceStartLine / beforeLines / afterLines`
  - 旧的 `operations / REPLACE_SYMBOL* / APPEND_FILE` 协议已从主代码路径删除
- 宿主 HTML、内联脚本、内联样式已经进入显式嵌入适配层
- `inline-script-workset` 已收紧为“多稳定符号批次”专用骨架；单入口 bootstrap 脚本直接走 `focused script region`，失配旧 `INLINE_SCRIPT_WORKSET` progress 会被丢弃而不是强行续跑
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
- implementation state snapshot 已持久化 `architectImplementationPatchTarget / reviewImplementationPatchTarget`；completed-plan PATCH continuation 现在只消费结构化 target，不再根据 `architectFailureReason` 猜修复语义
- implementation 未完成时，如果最新失败子任务已经给出结构化 `continuationPatchTarget / changeRequest / evidence`，这些字段现在会直接提升到 stage artifact 和 continuation 主链，不再退回成“继续完成未完成子任务”的泛化 prose
- runtime wiring retry 已改为 `SubtaskRevisionDirective` 结构化 override 驱动，失败后直接续跑当前子任务，不再靠 prose change request 猜下一轮 HTML scope
- `PATCH_EXISTING_IMPLEMENTATION` 已统一成结构化 `overrideChanges` 协议；review、revision note、repair note 和 completed-plan continuation 现在都消费同一份文件级 patch scope，不再允许空 scope 静默 replanning
- runtime ownership / wiring 检查只认宿主显式 `<script src>` 接线和 inline module import；`index.app.js` 默认 companion 路径、basename 猜测与 orphan root ownership 推断已从主链删除
- runtime wiring 检查不再把 JS 里引用的 `.class/#id` 与静态 HTML 宿主做逐字比对；动态创建节点属于运行时行为，不再被误判成接线失败
- html-entry planning contract 已显式化：`editScope / runtimeOwnership / hostHtmlPatchRequired` 必须成组声明，宿主 HTML 不再允许含混 `AUTO` scope
- testcase runtime contract 已改成按 capability surface 独立解析：
  - `primary-visual-surface`
  - `primary-interaction`
  - `timed-state-progression`
  不再把一个主选择器扇出给所有 surface
- `run-state-entry` 已收紧为“显式可启动控件”语义：
  - 只有 runtime probe 明确给出的 control candidate 才能保留 `run-state-entry`
  - `body/main` 这类宿主根节点不再被接受为启动入口
  - 不合法的 `run-state-entry` 现在会被降级为 `primary-control` / `primary-surface` 或直接删除
- testcase planner / sanitizer 已删除“从静态 HTML 猜交互控件”的旧行为：
  - 没有 runtime control candidate 时，不再从 `<button>`、id/class 或 selector 文本反推点击目标
  - 也不再补空的 `PRESS_KEY` 步骤
- implementation self-check 已切到 `tool-result-first`：
  - `ValidationExecutionReport.toolResults` 会贯通到 subtask attempt、implementation artifact 和 state snapshot
  - `PLAYWRIGHT_PROBE_PAYLOAD_INVALID / PLAYWRIGHT_PROBE_EXECUTION_FAILED` 会在 implementation 阶段直接标成 `REQUEST_HUMAN`
  - implementation continuation 已显式分成 `CONTINUE_SUBTASKS / BLOCK_STAGE`
  - `BLOCK_STAGE` 不再走 fake review/supervisor continuation，而是直接阻断到人工
- `QualityPlan` 现在会在浏览器 runtime snapshot 缺席时吸收静态 HTML 结构信号；`canvas/button` 这类宿主事实不会再在 implementation review 里被漏判成普通静态页
- canonical stage artifact 已不再混入 `Current Notes / Revision Summary` 之类瞬时 prose：
  - 阶段主产物只保留 canonical 内容
  - 阶段 directive 现在单独落到 `*_directive.md`
  - 历史旧稿中的 `Current Notes` 会在文档后处理阶段被清理掉，不再继续留在主文档
- parser 主链已删除 TSX heuristic symbol fallback；invalid parse 不再产出不稳定符号
- validation / testcase planning 已切到 deterministic primary path；模型失败时不再切另一套 fallback 语义主路径
- quality rules 加载已切成“资源默认规则 + 项目规则覆盖”的严格模式，不再支持运行时临时覆写
- structure risk 现在只保留为提示信息：
  - 不再把“是否外提主逻辑”作为 implementation/completeness 的阻断条件
  - 真正阻断只来自 contract、一致性检查和可运行性验证
- `PRD/DESIGN` 的 `Contract Metadata` 章节现在会同时持久化 `runtime.*` 与 `validation.*`，validation authority 不再在 post-process 阶段丢失
- `PRD` 的 `4.1 性能 / 5.2 质量验收` 已接入本地 authority canonicalization：无来源数值阈值会被删除；有 `hard.* / validation.*` 支撑的条目会被规范化保留
- `PRD` 主链现在会把固定章节确定性投影成 `PRODUCT_CONTRACT`：
  - `1-6` 号章节会被本地投影成显式 `requirementReferences`
  - 投影规则是“列表优先，段落兜底”，不再要求所有合法 PRD 都写成 bullet list
  - 下游主链只再消费 `PRODUCT_CONTRACT` block，不再从 PRD 正文回退抽取产品覆盖引用
- contract 抽取主链已改成无损且 block-first：
  - 关键 contract 列表不再按固定条数截断
  - `3.1 / 3.2` 之类分段能力不会再因为前几条已满被挤掉
- `PRD` review 现在会显式校验 `PRODUCT_CONTRACT`：
  - 缺块、空 `requirementReferences`、以及 block 与固定章节投影不一致，都会在文档阶段直接打回
- projected context / task memory 已改成 authority 与 trace 分层：
  - `upstreamContractSummary` 仍保留为可压缩 trace 信息
  - 下游真正消费的 durable contract 信息已切成 `authoritative coverage catalog`
  - 权威 coverage catalog 不再走 `contractView.toMarkdown() -> summarizeMarkdown(...)` 这条缩水链
- implementation / testcase planning / coverage gate 现在共享同一份 `AuthoritativeCoverageCatalog`：
  - 产品覆盖引用继续走 `CAP-* / ACC-*`
  - 纯质量能力继续走 `QCAP-*`
  - 若质量能力与产品 `CAP-*` 指向同一能力，只保留产品引用，不再向 planner 暴露 `CAP-1` 与 `QCAP-CAP_1` 两套重复锚点
- `QualityPlan` 不再吸收 product requirement refs：
  - product coverage 与 quality capability 各自保持独立来源
  - planner / gate / testcase 的统一覆盖约束改由 `AuthoritativeCoverageCatalog` 汇总

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
- completed-plan PATCH continuation 已改为显式读取 state snapshot 里的 patch target；runtime wiring / host externalization 不再依赖旧的 failureReason 推断
- `precise-html` 和 `precise-code` 的主入口稳定性已完成单元测试收口
- 主链兼容层与旧入口转发已删除，测试已改成真实 owner 装配
- 仓库级单测已通过；黄金路径集成尚未用这版代码重跑

后续继续推进时，先重跑黄金路径，再根据结果更新 `active-work-items.md` 与本文档。
