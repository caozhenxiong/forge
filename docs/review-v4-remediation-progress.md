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

- [x] 新增 `PlanningRequest`
- [x] `ImplementationPlanner.plan()` 切到 `PlanningRequest`
- [x] 新增 `RevisionContext`
- [x] `StageTransitionSupport.rerouteForRevision()` 切到 `RevisionContext`
- [x] `StageRevisionSupport.rerouteForRevision()` 切到 `RevisionContext`
- [x] `FlowDecisionExecutor.apply()` 提取共享 reroute 方法
- [x] 新增 `FlowDecisionExecutorTests`
- [x] Phase 1 `self-test`
- [x] Phase 1 `code review`
- [x] 同步更新方案文档与本文档

### Phase 2. StageReviewer 拆分

- [x] 新增 `DocumentStageReviewer`
- [x] 新增 `ImplementationStageReviewer`
- [x] 新增 `ExecutionStageReviewer`
- [x] `StageReviewer` 退化为 facade
- [x] `consumeLastTelemetry()` 归属明确并落地
- [x] `ReviewConfiguration` 补新 wiring
- [x] `StageReviewerTestSupport` 补新 wiring
- [x] Phase 2 `self-test`
- [x] Phase 2 `code review`
- [x] 同步更新方案文档与本文档

### Phase 3. 隐藏 new / 小修项收尾

- [x] `SupervisorFallbackPolicy` 去隐藏 `new`
- [x] `WorkflowRunLifecycleSupport` fatal guard 提取
- [x] `CoderTurnCoordinator.execute()` 改接 `ImplementationExecutionContext`
- [x] `ImplementationExecutor` / `ImplementationExecutorWiring` 同步改造
- [x] 新增 `CoderTurnCoordinatorTests`
- [x] `QualityRulesLoader` 抽共享严格 reader
- [x] `DiagnosisAgent` 异常日志
- [x] `DocumentStageComposer` 三方法签名统一
- [x] `ContextProjectionSummaryAssembler` 常量提取
- [x] `ImplementationPlanner` 构造器 `new` 移到 wiring / configuration
- [x] Phase 3 `self-test`
- [x] Phase 3 `code review`
- [x] 同步更新方案文档与本文档

## Current Status

- 当前阶段：`Phase 1-3 completed`
- 当前 blocker：`无`
- 当前约束：`禁止兼容层、禁止双轨并存、禁止“后续再清理”`

## Evidence Log

### Phase 1

- commit：`当前工作区已完成 Phase 1 参数对象化收口，待统一 commit`
- self-test：`mvn -q -DskipTests test-compile` 通过；`mvn -q -Dtest=ImplementationPlannerTests,StageTransitionSupportTests,FlowDecisionExecutorTests,DefaultWorkflowEngineTests test` 通过`
- code review：`ImplementationPlanner.plan()` 已收为 `PlanningRequest`；`StageTransitionSupport / StageRevisionSupport / StageRevisionRepairSupport` 已改走 `RevisionContext`；`FlowDecisionExecutor` 的 reroute 分支已收成一个私有入口`
- docs：`tracker 已同步 Phase 1 完成状态`

### Phase 2

- commit：`当前工作区已完成 Phase 2 reviewer 拆分收口，待统一 commit`
- self-test：`mvn -q -DskipTests test-compile` 通过；`mvn -q -Dtest=StageReviewerTests,DefaultWorkflowEngineTests test` 通过`
- code review：`StageReviewer` 已只剩 facade 路由与 telemetry 出口；`DocumentStageReviewer / ImplementationStageReviewer / ExecutionStageReviewer` 三条路径已拆开；`ReviewConfiguration` 与 `StageReviewerHarness` 已同步切到新 wiring`
- docs：`tracker 已同步 Phase 2 完成状态`

### Phase 3

- commit：`当前工作区已完成 Phase 3 收口，待统一 commit`
- self-test：`mvn -q -DskipTests test-compile` 通过；mvn -q -Dtest=ImplementationPlannerTests,StageTransitionSupportTests,FlowDecisionExecutorTests,StageReviewerTests,SupervisorFallbackPolicyTests,SupervisorAgentTests,WorkflowRunLifecycleSupportTests,QualityRulesLoaderTests,DiagnosisAgentTests,CoderTurnCoordinatorTests,StageProgressCoordinatorTests,DefaultWorkflowEngineTests test 通过`
- code review：`SupervisorFallbackPolicy / ImplementationPlanner 构造器中的隐藏装配已移到 wiring；ImplementationExecutor 负责解析并传递 ImplementationExecutionContext；WorkflowRunLifecycleSupport 的 fatal guard 已收成单一 helper；QualityRulesLoader / DiagnosisAgent / DocumentStageComposer / ContextProjectionSummaryAssembler 的尾项已收口`
- docs：`tracker 已同步 Phase 3 完成状态`

## Completion Gate

- [x] `ImplementationPlanner.plan()` 只剩 `PlanningRequest`
- [x] `StageTransitionSupport.rerouteForRevision()` 只剩 `RevisionContext`
- [x] `StageRevisionSupport.rerouteForRevision()` 只剩 `RevisionContext`
- [x] `FlowDecisionExecutor` 中 `RETRY / ROLLBACK / ROUTE_TO_REPAIR` 不再复制 reroute 调用
- [x] `StageReviewer` facade 中不再存在阶段专属逻辑与依赖
- [x] `StageReviewer` telemetry 出口归属已明确且边界清晰
- [x] `SupervisorFallbackPolicy` 构造器无 `new`
- [x] `ImplementationPlanner` 构造器无 `new`
- [x] `WorkflowRunLifecycleSupport` try-catch-markFatal 只在一处定义
- [x] `CoderTurnCoordinator.execute()` 改接 `ImplementationExecutionContext`
- [x] `QualityRulesLoader` 抽重复但不改变 fail-fast 语义
- [x] `DiagnosisAgent` catch Exception 有 `log.warn()`
- [x] `DocumentStageComposer` 三个 compose 方法签名一致
- [x] `ContextProjectionSummaryAssembler` magic numbers 已提取
- [x] `self-test + code review + docs + tracker` 已全部补齐

结果：`COMPLETED`
