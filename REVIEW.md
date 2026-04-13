# Forge Code Review（2026-04-13）

## 概览

| 指标 | 上次（2026-04-12） | 本次 |
|---|---|---|
| 主代码类数 | 527 | **746** |
| executor 包类数 | 344 | **426** |
| 最大单类行数 | ~ | **1134（ShellCommandAnalyzer）** |

---

## 进步点

| 改进项 | 之前 | 现在 |
|---|---|---|
| `DefaultWorkflowEngine` 构造器 | 93 行硬串依赖 | 委托给 `WorkflowRunLifecycleSupport`，结构清晰 |
| `SupervisorAgent` | 臃肿 | 薄封装，PromptAssembler/Sanitizer/FallbackPolicy 拆分到位 |
| `WorkflowEngine` 接口 | 方法语义混乱 | 接口简化为 4 个方法，契约清晰 |
| 上下文预算 | 无 | 新增 `ContextBudgetPlanner`，有 token 估算和比例分配逻辑 |
| tool 并行执行 | 无 | `ImplementationToolLoopExecutor` 用 `CompletableFuture` 批量并发执行 tool call |
| 模块分离 | executor 大杂烩 | 新增 `editing`/`parsing`/`validation`/`review` 独立模块 |

---

## 问题分级

### P1（阻碍可测试性或正确性）

#### 1. `WorkflowEngine` 接口仍是死接口

- **位置**：`orchestrator/WorkflowEngine.java`，`orchestrator/DefaultWorkflowEngine.java:109-136`
- **现状**：接口定义了 `startRun(UUID)` 等 4 个方法，`DefaultWorkflowEngine` 全部 `throw UnsupportedOperationException`；CLI 直接注入 `DefaultWorkflowEngine`（`interfaceadapter/cli/DevflowCliRunner.java:24`）
- **影响**：接口对任何调用者都是陷阱，Spring 注入无意义
- **方案**：见下方 Fix 1

#### 2. `executor` 模块失控增长

- **位置**：`executor/` 包根目录，426 个类无子包
- **现状**：`ShellCommandAnalyzer`（1134 行）、`ImplementationToolContext`（553 行，17 个字段）、`ImplementationToolLoopExecutor`（500 行）裸放在 executor 根包
- **影响**：无内聚边界，难以独立测试，新功能不知道放哪里
- **方案**：见下方 Fix 4（Group 3）

#### 3. `CompletableFuture.supplyAsync()` 没有指定 Executor

- **位置**：`executor/ImplementationToolLoopExecutor.java:319-322`
- **现状**：默认使用 `ForkJoinPool.commonPool()`，工具执行是 I/O 密集型
- **影响**：在 Spring Boot 下可能造成线程池饥饿（阻塞 IO 占满公共池）
- **方案**：见下方 Fix 3

---

### P2（设计债，不影响当前运行但迟早出问题）

#### 4. `SupervisorAgent` 异常静默吞掉（未修复）

- **位置**：`supervisor/SupervisorAgent.java:118`、`supervisor/SupervisorAgent.java:168`
- **现状**：两处 `catch (Exception ignored)` 无日志
- **影响**：supervisor 行为失败时完全黑盒，调试困难
- **方案**：见下方 Fix 2

#### 5. `ContextCompactor` 仍是 truncateMiddle（未修复）

- **位置**：`executor/ContextCompactor.java:50`
- **现状**：`ContextBudgetPlanner` 把预算算清楚了，但压缩执行仍是字符截断；四层结构（`context/ContextLayerAssembler.java`）已存在但未接通
- **影响**：超预算时丢失语义结构，Durable 层（用户目标/约束）和 Trace 层（历史摘要）被随机截断
- **方案**：见下方 Fix 5（Group 2）

#### 6. `context ↔ orchestrator` 双向耦合（未修复）

- **现状**：`context` 包大量导入 `StageType`/`RunRecord`/`StageExecution`；`orchestrator` 包反向导入 `ContextProjector`/`ProjectedContext`
- **根因**：`StageType`/`RunRecord` 应属于 domain 层，不应归属 orchestrator
- **方案**：将 `StageType`、`RunRecord`、`StageExecution` 移至 `domain/` 包（较大重构，暂列 P2 后期）

---

### P3（代码质量，不影响功能）

#### 7. `StageProgressCoordinator` 上帝对象改进有限

- **位置**：`orchestrator/StageProgressCoordinator.java`，10 个字段
- **现状**：`toFileChange()`、`implementationPatchTarget()` 等职责属于 payload 转换，不该在这里
- **建议**：提取 payload 转换到独立 converter 类

#### 8. 无沙箱隔离

- **现状**：Shell 命令通过 `ShellCommandAnalyzer`（1134 行）做白名单分析，是软件级分析，没有 OS 级隔离
- **建议**：后期考虑进程级沙箱（类 Codex landlock 或 Docker）

---

## 修复方案

### Group 1：Quick Wins

#### Fix 1: WorkflowEngine 接口修契约

**改动文件**：
- `orchestrator/WorkflowEngine.java` — 4 个方法签名加 `Path projectPath` 第一参数
- `orchestrator/DefaultWorkflowEngine.java` — 删除 throw 的 4 个旧方法，带 Path 的方法加 `@Override`
- `interfaceadapter/cli/DevflowCliRunner.java` — 注入类型从 `DefaultWorkflowEngine` 改为 `WorkflowEngine`
- `interfaceadapter/cli/CliRunCommandHandler.java` — 同上

**更新后的接口**：
```java
public interface WorkflowEngine {
    RunRecord startRun(Path projectPath, UUID runId);
    RunRecord resumeRun(Path projectPath, UUID runId);
    RunRecord approveStage(Path projectPath, UUID runId, StageType stageType, String reviewer);
    RunRecord rejectStage(Path projectPath, UUID runId, StageType stageType, String reviewer, String reason);
}
```

#### Fix 2: SupervisorAgent 异常日志

**改动文件**：`supervisor/SupervisorAgent.java`

```java
// 类顶部加
private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);

// 第 118 行改为
log.warn("Supervisor decision failed, using fallback. stage={}", currentStage, ignored);

// 第 168 行改为
log.warn("Generation recovery decision failed, using fallback. attempt={}", subtaskAttempt, ignored);
```

#### Fix 3: CompletableFuture 指定虚拟线程池

**改动文件**：`executor/ImplementationToolLoopExecutor.java`

```java
// 类字段加（Java 21 虚拟线程）
private static final Executor TOOL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

// 第 320 行改为
.map(call -> CompletableFuture.supplyAsync(() -> executeToolCall(call, context), TOOL_EXECUTOR))
```

---

### Group 2：Medium Effort

#### Fix 4（原 Fix 5）: ContextCompactor 四层感知压缩

**调用链**：
```
OllamaGenerationExecutor.generate()
  → ContextCompactor.compact(modelName, systemPrompt, userPrompt)
    → truncateMiddle(value, maxChars)   ← 改这里
```

**四层压缩优先级**：

| 层 | 类 | 压缩优先级 |
|---|---|---|
| Durable | `DurableContextView` | 最高（最后裁） |
| Working | `WorkingContextView` | 高 |
| Evidence | `EvidenceContextView` | 中 |
| Trace | `TraceContextView` | 最低（最先裁） |

**改动文件**：
- `executor/ContextCompactor.java`
  - 新增重载：`compact(String modelName, ContextViews contextViews)`
  - 按优先级（Trace→Evidence→Working→Durable）渐进裁剪至预算
  - 保留原有 `compact(modelName, system, user)` 作为兜底
- `executor/OllamaGenerationExecutor.java`
  - 优先调用新重载；否则 fallback 到字符串版本

---

### Group 3：Large Refactor

#### Fix 5: executor 子包拆分

**目标结构**：
```
executor/
├── llm/            # LlmProvider, OllamaLlmProvider, LlmChatMessage/Request/Response 等
├── context/        # ContextCompactor, ContextBudgetPlanner, ContextBudgetPlan 等
├── generation/     # GenerationEngine, OllamaGenerationExecutor, GenerationBudgetProfile 等
├── patch/          # Patch* (PatchPlan, PatchOperation, PatchVerifier, CodePatchKernel 等)
├── editing/        # File*, ExactReplace*, FullRewrite*, EmbeddedPatch*, Html* 编辑相关
├── implementation/ # Implementation* (Planner, Executor, ToolContext, ToolLoopExecutor 等)
├── testing/        # Test* (TestExecutor, TestCasePlanner, TestRunner 等)
├── tools/          # BashTool, FileReadTool, FileEditTool, GlobTool, GrepTool 等
└── shell/          # ShellCommandAnalyzer, CommandResult 等
```

**注意事项**：
- 只做包移动，不改类逻辑
- 建议分子包逐步移动，每次移完一个子包后验证 `mvn compile`

---

## 执行顺序

```
1. Group 1（Fix 1 + Fix 2 + Fix 3）— 一起提交，风险低
2. Group 2（Fix 4）— 独立分支，单测验证压缩行为
3. Group 3（Fix 5）— 独立分支，纯 refactor，逐子包移动
```

## 验证

- Group 1：`mvn compile` 无报错；CLI `startRun`/`resumeRun` 可正常路由
- Group 2：单测验证超预算时 Trace 层先被裁剪，Durable 层保留
- Group 3：`mvn compile` + `mvn test` 全绿
