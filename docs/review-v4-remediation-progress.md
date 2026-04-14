# Review V4 收口进度

## Purpose

这份文档是 `REVIEW_V4_REMEDIATION_PLAN.md` 的唯一执行 tracker。

规则：

- 只跟踪本轮 `REVIEW_V4` 收口，不混写 `V3` 收尾或集成测试结论
- 每个 phase 开始前先更新本文档
- 每完成一项，立即打勾并补证据
- 证据固定写：`commit / self-test / code review / docs`
- 如果 blocker 变化，先更新本文档，再继续改代码

## Final State

完成态必须同时满足：

- `ImplementationPlanner.plan()` 只剩 `PlanningRequest`
- `StageTransitionSupport.rerouteForRevision()` 与 `StageRevisionSupport.rerouteForRevision()` 只剩 `RevisionContext`
- `FlowDecisionExecutor` 不再复制 reroute 调用
- `StageReviewer` 只保留 facade 路由与 telemetry 出口
- 三条 reviewer 路径已拆为 `DocumentStageReviewer / ImplementationStageReviewer / ExecutionStageReviewer`
- `ReviewConfiguration` 与 `StageReviewerTestSupport` 已切到新 reviewer 骨架
- `SupervisorFallbackPolicy` / `ImplementationPlanner` 构造器中无隐藏 `new`
- `WorkflowRunLifecycleSupport` fatal guard 只在一处定义
- `CoderTurnCoordinator.execute()` 改接 `ImplementationExecutionContext`
- `QualityRulesLoader` 已抽重复但 fail-fast 语义保持不变
- `DiagnosisAgent` fallback 异常已可观测
- `DocumentStageComposer` 三个具名 compose 方法签名一致
- `ContextProjectionSummaryAssembler` 不再保留 magic numbers

## Removal Plan

本轮必须删除：

- `ImplementationPlanner.plan(...)` 的 20 参数入口
- `StageTransitionSupport.rerouteForRevision(...)` 的长参数入口
- `StageRevisionSupport.rerouteForRevision(...)` 的长参数入口
- `FlowDecisionExecutor.apply()` 中重复的 reroute 分支
- `StageReviewer` facade 中的阶段专属逻辑与依赖
- `SupervisorFallbackPolicy` 构造器中的隐藏 `new`
- `ImplementationPlanner` 构造器中的隐藏 `new`
- `WorkflowRunLifecycleSupport` 中重复 try-catch-markFatal 片段
- `QualityRulesLoader` 中重复的严格 property reader 片段
- `DocumentStageComposer` 中不一致的 compose 签名
- `ContextProjectionSummaryAssembler` 中散落的 char 预算常量

## Phase Checklist

### Phase 1. 参数对象化

- [ ] 新增 `PlanningRequest`
- [ ] `ImplementationPlanner.plan()` 切到 `PlanningRequest`
- [ ] 新增 `RevisionContext`
- [ ] `StageTransitionSupport.rerouteForRevision()` 切到 `RevisionContext`
- [ ] `StageRevisionSupport.rerouteForRevision()` 切到 `RevisionContext`
- [ ] `FlowDecisionExecutor.apply()` 提取共享 reroute 方法
- [ ] 新增 `FlowDecisionExecutorTests`
- [ ] Phase 1 `self-test`
- [ ] Phase 1 `code review`
- [ ] 同步更新方案文档与本文档

### Phase 2. StageReviewer 拆分

- [ ] 新增 `DocumentStageReviewer`
- [ ] 新增 `ImplementationStageReviewer`
- [ ] 新增 `ExecutionStageReviewer`
- [ ] `StageReviewer` 退化为 facade
- [ ] `consumeLastTelemetry()` 归属明确并落地
- [ ] `ReviewConfiguration` 补新 wiring
- [ ] `StageReviewerTestSupport` 补新 wiring
- [ ] Phase 2 `self-test`
- [ ] Phase 2 `code review`
- [ ] 同步更新方案文档与本文档

### Phase 3. 隐藏 new / 小修项收尾

- [ ] `SupervisorFallbackPolicy` 去隐藏 `new`
- [ ] `WorkflowRunLifecycleSupport` fatal guard 提取
- [ ] `CoderTurnCoordinator.execute()` 改接 `ImplementationExecutionContext`
- [ ] `ImplementationExecutor` / `ImplementationExecutorWiring` 同步改造
- [ ] 新增 `CoderTurnCoordinatorTests`
- [ ] `QualityRulesLoader` 抽共享严格 reader
- [ ] `DiagnosisAgent` 异常日志
- [ ] `DocumentStageComposer` 三方法签名统一
- [ ] `ContextProjectionSummaryAssembler` 常量提取
- [ ] `ImplementationPlanner` 构造器 `new` 移到 wiring / configuration
- [ ] Phase 3 `self-test`
- [ ] Phase 3 `code review`
- [ ] 同步更新方案文档与本文档

## Current Status

- 当前阶段：`PLAN_REVIEW_PENDING`
- 当前 blocker：`无`
- 当前约束：`禁止兼容层、禁止双轨并存、禁止“后续再清理”`

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

- [ ] `ImplementationPlanner.plan()` 只剩 `PlanningRequest`
- [ ] `StageTransitionSupport.rerouteForRevision()` 只剩 `RevisionContext`
- [ ] `StageRevisionSupport.rerouteForRevision()` 只剩 `RevisionContext`
- [ ] `FlowDecisionExecutor` 中 `RETRY / ROLLBACK / ROUTE_TO_REPAIR` 不再复制 reroute 调用
- [ ] `StageReviewer` facade 中不再存在阶段专属逻辑与依赖
- [ ] `StageReviewer` telemetry 出口归属已明确且边界清晰
- [ ] `SupervisorFallbackPolicy` 构造器无 `new`
- [ ] `ImplementationPlanner` 构造器无 `new`
- [ ] `WorkflowRunLifecycleSupport` try-catch-markFatal 只在一处定义
- [ ] `CoderTurnCoordinator.execute()` 改接 `ImplementationExecutionContext`
- [ ] `QualityRulesLoader` 抽重复但不改变 fail-fast 语义
- [ ] `DiagnosisAgent` catch Exception 有 `log.warn()`
- [ ] `DocumentStageComposer` 三个 compose 方法签名一致
- [ ] `ContextProjectionSummaryAssembler` magic numbers 已提取
- [ ] `self-test + code review + docs + tracker` 已全部补齐

结果：`IN_PROGRESS`
