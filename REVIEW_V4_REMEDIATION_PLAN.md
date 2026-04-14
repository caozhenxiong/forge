# Claude Review 收口方案 V4

## Summary

本轮收口 `REVIEW_V4.md` 中的 P1 + P2，不做 P3 和可观测性/ContextCompactor 升级。

核心主线有 4 条：
1. **参数对象化**：ImplementationPlanner / StageTransitionSupport / FlowDecisionExecutor 的参数爆炸
2. **StageReviewer 策略模式拆分**：God Class → 三个策略 + 薄路由
3. **残余隐藏 new 清理**：SupervisorFallbackPolicy + WorkflowRunLifecycleSupport try-catch
4. **小修项**：CoderTurnCoordinator、QualityRulesLoader、DiagnosisAgent、DocumentStageComposer 签名、magic numbers

## Scope

**纳入本轮**：

- P1 全部（4 条）
- P2 全部（7 条）
- P3 中仅纳入直接配合参数收口的：`ImplementationPlanner` 构造器 new 移到 Configuration

**不纳入本轮**：

- `QualityRulesLoader` 路径配置化（P3，独立需求）
- 可观测性（AgentTurnLoop tracing + Micrometer）
- `ContextCompactor` 语义摘要器

## Final State

完成态必须同时满足：

- `ImplementationPlanner.plan()` 不再平铺 20 个参数，通过 `PlanningRequest` 接收
- `StageTransitionSupport.rerouteForRevision()` 不再平铺 16 个参数，通过 `RevisionContext` 接收
- `FlowDecisionExecutor.apply()` 中 RETRY 和 ROUTE_TO_REPAIR 不再复制 16 参数调用，合并为共享方法
- `StageReviewer` 不再是 God Class，退化为薄路由，三条路径各自由独立策略类承载
- `SupervisorFallbackPolicy` 构造器不再 `new` 协作者，改为显式注入
- `WorkflowRunLifecycleSupport` 三处重复 try-catch-markFatal 合并为辅助方法
- `CoderTurnCoordinator.execute()` 改接收 `ImplementationExecutionContext`
- `QualityRulesLoader` 属性读取逻辑提取为泛型方法
- `DiagnosisAgent` 静默异常补 `log.warn()`
- `DocumentStageComposer` 三个方法签名统一
- `ContextProjectionSummaryAssembler` magic numbers 提取为常量
- `ImplementationPlanner` 构造器 new 移到 Configuration

## Key Changes

### 1. 参数对象化（P1）

**新增 `PlanningRequest`**（位于 `executor/implementation/planning/`）：

```java
public record PlanningRequest(
    Path projectPath,
    RunRecord runRecord,
    String analysis, String prd, String design, String note,
    String workspaceContext, String plannerContextMarkdown,
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
所有调用方（`CoderTurnCoordinator` 等）同步改造。

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

`StageTransitionSupport.rerouteForRevision(Path, RunRecord, StageType, RevisionContext, StageEntryAction)` 替换现有 16 参数方法。
`FlowDecisionExecutor.apply()` 中 RETRY 和 ROUTE_TO_REPAIR 统一通过 `RevisionContext` 构建后调用共享私有方法 `rerouteForRevision()`。

### 2. StageReviewer 策略模式拆分（P1）

**新增三个策略类**：

- `DocumentStageReviewer`：承载 ANALYSIS/PRD/DESIGN 的 `reviewDocument()` 逻辑，依赖 `documentReviewTurnExecutor`、`documentStructureGuard`、`documentReviewNormalizer` 等
- `ImplementationStageReviewer`：承载 IMPLEMENTATION 的 `reviewImplementation()` 逻辑，依赖 `implementationReviewTurnExecutor`、`testExecutor`、`contractExtractor` 等
- `ExecutionStageReviewer`：承载 CODE_REVIEW/TEST 的 `parseDecisionArtifact()` 逻辑

**`StageReviewer` 退化为薄路由**：

```java
public ReviewResult review(Path projectPath, RunRecord runRecord, StageType stageType, String artifactContent) {
    return switch (stageType) {
        case ANALYSIS, PRD, DESIGN -> documentReviewer.review(projectPath, runRecord, stageType, artifactContent);
        case IMPLEMENTATION -> implementationReviewer.review(projectPath, runRecord, artifactContent);
        case CODE_REVIEW, TEST -> executionReviewer.review(projectPath, runRecord, stageType, artifactContent);
    };
}
```

`ReviewConfiguration` 统一装配三个策略类，`StageReviewer` 只注入 3 个字段。

### 3. SupervisorFallbackPolicy 去隐藏 new（P2）

`SupervisorConfiguration` 新增两个 bean：

```java
@Bean SupervisorStageFallbackSupport supervisorStageFallbackSupport(StageFlowPolicy stageFlowPolicy) { ... }
@Bean SupervisorGenerationRecoverySupport supervisorGenerationRecoverySupport() { ... }
```

`SupervisorFallbackPolicy` 改为构造注入这两个 bean，删除内部 `new`。

### 4. WorkflowRunLifecycleSupport try-catch 提取（P2）

提取私有方法：

```java
private void withFatalFailureGuard(Path projectPath, UUID runId, StageType stageType, Runnable action) {
    try {
        action.run();
    } catch (RuntimeException ex) {
        stageTransitionSupport.markFatalFailure(projectPath, runId, stageType, ex);
    }
}
```

三处重复替换为调用此方法。

### 5. CoderTurnCoordinator 参数收口（P2）

`CoderTurnCoordinator.execute()` 改为接收已有的 `ImplementationExecutionContext`（该类已存在）：

```java
public ImplementationExecutionBundle execute(ImplementationExecutionContext context) { ... }
```

`ImplementationExecutor` 的 `execute()` 方法构造 `ImplementationExecutionContext` 后传入，不再平铺 9 个参数。

### 6. QualityRulesLoader 泛型 property reader（P2）

提取：

```java
private <T> T readProperty(Properties props, String key, T defaultValue, Function<String, T> parser) {
    String raw = props.getProperty(key);
    if (raw == null || raw.isBlank()) return defaultValue;
    try { return parser.apply(raw.trim()); }
    catch (Exception e) { return defaultValue; }
}
```

`readBoolean()`、`readPositiveInt()`、`readRiskLevel()` 改为调用此方法。

### 7. DiagnosisAgent 异常日志（P2）

```java
private static final Logger log = LoggerFactory.getLogger(DiagnosisAgent.class);
// catch Exception → catch (Exception ex)
log.warn("Diagnosis failed, using fallback brief. stage={}", stageType, ex);
```

### 8. DocumentStageComposer 签名统一（P2）

```java
// 改前
String composeAnalysis(RunRecord runRecord, String note)
// 改后
String composeAnalysis(Path projectPath, RunRecord runRecord, String note)
```

Analysis composition 内部用 `runRecord.projectPath()` 兼容时也可，但对外签名统一。

### 9. ContextProjectionSummaryAssembler 常量提取（P2）

```java
private static final int CURRENT_STAGE_SUMMARY_MAX_CHARS = 1800;
private static final int RECENT_HISTORY_SUMMARY_MAX_CHARS = 2200;
```

### 10. ImplementationPlanner 构造器 new 移到 Configuration（P3）

`ImplementationPlanner` 构造器内 `new` 的 6 个 Gate/Assembler 对象移到 `ImplementationExecutorConfiguration` 或新的 `ImplementationPlanningConfiguration` 中显式装配。

## Phase Plan

### Phase 1. 参数对象化（P1 主线）

- 新增 `PlanningRequest` record
- `ImplementationPlanner.plan()` 改为接收 `PlanningRequest`
- 新增 `RevisionContext` record
- `StageTransitionSupport.rerouteForRevision()` 改为接收 `RevisionContext`
- `FlowDecisionExecutor.apply()` 提取共享方法，消除 RETRY/ROUTE_TO_REPAIR 重复

### Phase 2. StageReviewer 策略拆分（P1 主线）

- 新增 `DocumentStageReviewer`、`ImplementationStageReviewer`、`ExecutionStageReviewer`
- `StageReviewer` 退化为薄路由（switch + 3 个字段）
- `ReviewConfiguration` 补装配三个策略 bean

### Phase 3. 剩余 P2 小修

- `SupervisorFallbackPolicy` 去隐藏 new + `SupervisorConfiguration` 补装配
- `WorkflowRunLifecycleSupport` try-catch 提取
- `CoderTurnCoordinator` 改接 `ImplementationExecutionContext`
- `QualityRulesLoader` 泛型 reader
- `DiagnosisAgent` 异常日志
- `DocumentStageComposer` 签名统一
- `ContextProjectionSummaryAssembler` 常量提取
- `ImplementationPlanner` 构造器 new 移到 Configuration

## Test Plan

```
mvn -q -DskipTests compile
mvn -q -DskipTests test-compile
mvn -q -Dtest=ArchitectureRulesTests,DefaultWorkflowEngineTests,\
  StageProgressCoordinatorTests,StageTransitionSupportTests,\
  FlowControllerTests,FlowDecisionExecutorTests,\
  StageReviewerTests,SupervisorAgentTests,DiagnosisAgentTests,\
  QualityPlanFactoryTests,ImplementationPlannerTests,\
  CoderTurnCoordinatorTests test
```

## Completion Gate

- [ ] `ImplementationPlanner.plan()` 参数 ≤ 1（PlanningRequest）
- [ ] `StageTransitionSupport.rerouteForRevision()` 参数 ≤ 5
- [ ] `FlowDecisionExecutor` RETRY / ROUTE_TO_REPAIR 不再复制调用
- [ ] `StageReviewer` 字段 ≤ 3，无任何 if (stageType ==) 条件
- [ ] `SupervisorFallbackPolicy` 构造器无 `new`
- [ ] `WorkflowRunLifecycleSupport` try-catch-markFatal 只在一处定义
- [ ] `DiagnosisAgent` catch Exception 有 `log.warn()`
- [ ] `DocumentStageComposer` 三个 compose 方法签名一致
- [ ] ArchUnit 测试全通过
- [ ] `self-test + code review + tracker` 全部补齐
