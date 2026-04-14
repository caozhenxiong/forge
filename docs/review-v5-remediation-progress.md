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
- `StageProgressCoordinator` 不再直接依赖 `toolResultLoader`、`toolResultGuard`、`implementationStateSupport`、`implementationContinuationSupport`
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

- [ ] `SupervisorAgent.decide()` 签名增加 `ProjectedContext` 参数
- [ ] `SupervisorAgent.decide()` 内部移除 `contextProjector.project()` 调用
- [ ] `StageProgressCoordinator.progress()` 传 `projectedContext` 进 `supervisorAgent.decide()`
- [ ] `FlowController.shouldContinue()` 补 `log.warn`
- [ ] `maxAutoRevisions` 检查提取为 `StageStatusSupport` 辅助方法
- [ ] `StageTransitionSupport.continueStage()` 切到辅助方法
- [ ] `StageRevisionSupport.rerouteForRevision()` 切到辅助方法
- [ ] `ImplementationExecutor` 删除 3 个短签名重载
- [ ] `ImplementationStageComposer` 与相关测试调用点同步更新
- [ ] 新增 `FlowControllerTests`
- [ ] Phase 1 `self-test`
- [ ] Phase 1 `code review`
- [ ] 同步更新方案文档与本文档

### Phase 2. SupervisorAgent 决策骨架统一

- [ ] 提取私有骨架方法（`decide()` 与 `decideGenerationFailure()` 共用）
- [ ] `decide()` 改用骨架方法
- [ ] `decideGenerationFailure()` 改用骨架方法
- [ ] Phase 2 `self-test`
- [ ] Phase 2 `code review`
- [ ] 同步更新方案文档与本文档

### Phase 3. StageProgressCoordinator 拆分

- [ ] 新增 `StageToolResultGate`（封装 `toolResultLoader` + `toolResultGuard`）
- [ ] `diagnosisAgent.shouldDiagnose()` 调用迁移（进 Gate 或独立 detector）
- [ ] implementation 特殊路径内聚（`implementationStateSupport` + `implementationContinuationSupport`）
- [ ] `StageProgressCoordinator` 不再直接持有工具结果与 implementation continuation 细节协作者
- [ ] `OrchestratorConfiguration` 补新 bean 装配
- [ ] 新增 `StageToolResultGateTests`
- [ ] Phase 3 `self-test`
- [ ] Phase 3 `code review`
- [ ] 同步更新方案文档与本文档

## Current Status

- 当前阶段：`PLAN_UPDATED_FOR_REVIEW`
- 当前 blocker：`无`
- 当前约束：`禁止兼容层、禁止双轨并存、禁止"后续再清理"`

## Evidence Log

### Phase 1

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

### Phase 2

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

### Phase 3

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

## Completion Gate

- [ ] `SupervisorAgent.decide()` 不再内部调用 `contextProjector.project()`
- [ ] `StageProgressCoordinator.progress()` 只调用一次 `contextProjector.project()`
- [ ] `FlowController.shouldContinue()` 在 `currentStage == null` 时有 `log.warn()`
- [ ] `maxAutoRevisions` 超限检查只在一处定义
- [ ] `ImplementationExecutor` 只剩一个规范 `execute()` 入口
- [ ] `SupervisorAgent.decide()` 与 `decideGenerationFailure()` 共用同一私有控制骨架
- [ ] `StageProgressCoordinator` 不再直接依赖 `toolResultLoader`
- [ ] `StageProgressCoordinator` 不再直接依赖 `toolResultGuard`
- [ ] `StageProgressCoordinator` 不再直接依赖 `implementationStateSupport`
- [ ] `StageProgressCoordinator` 不再直接依赖 `implementationContinuationSupport`
- [ ] `StageToolResultGate` 已提取并装配
- [ ] `self-test + code review + docs + tracker` 已全部补齐

结果：`IN_PROGRESS`
