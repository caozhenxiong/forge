# Review V5 收口进度

## Purpose

这份文档是 `REVIEW_V5_REMEDIATION_PLAN.md` 的唯一执行 tracker。

规则：

- 只跟踪本轮 `REVIEW_V5` 收口，不混写其他轮次内容
- 每个 phase 开始前先更新本文档
- 每完成一项，立即打勾并补证据
- 证据固定写：`commit / self-test / code review / docs`
- 如果 blocker 变化，先更新本文档，再继续改代码

## Final State

完成态必须同时满足：

- `SupervisorAgent.decide()` 不再内部调用 `contextProjector.project()`
- `StageProgressCoordinator.progress()` 只调用一次 `contextProjector.project()`
- `FlowController.shouldContinue()` 在 `currentStage == null` 时有 `log.warn()`
- `maxAutoRevisions` 超限检查只在一处定义
- `ImplementationExecutor` 只剩一个规范 `execute()` 入口
- `SupervisorAgent.decide()` 与 `decideGenerationFailure()` 共用同一私有控制骨架
- `StageProgressCoordinator` 不再直接依赖 `toolResultLoader`、`toolResultGuard`、`diagnosisAgent`、`implementationStateSupport`、`implementationContinuationSupport`
- `StageToolResultGate` 已提取并装配

## Removal Plan

本轮必须删除：

- `SupervisorAgent.decide()` 内部的 `contextProjector.project()` 调用
- `StageTransitionSupport.continueStage()` 中的 `maxAutoRevisions` 判断片段
- `StageRevisionSupport.rerouteForRevision()` 中的 `maxAutoRevisions` 判断片段
- `ImplementationExecutor` 中 3 个短签名重载
- `StageProgressCoordinator` 中直接注入的 `toolResultLoader`、`toolResultGuard`、`implementationStateSupport`、`implementationContinuationSupport` 字段

## Phase Checklist

### Phase 1. ContextProjector 单次投影 + 小修项

- [x] `SupervisorAgent.decide()` 签名增加 `ProjectedContext` 参数
- [x] `SupervisorAgent.decide()` 内部移除 `contextProjector.project()` 调用
- [x] `StageProgressCoordinator.progress()` 传 `projectedContext` 进 `supervisorAgent.decide()`
- [x] `FlowController.shouldContinue()` 补 `log.warn`
- [x] `maxAutoRevisions` 检查提取为 `StageStatusSupport` 辅助方法
- [x] `StageTransitionSupport.continueStage()` 切到辅助方法
- [x] `StageRevisionSupport.rerouteForRevision()` 切到辅助方法
- [x] `ImplementationExecutor` 删除 3 个短签名重载
- [x] `ImplementationStageComposer` 与相关测试调用点同步更新
- [x] 扩展 `FlowControllerTests`
- [x] Phase 1 `self-test`
- [x] Phase 1 `code review`
- [x] 同步更新方案文档与本文档

### Phase 2. SupervisorAgent 决策骨架统一

- [x] 提取私有骨架方法（`decide()` 与 `decideGenerationFailure()` 共用）
- [x] `decide()` 改用骨架方法
- [x] `decideGenerationFailure()` 改用骨架方法
- [x] Phase 2 `self-test`
- [x] Phase 2 `code review`
- [x] 同步更新方案文档与本文档

### Phase 3. StageProgressCoordinator 拆分

- [x] 新增 `StageToolResultGate`（封装 `toolResultLoader` + `toolResultGuard`）
- [x] 新增 `RepeatIssueDetector`（封装 `diagnosisAgent.shouldDiagnose()`）
- [x] implementation 特殊路径内聚（`implementationStateSupport` + `implementationContinuationSupport`）
- [x] `StageProgressCoordinator` 不再直接持有工具结果与 implementation continuation 细节协作者
- [x] `OrchestratorConfiguration` 补新 bean 装配
- [x] 新增 `StageToolResultGateTests`
- [x] 新增 `RepeatIssueDetectorTests`
- [x] Phase 3 `self-test`
- [x] Phase 3 `code review`
- [x] 同步更新方案文档与本文档

## Current Status

- 当前阶段：`IMPLEMENTED_SELF_TESTED_PENDING_COMMIT`
- 当前 blocker：`本轮范围无 blocker；全量 mvn -q test 仍有仓库既有失败：WorkspaceSnapshotStoreTests / StageOperationPolicyTests / PatchFailureRouterTests / GenerationBudgetProfileTests`
- 当前约束：`禁止兼容层、禁止双轨并存、禁止"后续再清理"`

## Evidence Log

### Phase 1

- commit：`待开始`
- self-test：`mvn -q -DskipTests compile` / `mvn -q -DskipTests test-compile` / `mvn -q -Dtest=FlowControllerTests,StageStatusSupportTests,StageToolResultGateTests,RepeatIssueDetectorTests,SupervisorAgentTests,StageTransitionSupportTests,StageProgressCoordinatorTests,ImplementationExecutorTests,DefaultWorkflowEngineTests test` 通过
- code review：`git diff --check` 通过；`rg` 确认 `StageProgressCoordinator` 已移除对 `toolResultLoader` / `toolResultGuard` / `diagnosisAgent` / `implementationStateSupport` / `implementationContinuationSupport` 的直接依赖
- docs：`已同步更新方案文档与 tracker`

### Phase 2

- commit：`待开始`
- self-test：`包含在同一轮 compile / test-compile / 定向单测`
- code review：`已检查共享骨架仅复用控制流，不引入类型层级兼容`
- docs：`已同步更新方案文档与 tracker`

### Phase 3

- commit：`待开始`
- self-test：`包含在同一轮 compile / test-compile / 定向单测`
- code review：`已检查 coordinator 新依赖面、bean 装配与新增单测覆盖`
- docs：`已同步更新方案文档与 tracker`

## Repo Baseline Note

- `mvn -q test` 已补跑，但失败不在本轮改动面内。
- 当前全量失败项：`WorkspaceSnapshotStoreTests.reviewChangePackListsAllChangedPathsAndMarksTruncation`
- 当前全量失败项：`StageOperationPolicyTests.reviewTimeoutCanBeOverriddenFromSystemProperty`
- 当前全量失败项：`PatchFailureRouterTests.customRoutingSettingsCanFurtherClampUnsplittableAttempts`
- 当前全量失败项：`GenerationBudgetProfileTests.documentFullDraftOutputRatioCanBeOverriddenBySystemProperty`
- 当前全量失败项：`GenerationBudgetProfileTests.embeddedPatchKindReadsRuntimeBudgetOverrides`
- 当前全量失败项：`GenerationBudgetProfileTests.wholeFileRewriteUsesConfiguredOutputRatio`

## Completion Gate

- [x] `SupervisorAgent.decide()` 不再内部调用 `contextProjector.project()`
- [x] `StageProgressCoordinator.progress()` 只调用一次 `contextProjector.project()`
- [x] `FlowController.shouldContinue()` 在 `currentStage == null` 时有 `log.warn()`
- [x] `maxAutoRevisions` 超限检查只在一处定义
- [x] `ImplementationExecutor` 只剩一个规范 `execute()` 入口
- [x] `SupervisorAgent.decide()` 与 `decideGenerationFailure()` 共用同一私有控制骨架
- [x] `StageProgressCoordinator` 不再直接依赖 `toolResultLoader`
- [x] `StageProgressCoordinator` 不再直接依赖 `toolResultGuard`
- [x] `StageProgressCoordinator` 不再直接依赖 `diagnosisAgent`
- [x] `StageProgressCoordinator` 不再直接依赖 `implementationStateSupport`
- [x] `StageProgressCoordinator` 不再直接依赖 `implementationContinuationSupport`
- [x] `StageToolResultGate` 已提取并装配
- [x] `RepeatIssueDetector` 已提取并装配
- [x] `self-test + code review + docs + tracker` 已全部补齐

结果：`DONE_PENDING_COMMIT`
