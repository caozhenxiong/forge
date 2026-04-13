# 架构重构方案

## 目标

解决两个核心结构问题：
1. `context ↔ orchestrator` 双向耦合
2. `executor` 包 426 个类无边界

这两项都是**纯机械改动**（移文件 + 更新 import），不改任何业务逻辑。

---

## Step 1：提取 `domain` 包（解耦双向依赖）

### 问题根因

`StageType`、`RunRecord` 等核心模型住在 `orchestrator` 包，导致：
- `context` 要用这些类 → 依赖 `orchestrator`
- `orchestrator` 要做上下文投影 → 反向依赖 `context`
- 结果：两个包互相依赖，无法独立测试

### 解法

新建 `devflow.agent.domain` 包，把被 orchestrator 之外大量引用的纯模型类移进去。

### 要移动的文件（7个）

从 `orchestrator/` → `domain/`：

| 文件 | 说明 |
|---|---|
| `StageType.java` | 阶段枚举，被 80+ 个文件引用 |
| `RunRecord.java` | 核心运行记录（record 类） |
| `RunStatus.java` | 运行状态枚举 |
| `RunConfig.java` | 运行配置（依赖 StageType、GatePolicy） |
| `GatePolicy.java` | 门禁策略枚举 |
| `StageExecution.java` | 单阶段执行状态 |
| `StageStatus.java` | 阶段状态枚举 |

`FlowAction`、`FlowDecision`、`FlowDecisionExecutor` 等留在 `orchestrator`（纯内部流程控制，不被外部引用）。

### 需要更新 import 的文件（80个）

以下文件中 `import devflow.agent.orchestrator.{StageType|RunRecord|RunStatus|RunConfig|GatePolicy|StageExecution|StageStatus}` 改为 `import devflow.agent.domain.*`：

**artifact 包（27个文件）**
```
artifact/AnalysisDocumentComposer.java
artifact/AnalysisDocumentPromptBuilder.java
artifact/AnalysisDocumentTemplateBuilder.java
artifact/ArtifactStore.java
artifact/ArtifactTemplateFactory.java
artifact/AuxiliaryArtifactNames.java
artifact/CodeReviewStageComposer.java
artifact/DesignDocumentComposer.java
artifact/DesignDocumentPromptBuilder.java
artifact/DesignDocumentTemplateBuilder.java
artifact/DocumentDraftAssembler.java
artifact/DocumentPromptAssembler.java
artifact/DocumentStageComposer.java
artifact/DocumentStageIntake.java
artifact/DocumentStagePostProcessor.java
artifact/DocumentStageTemplateBuilder.java
artifact/ExecutionStageTemplateBuilder.java
artifact/FileArtifactStore.java
artifact/ImplementationArtifactPersister.java
artifact/ImplementationStageComposer.java
artifact/PrdDocumentComposer.java
artifact/PrdDocumentPromptBuilder.java
artifact/PrdDocumentTemplateBuilder.java
artifact/StageArtifactComposer.java
artifact/StageArtifactInputResolver.java
artifact/StageArtifactNames.java
artifact/TestStageComposer.java
```

**context 包（7个文件）**
```
context/ArtifactContextSanitizer.java
context/ContextLayerAssembler.java
context/ContextProjectionArtifactReader.java
context/ContextProjector.java
context/FailureDigest.java
context/TraceContextView.java
context/WorkingContextView.java
```

**executor 包（14个文件）**
```
executor/CoderTurnCoordinator.java
executor/ImplementationContextResolver.java
executor/ImplementationEventJournal.java
executor/ImplementationExecutor.java
executor/ImplementationOutlinePromptBuilder.java
executor/ImplementationPlanner.java
executor/ImplementationPlanningPromptAssembler.java
executor/ImplementationPlanRunner.java
executor/ImplementationSharedContextFactory.java
executor/ImplementationSubtaskDetailPromptBuilder.java
executor/ImplementationToolContext.java
executor/ImplementationToolLoopExecutor.java
executor/SubtaskAttemptContext.java
executor/SubtaskAttemptRunner.java
executor/SubtaskExecutor.java
executor/SubtaskPerformanceGuidanceResolver.java
executor/SubtaskVerificationSupport.java
```

**其他包（12个文件）**
```
interfaceadapter/cli/CliArgumentSupport.java
interfaceadapter/cli/CliOutputRenderer.java
interfaceadapter/cli/CliRunCommandHandler.java
loop/AgentLoop.java
loop/LoopState.java
loop/LoopStepResult.java
loop/TransitionDecision.java
prompt/ConstraintPromptCatalog.java
prompt/DocumentStagePromptCatalog.java
prompt/PromptTemplateCatalog.java
repair/DiagnosisAgent.java
repair/DiagnosisArtifactReader.java
repair/DiagnosisPromptAssembler.java
repair/DiagnosisSimilaritySupport.java
review/DocumentIntegrityGuardSupport.java
review/DocumentReviewNormalizer.java
review/DocumentReviewTurnExecutor.java
review/DocumentStructureGuard.java
review/ImplementationReviewContextAssembler.java
review/ImplementationReviewTurnExecutor.java
review/RequiredDocumentSectionPolicy.java
review/ReviewArtifactLoader.java
review/StageReviewer.java
supervisor/DeliveryPolicySanitizer.java
supervisor/SupervisorAgent.java
supervisor/SupervisorDecision.java
supervisor/SupervisorDecisionPromptBuilder.java
supervisor/SupervisorDecisionSanitizer.java
supervisor/SupervisorFallbackPolicy.java
supervisor/SupervisorGenerationRecoveryPromptBuilder.java
supervisor/SupervisorPayloadNormalizer.java
supervisor/SupervisorPromptAssembler.java
supervisor/SupervisorStageFallbackSupport.java
```

### 改后依赖图

```
before:
  context ←→ orchestrator   (双向)

after:
  context    ──→  domain
  orchestrator ──→  domain
  context    ✗   orchestrator  (单向消除)
```

---

## Step 2：`executor` 子包拆分

### 问题

426 个类堆在同一个包，新功能无处安放，`implementation/` 相关类已有 100+ 个。

### 目标子包结构

```
executor/
├── llm/            约 23 个类
├── context/        约  7 个类
├── generation/     约 19 个类
├── patch/          约 52 个类
├── editing/        约 42 个类
├── implementation/ 约100 个类
├── testing/        约 41 个类
├── tools/          约 11 个类
├── shell/          约  5 个类
├── subtask/        约 21 个类
├── runtime/        约 25 个类
└── gate/           约 20 个类
```

### 各子包归属清单

**`llm/`** — LLM 通信层
```
ChatCapableLlmProvider, LlmChatMessage, LlmChatRequest, LlmChatResponse,
LlmChatRole, LlmFailureReason, LlmInvocationException, LlmOptionKeys,
LlmOptions, LlmProvider, LlmToolCall, LlmToolDefinition,
ModelBudgetProfile, ModelBudgetRegistry, ModelRole,
OllamaChatExecutor, OllamaClientPolicy, OllamaGenerationExecutor,
OllamaLlmProvider, OllamaProperties, OllamaStructuredReviewExecutor,
OllamaTransportClient, OllamaProperties
```

**`context/`** — 上下文预算和压缩
```
CompactedPrompt, ContextBudgetBreakdown, ContextBudgetPlan,
ContextBudgetPlanner, ContextCompactor,
OutputBudgetCalculator, OutputBudgetDecision,
PromptTokenEstimator, PromptTokenEstimatorSettings
```

**`generation/`** — 生成通用机制
```
GenerationAttemptExecutor, GenerationAttemptResult,
GenerationBudgetProfile, GenerationBudgetProperties,
GenerationEngine, GenerationExceptionClassifier,
GenerationExecutionPolicy, GenerationFailureClassifier,
GenerationFailureException, GenerationFailureExceptions,
GenerationFailureFactory, GenerationFailureReport,
GenerationFailureType, GenerationObserver, GenerationSpec,
GenerationTelemetry, GenerationTelemetryFormatter,
RetryPromptComposer, WorkerResult, WorkerResultAssembler
```

**`patch/`** — 补丁生成、应用、修复
```
CodePatchFeedbackPolicyFactory, CodePatchFeedbackRenderer,
CodePatchKernel, CodePatchPromptAssembler, CodePatchUnitExecutor,
DeterministicExactReplaceRepairer, DeterministicJsonPayloadRepairer,
DeterministicSyntaxRepairer, EmbeddedPatchApplySupport,
EmbeddedPatchBehavior, EmbeddedPatchExecutor,
EmbeddedPatchFeedbackPolicyFactory, EmbeddedPatchFeedbackRenderer,
EmbeddedPatchHostValidator, EmbeddedPatchKind, EmbeddedPatchOutcome,
EmbeddedPatchPromptAssembler, EmbeddedPatchPromptSupport,
EmbeddedPatchUnitExecutor, ExactReplacePromptSupport,
ExactReplaceSemanticRepairSupport, ExactReplaceSemanticRepairTurn,
ModelJsonRepairTurn, PatchApplyResult, PatchAttemptFailureSupport,
PatchAttemptFeedbackPolicy, PatchBudgetPolicy, PatchBudgetSettings,
PatchContextBuilder, PatchExecutionSupport, PatchFailure,
PatchFailureClass, PatchFailureDisposition, PatchFailureRouter,
PatchFailureRoutingSettings, PatchGenerationPrompt, PatchOperation,
PatchOperationType, PatchPayloadRepairSupport, PatchPlan,
PatchRepairClassifier, PatchRepairSettings, PatchTarget,
PatchTargetContext, PatchTargetKind, PatchUnitSizer, PatchVerifier,
RepairDiffScopeValidator, RepairScopeValidator,
SyntaxRepairSupport, SyntaxRepairTurn,
DeterministicGate, DeterministicSyntaxRepairer
```

**`editing/`** — 文件编辑策略（HTML/代码/内联脚本）
```
ChangeAction, CodeFileEditExecutor, CodeTargetedRewriteRequest,
EmbeddedTargetedRewriteRequest, EmbeddingAdapter, EmbeddingEditPlan,
FileEditAttemptState, FileEditProtocolNames, FileEditRequest,
FileEditRequestFactory, FileEditScope, FileEditStrategyNames,
FileEditStrategyResolver, FileFeedbackScopeSupport,
FileGenerationFailureFactory, FileMutationRecord,
FileProtocolRequestBuilder, FileProtocolRequestContext,
FileProtocolRequestContextFactory, FileProtocolRequestFactory,
FileScopedContextSupport, FocusedRegionHtmlPatchExecutor,
FullRewriteExecutor, FullRewritePromptAssembler, FullRewriteRequest,
HostHtmlPatchExecutor, HtmlEditRegion, HtmlEditRoutingPolicy,
HtmlFileEditExecutor, HtmlFocusedRegionResolver,
HtmlInlineScriptEmbeddingAdapter, HtmlInlineScriptWorkingSetResolver,
HtmlInlineStyleEmbeddingAdapter, HtmlInlineStyleWorkingSetResolver,
HtmlPatchFeedbackRenderer, HtmlPatchPromptAssembler,
HtmlStructureCaseBuilder, HtmlTargetedRewriteRequest,
HtmlTargetedRewriteRequestBuilder,
InlineScriptEditPlan, InlineScriptEmbeddedPatchBehavior,
InlineScriptExecutionOutcome, InlineScriptExtractToFileStrategy,
InlineScriptWorkingSet, InlineStyleEditPlan,
InlineStyleEmbeddedPatchBehavior, InlineStyleWorkingSet,
LanguageEditAdapter, PreciseCodePatchExecutor, PreciseHtmlPatchExecutor,
StructuredHtmlDraftFeedbackRenderer, StructuredHtmlDraftPromptAssembler,
StructuredHtmlPatchExecutor, StructuredPatchHunk, StructuredPatchSupport,
TargetedFileContextRenderer, TargetLocator, TreeSitterCodeEditAdapter,
TreeSitterTargetLocator, WholeFileFeedbackRenderer
```

**`implementation/`** — 实现阶段规划和执行（最大子包）
```
所有 Implementation* 开头的类（约 100 个），以及：
ArchitectIntegrationCheck, ArchitectIntegrationCheckResult,
ArchitectIntegrationCheckScope, ArchitectIntegrationFailureReason,
CoderReadFileState, CoderTurnCoordinator, CodeScaffoldExpansionPlanner,
DeliveryMode, DeliveryPolicyEnvelope,
EditUnit, EditUnitKind, EditUnitPlanner, EditUnitPlanningPolicy,
ReusableImplementationState, ScopedTaskPackageSupport,
TaskPackage, TaskPackageAssembler, TaskPackageMarkdownRenderer
```

**`testing/`** — 测试规划和执行
```
所有 Test* 开头的类（约 34 个），以及：
CollectedTestEvidence, CoverageLedgerBuilder, CoverageResult,
ExperienceFailureDisposition, ExperienceFailureDispositionResolver,
ExperienceFailureKind, ObservedInteractionTestCaseBuilder,
PerformanceCaseBuilder, PlannedTestCasesPayload,
PlaywrightCaseExecutor, PlaywrightCaseRunSupport,
PlaywrightExecutionPolicy, PlaywrightProbeRunner,
PlaywrightRuntimeSnapshotSupport, PlaywrightSupport
```

**`tools/`** — tool 定义和工具调用
```
BashTool, FileDeleteTool, FileEditTool, FileReadTool, FileWriteTool,
GlobTool, GrepTool, ImplementationTool, ImplementationToolContext,
ImplementationToolLoopExecutor, ImplementationToolLoopResult,
ImplementationToolPermissionContext, ImplementationToolPermissionPolicy,
ImplementationToolPermissionScope, ImplementationToolPromptBuilder,
ImplementationToolRegistry, ImplementationToolResultBudgetManager,
ImplementationToolResultMessage, ImplementationToolSessionState,
ImplementationToolSpecification,
FatalToolExecutionException, ToolFailureCode, ToolInvocationResult,
ToolLoopDiagnosticStatus, ToolLoopMutationOperation,
ToolLoopReadFileStateLedger, ToolLoopResultReplacementState,
ToolName, ToolResult, ToolStatus
```

**`shell/`** — Shell 命令分析
```
ShellCommandAnalyzer, ShellCommandDecision,
RipgrepCommandSupport, CommandResult
```

**`subtask/`** — 子任务分解和执行
```
Subtask, SubtaskAttemptContext, SubtaskAttemptProgress,
SubtaskAttemptReport, SubtaskAttemptResult, SubtaskAttemptRunner,
SubtaskAttemptStepExecutor, SubtaskExecutionReport,
SubtaskExecutionState, SubtaskExecutor,
SubtaskPerformanceGuidanceResolver, SubtaskRecoverySupport,
SubtaskRetryFeedbackRenderer, SubtaskReviewObserverFactory,
SubtaskReviewPolicy, SubtaskReviewPromptAssembler,
SubtaskRevisionDirective, SubtaskRunnableMilestoneGuard,
SubtaskRuntimeWiringGuard, SubtaskVerificationOutcome,
SubtaskVerificationSupport
```

**`runtime/`** — 运行时观测和合约
```
ExternalizedRuntimeHostNormalizer,
HtmlEntryRuntimeOwnershipInspection, HtmlEntryRuntimeOwnershipInspector,
HtmlRuntimeOwnershipContract,
RuntimeControlCandidate, RuntimeOwnershipMode,
RuntimeScriptGraphInspector, RuntimeSnapshot,
RuntimeSnapshotCaptureResult, RuntimeSnapshotCaptureStatus,
RuntimeSnapshotFailureCode, RuntimeSurfaceCandidate,
RuntimeWiringPatchDecision, RuntimeWiringPatchDecisionResolver,
RuntimeWiringRetryChangeFactory, RuntimeWorkingSetPolicy,
RuntimeWorkingSetResolver,
UiObservationMode, UiObservationTarget, UiRuntimeContract,
UiRuntimeContractResolver, UiRuntimeContractValidation,
UiRuntimeContractValidationKind, UiRuntimeObservationPolicy,
WebRuntimeAssetWiringInspection, WebRuntimeAssetWiringInspector,
WebRuntimeMetricKeys, WebRuntimeWiringCheck, WebRuntimeWiringResult
```

**`gate/`** — 内容校验和门禁
```
CapabilityCoverageBackfillSupport,
GateFailureDisposition, GateIssue, GateIssueContext,
GateReport, GeneratedAuxiliaryWrite,
GeneratedContentGate, GeneratedContentGateInput,
GeneratedContentValidationCode, GeneratedContentValidationFailure,
GeneratedFileOutput, GeneratedHtmlContentValidator,
GeneratedJavaScriptContentValidator,
GeneratedJavaScriptStructureValidator,
GeneratedPayloadSupport, GeneratedPreciseAnchorValidator,
GeneratedTreeSitterValidator,
JavascriptNoOpSwitchScanner, JavascriptPropertyStubScanner,
JavascriptScannerSupport,
StructuredPayloadException, StructuredPayloadFailureReason,
StructuredPayloadReader,
SelfCheckResult
```

---

## Step 3：顺手修的小问题（搭在 Step 1/2 一起）

### 3a. WorkflowEngine 接口修契约

**改动文件**：
- `orchestrator/WorkflowEngine.java` — 4 个方法加 `Path projectPath` 第一参数
- `orchestrator/DefaultWorkflowEngine.java` — 删除 throw 的 4 个旧方法，带 Path 的方法加 `@Override`
- `interfaceadapter/cli/DevflowCliRunner.java` — 注入类型改为 `WorkflowEngine`
- `interfaceadapter/cli/CliRunCommandHandler.java` — 同上

```java
public interface WorkflowEngine {
    RunRecord startRun(Path projectPath, UUID runId);
    RunRecord resumeRun(Path projectPath, UUID runId);
    RunRecord approveStage(Path projectPath, UUID runId, StageType stageType, String reviewer);
    RunRecord rejectStage(Path projectPath, UUID runId, StageType stageType, String reviewer, String reason);
}
```

### 3b. SupervisorAgent 异常日志

**改动文件**：`supervisor/SupervisorAgent.java`

```java
private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);

// line 118
log.warn("Supervisor decision failed, using fallback. stage={}", currentStage, ignored);

// line 168
log.warn("Generation recovery decision failed, using fallback. attempt={}", subtaskAttempt, ignored);
```

### 3c. CompletableFuture 指定虚拟线程池

**改动文件**：`executor/ImplementationToolLoopExecutor.java`

```java
// 类字段（Java 21 虚拟线程）
private static final Executor TOOL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

// line 320
.map(call -> CompletableFuture.supplyAsync(() -> executeToolCall(call, context), TOOL_EXECUTOR))
```

---

## 执行建议

```
Branch A（Step 1）: 提取 domain 包
  → 移 7 个文件，批量替换 80 个文件的 import
  → mvn compile 验证

Branch B（Step 2）: executor 子包拆分
  → 分 12 个子包逐个移动，每个子包 mvn compile 一次
  → 最后 mvn test

Branch A/B 可并行，不互相阻塞。
Step 3 小修可搭在 Branch A 的最后一个 commit 里。
```

## 验证

```bash
mvn compile         # 无报错
mvn test            # 全绿
```
