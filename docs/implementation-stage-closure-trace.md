# Implementation Stage Closure Trace

## Purpose

这份文档把已通过审阅的 `docs/implementation-stage-closure-plan.md` 抽成一份**可执行 trace 矩阵**。

它只回答 5 件事：

1. 当前要收的到底是哪几类问题
2. 每类问题的 canonical owner 是谁
3. 每类问题必须穿过哪条协议边界
4. 每类问题要靠哪些回归用例锁死
5. 每类问题在执行顺序里属于哪一 phase

这份文档不是 tracker，不记录完成进度；进度只记在 `docs/implementation-stage-closure-execution.md`。

## Inputs

- `docs/implementation-stage-closure-plan.md`
- `docs/active-work-items.md`
- `AGENTS.md`
- `docs/engineering-agreements.md`

## Out Of Scope

以下内容不是本轮 trace 的问题族，不得混入执行范围：

- token 预算或上下文预算整改
- 旧 runtime metadata 回灌 planning detail
- 已收口的 continuation routing 基础修复
- 已收口的 orphan runtime 基础修复
- 与本轮黄金路径失败无直接关系的新增能力扩展

## Trace Matrix

### T1. Capability Partition And Task Package Boundary

- 问题族：
  skeleton / runnable 子任务吞掉后续能力，planning 已收紧后又被 task package fallback 灌回。
- canonical owners：
  `ImplementationPlanNormalizationSupport`
  `ImplementationPlanGateInputBuilder`
  `ImplementationPlanningWiring`
  `ImplementationPlanner`
  `TaskPackage`
  `TaskPackageAssembler`
  `TaskPackageMarkdownRenderer`
  `ImplementationToolPromptBuilder`
- 协议边界：
  `ImplementationPlan`
  `Subtask`
  `TaskPackage`
- 必须删除：
  `ownedCapabilities <- acceptanceCriteria` fallback
  `TaskPackage.alignToSubtask()` 中把旧 capability 灌回当前 subtask 的 fallback
- 必须锁死的回归：
  `R1. skeleton capability boundary`
- 执行 phase：
  `Phase 1`
  `Phase 2`

### T2. Accepted Package Completeness For Runtime Split

- 问题族：
  新 runtime root 与 host entry patch 被拆到不同 execution package，coder 在执行期才临时发现需要改入口页。
- canonical owners：
  `ImplementationPlanGateInput`
  `ImplementationPlanGateInputBuilder`
  `PlanningRequest`
  `ImplementationPlanningWiring`
  `ImplementationOutlineGate`
  `ImplementationPlanCoverageAnalyzer`
  `ImplementationPlanChangeGate`
  `ImplementationSubtaskDetailGate`
- 协议边界：
  planning runtime facts 输入
  accepted change-set
  `FileChange`
- 语义边界：
  planning 只校验 accepted package 形状是否自洽，以及新增 runtime 脚本是否显式声明 `ROOT|LEAF`；
  不在 planning 阶段判真 root/leaf 语义，真实 runtime ownership 仍由后续 wiring / ownership check 收口。
- 事实来源约束：
  只允许来自：
  explicit host contract
  当前 accepted scope
  当前 HTML 已观察到的 structured wiring facts
- 必须删除：
  目录扫描
  root-script 猜测
  companion 文件名 fallback
- 必须锁死的回归：
  `R2. runtime split package completeness`
- 执行 phase：
  `Phase 1`

### T3. Subtask Structured Review Boundary Gate

- 问题族：
  subtask review 在 provider -> executor 边界被 flatten，导致 deferred capability / foreign capability 越权实现无法被 deterministic gate 消费。
- canonical owners：
  `LlmProvider`
  `StructuredReviewResult`
  `SubtaskReviewPromptAssembler`
  `SubtaskVerificationSupport`
  `OllamaStructuredReviewExecutor`
- 协议边界：
  `StructuredReviewResult`
  新增的 subtask-only typed payload 或等价 typed result
- 设计约束：
  不能把 implementation-subtask 专属 boundary 语义散入全局 `ReviewResult`
  也不能把它硬塞进通用 `ReviewSemantics`
- 必须删除：
  在 `SubtaskVerificationSupport` 直接 `.result()` flatten 后再靠 prose 判断边界
- 必须锁死的回归：
  `R1. skeleton capability boundary`
  `R6. typed payload round-trip`
- 执行 phase：
  `Phase 3`

### T4. Canonical Runtime Repair Package Round-Trip

- 问题族：
  当前失败子任务的 canonical repair package 没有真正进入 `implementation_state` 单一真相源，只停在 support / 派生展示层。
- canonical owners：
  `ImplementationStageStatus`
  `ImplementationArtifactPersister`
  `ImplementationStateSnapshotSerializer`
  `ImplementationStateSnapshot`
  `ImplementationStateCodec`
  `ImplementationStateArtifactSupport`
  `ImplementationProgressSupport`
  `ImplementationContinuationSupport`
  `ImplementationStageStatusPayload`
  `SubtaskRuntimeWiringGuard`
  `RuntimeWiringRetryChangeFactory`
  `SubtaskRevisionDirective`
  `ImplementationResumePolicy`
  `ImplementationStageStatusArtifactRenderer`
- 协议边界：
  `ImplementationStageStatus`
  `ImplementationStateSnapshot`
  `implementation_state.json`
  `ImplementationStageStatusPayload`
- 设计约束：
  子任务级 `PATCH_RUNTIME_WIRING` 只能复用 `RuntimeWiringRetryChangeFactory`
  不允许在 `SubtaskRuntimeWiringGuard` 自己再拼第二套 repair package
- 必须删除：
  只在 `implementation_stage_status.md` 或 support 层临时拼 repair package 的旧路径
- 必须锁死的回归：
  `R3. CONTINUE_SUBTASKS repair package scope clamp`
  `R6. typed payload round-trip`
- 执行 phase：
  `Phase 4`

### T5. Repair-Mode Patch-First Enforcement

- 问题族：
  repair/resume mode 虽然在权限层收紧，但工具层仍可能按旧 `deliveryMode == REWORK` 或旧 whole-rewrite 路径放行。
- canonical owners：
  `ImplementationToolPermissionPolicy`
  `ImplementationToolPermissionContext`
  `ImplementationToolLoopExecutor`
  `ImplementationToolContext`
  `ToolExecutionContext`
  `FileEditTool`
  `FileWriteTool`
  `BashTool`
  `ShellCommandAnalyzer`
- 协议边界：
  tool permission context
  tool execution context
  shell deny payload
  `pathIntents` diagnostics
- 工具级不变量：
  full Read before Edit/Write
  stale Read invalidates overwrite
  shell 写已有文件也必须走同一 fresh-read 防线
- 必须删除：
  只在 prompt 里说 patch-first
  只在 permission 层收紧，但工具层继续按旧分支放行 whole rewrite
- 必须锁死的回归：
  `R4. shell deny pathIntents diagnostics`
  `R5. tool-level full Read / stale Read invariants`
- 执行 phase：
  `Phase 5`

### T6. Run-State And Repair Reroute Consistency

- 问题族：
  repair route 已成立，但 run/stage 状态、transition artifact、event、revision note、stage re-entry 仍可能出现不一致。
- canonical owners：
  `StageProgressCoordinator`
  `FlowController`
  `FlowDecisionExecutor`
  `StageTransitionSupport`
  `StageRevisionSupport`
  `StageRevisionRepairSupport`
  `StageProgressArtifactSupport`
  `StageEntryExecutor`
  `StageStatusSupport`
- 协议边界：
  `RunRecord`
  `StageExecution`
  transition decision artifact
  event log
  stage directive / re-entry artifact
- 设计约束：
  repair reroute 的 review history、event、revision note、repair brief、stage re-entry 必须来自同一条链
- 必须删除：
  `REJECTED + ROUTE_TO_REPAIR` 最终却落成 `APPROVED / COMPLETED`
- 必须锁死的回归：
  `R7. run-state consistency`
- 执行 phase：
  `Phase 6`

## Protocol Boundaries

本轮必须显式承认的真实协议边界只有两组：

### Review Typed Boundary

- `LlmProvider`
- `StructuredReviewResult`
- subtask-only typed payload 或等价 typed result

要求：

- subtask boundary finding 必须穿过 provider 类型边界
- 不允许停留在 executor 层 ad hoc JSON
- 不允许 flatten 回 prose 再由下游猜语义

### Continuation Typed Boundary

- `ImplementationStageStatus`
- `ImplementationStateSnapshot`
- `implementation_state.json`
- `ImplementationStageStatusPayload`

要求：

- canonical repair package 必须穿过写侧和读侧真实协议链
- 展示物只能派生，不得反向充当事实源

## Execution Mapping

- `Phase 1`:
  `T1` + `T2`
- `Phase 2`:
  `T1`
- `Phase 3`:
  `T3`
- `Phase 4`:
  `T4`
- `Phase 5`:
  `T5`
- `Phase 6`:
  `T6`
- `Phase 7`:
  `self-test + code review`
- `Phase 8`:
  黄金路径集成测试

## Review Gate

执行文档审阅时，只需要检查 4 件事：

1. 每个问题族是否都有单一 canonical owner
2. 每个 typed payload 是否都落到真实协议边界
3. 每个问题族是否都绑定了具体回归矩阵项
4. 执行 phase 是否和 owner / 回归项一一对应
