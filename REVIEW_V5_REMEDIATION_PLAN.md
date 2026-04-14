# Claude Review 收口方案 V5

## Purpose

本文件定义 `REVIEW_V5.md` 的唯一收口方案。

执行时必须同步维护：

- `docs/review-v5-remediation-progress.md` 作为本轮唯一 tracker
- 每进入一个 phase 前先更新 tracker
- 每完成一个 phase，必须补 `self-test / code review / docs / commit` 证据

## Summary

本轮收口 `REVIEW_V5.md` 中的 P1 + P2，主线有 4 条：

1. `ContextProjector` 重复投影消除：`SupervisorAgent.decide()` 改接外部传入的 `ProjectedContext`
2. `FlowController.shouldContinue()` 静默 null 补日志
3. `maxAutoRevisions` 检查去重、`ImplementationExecutor` 重载链收口（小修）
4. `StageProgressCoordinator` 拆分：13 依赖上帝对象 → 责任分层与边界收口

## Scope

**纳入本轮**：

- P1 全部（2 条）
- P2 全部（4 条）
- P3 中仅纳入直接阻塞本轮主线的一条：
  - `SupervisorAgent.decide()` 与 `decideGenerationFailure()` 共同提取决策骨架

**不纳入本轮**：

- 可观测性（Micrometer / tracing / event timeline 增强）
- `ContextCompactor` 语义摘要器
- `QualityRulesLoader` 路径配置化
- `StageStatusSupport` 与 `StageRevisionSupport` 状态转移骨架合并（复杂度高、风险大，单独立项）

## Final State

完成态必须同时满足：

- `SupervisorAgent.decide()` 不再内部调用 `contextProjector.project()`，接收调用方传入的 `ProjectedContext`
- `StageProgressCoordinator.progress()` 只调用一次 `contextProjector.project()`，结果传给 `supervisorAgent.decide()`
- `FlowController.shouldContinue()` 在 `currentStage == null` 时有 `log.warn()` 记录
- `maxAutoRevisions` 超限检查只在一处定义，`StageTransitionSupport` 和 `StageRevisionSupport` 共用
- `ImplementationExecutor` 的冗余重载已清理，只保留一个规范入口
- `StageProgressCoordinator` 不再直接依赖 `toolResultLoader`、`toolResultGuard`、`implementationStateSupport`、`implementationContinuationSupport`
- 工具结果处理与 implementation 特殊路径已分离到独立协作类
- `SupervisorAgent.decide()` 与 `decideGenerationFailure()` 的 fallback→LLM→sanitize 控制骨架共用同一私有辅助方法

## Removal Plan

本轮必须删除：

- `SupervisorAgent` 中对 `contextProjector.project()` 的内部调用（`decide()` 路径）
- `StageTransitionSupport.continueStage()` 中的 `maxAutoRevisions` 判断片段（迁移到共用辅助）
- `StageRevisionSupport.rerouteForRevision()` 中的 `maxAutoRevisions` 判断片段（迁移到共用辅助）
- `ImplementationExecutor` 中 3 个仅做参数补全的短签名重载
- `StageProgressCoordinator` 中直接注入的 `toolResultLoader`、`toolResultGuard`、`implementationStateSupport`、`implementationContinuationSupport` 字段（迁移到协作类）

## Joint-Change Scope

### Phase 1 联动范围

- `supervisor/`
  - `SupervisorAgent`：`decide()` 签名增加 `ProjectedContext` 参数，移除内部 `project()` 调用
  - `SupervisorConfiguration`：如有 wiring 需要调整
- `orchestrator/`
  - `StageProgressCoordinator`：传 `projectedContext` 进 `supervisorAgent.decide()`
  - `StageTransitionSupport`：`maxAutoRevisions` 检查移到辅助方法
  - `StageRevisionSupport`：同步切到辅助方法
  - `FlowController`：补 `log.warn`
- `executor/`
  - `ImplementationExecutor`：删除冗余重载，保留单一规范 `execute()`
  - `artifact/ImplementationStageComposer`：切到单一规范入口
  - executor 相关测试与 test support：同步调整调用点

联动测试范围：

- `StageProgressCoordinatorTests`（已有）
- `SupervisorAgentTests`（已有）
- `StageTransitionSupportTests`（已有）
- `DefaultWorkflowEngineTests`（已有）
- 新增 `FlowControllerTests`

### Phase 2 联动范围

- `supervisor/`
  - `SupervisorAgent`：提取共享的“fallback / should-call-llm / llm-path / exception fallback”控制骨架，`decide()` 与 `decideGenerationFailure()` 共用

联动测试范围：

- `SupervisorAgentTests`（已有，验证两条路径行为不变）
- `SupervisorFallbackPolicyTests`（已有）

### Phase 3 联动范围

- `orchestrator/`
  - `StageProgressCoordinator`：拆分为薄编排层 + `StageToolResultGate` + `ImplementationProgressSupport`
  - 新增 `StageToolResultGate`：接管 `toolResultLoader` + `toolResultGuard`
  - 新增 `ImplementationProgressSupport`（或复用已有 support）：接管 `implementationStateSupport` + `implementationContinuationSupport` 的调用路径
  - `OrchestratorConfiguration`：补新 bean 装配

联动测试范围：

- `StageProgressCoordinatorTests`（已有，验证依赖数减少后行为不变）
- `DefaultWorkflowEngineTests`（已有）
- 新增 `StageToolResultGateTests`

## Phase Plan

### Phase 1. ContextProjector 单次投影 + 小修项

- `SupervisorAgent.decide()` 签名改为接收 `ProjectedContext`，移除内部 `project()` 调用
- `StageProgressCoordinator.progress()` 将已有 `projectedContext` 传入 `supervisorAgent.decide()`
- `FlowController.shouldContinue()` 补 `log.warn("...")` 在 `currentStage == null` 分支
- 提取 `maxAutoRevisions` 超限辅助方法，`StageTransitionSupport` 与 `StageRevisionSupport` 共用
- `ImplementationExecutor` 删除 3 个短签名重载，只保留全参规范入口
- `ImplementationStageComposer` 与相关测试调用点同步切到全参版本
- 新增 `FlowControllerTests`
- Phase 1 self-test / code review / docs

### Phase 2. SupervisorAgent 决策骨架统一

- 提取私有控制骨架方法（`decide()` 与 `decideGenerationFailure()` 共用 fallback→should-call-llm→llm-path→exception fallback 流程）
- 验证两条路径语义不变
- Phase 2 self-test / code review / docs

### Phase 3. StageProgressCoordinator 拆分

- 新增 `StageToolResultGate`：封装 `toolResultLoader.load()` + `toolResultGuard.guard()`
- 将 implementation 特殊路径（`implementationStateSupport`、`implementationContinuationSupport`）内聚
- `StageProgressCoordinator` 最终不再直接持有工具结果与 implementation continuation 细节协作者
- `OrchestratorConfiguration` 补新 bean 装配
- 新增 `StageToolResultGateTests`
- Phase 3 self-test / code review / docs

## Key Changes

### 1. ContextProjector 单次投影（P1）

**问题**：`StageProgressCoordinator.progress()` 在 line 120 调用 `contextProjector.project()`，随后 `supervisorAgent.decide()` 内部（line 86）也独立调用一次，导致同一个 progress 步骤内投影两次（重复 I/O，且两次调用间文件系统状态可能变化）。

**修复**：

`SupervisorAgent.decide()` 新签名：

```java
public SupervisorDecision decide(
    Path projectPath,
    RunRecord runRecord,
    StageType stageType,
    ReviewResult reviewResult,
    boolean repeatedIssue,
    ProjectedContext projectedContext      // 新增，由外部传入
)
```

`StageProgressCoordinator.progress()` 已在 line 120 持有 `projectedContext`，直接传入即可。`SupervisorAgent.decide()` 内部删除 `contextProjector.project()` 调用。

`decideGenerationFailure()` 路径仍保留独立投影。也就是说，这轮只删除 `decide()` 的内部重复投影，不把整个 `SupervisorAgent` 去投影化。

### 2. FlowController.shouldContinue() 静默 null（P1）

**问题**：`currentStage == null` 时 loop 停止但没有任何日志，生产环境无法区分正常结束与状态丢失。

**修复**：

```java
public boolean shouldContinue(RunRecord runRecord) {
    StageExecution currentStage = runRecord.stageStates().get(runRecord.currentStage());
    if (currentStage == null) {
        log.warn("shouldContinue: currentStage not found in stageStates, runId={} stage={}",
                runRecord.runId(), runRecord.currentStage());
        return false;
    }
    return runRecord.status() == RunStatus.IN_PROGRESS
            && currentStage.status() == StageStatus.RUNNING;
}
```

### 3. maxAutoRevisions 检查去重（P2）

**问题**：`StageTransitionSupport.continueStage()` line 140 与 `StageRevisionSupport.rerouteForRevision()` line 126 各自实现了同一段"超限则标 FAILED + 写事件 + save + return"逻辑。

**修复**：在 `StageStatusSupport` 中提取：

```java
Optional<RunRecord> checkMaxRevisions(
    Path projectPath,
    RunRecord runRecord,
    StageType stageType,
    StageExecution currentExecution,
    Map<StageType, StageExecution> nextStates
)
```

返回非空表示已超限并已落盘，调用方直接 return。两处调用点改为：

```java
Optional<RunRecord> exceeded = stageStatusSupport.checkMaxRevisions(...);
if (exceeded.isPresent()) return exceeded.get();
```

### 4. ImplementationExecutor 重载链收口（P2）

**问题**：4 个 `execute()` 重载通过链式补默认值，调用方不清楚规范入口。

**修复**：删除前 3 个短签名重载，只保留：

```java
public ImplementationExecutionBundle execute(
    Path projectPath,
    RunRecord runRecord,
    String analysis,
    String prd,
    String design,
    String note,
    ContractView authoritativeContractView,
    String previousStateJson,
    ImplementationProgressSink progressSink
)
```

调用方统一使用全参版本，`null` / `""` / `noop()` 显式传入。

### 5. SupervisorAgent 决策骨架提取（P2 配套）

**问题**：`decide()` 与 `decideGenerationFailure()` 结构几乎一致（fallback → 是否调 LLM → sanitize），但两条路径的 payload 和 sanitizer 签名不同，不能靠错误的返回类型约束硬凑成一个“统一泛型决策器”。

**修复**：提取共享控制骨架，而不是错误的 `T extends SupervisorDecision` 泛型：

```java
private <T> T resolveWithFallback(
    Supplier<T> fallbackSupplier,
    BooleanSupplier shouldCallLlm,
    Supplier<T> llmPath,
    Consumer<Exception> failureLogger
)
```

语义是：

- 先求 fallback
- 决定是否需要调 LLM
- 如果需要则执行各自的 llm-path，其中各自完成 payload parse + sanitize
- 失败时统一回落 fallback，并记录各自日志

这样共用的是控制骨架，而不是伪统一两条不同业务返回值的类型层次。

### 6. StageProgressCoordinator 拆分（P2 主线）

**问题**：13 个依赖，存储层 + 诊断决策 + 上下文投影 + 执行决策流 + 工具结果处理 + implementation 特殊路径全混在一个类，单元测试极难。

**目标是边界收口，不是机械压字段数**：

新增 `StageToolResultGate`（封装 `toolResultLoader` + `toolResultGuard`）：

```java
class StageToolResultGate {
    ReviewResult apply(Path projectPath, RunRecord runRecord,
                       StageType stageType, ReviewResult reviewed)
}
```

`StageProgressCoordinator` 中 implementation 特殊路径（`implementationStateSupport.renderImplementationReviewSummary()` 与 `implementationContinuationSupport`）已通过现有 support 对象内聚，coordinator 不再直接持有这两个字段，而是内聚到一个 `ImplementationStageProgressAdapter`（或复用已有辅助类）中，减少 coordinator 直接感知的 implementation 细节。

一个合理的最终依赖面可以接近下面这个形态，但它只是结果示意，不是验收门槛：

| 字段 | 职责 |
|---|---|
| `stageOperationExecutor` | stage 执行 |
| `stageToolResultGate` | 工具结果聚合（新） |
| `supervisorAgent` | supervisor 决策 |
| `flowController` | flow 决策 |
| `flowDecisionExecutor` | 决策落盘 |
| `contextProjector` | 上下文投影 |
| `artifactSupport` | 产物写入 |

`diagnosisAgent.shouldDiagnose()` 调用移入 `StageToolResultGate` 或单独提取为 `RepeatIssueDetector`（视实现复杂度决定）。

完成门槛不是“字段数 ≤ 7”，而是：

- coordinator 不再直接依赖 `toolResultLoader`
- coordinator 不再直接依赖 `toolResultGuard`
- coordinator 不再直接依赖 `implementationStateSupport`
- coordinator 不再直接依赖 `implementationContinuationSupport`
- implementation 特殊路径由独立协作者承接
- 工具结果 gate 有独立测试

## Test Plan

编译验证：

```
mvn -q -DskipTests compile
mvn -q -DskipTests test-compile
```

定向测试：

```
mvn -q -Dtest=StageProgressCoordinatorTests,SupervisorAgentTests,StageTransitionSupportTests test
mvn -q -Dtest=DefaultWorkflowEngineTests,FlowControllerTests test
mvn -q -Dtest=SupervisorFallbackPolicyTests,StageToolResultGateTests test
```

说明：

- `FlowControllerTests` 当前不存在，本轮应新增
- `StageToolResultGateTests` 当前不存在，Phase 3 新增

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
- [ ] `OrchestratorConfiguration` 无残留旧 wiring
- [ ] `self-test + code review + docs + tracker` 已全部补齐
