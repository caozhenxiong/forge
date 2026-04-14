# Forge Code Review V4（2026-04-14）

## 概览

| 指标 | V3 | V4（本次） |
|---|---|---|
| 综合评分 | 8.1 | 待修复后约 8.6 |
| executor root 类数 | 10 | 10 |
| ContextProjector | 34 行 | 34 行（已收口） |
| StageProgressCoordinator | orchestration 薄层 | 正常 |

V3 收口后整体架构稳定。本次 review 深挖了 orchestration 参数爆炸、StageReviewer God Class、repair/quality 模块等此前未覆盖的区域。

---

## 现存问题

### P1（功能正确性或严重可测试性问题）

#### 1. `ImplementationPlanner.plan()` 20 个参数

位置：`executor/implementation/planning/ImplementationPlanner.java`

```java
public ImplementationPlan plan(
    Path projectPath, RunRecord runRecord, String analysis, String prd,
    String design, String note, String workspaceContext, String plannerContextMarkdown,
    String performanceValidationGuidance, boolean preferSkeletonFlow,
    DeliveryPolicyEnvelope deliveryPolicy, ContractView contractView,
    QualityPlan qualityPlan, ProjectFingerprint fingerprint,
    DocumentLanguage language, FixMode fixMode,
    ImplementationPatchTarget implementationPatchTarget, String requirementCatalog,
    ImplementationContinuationConstraints continuationConstraints,
    ImplementationEventJournal eventJournal   // 20 个
)
```

无法在任何人不看文档的情况下正确调用，实际是不可维护的接口。应封装为 `PlanningRequest` 参数对象。

#### 2. `StageTransitionSupport.rerouteForRevision()` 16 个参数

位置：`orchestrator/StageTransitionSupport.java`

16 个参数，其中 `overrideChanges`、`supervisorDecision`、`forceRepair`、`repeatedIssue` 等都是决策层信息混入转换层。应封装为 `RevisionContext`。

#### 3. `FlowDecisionExecutor.apply()` RETRY 和 ROUTE_TO_REPAIR 重复逻辑

位置：`orchestrator/FlowDecisionExecutor.java`

`RETRY_STAGE` 和 `ROUTE_TO_REPAIR` 两个分支调用 `rerouteForRevision()` 的 16 个参数完全一样，唯一差异是 `forceRepair=false` vs `forceRepair=true`。这是明显的重复，应提取共享私有方法。

#### 4. `StageReviewer` God Class（14 个依赖，296 行）

位置：`review/StageReviewer.java`

14 个注入字段，`review()` 方法用 if 链分发 6 个阶段，每个阶段走完全不同的逻辑路径：
- 文档阶段（ANALYSIS/PRD/DESIGN）→ `reviewDocument()`
- 实现阶段 → `reviewImplementation()`（含 selfCheck + contractGate + failureVerification）
- CODE_REVIEW/TEST → `parseDecisionArtifact()`

三条路径差异极大，不适合共用一个类。应按策略模式拆为 `DocumentStageReviewer`、`ImplementationStageReviewer`、`ExecutionStageReviewer`，由 `StageReviewer` 做薄路由。

---

### P2（设计债）

#### 5. `SupervisorFallbackPolicy` Spring bean 内部 `new` 协作者

位置：`supervisor/SupervisorFallbackPolicy.java:28-29`

```java
public SupervisorFallbackPolicy(StageFlowPolicy stageFlowPolicy) {
    this.stageFallbackSupport = new SupervisorStageFallbackSupport(stageFlowPolicy);   // hidden new
    this.generationRecoverySupport = new SupervisorGenerationRecoverySupport();        // hidden new
}
```

`@Component` 类在构造器里 `new` 协作者，`SupervisorConfiguration` 未装配这两个子类。应改为显式 bean 注入。

#### 6. `WorkflowRunLifecycleSupport` 三处重复 try-catch-markFatal 模式

位置：`orchestrator/WorkflowRunLifecycleSupport.java:96-100`、`111-118`、`149-153`

```java
try {
    ...
} catch (RuntimeException ex) {
    stageTransitionSupport.markFatalFailure(projectPath, runId, stageType, ex);
}
```

同一个模式三次重复，应提取为 `withFatalFailureGuard(runnable)` 辅助方法。

#### 7. `CoderTurnCoordinator.execute()` 9 个参数

位置：`executor/implementation/CoderTurnCoordinator.java`

9 个参数且 execute 方法 170+ 行。`ImplementationExecutionContext` 已经做了部分封装，但调用层仍在平铺传参，应让 `execute` 直接接收 `ImplementationExecutionContext`。

#### 8. `QualityRulesLoader` 重复属性读取模式

位置：`quality/QualityRulesLoader.java:99-145`

`readBoolean()`、`readPositiveInt()`、`readRiskLevel()` 三个方法各自实现了相同的"读 key → 缺失给默认值 → 解析类型 → 校验"逻辑，约 15 行重复三次，应提取为泛型 `readProperty(key, defaultValue, parser)` 方法。

#### 9. `DiagnosisAgent` 静默吞异常

位置：`repair/DiagnosisAgent.java`（grep 发现有 `catch (Exception exception)` 但无日志）

与之前 SupervisorAgent、ValidationStrategyPlanner 同类问题，应补 `log.warn()`。

#### 10. `DocumentStageComposer` 签名不一致

位置：`artifact/DocumentStageComposer.java:36-46`

```java
String composeAnalysis(RunRecord runRecord, String note)                       // 无 projectPath
String composePrd(Path projectPath, RunRecord runRecord, String note)          // 有 projectPath
String composeDesign(Path projectPath, RunRecord runRecord, String note)       // 有 projectPath
```

三个方法签名不一致，调用方必须分开处理。应统一为 `compose(Path projectPath, RunRecord runRecord, String note)`，Analysis 内部从 `runRecord.projectPath()` 取值。

#### 11. `ContextProjectionSummaryAssembler` magic numbers

位置：`context/ContextProjectionSummaryAssembler.java:25,27-29`

```java
PlaceholderValues.truncateMarkdown(artifacts.currentStageSummary(), 1800)
PlaceholderValues.truncateMarkdown(artifacts.recentHistorySummary(), 2200)
```

`1800` / `2200` 是 char 预算，应提取为命名常量。

---

### P3（代码质量）

#### 12. `QualityRulesLoader` 路径硬编码

位置：`quality/QualityRulesLoader.java:22`

`.devflow/quality-rules.properties` 路径硬编码，应纳入配置属性（如 `devflow.quality.rules-path`）。

#### 13. `ImplementationPlanner` 构造器内 `new` 6 个对象

位置：`executor/implementation/planning/ImplementationPlanner.java:55-76`

构造器内部 `new` 了 6 个 Gate/Assembler 对象，这些是无状态的，应由 `ImplementationExecutorWiring` 或专用 Configuration 类统一装配。

---

## 整体评分（V4 完成后预估）

| 维度 | V3 | V4 完成后 |
|---|---|---|
| 架构层次清晰度 | 8.5 | **9** |
| 模块边界 | 9 | **9** |
| 契约完整性 | 9.5 | **9.5** |
| 可测试性 | 8.5 | **9** |
| 可观测性 | 3 | **3**（未动） |
| 配置管理 | 8.5 | **8.5** |
| 上下文管理 | 6 | **6**（未动） |
| **综合** | **8.1** | **约 8.6** |

---

## 下一步优先级

```
P1  ImplementationPlanner.plan() 封装 PlanningRequest（20 参数）
P1  StageTransitionSupport.rerouteForRevision() 封装 RevisionContext（16 参数）
P1  FlowDecisionExecutor RETRY/ROUTE_TO_REPAIR 提取共享方法
P1  StageReviewer 按策略模式拆分（DocumentStageReviewer / ImplementationStageReviewer / ExecutionStageReviewer）

P2  SupervisorFallbackPolicy 去隐藏 new，SupervisorConfiguration 补装配
P2  WorkflowRunLifecycleSupport try-catch-markFatal 提取辅助方法
P2  CoderTurnCoordinator.execute() 改接收 ImplementationExecutionContext
P2  QualityRulesLoader 重复 property reader 提取泛型方法
P2  DiagnosisAgent 异常补日志
P2  DocumentStageComposer 签名统一
P2  ContextProjectionSummaryAssembler magic numbers 提取常量

P3  QualityRulesLoader 路径配置化
P3  ImplementationPlanner 构造器 new 移到 Configuration
```

---

## 到 9 分还需要什么

```
本轮 P1 全收口     → 8.6
P2 主要问题收口    → 8.8
可观测性接入       → 9.2
ContextCompactor 语义摘要 → 9.5
```
