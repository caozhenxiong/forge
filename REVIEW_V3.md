# Forge Code Review V3（2026-04-14）

## 概览

| 指标 | V2 | V3（本次） |
|---|---|---|
| 主代码类数 | 755 | **759** |
| executor root 类数 | 72 | **10**（精确收口） |
| `System.getProperty` 残留 | 10 处 | **0** |
| `WorkflowAction` 统一 | 双枚举 | **单枚举** |
| ArchUnit 守门 | 无 | **3 条规则** |

---

## V2 修复成果

本轮 5 个 Phase 完成度约 90%，主要进步：

- executor root 精确收口为 10 个类，`implementation/` 四层分包（planning/state/render/toolloop）
- `System.getProperty` → `@ConfigurationProperties` 全面替换，0 残留
- `FlowAction` / `SupervisorAction` 合并为 `domain.WorkflowAction`
- `TestExecutor` / `StageReviewer` 及 8 个同类去工厂化
- `StageArtifactNames` / `StageFlowPolicy` switch expression
- `DocumentLanguage.detect()` 返回 null，`LanguagePolicy` 接管默认值
- ArchUnit 守门测试接入（executor root 白名单 / quality→testing 禁止 / editing.precise→executor 禁止）

---

## 现存问题

### P1（阻碍结构或正确性）

#### 1. `requireStage()` 三处重复

同一个辅助方法在三个类里各自实现：

- `StageRevisionSupport.java:211`
- `StageTransitionSupport.java:295`
- `StageProgressCoordinator.java:278`

三个实现完全一致，应提取到 `StageStatusSupport`（已存在该类）或 domain 层工具方法。

#### 2. `StageProgressCoordinator` payload 转换仍未下沉

**tracker 标记完成，但代码没动。**

`toFileChange()`、`requiredContinuationField()`、`implementationPatchTarget()`、`implementationReasonCode()` 仍在 `StageProgressCoordinator`，285 行，12 个字段。这是 progress tracker 与实际代码的不一致。

#### 3. `continueStage()` 参数 10 个

位置：`StageTransitionSupport.java:194`

```java
public RunRecord continueStage(
    Path projectPath, RunRecord runRecord, StageType stageType,
    String summary, String changeRequest, String evidence, String actionItems,
    List<FileChange> overrideChanges, ImplementationPatchTarget patchTarget,
    StageEntryAction stageEntryAction   // 10 个参数
)
```

应封装成 `StageContinuationContext` 参数对象。

---

### P2（设计债）

#### 4. `StageRevisionSupport` 双构造器 + 隐藏 `new`

两个公开构造器形成链式调用，内部 `new` 了 `SupervisorGuidanceRenderer`、`StageRevisionRepairSupport`、`StageRevisionNoteBuilder`，混合了依赖注入与工厂模式。

#### 5. `SupervisorDecisionSanitizer` 内部创建 `SupervisorPayloadNormalizer`

位置：`supervisor/SupervisorDecisionSanitizer.java:36`

```java
this.payloadNormalizer = new SupervisorPayloadNormalizer();
```

Spring 管理类里不应 `new` 协作者，应注入。

#### 6. `FlowController.mapReason()` 仍是 if 链

位置：`orchestrator/FlowController.java`

`WorkflowAction` 已统一为单枚举，`mapReason()` 应同步改为 switch expression，编译器能强制覆盖所有枚举值。

#### 7. `ValidationStrategyPlanner` 静默吞异常

位置：`validation/ValidationStrategyPlanner.java:48`

```java
} catch (Exception ignored) {
}
return deterministicPlan;
```

无日志，失败时完全黑盒，与 SupervisorAgent 之前的问题一样。

#### 8. `ContextProjector` 职责过多

位置：`context/ContextProjector.java:53-120`

`project()` 方法承担了：artifact 读取、contract 提取、8 次 summarize、2 次 sanitize、TaskMemory 装配、ContextViews 装配。应拆分为：
- `readArtifacts()` → `extractContracts()` → `summarizeComponents()` → `assembleContextViews()`

或让 `ContextLayerAssembler` 接收已预处理的组件，不再接收原始数据。

#### 9. Artifact 三组 Composer 结构高度重复

`AnalysisDocumentComposer`、`PrdDocumentComposer`、`DesignDocumentComposer` 三个类：
- 字段完全相同（6 个，`contractExtractor`/`intake`/`draftAssembler`/`postProcessor`/`promptAssembler`/`generationSupport`）
- 执行流程完全相同（读上游 → 构建 metadata → buildContext → 生成 → postProcess → merge → upsert）
- 唯一差异：prompt 标签和 upsert 参数

应抽取 `DocumentCompositionTemplate` 基类或策略对象，三个类退化为配置差异。

#### 10. `TestExecutor` 14 个构造参数

去工厂化后构造参数从 0 变成 14 个。Spring 能注入，但参数太多说明职责不清，可按职责群分组：
- 规划层：strategyPlanner、testCasePlanner、testToolSelector
- 执行层：validationExecutor、testRunner、testEvidenceCollector
- 结果层：testEvidenceGate、coverageLedgerBuilder、experienceFailureDispositionResolver、testArtifactRenderer

分层后考虑用 `TestExecutorComponents` 聚合对象注入。

---

### P3（代码质量）

#### 11. `TestCaseBehaviorRepairSupport` 887 行，7 个修复策略混杂

7 个策略（observationSurface 规范化、semantic 规范化、keyboard 修复、running start 前置条件注入、observable 序列修复、capability observation 规范化、脆弱断言降级）全部内联，40+ 个私有方法。

应拆成独立策略类，`TestCaseBehaviorRepairSupport` 只做 pipeline 编排。

#### 12. `SupervisorAgent` catch 变量命名误导

```java
} catch (Exception ignored) {
    log.warn("...", currentStage, ignored);  // 其实用了 ignored，名字是误导
```

变量名 `ignored` 含义是"不关心此异常"，但实际上传给了 `log.warn`。应改为 `e` 或 `ex`。

#### 13. `FlowController` 有一处 hardcode 分隔符

位置：`orchestrator/FlowController.java:105`

```java
return transitionSummary + " | " + ...
```

`" | "` 应提取为常量。

#### 14. ArchUnit 缺少 `System.getProperty` 守门规则

当前 3 条规则没有覆盖禁止 `System.getProperty("devflow...")`，应补充：

```java
@Test
void mainCodeMustNotReadSystemProperties() {
    noClasses().that().resideInAPackage("devflow.agent..")
        .should().callMethod(System.class, "getProperty", String.class)
        .check(importedClasses);
}
```

#### 15. `ContextCompactor` 四层内部仍是 `truncateMiddle`

已知，文档注明是有意为之的最小可用版。后续接入语义摘要器时替换。

---

## 整体评分（V3）

| 维度 | V1 | V2 | V3 |
|---|---|---|---|
| 架构层次清晰度 | 3 | 7 | **7.5** |
| 模块边界 | 2 | 8.5 | **8.5** |
| 契约完整性 | 3 | 9 | **9** |
| 可测试性 | 3 | 7.5 | **7.5** |
| 可观测性 | 2 | 3 | **3** |
| 配置管理 | 1 | 8 | **8** |
| 上下文管理 | 2 | 6 | **6** |
| **综合** | **2.4** | **7.2** | **7.4** |

---

## 下一步优先级

```
P1  requireStage() 三处重复提取到 StageStatusSupport
P1  StageProgressCoordinator payload 转换真正下沉（tracker 对齐代码）
P1  continueStage() 封装成 StageContinuationContext

P2  StageRevisionSupport 双构造器 + 隐藏 new 清理
P2  SupervisorDecisionSanitizer 注入 SupervisorPayloadNormalizer
P2  FlowController.mapReason() 改 switch expression
P2  ValidationStrategyPlanner 静默异常加日志
P2  ContextProjector 职责拆分
P2  Artifact 三组 Composer 提取模板基类
P2  TestExecutor 14 参数分组聚合

P3  TestCaseBehaviorRepairSupport 拆分为策略管道
P3  SupervisorAgent catch 变量名改为 e/ex
P3  FlowController hardcode 分隔符提取常量
P3  ArchUnit 补 System.getProperty 守门规则
P3  ContextCompactor 接入语义摘要器（可观测性提升后做）
```

---

## 到 8 分还需要做什么

```
本轮 P1 全部收口        → 7.6
P2 主要问题收口         → 7.8
可观测性（AgentTurnLoop + Micrometer）→ 8.3
ContextCompactor 语义摘要             → 8.5
```
