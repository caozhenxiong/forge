# Review V12 Patch-Continue Convergence Progress

## Purpose

这份文档是 [review-v12-patch-continue-convergence-plan.md](/home/linus/workspace/forge/docs/review-v12-patch-continue-convergence-plan.md) 的唯一执行 tracker。

它只负责：

- 把 `v12` 的 5 个 scope 拆成可打勾待办
- 固定实现顺序，避免边改边漂
- 为每个 scope 记录统一证据位：
  `commit / self-test / code review / docs`
- 在开始集成测试前，先确认 owner 收口、scope clamp、tool loop closure 和 milestone gate 已全部落地

## Rules

- 只跟踪 `review-v12` 这一轮 patch-continue convergence，不混写其他整改
- 每个 scope 开始前先更新本文档
- 每完成一项，立即打勾
- 如果 blocker、顺序或 owner 变化，先更新本文档，再继续改代码
- 不允许把“后续再清理”写进 checklist
- 不允许在 feedback channel 里继续偷偷保留 active patch package machine scope
- `TaskPackage` 只允许从当前 active scope 派生，不允许从 merged feedback 回捞 concrete package
- `ImplementationToolLoopExecutor` closure 必须改成 workspace-state 判定，否则本轮不得标记完成

## Final State

完成态必须同时满足：

- `S1` tool loop closure 改成 workspace-state based，repair-mode tool surface 与真实权限完全一致
- `S2` canonical patch package 在 `revision note -> directive parser -> resumed execution state -> active execution state` 单轨收口
- `S3` stage gate / TEST 对 patch scope 只允许 fail-fast 或安全 canonical scope，不再静默扩范围
- `S4` runnable milestone 只有在当前里程碑运行态证据达标时才允许推进
- `S5` 回归矩阵与黄金路径集成测试全部通过
- 当前 active subtask 的 patch package machine owner 只剩 `SubtaskExecutionState.effectiveChanges`
- `TaskPackage` 只保留 active scope 的派生视图职责
- `feedback` 只承载 prose 与辅助约束，不再承担 active patch package owner
- 完成 `self-test + code review + docs`

## Removal Plan

本轮必须删除或封死：

- `ImplementationToolLoopExecutor` 中“declared changes satisfied 必须来自本轮 mutation”的旧语义
- repair-mode 下 whole-file rewrite / Bash 伪回退仍然可见的旧路径
- `ImplementationResumePolicy` 中“persisted PATCH_CONTINUE 优先于 fresh reroute package”的旧语义
- `ExecutionDirectivePayload` / `ExecutionDirectiveFeedbackSupport` / `ImplementationPlanRunner` / `SubtaskRecoverySupport` 中“旧 concrete package 可在 merge 后残留”的旧语义
- feedback channel / merged feedback 继续承担 active patch package owner 的旧语义
- `TestExecutor` 对 `previousFailure.overrideChanges()` / 旧 patch target 的回退复用
- `ImplementationStageGate` 对空 scope / 错 scope 自动回退 `allowedScope` 的旧 fallback
- runnable milestone 对弱壳页面的过早放行路径

## Execution Order

必须按下面顺序推进，不允许跳步：

1. `Scope 1` Tool Loop Convergence Closure
2. `Scope 2` Canonical Patch Package Precedence Closure
3. `Scope 3` Stage Gate Scope Discipline Closure
4. `Scope 4` Runnable Milestone Quality Closure
5. `Scope 5` Regression Matrix + Golden Path Integration

原因：

- 不先改 `Scope 1`，`PATCH_CONTINUE` 仍会卡在假失败
- 不先改 `Scope 2`，fresh package 仍会在 resume / merge 链里丢失
- 不改 `Scope 3`，错 scope 仍会被静默放大
- 不改 `Scope 4`，弱壳页面仍会提前过 milestone
- `Scope 5` 只能在前 4 条都落地后执行，否则集成测试没有诊断价值

## Scope Checklist

### Scope 1. Tool Loop Convergence Closure

- [ ] `ImplementationToolLoopExecutor` 的 declared-change completion 改成 workspace-state 判定
- [ ] 已满足当前 canonical package 终态的文件允许零 mutation 收口
- [ ] `ImplementationToolPromptBuilder` / `ImplementationToolPermissionPolicy` / `ImplementationToolRegistry` 使用同一 repair-mode 工具口径
- [ ] repair-mode 下不再把 Bash 暴露成伪回退路径
- [ ] whole-file rewrite reject 返回结构化 localized-edit 指引
- [ ] `FileEditTool` / `FileWriteTool` / `ToolExecutionContext` 与上面口径一致
- [ ] Scope 1 `self-test`
- [ ] Scope 1 `code review`
- [ ] Scope 1 `docs`

### Scope 2. Canonical Patch Package Precedence Closure

- [ ] `StageRevisionRepairSupport` / `StageRevisionNoteBuilder` 只落 fresh canonical patch package
- [ ] `ImplementationDirectiveResolver` 只解析 fresh package，不再从其他路径补第二份 owner
- [ ] `ExecutionDirectivePayload` / `ExecutionDirectiveFeedbackSupport` merge 不再复活 base concrete package
- [ ] `ImplementationPlanRunner` / `SubtaskRecoverySupport` 不再通过 merged feedback 持有 active patch package
- [ ] stage-level fresh package 只沿 `revision note -> directive parser -> resumed execution state` 进入 implementation
- [ ] 当前 active subtask 的 machine owner 固定为 `SubtaskExecutionState.effectiveChanges`
- [ ] `SubtaskRevisionDirective` 成为 active execution state 的唯一结构化写入口
- [ ] `TaskPackage` / scoped task package 只从当前 active scope 派生，不再承担 machine owner
- [ ] `ImplementationResumePolicy` / `CoderTurnCoordinator` / `ImplementationStageComposer` 对 fresh package precedence 使用同一口径
- [ ] Scope 2 `self-test`
- [ ] Scope 2 `code review`
- [ ] Scope 2 `docs`

### Scope 3. Stage Gate Scope Discipline Closure

- [ ] `ImplementationStageGate` 对空 scope / 完全不相交 scope 直接 fail-fast
- [ ] `ImplementationStageGate` 不再把错 scope 扩成整份 `allowedScope`
- [ ] `TestExecutor` targeted reverification 不再回退复用旧 `overrideChanges`
- [ ] `ExperienceFailureDispositionResolver` 不再把旧 patch target / 旧 scope 当当前 scope 使用
- [ ] 自动 patch continue 只允许在当前轮拥有安全 canonical scope 时发生
- [ ] Scope 3 `self-test`
- [ ] Scope 3 `code review`
- [ ] Scope 3 `docs`

### Scope 4. Runnable Milestone Quality Closure

- [ ] `SubtaskRunnableMilestoneGuard` 不再只靠 smoke/resource/syntax 放行
- [ ] `SubtaskVerificationSupport` 对当前 milestone 的运行态证据使用更强 gate
- [ ] `ImplementationSelfCheckReviewResolver` 与 runnable milestone 新口径保持一致
- [ ] `TestExecutor.shouldRunImplementationFunctionalVerification()` 对 runnable milestone 正确触发更强验证
- [ ] `TestExecutor.scopedImplementationVerificationPlan()` 对 runnable milestone 使用足够强的验证面
- [ ] `ValidationExecutor` / `WebRuntimeWiringValidationSupport` / `WebResourceValidationSupport` 与上面口径一致
- [ ] 弱壳页面在当前子任务内被打回，不再提前推进
- [ ] Scope 4 `self-test`
- [ ] Scope 4 `code review`
- [ ] Scope 4 `docs`

### Scope 5. Regression Matrix And Golden Path Integration

- [ ] `ImplementationToolLoopExecutorTests` 补住 workspace-state closure 回归
- [ ] `FileEditToolTests` / `FileWriteToolTests` 补住 repair-mode localized edit / overwrite 边界
- [ ] `BashToolTests` / `BashToolFailureDiagnosticsTests` 补住 repair-mode Bash 不再承担伪回退
- [ ] `ExecutionDirectiveFeedbackSupportTests` 补住 merge 不复活 base concrete package
- [ ] `ImplementationPlanRunnerTests` 补住 intra-stage feedback merge 不再把旧 concrete package 带入后续 active subtask
- [ ] `ImplementationResumePolicyTests` 补住 fresh reroute package 覆盖 persisted continuation
- [ ] `SubtaskExecutionStateTests` 补住 active patch package 只由 `effectiveChanges` 持有
- [ ] `TaskPackageTests` 补住 scoped task package 只从 active scope 派生
- [ ] 新增 revision note render / parse round-trip 回归
- [ ] `ImplementationStageGateTests` 补住空 scope / 错 scope fail-fast
- [ ] `TestExecutorTests` / `ExperienceFailureDispositionResolverTests` 补住 targeted reverification scope clamp 和 runnable milestone 强验证
- [ ] `SubtaskVerificationSupportTests` / `ImplementationSelfCheckReviewResolverTests` 补住弱壳 milestone 误放行
- [ ] 黄金路径集成测试重跑
- [ ] 如果失败，先把 blocker 与证据回填到本文档，再决定是否继续改代码
- [ ] Scope 5 `self-test`
- [ ] Scope 5 `code review`
- [ ] Scope 5 `docs`

## Current Status

- 当前阶段：`PLAN_REVIEW_PENDING`
- 当前 blocker：`执行 tracker 待 review`
- 当前约束：`禁止兼容层、禁止双轨并存、禁止“后续再清理”`

## Evidence Log

### Scope 1

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

### Scope 2

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

### Scope 3

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

### Scope 4

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

### Scope 5

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

## Completion Gate

- [ ] `S1` tool loop closure 已改成 workspace-state based，repair-mode tool surface 已与权限完全一致
- [ ] `S2` canonical patch package 已在 `revision note -> directive parser -> resumed execution state -> active execution state` 单轨收口
- [ ] `S3` stage gate / TEST 对 patch scope 已只允许 fail-fast 或安全 canonical scope
- [ ] `S4` runnable milestone 已只在当前里程碑运行态证据达标时放行
- [ ] `S5` 回归矩阵与黄金路径集成测试已全部通过
- [ ] active patch package machine owner 已只剩 `SubtaskExecutionState.effectiveChanges`
- [ ] `TaskPackage` 已只保留派生视图职责
- [ ] `feedback` 已不再承担 active patch package owner
- [ ] `self-test + code review + docs` 已全部完成

结果：`NOT_STARTED`
