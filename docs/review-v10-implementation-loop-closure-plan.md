# Review V10: Implementation Loop Closure Plan

## Summary

- 当前不继续盲跑集成测试，也不先动代码。
- `v177` 的失败主因已经可以收敛成 4 条当前主线问题，不再是之前已经收口的 `tool-loop completion`、`completed-plan owner resolver` 或 `shell deny diagnostics`。
- 这轮先把问题、证据、最终态和联动改动范围写清楚，作为后续实现的唯一方案入口。

## Scope Boundary

### 本轮明确不重做的已收口项

- 不重开 `tool-loop declared-changes-satisfied` 的 completion 收口。
- 不重开 `completed-plan PATCH owner resolver` 的已修路径。
- 不重开 `runtime contract resolver` 的目录扫描 / orphan root 旧问题。
- 不重开 `Bash deny pathIntents diagnostics` 已落地链路。

### 本轮真正要收的主问题

1. 上游 `runtime contract` 仍会把“网页版即可”收紧成 `entry-owned`，并与 accepted implementation plan 冲突。
2. shared-file incremental subtask 没有把下游 capability boundary 变成可执行契约。
3. 子任务驳回后的 canonical repair package 没有沿 `review -> retry feedback -> execution_state -> stage continuation` 保持单一真相源。
4. 当前 subtask 在 `3/3` 尝试耗尽后，stage 仍被 generic `CONTINUE_SUBTASKS` 重开，导致 concrete continuation 在下一次 `IMPLEMENTATION` 尝试里丢失。

## Current Evidence

### v177 直接证据

- 项目：`/home/linus/workspace/tetris_test_itest_v177`
- run id：`627e2d46-d929-46b9-81ef-96bf98e498af`
- 关键产物：
  - `.devflow/runs/627e2d46-d929-46b9-81ef-96bf98e498af/events.log`
  - `.devflow/runs/627e2d46-d929-46b9-81ef-96bf98e498af/implementation_state.attempt-1.json`
  - `.devflow/runs/627e2d46-d929-46b9-81ef-96bf98e498af/implementation_state.json`
  - `.devflow/runs/627e2d46-d929-46b9-81ef-96bf98e498af/task_packages.md`
  - `.devflow/runs/627e2d46-d929-46b9-81ef-96bf98e498af/implementation_shared_context.md`
  - `index.html`
  - `src/game.js`

### 证据 1：上游 contract 过度收紧

- `PRD` 与 `DESIGN` 都把 `runtime.entryPackagingMode=entry-with-local-dependencies` 和 `runtime.runtimeOwnershipMode=entry-owned` 同时写成绑定 contract。
- `DESIGN` 还把“单 HTML 文件部署”写成 `soft.designDecisions`。
- 但 accepted implementation plan 第一子任务已经合法拆成：
  - `index.html`
  - `src/game.js`
- 同一轮里，`implementation_state.attempt-1.json` 又要求把当前运行时修回 `INLINE_HOST`，并生成 `PATCH_RUNTIME_WIRING`。

结论：

- 当前系统同时存在两套互相冲突的执行语义：
  - 上游 contract 要求 `entry-owned / INLINE_HOST`
  - accepted implementation plan 允许 `index.html + external companion js`

### 证据 2：shared-file capability boundary 没有落到执行面

- `implementation_state.json` 和 `task_packages.md` 里，3 个 subtasks 的 `deferredCapabilities` 全为空。
- 第一子任务只负责 `CAP-1` 的基础界面与画布，但当前产物 `src/game.js` 已实现：
  - 移动
  - 旋转
  - 下落
  - 消行
  - 得分/等级
  - 暂停
  - 游戏结束
- 当前 task package 的：
  - `后续负责能力`
  - `必须优先修复`
  - `禁止方向`
  都没有形成能约束 coder / reviewer 的边界契约。

结论：

- 这不是单次模型偶发失误，而是 shared-file incremental planning 没有把“当前能做什么、不能提前做什么”结构化传到下游。

### 证据 3：repair 没有沿单一 canonical package 续跑

- `implementation_state.attempt-1.json` 里已经出现 concrete continuation：
  - `continuationPatchTarget=PATCH_RUNTIME_WIRING`
  - `continuationOverrideChanges=[index.html]`
- 但 repair 回合里仍然发生：
  - `Edit replace_all=true index.html`
  - `touch index.html`
  - `echo > index.html`
  - `ls -la`
  - `cat index.html`
  - `REPAIR_MODE_READ_ONLY_SHELL_DENIED`
- 最终进入下一次 `IMPLEMENTATION` 尝试后，`implementation_state.json` 被重新折叠成：
  - `continuationMode=CONTINUE_SUBTASKS`
  - `continuationPatchTarget=NONE`
  - `continuationOverrideChanges=[]`

结论：

- concrete repair package 并没有稳定穿过：
  - subtask review
  - retry feedback
  - execution state
  - implementation_state
  - stage continuation

### 证据 4：subtask exhaustion 被 generic continue 覆盖

- `events.log` 里清楚出现：
  - 第一子任务 `REVISION_REQUIRED` 三次
  - `子任务结束｜完成=false｜总尝试=3`
  - 紧接着 `阶段｜继续执行｜原因=STAGE_CONTINUE`
  - 再进入 `IMPLEMENTATION｜尝试=2`
  - `实现阶段｜复用旧计划｜已完成前缀=0｜计划子任务=3`
- `implementation_progress.md` 里此时又显示：
  - `executedSubtasks: 0`
  - `completedSubtasks: 0`
  - `currentSubtask: 构建基础HTML结构和游戏画布`

结论：

- 当前状态机没有区分：
  - 正常的 mid-plan continuation
  - concrete patch continuation
  - 当前 subtask 已耗尽重试次数但仍未收敛
- 结果就是 stage attempt 被错误重开，且上一轮 concrete continuation 被冲掉。

## Problem -> Solution Mapping

### P1. Runtime Contract Provenance Drift

#### Problem

- `PRD/DESIGN` 把“网页版即可”过度收紧成 `entry-owned`。
- accepted plan 又允许 `index.html + companion js`。
- implementation 阶段因此先按 split package 编排，再被 contract gate 打回 `INLINE_HOST`。

#### Solution

- 把 `runtimeOwnershipMode` 的绑定 authority 收回到单一来源：
  - 只有 `hard.*`、显式 contract metadata、或后续已接受的 structured continuation contract 才能写成绑定运行时所有权。
- 对普通 `html-entry + entry-with-local-dependencies`，默认不能再自动升级为 `entry-owned`。
- `PRD/DESIGN` prompt、contract metadata consistency guard、extractor 必须一起收紧，禁止把“单网页”偷换成“单文件 + inline-host”。
- planning 与 accepted package 若已合法声明 split runtime package，就不能再被上游 prose/soft design choice 反向打成 `INLINE_HOST`。

### P2. Shared-File Capability Boundary Missing

#### Problem

- 当前 capability partition 只检查“owned/deferred 是否冲突”，没有确保 shared-file incremental subtasks 把后续边界显式写出来。
- task package 和 reviewer prompt 因此缺少“禁止提前实现后续能力”的硬约束。

#### Solution

- 对 shared-file incremental subtasks，必须形成显式 downstream boundary contract：
  - 当前负责能力
  - 后续负责能力
  - 当前不得提前实现的能力边界
- 这个 boundary contract 必须同时进入：
  - planning gate
  - task package
  - coder prompt
  - subtask review prompt
  - deterministic boundary gate
- 一旦提前实现 downstream capability，必须形成 deterministic rejection，并产出最小 offending file scope；不能只在 prose 里“记录为 deferred”。

### P3. Canonical Repair Package Lost Across Retry

#### Problem

- concrete patch continuation 已在 `attempt-1` 出现，但 retry feedback 仍可退化成抽象 prose。
- repair actor 因为拿不到稳定 patch package，继续 whole-file rewrite 和 shell read。
- 进入下一次 stage attempt 后，structured patch 又被重新折叠成 generic continue。

#### Solution

- 建立单一 canonical repair package owner。
- 一旦当前 review / gate 已产出 concrete patch package，它必须成为以下链路的唯一输入：
  - retry feedback
  - next attempt effective changes
  - implementation shared context
  - implementation_state
  - stage continuation note
  - implementation resume
- 任何 concrete patch target 若没有 canonical overrideChanges：
  - 不允许 generic continue
  - 不允许 whole-file rewrite
  - 直接 `BLOCK_STAGE` / `REQUEST_HUMAN`

### P4. Subtask Exhaustion Misclassified As Mid-Plan Continue

#### Problem

- 当前 implementation stage 只要看到 `incompleteSubtasks`，就仍可能走 generic `CONTINUE_SUBTASKS`。
- 当前 subtask 即使已经 `3/3` 耗尽且未完成，也会被当成“阶段中间态”，再重开下一次 implementation attempt。

#### Solution

- implementation stage 必须把 continuation 明确分成 3 类：
  1. `MID_PLAN_CONTINUE`
     只用于当前 stage attempt 仍在正常推进，且没有 exhausted subtask。
  2. `PATCH_CONTINUE`
     只用于已经拿到 canonical repair package 的 concrete patch continuation。
  3. `BLOCKED_EXHAUSTED_SUBTASK`
     当前 subtask 已耗尽尝试、没有安全 continuation package，必须阻断到人工或更高层 reroute。
- 不能再用 generic `CONTINUE_SUBTASKS + NONE` 覆盖 concrete patch 或 exhausted failure。

## Final State

- `runtimeOwnershipMode` 不再被普通网页场景自动写死成 `entry-owned`；binding runtime contract 与 accepted implementation package 保持一致。
- shared-file incremental subtasks 必须携带明确 downstream boundary contract；task package、coder、reviewer 都看到同一份边界。
- 子任务级 `REVISION_REQUIRED` 一旦需要 patch，只能沿同一份 canonical repair package 续跑；没有 safe package 时直接阻断。
- `implementation_state`、`implementation_progress`、stage continuation note、resume policy 对同一 continuation 使用同一份结构化 patch 事实。
- 当前 subtask `3/3` 耗尽后，不再被重写成 generic mid-plan continue；必须要么 concrete patch 续跑，要么 block。
- 黄金路径重新执行时，不再出现：
  - accepted plan 允许 split runtime，但 contract gate 打回 inline-host
  - 第一子任务提前吞掉后续能力
  - attempt-1 的 concrete patch 在 attempt-2 丢失
  - subtask exhaustion 后又从 `IMPLEMENTATION attempt=2` 重新开始

## Removal Plan

本轮必须删除或封死：

- “普通 html-entry 默认 entry-owned”的隐式收紧路径。
- shared-file incremental subtasks 上 `deferredCapabilities=(无)` 仍可通过的规划缺口。
- reviewer prompt 中“后续能力只能记为 deferred，不得阻塞当前子任务”的旧宽口径。
- concrete patch 已存在，但 retry feedback / continuation note / implementation_state 又把它清空回 `NONE` 的双轨。
- exhausted subtask 仍能被 `incompletePlanContinuation()` 重写成 generic `CONTINUE_SUBTASKS` 的旧路径。
- concrete patch continuation 下 whole-file rewrite / read-only Bash exploration 的旧续跑方向。

## Joint-Change Scope

### Scope 1. Runtime Contract Authority / Upstream Contract Consistency

- `src/main/java/devflow/agent/prompt/ConstraintPromptCatalog.java`
- `src/main/java/devflow/agent/context/ContractExtractor.java`
- `src/main/java/devflow/agent/context/ExecutionContract.java`
- `src/main/java/devflow/agent/review/ContractMetadataConsistencyGuard.java`
- `src/main/java/devflow/agent/context/ContractView.java`

目标：

- 收紧 `PRD/DESIGN -> ExecutionContract` 的 binding authority。
- 禁止把普通网页运行约束直接升级成 `entry-owned`。
- 让 accepted implementation package 与 binding runtime contract 不再互相冲突。

### Scope 2. Shared-File Capability Boundary Contract

- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationOutlineGate.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanGate.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanCoverageAnalyzer.java`
- `src/main/java/devflow/agent/executor/implementation/render/TaskPackageAssembler.java`
- `src/main/java/devflow/agent/executor/editing/TaskPackageMarkdownRenderer.java`
- `src/main/java/devflow/agent/executor/subtask/TaskPackage.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskReviewPromptAssembler.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskBoundaryGate.java`

目标：

- 让 shared-file incremental subtasks 必须显式声明 downstream boundary。
- 让 coder / reviewer / deterministic gate 使用同一份 boundary contract。

### Scope 3. Canonical Repair Package Single Owner

- `src/main/java/devflow/agent/executor/subtask/SubtaskRepairDirectiveResolver.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskVerificationSupport.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskRetryFeedbackRenderer.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskExecutionState.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskAttemptStepExecutor.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskExecutor.java`
- `src/main/java/devflow/agent/executor/testing/TestExecutor.java`
- `src/main/java/devflow/agent/orchestrator/StageContinuationNoteBuilder.java`
- `src/main/java/devflow/agent/executor/implementation/state/ImplementationStateSnapshotSerializer.java`
- `src/main/java/devflow/agent/executor/implementation/state/ImplementationStateArtifactSupport.java`
- `src/main/java/devflow/agent/orchestrator/ImplementationContinuationSupport.java`

目标：

- 让 concrete patch package 从 review 产生之后，不再在任一中间层退化成 prose-only 或 `overrideChanges=[]`。

### Scope 4. Implementation Continuation / Stage Attempt Classification

- `src/main/java/devflow/agent/executor/gate/ImplementationStageGate.java`
- `src/main/java/devflow/agent/orchestrator/ImplementationProgressSupport.java`
- `src/main/java/devflow/agent/orchestrator/ImplementationRevisionFacts.java`
- `src/main/java/devflow/agent/orchestrator/StageProgressCoordinator.java`
- `src/main/java/devflow/agent/orchestrator/FlowController.java`
- `src/main/java/devflow/agent/orchestrator/FlowDecisionExecutor.java`
- `src/main/java/devflow/agent/orchestrator/StageTransitionSupport.java`
- `src/main/java/devflow/agent/orchestrator/StageEntryExecutor.java`
- `src/main/java/devflow/agent/executor/implementation/ImplementationResumePolicy.java`

目标：

- 让 `mid-plan continue / concrete patch continue / blocked exhausted subtask` 三种状态各有唯一 owner 和唯一流向。

### Scope 5. Regression Matrix

- contract metadata / execution contract 回归
- shared-file capability boundary 回归
- canonical repair package round-trip 回归
- exhausted subtask blocking 回归
- generic continue 与 concrete patch continue 分流回归
- 黄金路径集成验证回归

## Non-Goals

- 不顺手扩新的产品能力。
- 不顺手重做全套 runtime ownership 架构。
- 不顺手改 completed-plan owner resolver 的既有语义。
- 不顺手放松 repair-mode patch-first 工具约束。

## Closure Decision

- 这轮可以一次性收口，但前提是 4 个 scope 必须成组落地。
- 不能只改单个点：
  - 只改 subtask review，不改 stage continuation，会继续丢 concrete patch。
  - 只改 stage continuation，不改 upstream contract authority，会继续被错误的 `entry-owned` 打回。
  - 只改 planning，不改 retry feedback / implementation_state，会继续 whole-file rewrite 漂移。
- 当前阶段停在方案，不进入实现。

## Risks / Blockers

- 这轮真正的风险不在“方案不清楚”，而在实现时如果拆着改，会再次形成半成品。
- `runtimeOwnershipMode` 的 authority 若不一次改清楚，后续 reviewer 仍会把 split runtime 判成 contract violation。
- `shared-file boundary` 若只写在 prompt、不进 deterministic gate，会再次退回 prose 约定。
- `canonical repair package` 若只在 subtask 内收紧、不进入 `implementation_state` 与 stage continuation，下一次 stage attempt 仍会丢 scope。

## Implementation

- 本文档通过 review 前，不改代码。
- 通过后按 `Scope 1 -> Scope 2 -> Scope 3 -> Scope 4 -> Scope 5` 的顺序执行。
- 每个 scope 完成后必须做：
  - `self-test`
  - `code review`
  - 文档同步
- 全部 scope 完成后，才重新跑黄金路径集成测试。

## Verification Matrix

1. `html-entry + entry-with-local-dependencies` 在没有硬来源要求时，不能自动变成 `entry-owned`。
2. `shared-file incremental subtask` 若没有 downstream boundary contract，planning 必须失败。
3. 第一子任务提前实现后续 capability 时，review 必须 deterministic reject，并给出最小 offending file scope。
4. concrete patch package 必须经过：
   - `review -> retry feedback -> execution_state -> implementation_state.json -> continuation note -> resume`
   后保持同一份结构化范围。
5. 当前 subtask `3/3` 耗尽且没有 safe package 时，必须 `BLOCK_STAGE` / `REQUEST_HUMAN`，不能再进入 generic `STAGE_CONTINUE`。
6. 普通 mid-plan continuation 仍然必须保留，避免把真正未执行完的 stage 一律打成阻断。
7. 黄金路径重跑时，`v177` 这类 case 不能再出现：
   - accepted split runtime 与 entry-owned contract 冲突
   - concrete patch 在下一次 implementation attempt 丢失
   - exhausted subtask 被重开成新的 implementation attempt

## Completion Gate Result

- 当前结果：`Result B`
- 状态：`只输出完整收口方案，不提交半成品实现`
- 下一步：`review 本文档；review 通过后再进入实现`
