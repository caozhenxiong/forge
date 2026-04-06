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

文档 agent mode：

- `FULL_DRAFT`
  - 首次完整生成
- `FILL_MISSING_SECTIONS`
  - reviewer 明确指出缺失章节时，只补这些章节
- `REVISE_WITH_EXISTING_DRAFT`
  - reviewer 提的是逻辑修订意见时，只输出需要替换的顶层章节，再合并回旧稿

默认 `num_predict`：

- `2200`

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
  - `7. 当前备注`
- 不允许留 `[TODO]`
- 功能范围要求拆成“核心功能 / 辅助功能 / 异常与边界场景”
- 验收标准必须可测、可执行
- 如果当前备注包含修订意见，优先保留上一轮已经合格的章节
- 当备注中明确指出缺失章节时，系统会要求模型只输出缺失章节，再由程序合并回旧稿
- 即使备注是逻辑修订意见而不是缺章节，系统仍会按顶层章节把新草稿合并回旧稿，避免已有章节被覆盖丢失
- reviewer 还会检查每个必需章节是否有正文；只有标题没有内容也会被判为缺失章节

默认 `num_predict`：

- `2200`

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

默认 `num_predict`：

- `2400`

## `IMPLEMENTATION` 总览

来源：

- [ImplementationExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/ImplementationExecutor.java)

`IMPLEMENTATION` 不是一个单 prompt，而是 4 类 prompt：

1. implementation plan
2. file generation
3. subtask verifier
4. JSON repair

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
- 对复杂前端/网页/游戏任务，优先拆成“骨架 -> 功能填充 -> 交互补全 -> polish”
- 不要试图在一个子任务里完成整个页面或整个产品

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

### 1.4 implementation plan 的前端小步交付附加规则

当任务目标属于复杂前端/网页/游戏时，追加：

```text
1. 第一子任务优先建立最小可运行骨架，deliveryMode 使用 SKELETON
2. 前端项目优先拆成 index.html + styles.css + app.js/game.js 等分职责文件
3. 后续子任务使用 INCREMENTAL，逐步填充核心逻辑、输入控制、状态更新和 polish
4. 不要试图在一个子任务里完成整个页面或整个游戏
5. 每个子任务完成后，项目应保持“至少可打开、可自检”
```

user prompt 输入：

- 目标
- 约束
- 需求分析
- 产品需求文档
- 技术方案设计
- 当前备注
- 当前工作区上下文

默认 `num_predict`：

- `2600`

## `IMPLEMENTATION` file generation

来源：

- [ImplementationExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/ImplementationExecutor.java)

system prompt 核心：

```text
你是资深软件工程师。请只输出目标文件的完整最终内容。
不要解释，不要 markdown 代码块，不要补充额外文字。
```

当前 file generation 会根据 `deliveryMode` 追加不同约束：

- `SKELETON`
  - 只建立最小可运行骨架
  - 允许占位函数/容器
  - 默认 `num_predict = 1200`
- `INCREMENTAL`
  - 只补当前子任务功能
  - 保持已有骨架和模块边界
  - 默认 `num_predict = 1600`
- `PATCH`
  - 最小补丁修复
  - 默认 `num_predict = 1600`
- `REWORK`
  - 允许较大范围调整
  - 默认 `num_predict = 2200`

user prompt 输入：

- 总体实现摘要
- 当前子任务标题/目标
- 当前子任务 `deliveryMode`
- 验收标准
- 文件路径
- 变更原因
- `analysis / prd / design`
- 上一轮反馈
- 当前相关文件上下文
- 当前文件内容

## `SupervisorAgent`

来源：

- [SupervisorAgent.java](/home/linus/workspace/forge/src/main/java/devflow/agent/supervisor/SupervisorAgent.java)

system prompt 核心：

```text
你是 SupervisorAgent，负责决定 Forge 的下一步流程动作。
你必须只返回 JSON。
```

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
- 保守 fallback 决策

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
- 对复杂前端/网页/游戏任务，优先给出更小粒度的 `focus / constraints`
- 鼓励“先可运行骨架，再渐进填充”，不要鼓励单轮完成整个产品
- `ROUTE_TO_REPAIR` 只在重复问题明确且适合定点修补时使用
- `ROLLBACK_STAGE` 只在根因明显属于上游文档/设计时使用
- `WorkflowEngine` 仍会对 supervisor 输出做合法性校验；模型决策失败时回退到保守规则

### 2. file generation

system prompt 核心：

```text
你是资深软件工程师。请只输出目标文件的完整最终内容。
不要解释，不要 markdown 代码块，不要补充额外文字。
```

#### `PATCH` 模式附加规则

```text
当前处于修复模式：
1. 只修复反馈中明确指出的问题
2. 尽量保留既有代码结构和已有功能
3. 不要为了修一个点而重写整份文件
```

#### `REWORK` 模式附加规则

```text
当前处于重构模式：
1. 允许较大范围调整结构来解决根本性问题
2. 优先解决重复模块、入口未接线、模块边界混乱
3. 重构后必须保持入口文件、模块引用和测试链路一致
```

#### `repair brief` 强约束附加规则

```text
当前输入包含 repair brief：
1. 优先修复 diagnosis 明确指出的根因
2. 不要偏离 repair brief 中的 affectedFiles、doNotChange 和 acceptanceTarget
3. 产出必须能回应 Must Fix First、Forbidden Directions、Acceptance Checks
```

user prompt 输入：

- 总体实现摘要
- 当前子任务：
  - 标题
  - 目标
  - 验收标准
- 文件路径
- 变更原因
- 需求分析
- 产品需求文档
- 技术方案设计
- 上一轮反馈
- 当前相关文件上下文
- 当前文件内容

默认 `num_predict`：

- `2600`

### 3. subtask verifier

来源：

- `ImplementationExecutor.verifySubtask`

system prompt：

```text
你是实现阶段的子任务验证器。请只根据子任务目标、验收标准和当前文件内容判断该子任务是否已经完成。
```

candidate 输入包括：

- 子任务标题
- 子任务目标
- 验收标准
- 自检结果
  - `passed`
  - `summary`
  - `details`
- 当前相关文件内容
- 若 `DESIGN` 定义了性能验证策略，还会附带对应策略摘要
- 若当前子任务处于 repair brief 路径，还会附带 repair brief 强约束上下文

默认 `num_predict`：

- `220`

verifier 额外约束：

- 若当前输入包含 repair brief：
  - verifier 必须优先检查 `Must Fix First`
  - verifier 必须按 `Acceptance Checks` 判定是否收敛
  - 若实现继续沿着 `Forbidden Directions` 修改，必须拒绝

### 4. JSON repair

来源：

- `ImplementationExecutor.repairPlan`

system prompt：

```text
你是 JSON 修复器。请修复输入中的 implementation plan，使其成为合法 JSON。
你必须只返回修复后的 JSON 对象，不要输出任何额外解释。
```

目标格式仍然是 implementation plan 的标准 JSON。

user prompt 输入：

- 当前 JSON 解析错误
- 待修复内容

默认 `num_predict`：

- `2600`

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

默认 `num_predict`：

- `1400`

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

默认 `num_predict`：

- `600`

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
      "entry": "index.html",
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
- `required=true` 的 case 控制在 `2-5` 条
- 优先设计“页面能跑起来、关键交互可用”的用例
- 不依赖外部网络、登录或人工操作
- 网页/小游戏至少包含：
  - 页面加载
  - 关键元素存在
  - 无运行时错误
  - 至少一种交互

user prompt 输入：

- 目标
- 约束
- 项目特征
- PRD
- 技术方案
- 实现报告
- 当前代码上下文

默认 `num_predict`：

- `1200`

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
