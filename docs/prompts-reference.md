# Prompt 参考

## 目的

这份文档整理 `Forge` 当前代码里各阶段实际使用的 prompt。

目标是方便：

- 查阅各阶段 agent 的职责和输出约束
- 对照当前实现调整 prompt
- 在不翻源码的情况下理解工作流行为

说明：

- 这里记录的是**当前代码中的 prompt 结构和关键约束**
- 不是理想设计稿
- 后续如果源码 prompt 变化，这份文档也要同步更新
- 文档整稿、implementation planning、precise-html、precise-code、inline patch 这类大生成任务现在默认按动态预算比例申请输出；这里如果提到预算，应优先理解成 `outputBudgetRatio + OutputBudgetCalculator` 的动态裁剪结果

## Prompt 分层

当前 prompt 主要分布在这些模块：

- `StageArtifactComposer`
  - `ANALYSIS / PRD / DESIGN / CODE_REVIEW`
- `ImplementationExecutor`
  - implementation plan
  - subtask file generation
  - subtask verifier
  - JSON repair
- `ValidationStrategyPlanner`
  - self-check 策略规划
- `TestCasePlanner`
  - testcase 设计
  - 若 `DESIGN` 定义了性能验收要求，还可生成 `MEASURE_PAGE_LOAD_MAX_MS` 和 `ASSERT_WINDOW_METRIC_MAX_MS`
- `OllamaLlmProvider.review`
  - 通用 JSON review 包装器
- `StageReviewer`
  - 各阶段使用什么 reviewer prompt
- `SupervisorAgent`
  - 主流程决策 prompt

## `ANALYSIS`

来源：

- [StageArtifactComposer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/artifact/StageArtifactComposer.java)

system prompt：

```text
你是一个资深需求分析师。请输出结构清晰、内容完整、面向研发落地的 Markdown 文档。
不要输出多余前言，不要解释你自己。
```

user prompt 结构：

- 目标
- 约束
- 当前备注
- 上一轮草稿（修订时会带入）

输出要求：

- 标题必须是 `# 需求分析与调研`
- 必须严格按模板输出，保留章节编号和标题
- 模板主结构固定为：
  - `1. 背景与问题定义`
  - `2. 目标与成功标准`
  - `3. 关键约束`
  - `4. 初步调研与假设`
  - `5. 边界与非目标`
  - `6. 风险与待确认问题`
  - `7. 当前备注`
- 不允许留 `[TODO]`
- “明确不做”和“待确认问题”必须写出
- 如果当前备注包含修订意见，优先保留上一轮已经合格的章节
- 当备注中明确指出缺失章节时，系统会要求模型只输出缺失章节，再由程序合并回旧稿
- 即使备注是逻辑修订意见而不是缺章节，系统仍会按顶层章节把新草稿合并回旧稿，避免已有章节被覆盖丢失
- 首次完整生成与补缺失章节/修订章节都走同一个文档模型角色；差异通过 prompt 中的“完整生成 / 只补缺失章节 / 合并旧稿”模式体现
- reviewer 还会检查每个必需章节是否有正文；只有标题没有内容也会被判为缺失章节
- 若约束只有“可直接打开运行”“无需编译或打包”这类表述，不允许自行推断成“所有代码必须内联到单个 HTML 文件”；默认允许本地相对路径的 `js/css/image` 资源

文档 agent mode：

- `FULL_DRAFT`
  - 首次完整生成
- `FILL_MISSING_SECTIONS`
  - reviewer 明确指出缺失章节时，只补这些章节
- `REVISE_WITH_EXISTING_DRAFT`
  - reviewer 提的是逻辑修订意见时，只输出需要替换的顶层章节，再合并回旧稿

预算说明：

- `FULL_DRAFT` 现在默认按动态预算上限请求输出
- 最终仍会由 `OutputBudgetCalculator` 按 `safeOutputRatio`、`num_ctx`、`promptTokens` 和动态预留统一裁剪

## `PRD`

来源：

- [StageArtifactComposer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/artifact/StageArtifactComposer.java)

system prompt：

```text
你是一个资深产品经理。请把输入材料整理成面向研发执行的 PRD，内容具体、可验证、可拆解。
不要输出多余解释。
```

user prompt 结构：

- 输入材料：上游 `analysis.md`
- 当前备注
- 上一轮草稿（修订时会带入）

输出要求：

- 标题必须是 `# 产品需求文档`
- 必须严格按模板输出，保留章节编号和标题
- 模板主结构固定为：
  - `1. 产品目标`
  - `2. 目标用户与使用场景`
  - `3. 功能范围`
  - `4. 非功能要求`
  - `5. 验收标准`
  - `6. 不做什么`
  - `7. Contract Metadata`
  - `8. Source Metadata`
- 不允许留 `[TODO]`
- 功能范围要求拆成“核心功能 / 可选增强 / 异常与边界场景”
- `3.1 核心功能` 会进入 implementation planning required coverage；`3.2 可选增强` 只保留为 optional requirement refs，不得混入待确认问题
- PRD 的 `1-6` 正文只保留产品承诺；显式 `推断 / 设计选择 / 建议 / 待确认问题` 只保留在 `Source Metadata`
- `3.2 可选增强` 只允许保留已确定的 optional capability；没有则留空，不要用建议项补满
- 带 `待确认` 标记或直接写成问题句式的条目不会进入 `PRODUCT_CONTRACT` machine block；在 PRD 中默认只保留到 `Source Metadata.open.questions`
- 验收标准必须可测、可执行
- 如果当前备注包含修订意见，优先保留上一轮已经合格的章节
- 当备注中明确指出缺失章节时，系统会要求模型只输出缺失章节，再由程序合并回旧稿
- 即使备注是逻辑修订意见而不是缺章节，系统仍会按顶层章节把新草稿合并回旧稿，避免已有章节被覆盖丢失
- reviewer 还会检查每个必需章节是否有正文；只有标题没有内容也会被判为缺失章节
- 若约束只有“可直接打开运行”“无需编译或打包”这类表述，不允许自行推断成“单文件 HTML”；默认允许相对路径引用本地资源

预算说明：

- 按动态 output ratio 申请输出
- 最终由 `OutputBudgetCalculator` 按上下文、预留和可用输出统一裁剪

## `DESIGN`

来源：

- [StageArtifactComposer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/artifact/StageArtifactComposer.java)

system prompt：

```text
你是一个资深架构师。请把 PRD 转成面向工程实现的技术方案，要求结构化、具体、可实施。
不要输出额外解释。
```

user prompt 结构：

- 输入材料：上游 `prd.md`
- 当前备注
- 上一轮草稿（修订时会带入）

输出要求：

- 标题必须是 `# 技术方案设计`
- 必须严格按模板输出，保留章节编号和标题
- 模板主结构固定为：
  - `1. 技术目标`
  - `2. 系统边界与模块划分`
  - `3. 核心数据模型`
  - `4. 关键流程`
  - `5. 接口、页面或命令设计`
  - `6. 测试与验证策略`
  - `7. 风险与取舍`
  - `8. 当前备注`
- 不允许留 `[TODO]`
- 模块划分、数据模型、关键流程必须尽量落到当前项目上下文
- 如果当前备注包含修订意见，优先保留上一轮已经合格的章节
- 当备注中明确指出缺失章节时，系统会要求模型只输出缺失章节，再由程序合并回旧稿
- 即使备注是逻辑修订意见而不是缺章节，系统仍会按顶层章节把新草稿合并回旧稿，避免已有章节被覆盖丢失
- reviewer 还会检查每个必需章节是否有正文；只有标题没有内容也会被判为缺失章节
- 若上游只要求“HTML 入口可直接打开运行”，技术方案不能自行收缩成“所有代码必须位于单个 HTML 文件”；除非用户明确要求单文件/内联资源

预算说明：

- 按动态 output ratio 申请输出
- 最终由 `OutputBudgetCalculator` 按上下文、预留和可用输出统一裁剪

## `IMPLEMENTATION` 总览

来源：

- [ImplementationExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/ImplementationExecutor.java)

`IMPLEMENTATION` 当前不是“单次整文件生成”，而是 4 类 prompt 协作：

1. implementation plan
2. coder tool loop
3. subtask verifier
4. structured repair

### 1. implementation plan

system prompt 核心：

```text
你是一个资深软件工程师。你需要把一次大的实现任务拆成可落地、可验证的子步骤。
你必须只返回一个 JSON 对象，不要输出任何额外解释。
```

要求输出：

```json
{
  "summary": "本次实现总体摘要",
  "subtasks": [
    {
      "title": "子任务标题",
      "goal": "该子任务要完成什么",
      "deliveryMode": "SKELETON|INCREMENTAL|PATCH|REWORK",
      "acceptanceCriteria": ["验收标准1", "验收标准2"],
      "changes": [
        {
          "path": "相对路径",
          "action": "WRITE|DELETE",
          "reason": "为什么要改这个文件"
        }
      ]
    }
  ]
}
```

基础约束：

- 子任务数量 `3-6`
- 每个子任务最多改 `2` 个文件
- 每个子任务必须可单独验证
- 优先最小改动
- 只列真正需要改动的文件
- 不在这个步骤输出文件内容
- 保持项目可编译、可测试
- `deliveryMode` 必须明确选择
- 如果 `DESIGN` 明确定义了性能/耗时/benchmark 验证要求，需要把对应基础测量入口纳入实现子任务
- 如果 `DESIGN` 未定义性能验证要求，不要自行发散额外 benchmark
- 对需要入口、可启动或可见运行表面的任务，优先拆成“最小可运行入口/表面 -> 核心功能填充 -> 接线与验证 -> polish”
- 不要试图在一个子任务里完成整个页面或整个产品
- 面向人的正文、标题和说明跟随用户请求语言；仅 `Contract Metadata` 标题和键名保持英文
- `ANALYSIS / PRD / DESIGN` 的文档 prompt 不按具体场景补规则，而是统一按“用户要求 / 上游事实 / 推断 / 设计选择 / 建议 / 待确认问题”管理信息来源
- 低确定性的内容不能升级成硬约束；设计选择必须写成设计选择，不能伪装成用户要求

### 1.1 implementation plan 的 `PATCH` 模式附加规则

当 note 中包含 `[FIX_MODE=PATCH]` 时，追加：

```text
这是修复模式：
1. 只围绕当前反馈做最小补丁修改
2. 优先复用现有文件和现有模块，不要新增重复模块
3. 不要推翻已经完成的功能
4. 子任务数量尽量控制在 1 到 3 个
```

### 1.2 implementation plan 的 `REWORK` 模式附加规则

当 note 中包含 `[FIX_MODE=REWORK]` 时，追加：

```text
这是重构模式：
1. 可以调整文件结构和模块划分来解决结构性问题
2. 优先消除重复实现、未接线文件和错误分层
3. 仍然要围绕当前代码和需求收敛，不要无意义推翻重来
4. 子任务数量控制在 3 到 6 个
```

### 1.3 implementation plan 的 `repair brief` 模式附加规则

当 note 中包含 `[REPAIR_BRIEF]` 时，追加：

```text
这是 repair brief 驱动的修复：
1. 子任务要直接对准 Must Fix First、Acceptance Target 和 Acceptance Checks
2. 不要重新发散成新的大范围实现目标
3. 不要沿着 Forbidden Directions 继续重复失败路径
```

### 1.4 implementation plan 的渐进交付附加规则

当交付契约要求“有入口、可启动、可见运行表面”，且本轮适合渐进交付时，追加：

```text
1. 第一子任务优先建立最小可运行入口或运行表面，deliveryMode 使用 SKELETON
2. 优先按入口、接线、核心逻辑、验证与 polish 逐步拆分
3. 后续子任务使用 INCREMENTAL，逐步补齐核心能力与交互
4. 不要试图在一个子任务里完成整个产品
5. 每个子任务完成后，项目应保持“至少可启动、可自检”
```

user prompt 输入：

- 目标
- 约束
- 需求分析
- 产品需求文档
- 技术方案设计
- 当前备注
- 当前工作区上下文

预算说明：

- implementation planning 按动态 output ratio 申请输出
- 不再固定写死 `num_predict`

## `IMPLEMENTATION` coder tool loop

来源：

- [ImplementationToolPromptBuilder.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/ImplementationToolPromptBuilder.java)
- [ImplementationToolLoopExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/ImplementationToolLoopExecutor.java)

system prompt 当前围绕下面几类事实组织：

```text
你是当前 implementation 子任务的 coder。
你必须在当前 task package、当前文件契约和当前工作区事实内工作。
优先通过工具读取、编辑、写入和验证，不要只写 prose。
```

当前主链约束：

- coder 只能在当前 `task package`、`accepted change-set` 和项目既有资产范围内引入本地依赖
- prompt 会显式展示 `Current File Contracts`
- 当前 attempt 使用的 task package 必须和 `executionState.effectiveChanges()` 对齐
- 不能把“未真正落盘的解释性 prose”当作成功
- 若文件交付契约未被工具真实满足，执行阶段会以 `NO_MATERIAL_CHANGE` 失败
- runtime ownership / wiring 不再由 planning detail 预判，而由执行与 verifier 基于产物事实校验

预算说明：

- coder tool loop 统一按动态 output ratio 申请输出
- 不再给 implementation 主链单独写固定 `num_predict`

当前可用工具：

- `Read`
- `Edit`
- `Write`
- `Delete`
- `Glob`
- `Grep`
- `Bash`

user prompt 输入：

- 总体实现摘要
- 当前子任务标题/目标
- 验收标准
- 当前 `task package`
- 当前文件契约
- 上一轮失败/修复上下文
- `analysis / prd / design`
- 当前相关文件上下文与已读文件状态

### 2.1 低层编辑内核

当前 stage-level coder prompt 下面，文件编辑工具内部仍可能使用更细的结构化编辑 prompt，但它们已经不是 implementation 阶段的顶层 prompt 分类。

这类低层 prompt 主要包括：

- HTML 精确改写
- 代码结构化 diff 改写
- JSON / payload repair

它们的职责是：

- 在受控文件范围内做局部编辑
- 产出结构化 patch / payload
- 失败时优先 repair，再决定是否升级

而不是重新承担 implementation 的整体规划职责

### 2.2 HTML 精确改写

当文件工具在已有 HTML 宿主上执行局部编辑时，会使用结构化 HTML patch prompt，而不是整页重写。

system prompt 核心：

```text
你是资深前端工程师。请对现有 HTML 页面做“精确改写”，不要整页重写。
你必须只返回一个 JSON 对象。
```

输出 JSON 结构：

```json
{
  "markupHtml": "main#app-root 的内部 HTML；不修改则返回 null",
  "styleCss": "style#app-style 的 CSS 内容；不修改则返回 null",
  "scriptJs": "script#app-script 的 JS 内容；不修改则返回 null",
  "headAppendHtml": "需要追加到 <head> 末尾的 HTML 片段；不修改则返回 null",
  "bodyAppendHtml": "需要追加到 <body> 末尾的 HTML 片段；不修改则返回 null"
}
```

关键约束：

- 不输出完整 HTML 文档
- 只修改必要区块
- `markupHtml` 只包含 `<main id="app-root">` 的内部内容
- `styleCss` 只包含纯 CSS
- `scriptJs` 只包含纯 JavaScript
- `headAppendHtml` / `bodyAppendHtml` 用于追加新的资源接线或结构片段，例如 `<script src="...">`、`<link rel="stylesheet" ...>` 或额外挂载节点
- 若连续失败，执行器会把失败原因分类为：
  - `INVALID_PATCH_JSON`
  - `PATCH_SCHEMA_INVALID`
  - `EDIT_UNIT_SCOPE_VIOLATION`
  - `TREE_SITTER_PARSE_FAILED`
  - `RESULT_FILE_INVALID`
  然后交给 supervisor 决定是否继续精确改写

### 2.3 代码结构化 diff 改写

当文件工具在已有代码文件上执行局部编辑时，会使用结构化 diff hunk prompt，而不是直接整文件重写。

system prompt 核心：

```text
你是资深工程师。请对现有源码做基于当前文件状态的结构化 diff 改写。
这条链保留“符号级精确改写”的任务语义，但返回协议已经切到 structured diff hunk。
你必须只返回一个 JSON 对象。
```

输出 JSON 结构：

```json
{
  "expectedSourceHash": "必须原样拷贝输入中的 sourceHash",
  "hunks": [
    {
      "sourceStartLine": 1,
      "beforeLines": ["当前文件中该位置原样存在的逐行内容；纯插入可为空数组"],
      "afterLines": ["修改后的逐行内容；纯删除可为空数组"]
    }
  ]
}
```

关键约束：

- 不输出完整源码文件
- `expectedSourceHash` 必须与 prompt 输入里的 `sourceHash` 完全一致
- `sourceStartLine` 使用 1-based 行号，并严格对应输入中的行号
- `beforeLines` 必须逐行匹配当前文件真实内容
- `afterLines` 只写修改后的目标内容，不带 `+/-/@@`
- append-only 单元只能生成文件尾部最小骨架 hunk
- restricted/single-symbol 单元只能围绕当前 `allowedSymbols` 对应区域生成最小 hunk
- 若连续失败，执行器会把失败原因分类为：
  - `INVALID_PATCH_JSON`
  - `PATCH_SCHEMA_INVALID`
  - `EDIT_UNIT_SCOPE_VIOLATION`
  - `SYMBOL_NOT_FOUND`
  - `TREE_SITTER_PARSE_FAILED`
  - `RESULT_FILE_INVALID`
  然后交给 supervisor 决定是否继续保持局部编辑、收缩改单范围或停止当前子任务

## `IMPLEMENTATION` verifier

来源：

- `SubtaskVerificationSupport`
- `Implementation verification` 相关执行链

verifier 的职责不是重新规划实现，而是判断：

- 当前子任务是否满足自己的 `acceptanceCriteria`
- 当前文件交付契约是否真实落盘
- 结构/语法/runtime evidence 是否支持通过
- 当前失败是否属于 implementation patch，还是必须阻断人工

预算说明：

- verifier 走小任务动态预算
- 不再在文档层维护固定 `num_predict`

verifier 额外约束：

- 若当前输入包含 repair brief：
  - verifier 必须优先检查 `Must Fix First`
  - verifier 必须按 `Acceptance Checks` 判定是否收敛
  - 若实现继续沿着 `Forbidden Directions` 修改，必须拒绝

## `IMPLEMENTATION` structured repair

来源：

- `PatchPayloadRepairSupport`
- `ModelJsonRepairTurn`
- `repair-before-regenerate` 相关链路

当前 repair 主线遵循：

- 先 deterministic repair
- 再 model repair
- 再决定是否升级或重试

### 4.1 JSON repair

system prompt：

```text
你是 JSON 修复器。请修复输入中的 implementation plan 或 patch payload，使其成为合法 JSON。
你必须只返回修复后的 JSON 对象，不要输出任何额外解释。
```

目标格式按调用方而定：

- implementation plan repair: 修复为 plan 标准 JSON
- patch payload repair: 修复为 structured diff JSON
  - `expectedSourceHash`
  - `hunks[].sourceStartLine`
  - `hunks[].beforeLines`
  - `hunks[].afterLines`

user prompt 输入：

- 当前 JSON 解析错误
- 待修复内容

预算说明：

- JSON repair 走动态预算申请
- 最终仍由统一预算链按当前上下文裁剪

## `SupervisorAgent`

来源：

- [SupervisorAgent.java](/home/linus/workspace/forge/src/main/java/devflow/agent/supervisor/SupervisorAgent.java)

system prompt 核心：

```text
你是 SupervisorAgent，负责决定 Forge 的下一步流程动作。
你必须只返回 JSON。
```

除主流程决策外，当前还承担一条 implementation 内部恢复路径：

- 当文件生成或精确 patch 连续失败时
- implementation 执行链会把结构化 failure 交给 supervisor
- supervisor 再输出：
  - `RETRY_SUBTASK`
  - `ROUTE_TO_REPAIR`
  - `FAIL_SUBTASK`
  - 必要时附带更保守的 `deliveryPolicy`

输出 JSON 结构：

```json
{
  "action": "ADVANCE_STAGE|REQUEST_HUMAN_REVIEW|RETRY_STAGE|ROUTE_TO_REPAIR|ROLLBACK_STAGE|COMPLETE_RUN|FAIL_RUN",
  "targetStage": "ANALYSIS|PRD|DESIGN|IMPLEMENTATION|CODE_REVIEW|TEST|null",
  "mode": "NONE|PATCH|REWORK",
  "reason": "一句话说明",
  "focus": ["本轮必须优先处理的问题"],
  "constraints": ["本轮额外约束"],
  "humanRequired": false
}
```

输入内容：

- `goal`
- `constraints`
- 当前阶段
- 默认下一阶段
- 当前 gate
- 当前 attempt
- 当前 review 的：
  - `decision`
  - `fixMode`
  - `summary`
  - `changeRequest`
  - `evidence`
  - `actionItems`
- 是否已识别为重复问题
- 当前 artifact 摘要
- 最近 review history 摘要
- `repair_brief` 摘要
- 保守流程决策

关键约束：

- 只做流程决策，不直接写代码
- review 通过时优先选：
  - `ADVANCE_STAGE`
  - `REQUEST_HUMAN_REVIEW`
  - `COMPLETE_RUN`
- review 未通过时优先选：
  - `RETRY_STAGE`
  - `ROUTE_TO_REPAIR`
  - `ROLLBACK_STAGE`
  - `FAIL_RUN`
- `REQUEST_HUMAN_REVIEW` 只有当前阶段 gate 为 `AGENT_PLUS_HUMAN` 时才允许选择
- 对需要入口、运行表面或严格交付契约的任务，优先给出更小粒度的 `focus / constraints`
- 鼓励“先可运行骨架，再渐进填充”，不要鼓励单轮完成整个产品
- `ROUTE_TO_REPAIR` 只在重复问题明确且适合定点修补时使用
- `ROLLBACK_STAGE` 只在根因明显属于上游文档或设计时使用
- `WorkflowEngine` 仍会对 supervisor 输出做合法性校验；模型决策失败时回退到保守规则

## `CODE_REVIEW`

来源：

- [StageArtifactComposer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/artifact/StageArtifactComposer.java)

system prompt 核心：

```text
你是严格的资深代码审阅者。请结合实现报告和实际代码变更做 code review。
输出 Markdown，不要输出额外解释。
```

输出头部要求：

```text
- decision: APPROVED|REVISION_REQUIRED|REJECTED
- fixMode: NONE|PATCH|REWORK
- summary: 一句话总结
- changeRequest: 如需修改则写清楚，否则留空
```

fixMode 规则：

- `APPROVED` 时必须是 `NONE`
- `PATCH` 表示结构基本可接受，只做增量修补
- `REWORK` 表示结构存在明显问题，允许较大范围重构

附加审阅约束：

- Findings 只能写能从“实际代码变更”或“实现报告”中直接证实的问题
- 不要猜测“可能缺少某功能”，除非代码里确实没有对应实现证据
- 如果 `decision=APPROVED`，则 Findings 必须为空，或只写“无阻塞问题”
- 不要因为空目录、历史残留说明、已经删除的模块名称，就判定当前代码仍有结构问题

user prompt 输入：

- 当前备注
- 实现报告
- 实际代码变更

输出要求：

- 先输出 `decision / fixMode / summary / changeRequest`
- 然后给出 Findings
- Findings 尽量引用具体文件/代码证据

预算说明：

- `CODE_REVIEW` 走动态预算申请
- 实际生效输出由统一预算链裁剪

## `TEST`

`TEST` 当前不是单一 prompt，而是三层：

1. self-check 策略规划
2. testcase 设计
3. testcase 执行

### 1. self-check 策略规划

来源：

- [ValidationStrategyPlanner.java](/home/linus/workspace/forge/src/main/java/devflow/agent/validation/ValidationStrategyPlanner.java)

system prompt 核心：

```text
你是验证策略规划器。请根据项目特征，从给定 capability 列表中选择一个有序子集作为 self-check 策略。
你必须只返回 JSON
```

输出格式：

```json
{
  "summary": "一句话总结",
  "steps": [
    {
      "capability": "枚举值",
      "reason": "为什么选择该能力",
      "required": true
    }
  ]
}
```

约束：

- 只能从候选 capability 中选
- 优先使用项目已有工具链
- 没有构建链路时，退回通用静态 Web 检查
- 不发明新工具，不输出命令行

user prompt 输入：

- 项目特征
- 候选 capability 列表

预算说明：

- self-check 策略规划走动态预算申请
- 实际生效输出由统一预算链裁剪

### 2. testcase 设计

来源：

- [TestCasePlanner.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/TestCasePlanner.java)

system prompt 核心：

```text
你是测试用例设计器。请根据目标、PRD、技术方案和当前实现，为当前项目输出“可执行”的结构化测试用例。
你必须只返回 JSON
```

输出格式：

```json
{
  "summary": "一句话总结",
  "cases": [
    {
      "id": "TC-001",
      "title": "标题",
      "type": "smoke|functional",
      "required": true,
      "entry": "实际入口相对路径，例如 public/index.html",
      "preconditions": "",
      "expected": "预期结果",
      "steps": [
        {
          "action": "ASSERT_SELECTOR|ASSERT_CANVAS_MIN|CLICK|PRESS_KEY|WAIT|ASSERT_NO_ERRORS|ASSERT_TEXT_CONTAINS",
          "selector": "",
          "key": "",
          "count": 1,
          "ms": 200,
          "text": "",
          "optional": false
        }
      ]
    }
  ]
}
```

约束：

- 只能使用给定的 action 枚举
- 必须覆盖 `QualityPlan` 中的 required capability surfaces
- 优先先满足确定性基础 testcase，再让模型补充或细化步骤
- 不依赖外部网络、登录或人工操作
- 不再按“网页/小游戏至少...”这种产品特判生成用例
- `run-state-entry` 只允许落在 runtime contract 已声明的启动入口，或 runtime probe 明确识别出的 control candidate
- 不允许把 `body`、`main`、宿主根节点或 selector 文本猜测结果写成 `run-state-entry`
- 没有 runtime control candidate 时，不允许臆造启动点击步骤
- `PRESS_KEY` 必须携带非空 `key`
- 观察 surface 按 capability surface 独立绑定；`primary-visual-surface`、`primary-interaction`、`timed-state-progression` 不要求共用同一个 selector

user prompt 输入：

- 目标
- 约束
- 项目特征
- PRD
- 技术方案
- 实现报告
- 当前代码上下文

预算说明：

- testcase 设计走动态 output ratio 申请输出
- 最终仍由统一预算链按上下文、预留和可用输出裁剪

### 3. testcase 执行

当前网页项目优先使用：

- [run-testcases.mjs](/home/linus/workspace/forge/tools/playwright-smoke/run-testcases.mjs)

这层不是 LLM prompt，而是结构化执行器。

它支持的 action：

- `ASSERT_SELECTOR`
- `ASSERT_CANVAS_MIN`
- `CLICK`
- `PRESS_KEY`
- `WAIT`
- `ASSERT_NO_ERRORS`
- `ASSERT_TEXT_CONTAINS`

执行侧额外约束：

- 空 `PRESS_KEY` 会在 sanitizer 阶段直接删除
- 不合法的 `run-state-entry` 会在 sanitizer/repair 阶段被降级或移除，不再原样透传到执行器
- 交互补强只消费 runtime probe 给出的 control candidate，不再从静态 HTML 的 `<button>`、id/class 或 selector token 猜交互目标

## 通用 review JSON 包装

来源：

- [OllamaLlmProvider.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/OllamaLlmProvider.java)

所有 `llmProvider.review(...)` 都会被包上一层统一 prompt：

```text
请对下面的内容做审阅，必须只返回一个 JSON 对象
{
  "decision": "APPROVED|REVISION_REQUIRED|REJECTED",
  "fixMode": "NONE|PATCH|REWORK",
  "summary": "不超过60字",
  "changeRequest": "不超过120字，无则返回空字符串"
}
```

附加约束：

- 只能返回 JSON
- 不要 markdown
- 通过时 `fixMode=NONE`
- 不通过时必须明确 `PATCH` 或 `REWORK`

## 阶段 reviewer prompt

来源：

- [StageReviewer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/review/StageReviewer.java)

### `ANALYSIS` reviewer

```text
你是资深需求评审，请检查需求分析是否完整、清晰、可执行。
只审需求分析阶段应该承担的内容：
1. 问题定义是否清楚。
2. 目标、成功标准、关键约束是否明确。
3. 边界、非目标、风险与待确认问题是否覆盖。
4. 初步调研与假设是否足以支撑进入 PRD。
不要把以下内容当成 ANALYSIS 阶段的阻塞项：
- 算法实现细节
- 唯一解验证机制细节
- 量化性能测试方案
- 页面原型或交互原型细节
这些属于 DESIGN 或 TEST_CASE 阶段。
```

### `PRD` reviewer

```text
你是资深产品评审，请检查 PRD 是否具体、可验收、边界清晰。
```

### `DESIGN` reviewer

```text
你是资深架构评审，请检查技术方案是否可实施、风险是否说明充分。
```

### `IMPLEMENTATION` reviewer

```text
你是严格的软件工程评审，请判断这次实现是否真正落地、是否有明显遗漏或危险改动。若实现报告中的子任务状态与实际代码变更、自检结果冲突，以实际代码和自检结果为准，不要因为报告里旧的 failed 标记而直接拒绝。
```

它的 candidate 输入包括：

- `implementation.md`
- 当前 `self-check`
- 实际代码变更

附加规则：

- 若 `DESIGN` 未定义性能测量要求，性能风险不会阻塞 `IMPLEMENTATION`
- 若 `DESIGN` 已定义性能测量要求，reviewer 会要求先补设计中规定的基础测量结果
- 没有测量证据时，不能直接写“性能未达标”

### `CODE_REVIEW` reviewer

`CODE_REVIEW` 不再另加 reviewer prompt，而是直接解析 `code_review.md` 头部的 `decision / fixMode / summary / changeRequest`。

附加逻辑：

- 如果 `decision=APPROVED`
- 但 `Findings` 仍列出阻塞问题
- 会自动降级成 `REVISION_REQUIRED`

### `TEST` reviewer

`TEST` 不走模型 reviewer，优先读取 `test_report.md` 顶部：

- `decision`

如果有：

- `decision: APPROVED`

就直接判通过。

## 维护说明

后续调整 prompt 时，优先遵守：

1. prompt 改动要和代码行为一致
2. 修改系统 prompt 时，文档同步更新
3. 如果 prompt 约束承担了关键流程语义，也要考虑是否应当下沉成代码规则，而不是只靠提示词
