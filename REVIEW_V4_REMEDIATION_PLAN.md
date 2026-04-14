# Claude Review 收口方案 V4

## Purpose

本文件定义 `REVIEW_V4.md` 的唯一收口方案。

执行时必须同步维护：

- `docs/review-v4-remediation-progress.md` 作为本轮唯一 tracker
- 每进入一个 phase 前先更新 tracker
- 每完成一个 phase，必须补 `self-test / code review / docs / commit` 证据

## Summary

本轮收口 `REVIEW_V4.md` 中的 P1 + P2，不做 P3 中与本轮主线无关的独立改造，也不顺手带上可观测性或上下文压缩升级。

核心主线有 4 条：

1. 参数对象化：`ImplementationPlanner` / `StageTransitionSupport` / `StageRevisionSupport` / `FlowDecisionExecutor`
2. `StageReviewer` 策略模式拆分：God Class → 三个 reviewer 策略 + 薄 facade
3. 隐藏 `new` 与重复流程收口：`SupervisorFallbackPolicy` / `ImplementationPlanner` / `WorkflowRunLifecycleSupport`
4. 小修项：`CoderTurnCoordinator`、`QualityRulesLoader`、`DiagnosisAgent`、`DocumentStageComposer` 签名、`ContextProjectionSummaryAssembler` 常量

## Scope

**纳入本轮**：

- P1 全部（4 条）
- P2 全部（7 条）
- P3 中仅纳入直接阻塞本轮收口的一条：
  - `ImplementationPlanner` 构造器中的隐藏 `new` 移出到 wiring / configuration

**不纳入本轮**：

- `QualityRulesLoader` 路径配置化
- 可观测性（AgentTurnLoop tracing / Micrometer / event timeline 增强）
- `ContextCompactor` 语义摘要器
- 其他与 V4 主线无关的 executor 内核升级

## Final State

完成态必须同时满足：

- `ImplementationPlanner.plan()` 不再平铺 20 个参数，只接收 `PlanningRequest`
- `StageTransitionSupport.rerouteForRevision()` 与 `StageRevisionSupport.rerouteForRevision()` 同时切到 `RevisionContext`
- `FlowDecisionExecutor.apply()` 中 `RETRY_STAGE / ROLLBACK_STAGE / ROUTE_TO_REPAIR` 不再复制 reroute 调用
- `StageReviewer` 不再持有阶段专属护栏和执行器，只保留 facade 路由与 telemetry 出口
- `StageReviewer` 的三个执行路径分别由 `DocumentStageReviewer`、`ImplementationStageReviewer`、`ExecutionStageReviewer` 独立承载
- `ReviewConfiguration` 与 `StageReviewerTestSupport` 已切到新 reviewer 骨架，没有旧依赖残留
- `SupervisorFallbackPolicy` 构造器不再 `new` 协作者
- `WorkflowRunLifecycleSupport` 的 fatal catch/mark 模式只在一个辅助方法中定义
- `CoderTurnCoordinator.execute()` 改为接收 `ImplementationExecutionContext`
- `ImplementationExecutor` / `ImplementationExecutorWiring` 已同步收敛 context 创建与传递
- `QualityRulesLoader` 重复属性读取逻辑被提取，但保持当前 fail-fast 语义，不引入默认值兜底
- `DiagnosisAgent` 的 fallback 路径在异常时会 `log.warn()`
- `DocumentStageComposer` 继续保留三个具名 compose 方法，但三个方法签名完全一致：`(Path projectPath, RunRecord runRecord, String note)`
- `ContextProjectionSummaryAssembler` 的摘要预算不再散落 magic number
- `ImplementationPlanner` 构造器中的 Gate / Assembler `new` 已移到 wiring / configuration，planner 本体不再藏装配

## Removal Plan

本轮必须删除：

- `ImplementationPlanner.plan(...)` 的 20 参数旧签名
- `StageTransitionSupport.rerouteForRevision(...)` 的长参数旧签名
- `StageRevisionSupport.rerouteForRevision(...)` 的长参数旧签名
- `FlowDecisionExecutor.apply()` 中重复的 reroute 分支实现
- `StageReviewer` facade 内部按阶段分支的业务逻辑
- `StageReviewer` facade 中阶段专属依赖字段
- `SupervisorFallbackPolicy` 构造器里的隐藏 `new`
- `ImplementationPlanner` 构造器里的隐藏 `new`
- `WorkflowRunLifecycleSupport` 三处重复 try-catch-markFatal 片段
- `QualityRulesLoader` 中三段重复的严格读取逻辑
- `DocumentStageComposer` 不一致的 `composeAnalysis` 签名
- `ContextProjectionSummaryAssembler` 中散落的 `1800 / 2200` 常量

## Joint-Change Scope

这轮必须一起改，否则会形成半成品：

- `executor/implementation/planning`
  - `ImplementationPlanner`
  - 新增 `PlanningRequest`
- `executor/implementation`
  - `CoderTurnCoordinator`
  - `ImplementationExecutor`
  - `ImplementationExecutorWiring`
  - 必要时新增 planning wiring bean
- `orchestrator`
  - `StageTransitionSupport`
  - `StageRevisionSupport`
  - `FlowDecisionExecutor`
  - `WorkflowRunLifecycleSupport`
  - 新增 `RevisionContext`
- `review`
  - `StageReviewer`
  - 新增 `DocumentStageReviewer`
  - 新增 `ImplementationStageReviewer`
  - 新增 `ExecutionStageReviewer`
  - `ReviewConfiguration`
  - `StageReviewerTestSupport`
- `supervisor`
  - `SupervisorFallbackPolicy`
  - `SupervisorConfiguration`
- `quality`
  - `QualityRulesLoader`
- `repair`
  - `DiagnosisAgent`
- `artifact`
  - `DocumentStageComposer`
  - `StageArtifactComposer`
- `context`
  - `ContextProjectionSummaryAssembler`

联动测试范围必须至少覆盖：

- `StageReviewerTests`
- `StageTransitionSupportTests`
- `DefaultWorkflowEngineTests`
- `ImplementationPlannerTests`
- `DiagnosisAgentTests`
- `QualityRulesLoaderTests`
- 新增 `FlowDecisionExecutorTests`
- 新增 `CoderTurnCoordinatorTests`

## Closure Decision

本轮可以一次收口，但前提是按以下顺序整批修改：

1. 先做参数对象化，切断长参数主链
2. 再做 `StageReviewer` 策略拆分和 wiring 收敛
3. 最后收剩余隐藏 `new`、重复逻辑、小修项与测试

如果 `RevisionContext` 只改一层、`StageReviewer` 只拆 facade 不改 wiring、或 `QualityRulesLoader` 抽重复时改变 fail-fast 语义，都视为未完成，不得声称收口。

## Key Changes

### 1. 参数对象化（P1 主线）

**新增 `PlanningRequest`**（位于 `executor/implementation/planning/`）：

```java
public record PlanningRequest(
    Path projectPath,
    RunRecord runRecord,
    String analysis,
    String prd,
    String design,
    String note,
    String workspaceContext,
    String plannerContextMarkdown,
    String performanceValidationGuidance,
    boolean preferSkeletonFlow,
    DeliveryPolicyEnvelope deliveryPolicy,
    ContractView contractView,
    QualityPlan qualityPlan,
    ProjectFingerprint fingerprint,
    DocumentLanguage language,
    FixMode fixMode,
    ImplementationPatchTarget implementationPatchTarget,
    String requirementCatalog,
    ImplementationContinuationConstraints continuationConstraints,
    ImplementationEventJournal eventJournal
)
```

`ImplementationPlanner.plan(PlanningRequest request)` 替换现有 20 参数方法。

**新增 `RevisionContext`**（位于 `orchestrator/`）：

```java
public record RevisionContext(
    ReviewDecision decision,
    FixMode fixMode,
    ImplementationPatchTarget implementationPatchTarget,
    String summary,
    String changeRequest,
    String evidence,
    String actionItems,
    List<FileChange> overrideChanges,
    SupervisorDecision supervisorDecision,
    StageType rerouteStage,
    boolean forceRepair,
    boolean repeatedIssue
)
```

这里不是只改 `StageTransitionSupport`。最终态要求：

- `StageTransitionSupport.rerouteForRevision(Path, RunRecord, StageType, RevisionContext, StageEntryAction)`
- `StageRevisionSupport.rerouteForRevision(Path, RunRecord, StageType, RevisionContext, StageEntryAction)`

两层同步切换，避免只是把参数爆炸下沉一层。

`FlowDecisionExecutor.apply()` 中 `RETRY_STAGE / ROLLBACK_STAGE / ROUTE_TO_REPAIR` 统一通过一个私有 `rerouteForRevision(...)` 入口构造 `RevisionContext` 并调用。

### 2. `StageReviewer` 策略模式拆分（P1 主线）

**新增三个 reviewer 策略类**：

- `DocumentStageReviewer`
  - 承载 `ANALYSIS / PRD / DESIGN`
  - 吃 `documentReviewTurnExecutor`、`documentStructureGuard`、`reviewArtifactLoader`、`languagePolicy`
- `ImplementationStageReviewer`
  - 承载 `IMPLEMENTATION`
  - 吃 `implementationReviewTurnExecutor`、`testExecutor`、`contractExtractor`、`architectIntegrationCheck`、`reviewArtifactLoader`
- `ExecutionStageReviewer`
  - 承载 `CODE_REVIEW / TEST`
  - 吃 `reviewDecisionArtifactParser`
  - 保留 test coverage gate

**`StageReviewer` 退化为薄 facade**：

```java
public ReviewResult review(Path projectPath, RunRecord runRecord, StageType stageType, String artifactContent) {
    return switch (stageType) {
        case ANALYSIS, PRD, DESIGN -> documentStageReviewer.review(projectPath, runRecord, stageType, artifactContent);
        case IMPLEMENTATION -> implementationStageReviewer.review(projectPath, runRecord, artifactContent);
        case CODE_REVIEW, TEST -> executionStageReviewer.review(projectPath, runRecord, stageType, artifactContent);
    };
}
```

**telemetry 归属必须明确**：

- `StageReviewer` 继续作为唯一公共 facade，保留 `consumeLastTelemetry()`
- facade 只保留路由与 telemetry 出口，不再持有任何阶段专属逻辑依赖
- strategy reviewer 不再对外暴露 telemetry API

`ReviewConfiguration` 必须同步装配三条 reviewer 策略；`StageReviewerTestSupport` 必须同步切到新 wiring，不能残留旧构造假设。

### 3. `SupervisorFallbackPolicy` 去隐藏 `new`（P2）

`SupervisorConfiguration` 新增显式 bean：

```java
@Bean
SupervisorStageFallbackSupport supervisorStageFallbackSupport(StageFlowPolicy stageFlowPolicy) { ... }

@Bean
SupervisorGenerationRecoverySupport supervisorGenerationRecoverySupport() { ... }
```

`SupervisorFallbackPolicy` 改为构造注入，删除内部 `new`。

### 4. `WorkflowRunLifecycleSupport` fatal guard 收敛（P2）

提取统一辅助方法：

```java
private <T> T withFatalFailureGuard(
    Path projectPath,
    UUID runId,
    StageType stageType,
    Supplier<T> action
) { ... }
```

或等价的 `Runnable / Supplier` 组合。

要求只有一处 `try-catch-markFatal` 定义，`startRun / resumeRun / progress` 全部走它。

### 5. `CoderTurnCoordinator` 参数收口（P2）

`CoderTurnCoordinator.execute()` 改为接收已有的 `ImplementationExecutionContext`：

```java
public ImplementationExecutionBundle execute(
    ImplementationExecutionContext executionContext,
    ImplementationProgressSink progressSink
) { ... }
```

为了做到这一点，`ImplementationExecutor` 与 `ImplementationExecutorWiring` 必须同步调整，让 context 创建逻辑不再藏在 coordinator 内部。

### 6. `QualityRulesLoader` 提取共享严格读取模板（P2）

这里**不能**改成“缺失给默认值，非法值静默回退”的宽松 reader。

最终态必须保留当前语义：

- 缺 key 直接失败
- 非法布尔/整数/risk level 直接失败

允许提取的只有共享骨架，例如：

```java
private <T> T readRequiredProperty(
    Properties properties,
    String key,
    Function<String, T> parser,
    BiFunction<String, String, IllegalStateException> invalidValueFactory
) { ... }
```

也就是“抽重复，不改语义”。

### 7. `DiagnosisAgent` 异常日志（P2）

```java
private static final Logger log = LoggerFactory.getLogger(DiagnosisAgent.class);

catch (Exception ex) {
    log.warn("Diagnosis failed, falling back to deterministic brief. stage={}", stageType, ex);
    return diagnosisFallbackBriefBuilder.build(...);
}
```

### 8. `DocumentStageComposer` 签名统一（P2）

这里不收成单一 `compose(...)`，避免把阶段语义又糊回一个方法。

最终态是：

```java
String composeAnalysis(Path projectPath, RunRecord runRecord, String note)
String composePrd(Path projectPath, RunRecord runRecord, String note)
String composeDesign(Path projectPath, RunRecord runRecord, String note)
```

三个具名方法保留，但签名完全一致，调用方无需再对 `ANALYSIS` 特判。

### 9. `ContextProjectionSummaryAssembler` 常量提取（P2）

```java
private static final int CURRENT_STAGE_SUMMARY_MAX_CHARS = 1800;
private static final int RECENT_HISTORY_SUMMARY_MAX_CHARS = 2200;
private static final int REPAIR_SUMMARY_MAX_CHARS = 1800;
private static final int WORKING_SET_SUMMARY_MAX_CHARS = 2200;
```

### 10. `ImplementationPlanner` 构造器隐藏 `new` 移到 wiring（P3 配套）

`ImplementationPlanner` 构造器中 `ImplementationPlanGate / ImplementationOutlineGate / ImplementationSubtaskDetailGate / ImplementationPlanAssembler / ImplementationPlanningFeedbackRouter / ImplementationPlanGateInputBuilder` 的创建移出本体。

允许的落点：

- `ImplementationExecutorWiring`
- 或新增 `ImplementationPlanningConfiguration`

但不允许保留旧路径并再包一层。

## Phase Plan

### Phase 1. 参数对象化（P1 主线）

- 新增 `PlanningRequest`
- `ImplementationPlanner.plan()` 切到 `PlanningRequest`
- 新增 `RevisionContext`
- `StageTransitionSupport.rerouteForRevision()` 切到 `RevisionContext`
- `StageRevisionSupport.rerouteForRevision()` 切到 `RevisionContext`
- `StageRevisionSupport.buildRevisionNote()` 的调用链改为从 `RevisionContext` 取值，不再透传长参数
- `FlowDecisionExecutor.apply()` 提取共享 reroute 方法
- `StageTransitionSupportTests` / `DefaultWorkflowEngineTests` / `ImplementationPlannerTests` 同步改签
- 新增 `FlowDecisionExecutorTests`

### Phase 2. `StageReviewer` 策略拆分（P1 主线）

- 新增 `DocumentStageReviewer`
- 新增 `ImplementationStageReviewer`
- 新增 `ExecutionStageReviewer`
- `StageReviewer` 退化为 facade
- 明确 `consumeLastTelemetry()` 保留在 facade
- `ReviewConfiguration` 补装配
- `StageReviewerTestSupport` 补新 wiring
- `StageReviewerTests` 同步更新

### Phase 3. 剩余 P2 / 配套收尾

- `SupervisorFallbackPolicy` 去隐藏 `new`
- `WorkflowRunLifecycleSupport` fatal guard 提取
- `CoderTurnCoordinator` 改接 `ImplementationExecutionContext`
- `ImplementationExecutor` / `ImplementationExecutorWiring` 同步改造
- 新增 `CoderTurnCoordinatorTests`
- `QualityRulesLoader` 提取严格 reader 模板
- `DiagnosisAgent` 异常日志
- `DocumentStageComposer` 三方法签名统一
- `ContextProjectionSummaryAssembler` 常量提取
- `ImplementationPlanner` 构造器 `new` 移到 wiring / configuration
- `QualityRulesLoaderTests` / `DiagnosisAgentTests` 同步补充

## Test Plan

编译验证：

- `mvn -q -DskipTests compile`
- `mvn -q -DskipTests test-compile`

定向测试：

- `mvn -q -Dtest=ImplementationPlannerTests,StageTransitionSupportTests,DefaultWorkflowEngineTests test`
- `mvn -q -Dtest=StageReviewerTests,DiagnosisAgentTests,QualityRulesLoaderTests test`
- `mvn -q -Dtest=FlowDecisionExecutorTests,CoderTurnCoordinatorTests test`

说明：

- `FlowDecisionExecutorTests` 与 `CoderTurnCoordinatorTests` 当前不存在，本轮应新增
- 如果 phase 中途新增额外 test support，也必须纳入 tracker

## Completion Gate

- [ ] `ImplementationPlanner.plan()` 只剩 `PlanningRequest`
- [ ] `StageTransitionSupport.rerouteForRevision()` 只剩 `RevisionContext`
- [ ] `StageRevisionSupport.rerouteForRevision()` 只剩 `RevisionContext`
- [ ] `FlowDecisionExecutor` 中 `RETRY / ROLLBACK / ROUTE_TO_REPAIR` 不再复制 reroute 调用
- [ ] `StageReviewer` facade 中不再存在阶段专属逻辑与专属依赖
- [ ] `StageReviewer` 的 `consumeLastTelemetry()` 归属已明确且不破坏 facade 边界
- [ ] `SupervisorFallbackPolicy` 构造器无 `new`
- [ ] `ImplementationPlanner` 构造器无 `new`
- [ ] `WorkflowRunLifecycleSupport` try-catch-markFatal 只在一处定义
- [ ] `CoderTurnCoordinator.execute()` 改接 `ImplementationExecutionContext`
- [ ] `QualityRulesLoader` 已抽重复，但严格失败语义保持不变
- [ ] `DiagnosisAgent` catch Exception 有 `log.warn()`
- [ ] `DocumentStageComposer` 三个 compose 方法签名一致
- [ ] `ContextProjectionSummaryAssembler` magic numbers 已提取
- [ ] `self-test + code review + tracker` 全部补齐
