# Implementation Stage Closure Execution

## Purpose

这份文档是 `docs/implementation-stage-closure-plan.md` 的**唯一执行 tracker**。

它只负责：

- 按 phase 跟踪执行顺序
- 把 `trace` 中的问题族落实成可打勾任务
- 记录证据：
  `commit / self-test / code review / docs`
- 在开始实现前先把当前执行状态写清楚

`trace` 单独维护在：

- `docs/implementation-stage-closure-trace.md`

## Rules

- 只跟踪本轮 implementation-stage closure，不混写其他整改
- 每个 phase 开始前先更新本文档
- 每完成一项，立即打勾
- 如果 blocker 变化，先更新本文档，再继续改代码
- 不允许把“后续再清理”写进 checklist
- 本文档通过审阅后，才进入代码实现

## Final State

完成态必须同时满足：

- `T1` capability partition 与 task package fallback 收口
- `T2` accepted package completeness 在 planning 前置拦截
- `T3` subtask structured review 能稳定产出并消费 boundary typed payload
- `T4` canonical repair package 进入 `implementation_state` 写侧协议链，并能 round-trip 到 continuation
- `T5` repair-mode patch-first 收敛到 permission + tool-context + tool implementation 全链
- `T6` repair reroute 的状态、artifact、event、re-entry 全链一致
- `R1 ~ R7` 全部有对应回归
- 完成 `self-test + code review`
- 再跑黄金路径集成测试

## Removal Plan

本轮必须删除或封死：

- `ownedCapabilities <- acceptanceCriteria` fallback
- `TaskPackage.alignToSubtask()` capability 回灌
- runtime split package 不完整仍能进入执行
- subtask structured review 在 `.result()` 处 flatten 后再靠 prose 判边界
- continuation repair package 停在 support / markdown 层，不进入 `implementation_state`
- repair-mode 只在 prompt/permission 收紧，但工具层仍可 whole rewrite
- repair reroute 已成立但 run/stage 最终状态仍可落成 approved/completed

## Phase Checklist

### Phase 1. Planning Input / Wiring / Gate

- [x] `T1` 删除 `ImplementationPlanNormalizationSupport` 的 capability fallback
- [x] `T2` 把 planning runtime facts 接入 `ImplementationPlanGateInput`
- [x] `T2` 把 planning runtime facts 装配集中到 `ImplementationPlanGateInputBuilder`
- [x] `T2` `ImplementationPlanningWiring` 与 `ImplementationPlanner` 切到单一 planning runtime facts 输入
- [x] `T2` `ImplementationOutlineGate` / `ImplementationPlanChangeGate` / `ImplementationSubtaskDetailGate` 接 accepted package completeness gate
- [x] `T2` 明确禁止目录扫描、root-script 猜测、companion 文件名 fallback
- [x] Phase 1 `self-test`
- [x] Phase 1 `code review`
- [x] Phase 1 `docs`

### Phase 2. Task Package And Coder Input

- [x] `T1` 删除 `TaskPackage.alignToSubtask()` 的 capability 回灌
- [x] `T1` `TaskPackageAssembler` 与 `TaskPackageMarkdownRenderer` 切到 canonical capability partition
- [x] `T1` `ImplementationToolPromptBuilder` 与 task package 对齐，不再展示旧能力边界
- [x] Phase 2 `self-test`
- [x] Phase 2 `code review`
- [x] Phase 2 `docs`

### Phase 3. Subtask Structured Review Boundary Gate

- [x] `T3` 明确 subtask-only typed payload 的真实类型边界
- [x] `T3` `LlmProvider` 能承载 subtask structured review typed payload
- [x] `T3` `OllamaStructuredReviewExecutor` 接入对应 typed payload schema
- [x] `T3` `SubtaskVerificationSupport` 不再直接 flatten `.result()`，而是先消费 typed payload
- [x] `T3` deterministic boundary gate 对 deferred / foreign capability 越权直接驳回
- [x] Phase 3 `self-test`
- [x] Phase 3 `code review`
- [x] Phase 3 `docs`

### Phase 4. Runtime Repair Package / Resume Round-Trip

- [x] `T4` canonical repair package 从 `ImplementationStageStatus` 开始形成
- [x] `T4` `ImplementationStateSnapshotSerializer` 把 canonical repair package 写入 `implementation_state`
- [x] `T4` `ImplementationStateSnapshot` / `ImplementationStateCodec` 完整承载并校验 required fields
- [x] `T4` `ImplementationArtifactPersister` 先写 `implementation_state`，再写派生展示物
- [x] `T4` `ImplementationStateArtifactSupport` / `ImplementationContinuationSupport` 从结构化 payload 恢复 continuation
- [x] `T4` `SubtaskRuntimeWiringGuard` 复用 `RuntimeWiringRetryChangeFactory`
- [x] `T4` `ImplementationResumePolicy` 只消费 canonical repair package，不再自行重建
- [x] Phase 4 `self-test`
- [x] Phase 4 `code review`
- [x] Phase 4 `docs`

### Phase 5. Repair-Mode Permission / Tool Enforcement

- [x] `T5` `ImplementationToolPermissionPolicy` 接入 repair/resume mode
- [x] `T5` `ImplementationToolLoopExecutor` 显式把 repair/resume mode 传给 permission policy
- [x] `T5` `ImplementationToolContext` 收紧 whole rewrite / fresh read enforcement
- [x] `T5` `ToolExecutionContext` 保持同一组工具级不变量
- [x] `T5` `FileEditTool` 对已有文件继续严格执行 full Read / stale Read / whole rewrite 限制
- [x] `T5` `FileWriteTool` 对已有文件继续严格执行 full Read / whole rewrite 限制
- [x] `T5` `BashTool` 对 deny payload、`pathIntents` diagnostics、pre/post validation 全链同步 patch-first 约束
- [x] `T5` `ShellCommandAnalyzer` 与 `BashTool` 行为对齐
- [x] Phase 5 `self-test`
- [x] Phase 5 `code review`
- [x] Phase 5 `docs`

### Phase 6. Run-State Consistency And Repair Re-entry

- [x] `T6` `FlowDecisionExecutor` / `StageTransitionSupport` / `StageStatusSupport` 对 repair route 保持同一最终状态
- [x] `T6` `StageRevisionSupport` / `StageRevisionRepairSupport` 对 review history、event、revision note、repair brief 口径一致
- [x] `T6` `StageProgressArtifactSupport` 对 transition artifact 与 event 口径一致
- [x] `T6` `StageEntryExecutor` 对 stage re-entry、attempt 推进、artifactPath 持久化口径一致
- [x] `T6` `run.json`、artifact、`events.log` 最终一致
- [x] Phase 6 `self-test`
- [x] Phase 6 `code review`
- [x] Phase 6 `docs`

### Phase 7. Regression Matrix

- [x] `R1` skeleton capability boundary
- [x] `R2` runtime split package completeness
- [x] `R3` CONTINUE_SUBTASKS repair package scope clamp
- [x] `R4` shell deny pathIntents diagnostics
- [x] `R5` tool-level full Read / stale Read invariants
- [x] `R6` typed payload round-trip
- [x] `R7` run-state consistency
- [x] Phase 7 `self-test`
- [x] Phase 7 `code review`
- [x] Phase 7 `docs`

### Phase 8. Golden Path Integration

- [ ] 跑黄金路径集成测试
- [ ] 根据结果更新 `docs/current-state.md`
- [ ] 根据结果更新本文档
- [ ] 根据结果更新 `docs/active-work-items.md`（若主线状态发生变化）

## Current Status

- 当前阶段：`PHASE_7_REOPENED_BY_REVIEW_V7`
- 当前 blocker：`review-v7 指出的 4 个缺口尚未收口：capability partition gate、mixed runtime-root package completeness、PATCH_RUNTIME_WIRING retry scope、repair-mode shell deny pathIntents`
- 当前约束：`禁止兼容层、禁止 fallback、禁止双轨并存、禁止“后续再清理”`
- 当前执行入口：`先完成 docs/review-v7-remediation-plan.md，再恢复 Phase 8：golden path integration`

## Evidence Log

### Phase 1

- commit：`待提交`
- self-test：`mvn -q -Dtest=ImplementationPlanNormalizationSupportTests,ImplementationPlanGateTests,ImplementationSubtaskDetailGateTests,ImplementationPlannerTests,ImplementationToolPromptBuilderTests,CoderTurnCoordinatorTests,PlanningRuntimeFactsResolverTests test`
- code review：`已完成自查；核对了 planning runtime facts owner、accepted package completeness gate、task package capability partition，无新增 fallback / 双轨 / 目录扫描路径`
- docs：`本文档已更新`

### Phase 2

- commit：`待提交`
- self-test：`同 Phase 1`
- code review：`已完成自查；确认 TaskPackage / coder prompt 只消费 canonical capability partition，不再回灌旧能力`
- docs：`本文档已更新`

### Phase 3

- commit：`待提交`
- self-test：`mvn -q -Dtest=PlanningRuntimeFactsResolverTests,ImplementationPlanNormalizationSupportTests,ImplementationPlanGateTests,ImplementationSubtaskDetailGateTests,ImplementationPlannerTests,ImplementationToolPromptBuilderTests,CoderTurnCoordinatorTests,SubtaskBoundaryGateTests,OllamaLlmProviderTests test`
- code review：`已完成自查；确认 subtask boundary 语义只落在 StructuredReviewResult -> SubtaskBoundaryReviewPayload -> SubtaskBoundaryGate，本地 gate 消费 typed payload，不再 flatten 回 prose`
- docs：`本文档已更新`

### Phase 4

- commit：`implement runtime repair package round-trip`
- self-test：`mvn -q -Dtest=SubtaskRuntimeWiringGuardTests,ImplementationResumePolicyTests,ImplementationStateArtifactSupportTests,ImplementationContinuationSupportTests,ImplementationStageGateTests,StageProgressCoordinatorTests test`
- code review：`已完成自查；确认 SubtaskRuntimeWiringGuard 直接复用 RuntimeWiringRetryChangeFactory；ImplementationResumePolicy 只消费 continuation overrideChanges，不再从 contract gate 反推 runtime wiring scope；ImplementationStateCodec / ImplementationContinuationSupport 对“具体 patch 但无 canonical overrideChanges”的状态直接判无效`
- docs：`本文档已更新`

### Phase 5

- commit：`implement repair mode permission enforcement`
- self-test：`mvn -q -Dtest=ImplementationToolPermissionPolicyTests,ImplementationToolRegistryTests,FileEditToolTests,FileWriteToolTests,BashToolTests,BashToolFailureDiagnosticsTests,ImplementationToolLoopExecutorTests test`
- code review：`已完成自查；确认 repairMode 只由 ImplementationToolLoopExecutor -> ImplementationToolPermissionContext 单链注入；ImplementationToolContext 不再通过 deliveryMode==REWORK 直接放行 whole rewrite；tool loop 的 owned scope、prompt 文件契约、交付校验全部切到 executionState.effectiveChanges`
- docs：`本文档已更新`

### Phase 6

- commit：`lock repair reroute consistency regressions`
- self-test：`mvn -q -Dtest=StageTransitionSupportTests,StageProgressCoordinatorTests,FlowDecisionExecutorTests,StageEntryExecutorTests,StageStatusSupportTests test`
- code review：`已完成自查；确认 repair route 的 transition artifact、reroute event、repair brief、stage directive、run.json 在 CODE_REVIEW/TEST -> IMPLEMENTATION repair 路径上由单链收尾；StageProgressCoordinator 级回归同时锁死“重入 implementation 第 2 次尝试”的真实状态`
- docs：`本文档已更新`

### Phase 7

- commit：`lock repair reroute consistency regressions`
- self-test：`mvn -q -Dtest=ImplementationPlanNormalizationSupportTests,ImplementationPlanGateTests,ImplementationSubtaskDetailGateTests,ImplementationPlannerTests,ImplementationToolPromptBuilderTests,CoderTurnCoordinatorTests,PlanningRuntimeFactsResolverTests,SubtaskBoundaryGateTests,OllamaLlmProviderTests,SubtaskRuntimeWiringGuardTests,ImplementationResumePolicyTests,ImplementationStateArtifactSupportTests,ImplementationContinuationSupportTests,ImplementationStageGateTests,ImplementationToolPermissionPolicyTests,ImplementationToolRegistryTests,FileEditToolTests,FileWriteToolTests,BashToolTests,BashToolFailureDiagnosticsTests,ImplementationToolLoopExecutorTests,StageTransitionSupportTests,StageProgressCoordinatorTests,FlowDecisionExecutorTests,StageEntryExecutorTests,StageStatusSupportTests test`
- code review：`已完成自查；确认 R1-R7 回归仍只消费各 phase 已收口的 canonical owner，没有回灌旧 fallback、旧 capability 边界或 support 层临时 repair package`
- docs：`本文档已更新`

### Phase 8

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

## Completion Gate

- [x] `T1` capability partition 与 task package fallback 已收口
- [x] `T2` accepted package completeness 已前置拦截
- [x] `T3` subtask structured review typed payload 已落到真实协议边界
- [x] `T4` canonical repair package 已进入 `implementation_state` 单一真相源并可 round-trip
- [x] `T5` patch-first 已锁死在 permission + tool-context + tool implementation
- [x] `T6` repair reroute 的状态、artifact、event、re-entry 已全链一致
- [x] `R1 ~ R7` 全部通过
- [x] `self-test + code review + docs` 全部补齐
- [ ] 黄金路径集成测试已执行

结果：`READY_FOR_PHASE_8`
