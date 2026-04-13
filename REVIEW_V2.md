# Forge Code Review V2（2026-04-14）

## 概览

| 指标 | V1（2026-04-12） | V2（本次） |
|---|---|---|
| 主代码类数 | 527 | **755** |
| executor 根包类数 | 344（无子包） | **72**（已拆 12 个子包） |
| domain 包 | 无 | **已建立**（7 个核心模型类） |
| WorkflowEngine 契约 | 假接口（全部 throw） | **完整 7 方法 facade** |
| 测试类数 | — | **145** |

---

## 本轮架构整改完成情况

| 项目 | 结果 |
|---|---|
| `domain` 包提取，context → orchestrator 依赖消除 | ✅ |
| `WorkflowEngine` 完整契约，UnsupportedOperationException 全部删除 | ✅ |
| CLI 改依赖 `WorkflowEngine` 接口 | ✅ |
| `SupervisorAgent` 两处静默异常改 `log.warn()` | ✅ |
| `CompletableFuture` 指定 `ExecutorService`，虚拟线程池生命周期托管 | ✅ |
| `ImplementationExecutor` 去工厂化，装配逻辑移至 `ImplementationExecutorWiring` | ✅ |
| `ImplementationExecutorConfiguration` 用 `ObjectProvider` + `@Bean(destroyMethod)` 管理 | ✅ |
| `SubtaskExecutionContext` 引入，executeSubtask() 16 参数收成单一 context | ✅ |
| `continuationPrompt` 归位到 `ImplementationToolPromptBuilder` | ✅ |
| executor 拆 12 个子包 | ✅ |
| `ContextCompactor` 接入四层结构（`LlmPromptContext` durable/working/evidence/trace） | ✅ |

---

## 进步点说明

**上下文压缩**（重要修正）

`ContextCompactor` 已升级：接收 `LlmGenerateRequest`，内含 `LlmPromptContext` 四层结构，`ContextBudgetPlanner` 按 `trace → evidence → working → durable` 优先级分配预算，每层独立压缩。每层内部仍用 `truncateMiddle`，注释说明这是有意为之的"最小可用版"，为后续语义摘要器留出扩展点。结构已接通，这是真实进步。

**架构层面**

- `orchestrator → context` 单向依赖保留，context 不再反向依赖 orchestrator
- `ImplementationExecutor` 真正成为薄门面（2 个字段，`AutoCloseable`）
- `ImplementationExecutorConfiguration` 使用 `ObjectProvider` 处理可选 Bean，生命周期规范
- `AgentTurnLoop` 干净的通用状态机，不耦合任何业务语义
- tool loop 的 truncation 续跑、fatal 错误分类、交付契约校验链路完整

---

## 现存问题

### P1（阻碍结构完整性或正确性）

#### 1. executor root 仍有 72 个类未归入子包

`implementation/` 子包只有 `toolloop/` 一层（19 类），但 executor root 还有 `ImplementationPlanner`、`ImplementationStateSnapshot`、`ImplementationPlanRunner`、`ImplementationStateSnapshotSerializer` 等大量规划/状态/渲染类。

executor root 最终应只剩 wiring/configuration 入口（不超过 10 个类）。

#### 2. `System.getProperty` 全面替代 Spring 配置体系

以下 10 处均通过 `System.getProperty()` 读取配置，绕开了 Spring `@ConfigurationProperties`：

- `executor/ImplementationExecutionPolicy.java`（8 个 key）
- `executor/patch/PatchBudgetSettings.java`
- `executor/patch/PatchRepairSettings.java`
- `executor/llm/OllamaClientPolicy.java`
- `executor/context/PromptTokenEstimatorSettings.java`
- `executor/editing/RuntimeWorkingSetPolicy.java`
- `executor/editing/EditUnitPlanningPolicy.java`
- `executor/tools/ImplementationToolPermissionPolicy.java`

**影响**：
- 无法通过 `application.yml` / `application.properties` 统一管理
- 无类型安全，无 IDE 补全，无 Spring Boot Actuator 可见性
- 不支持 profile 切换（dev/prod 配置分离）

**方案**：统一改为 `@ConfigurationProperties` + YAML，把所有 `devflow.*` key 收进一个配置类。

---

### P2（设计债，需在下一轮解决）

#### 3. `TestExecutor` 和 `StageReviewer` 是构造器工厂（同 ImplementationExecutor 旧问题）

**`TestExecutor`**（`testing/TestExecutor.java:74-88`）：
- `@Component` + 单构造器，但内部 `new` 了 `TreeSitterSupport`、`ProjectInspector`、`ValidationStrategyPlanner`、`TestCasePlanner`、`PlaywrightCaseExecutor` 等 13 个对象
- 与已修复的 `ImplementationExecutor` 旧形态完全相同

**`StageReviewer`**（`review/StageReviewer.java:74-88`）：
- `@Component` + `@Autowired` 构造器，内部 `new FileProjectWorkspace()`、`new TreeSitterSupport()`、`new ArchitectIntegrationCheck()` 等
- `FileProjectWorkspace` 是有副作用的对象，在构造器里直接 `new` 不可测试

`review/DocumentStructureGuard`、`review/DocumentReviewNormalizer`、`review/ImplementationReviewNormalizer`、`quality/HtmlStructureRuntimeSignalResolver`、`executor/subtask/SubtaskPerformanceGuidanceResolver` 中也有同类问题，共 10 处隐式 `new`。

#### 4. `SupervisorAction` 和 `FlowAction` 是重复枚举

两个枚举值完全相同（7 个值一一对应），`FlowController.mapAction()` 是纯 1:1 映射，没有任何逻辑转换。

```java
// FlowController.mapAction 全部是这种形式
if (action == SupervisorAction.ADVANCE_STAGE) return FlowAction.ADVANCE_STAGE;
```

**方案**：合并为一个枚举放入 `domain/` 包，supervisor 和 orchestrator 共用，消除无意义的转换层。

#### 5. `StageArtifactNames` 和 `StageFlowPolicy` 用 if 链代替 switch expression

Java 21 已支持 switch expression，但以下类仍用旧式 if 链：

- `artifact/StageArtifactNames.java`：3 个方法各 5 层 if，共 15 个分支
- `orchestrator/StageFlowPolicy.java`：`nextStage()` 5 层 if

应改为：
```java
// StageArtifactNames.artifact()
return switch (stageType) {
    case ANALYSIS -> "analysis.md";
    case PRD -> "prd.md";
    ...
};
```
编译器会强制覆盖所有枚举值，漏写会报错，比 if 链更安全。

#### 6. `editing` 双包结构二义性

- `devflow.agent.editing`：精确编辑器底层（`CodePreciseEditor`、`HtmlPreciseEditor`）
- `devflow.agent.executor.editing`：编辑策略执行层

两包存在轻度双向引用，层次关系不清晰。建议顶层包改名为 `editing.core` 或 `editing.precise`。

#### 7. `executor/tools` 和 `executor/editing` 中有错放的类

- `tools/`：`StructuredPatchSupport`、`StructuredPatchHunk` → 应在 `patch/`
- `editing/`：`RuntimeWorkingSetPolicy`、`RuntimeWorkingSetResolver` → 应在 `runtime/`；`ScopedTaskPackageSupport`、`TaskPackageMarkdownRenderer` → 应在 `implementation/`

#### 8. `quality` 包依赖 `executor.testing`

`quality/FeatureProfiler`、`quality/QualityPlanFactory`、`quality/HtmlStructureRuntimeSignalResolver` 导入 `executor.testing.RuntimeSnapshot`。

`quality` 是领域模型层，不应依赖执行层。`RuntimeSnapshot` 应上移至 `executor.runtime/`。

#### 9. `StageProgressCoordinator` payload 转换职责未下沉

11 个字段，280 行，`toFileChange()`、`implementationPatchTarget()` 等 payload 转换逻辑仍在 orchestration 类里。

---

### P3（代码质量）

#### 10. `StageArtifactNames` 有语义不清的命名

CODE_REVIEW 阶段的 review 产物被命名为 `code_review_review.md`，含义模糊。建议改为 `code_review_feedback.md`。

#### 11. `DocumentLanguage.detect` 空内容默认返回 ZH

当输入全为空/null 时，`cjkCount == 0 && latinCount == 0`，直接返回 `ZH`。

空内容不代表"中文"，应改为返回系统默认语言或抛出明确异常，而不是隐式假设。

#### 12. `TestCaseBehaviorRepairSupport` 877 行

承担 8 个独立修复操作，功能单一但体量过大。建议拆成 `BehaviorRepairStep` 策略组合。

#### 13. `AgentTurnLoop` Phase 6 tracing 未实现

注释标注"tracing、持久化、事件写盘后续 Phase 6 接入"，turn loop 内部过程对外完全黑盒。

#### 14. `ContextCompactor` 四层内部仍是 `truncateMiddle`

结构已接通，但每层压缩仍是字符截断。对于长任务，Evidence 层的失败摘要和 Trace 层的历史可能被截成碎片。后续可接入语义摘要器（如调用小模型对每层做摘要压缩）。

---

## 整体评分

| 维度 | V1 | V2 |
|---|---|---|
| 架构层次清晰度 | 3/10 | **7/10** |
| 模块边界 | 2/10 | **6/10** |
| 契约完整性 | 3/10 | **8/10** |
| 可测试性 | 3/10 | **5/10** |
| 可观测性 | 2/10 | **3/10** |
| 配置管理 | 1/10 | **2/10**（System.getProperty 仍满天飞） |
| 上下文管理 | 2/10 | **6/10**（四层已接通） |

---

## 下一步优先级

```
P1  executor root 剩余 Implementation* 移入 implementation/ 子包
P1  System.getProperty 全面改为 @ConfigurationProperties + YAML

P2  TestExecutor / StageReviewer 去工厂化（参照 ImplementationExecutorWiring 模式）
P2  SupervisorAction + FlowAction 合并为 domain 层单枚举
P2  StageArtifactNames / StageFlowPolicy 改用 switch expression
P2  editing 双包命名整理
P2  tools/ editing/ 错放类归位
P2  quality → executor.testing 依赖反转（RuntimeSnapshot 上移）
P2  StageProgressCoordinator payload 转换下沉

P3  code_review_review.md 改名
P3  DocumentLanguage.detect 空内容默认值修正
P3  TestCaseBehaviorRepairSupport 拆分为 RepairStep 策略
P3  AgentTurnLoop Phase 6 tracing 接入
P3  ContextCompactor 四层内部接入语义摘要器
```
