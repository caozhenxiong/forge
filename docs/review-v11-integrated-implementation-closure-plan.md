# Review V11: Integrated Implementation Closure Plan

## Summary

- 这轮不再按单点修补推进，也不继续盲跑集成测试。
- `v178` 的失败已经把当前第一阻塞重新钉死在 `IMPLEMENTATION outline` 规划链，而不是 coder、runtime wiring repair 或 TEST 阶段。
- reviewer 提出的 5 条后继怀疑链路是成立的，但它们不是同一优先级：
  - 当前主阻塞：`planning / shared-file boundary`
  - 当前代码里已存在的下一层显式缺口：`continuation semantics 被流程层压扁`
  - planning 打通后最可能暴露的下一层：`canonical repair package / resume restore / directive merge`
  - 需要同轮纳入审计但不是本次首因：`authority corpus loop`、`validation false negative`
- 本文档把这些问题整合成一条连续收口方案，作为后续实现的唯一入口。

## Scope Boundary

### 本轮明确不重做的已收口项

- 不重开 `tool-loop declared-changes-satisfied` 的 completion 收口。
- 不重开 `completed-plan PATCH owner resolver` 的既有修复。
- 不重开 `runtime contract resolver` 的目录扫描 / orphan root 旧问题。
- 不重开 `Bash deny pathIntents diagnostics` 已落地链路。
- 不顺手扩新的产品能力或重做全套 runtime ownership 架构。

### 本轮真正要收的 6 条问题链

1. `IMPLEMENTATION outline` 没有稳定产出 shared-file downstream capability boundary。
2. implementation continuation 虽然协议层已经有三分类，但流程层和产物层仍会把非阻断 continuation 压成 generic continue。
3. canonical repair package 没有沿 `review / retry feedback / implementation_state / continuation / resume` 保持单一真相源。
4. `resume restore` 仍可能把正确 continuation 还原成旧 scope、旧 effective changes 或旧 delivery mode。
5. upstream authority corpus 仍可能把错误 runtime contract 循环强化回文档阶段。
6. validation 链仍可能在 implementation 真修对后给出 false negative。

## Current Evidence

### v178 直接证据：当前首因已经前移到 planning

- 项目：`/home/linus/workspace/tetris_test_itest_v178`
- run id：`eda2515f-33b3-4ec2-bac7-25224d05ba84`
- 关键产物：
  - `.devflow/runs/eda2515f-33b3-4ec2-bac7-25224d05ba84/events.log`
  - `.devflow/runs/eda2515f-33b3-4ec2-bac7-25224d05ba84/implementation_planning_outline-outline.raw.attempt-1.txt`
  - `.devflow/runs/eda2515f-33b3-4ec2-bac7-25224d05ba84/implementation_planning_outline-outline.raw.attempt-2.txt`
  - `.devflow/runs/eda2515f-33b3-4ec2-bac7-25224d05ba84/implementation_planning_outline-outline.raw.attempt-3.txt`
  - `.devflow/runs/eda2515f-33b3-4ec2-bac7-25224d05ba84/run.json`

直接现象：

- `ANALYSIS / PRD / DESIGN` 全部一次通过。
- `IMPLEMENTATION` 没有进入 coder / review / test 主链，直接在 outline 内部 3 次重试耗尽后失败。
- 3 次驳回理由逐步收窄到同一个问题族：
  - deferred capability 没有唯一后续 owner
  - shared `src/app.js` 没有显式 deferred boundary
  - shared `index.html` / `src/style.css` / `src/app.js` 上，下游 owner capability 没有完整写进 `deferredCapabilities`

结论：

- 当前第一阻塞不是“代码生成质量”或“测试误拒绝”，而是 planning/gate 协议比 outline prompt 更严格，模型没有稳定学会这套 shared-file boundary 语义。

### reviewer 5 条后继怀疑链路的当前判断

#### R1. Resume Restore Drift

- `ImplementationResumePolicy` 自身不恢复细节，依赖 `ImplementationSnapshotRestorer` 还原 subtask、report、effectiveChanges、contract gate。
- 这条链在本次 `v178` 里还没有真正进入主路径，因为 outline 阶段就失败了。

结论：

- 不是本次首因，但 planning 一旦打通，这会立刻变成高优先级真实风险。

#### R2. Flow Layer Still Flattens Continuation

- 协议层已经引入：
  - `MID_PLAN_CONTINUE`
  - `PATCH_CONTINUE`
  - `BLOCKED_EXHAUSTED_SUBTASK`
- 但流程侧当前仍主要表现为：
  - `ImplementationProgressState(stageReady, blocked, ...)`
  - `StageProgressCoordinator` 对所有非阻断 continuation 统一写 `TransitionReason.STAGE_CONTINUE`

结论：

- 这是当前代码里的显式缺口，不是猜测。
- 如果不一起收，后续 concrete patch 与普通 mid-plan continue 还会在流程层被重新混掉。

#### R3. Execution Directive Merge May Still Eat Scope

- `ExecutionDirectiveProtocol.parseMerged()` 仍是线性 merge。
- `ExecutionDirectivePayload.merge()` 的文本字段是覆盖式，`overrideChanges` 也是整组替换式。

结论：

- 这条暂时不是 `v178` 的首因，但如果 canonical repair package 在 `Scope 3/4` 之后仍然丢失，这会是第一优先级下挖点。
- 本轮不再把它列为“下一轮再说”的观察项，而是纳入同轮审计与最小收口。

#### R4. Authority Corpus May Reinforce Wrong Runtime Contract

- `ConstraintAuthoritySupport` 仍会把 `executionContract.runtimeOwnershipMode()` 放入 authority corpus。
- `DocumentStageIntake` 与 `StageArtifactInputResolver` 仍使用这条 authority corpus。

结论：

- 当前 `v178` 的 `design.md` 已经是 `runtimeOwnershipMode=not-applicable`，不是旧的 `entry-owned` 回流，因此它不是本次首因。
- 但如果这轮继续改 planning / contract，又不把 authority loop 一并审计，错误 runtime contract 仍可能在后续文档阶段重新长回来。

#### R5. Validation-Side False Negative

- `ValidationExecutor` 仍先跑 resource/runtime wiring/syntax/smoke。
- `WebRuntimeWiringValidationSupport` 与 `WebResourceValidationSupport` 仍有机会在行为正确后因旧验证语义而提前打回。

结论：

- 本次 `v178` 没进入 TEST，当前不是首因。
- 但这轮如果只修 implementation，不同步做 validation-side 审计，下一次集成可能会从“planning failed”切换成“behavior ok but TEST false negative”。

## Problem -> Solution Mapping

### P0. Planning Boundary Contract Is Not Model-Reachable

#### Problem

- `ImplementationPlanCoverageAnalyzer.validateSharedFileDeferredBoundary()` 已经把 shared-file incremental subtask 的边界规则写成 hard gate。
- 但 `ImplementationOutlinePromptBuilder` 仍只提供泛化约束，没有把这条规则显式协议化给 outline 模型。
- 结果是模型反复被 gate 打回，但不知道怎样构造“共享文件 -> 下游 owner capability -> deferredCapabilities”的合法结构。

#### Solution

- 把 shared-file boundary 协议前移成 outline prompt 的显式规则，而不是只放在 gate 里做事后裁决。
- prompt、retry feedback、outline gate、detail gate、final plan gate、task package、review prompt 必须围绕同一份 boundary contract 表达：
  - 当前子任务拥有的能力
  - 与后续子任务共享的文件
  - 每个 future owner 的 ownedCapabilities
  - 当前子任务必须显式 defer 的 capability 集合
- final plan gate 若命中 shared-file / accepted-package completeness 问题，反馈回流必须继续沿同一份 boundary contract 路由到正确 planning unit：
  - outline
  - 或具体 subtask detail
- 对 shared-file boundary，不允许再接受：
  - `deferredCapabilities=[]`
  - 只 defer 一部分下游能力
  - 同一共享文件上的 future owner boundary 被 prose 摘要代替

### P1. Continuation Semantics Still Flattened By Flow Layer

#### Problem

- `ImplementationContinuationMode` 已三分类，但 `ImplementationProgressSupport` / `ImplementationProgressState` / `StageProgressCoordinator` 仍主要按 blocked/not-blocked 处理。
- 这会把：
  - `PATCH_CONTINUE`
  - `MID_PLAN_CONTINUE`
  重新混成 generic `STAGE_CONTINUE`。

#### Solution

- continuation 语义必须在以下层级保持一致：
  - stage gate
  - stage status internal record
  - implementation_state payload / codec / renderer
  - progress support / revision facts
  - flow controller / transition reason
  - coordinator / stage re-entry
- `PATCH_CONTINUE` 与 `MID_PLAN_CONTINUE` 必须在流程层拥有不同的 transition reason 和 continuation context，不再共享 generic `STAGE_CONTINUE`。

### P2. Canonical Repair Package Still Has Multiple Owners

#### Problem

- concrete patch package 目前仍可能在：
  - review normalization
  - retry feedback merge
  - execution directive payload
  - implementation_state
  - continuation parsing
  之间被折叠、拼接或 prose 化。

#### Solution

- 确立单一 canonical repair package owner，并让它稳定穿过：
  - review/test producer
  - retry feedback
  - next attempt effective changes
  - execution directive payload
  - implementation_state serialize / parse
  - stage continuation context
  - reroute-to-repair note / repair brief producer
  - resume policy
- `implementation_shared_context.md` 明确降级为 repair summary，仅供阅读，不承担 machine truth。

### P3. Resume Restore Can Still Rehydrate Wrong Scope

#### Problem

- 即使 `implementation_state.json` 写对，`ImplementationSnapshotRestorer` 仍可能把 continuation 还原成旧 effective change-set、旧 delivery mode 或旧 report-derived scope。

#### Solution

- `ImplementationResumePolicy` 与 `ImplementationSnapshotRestorer` 必须一起收口：
  - restore 只能恢复 canonical persisted state，不得再从旧 report 语义、临时 narrowed scope 或隐式 fallback 重建 continuation
  - resumed execution state 的 patch target / override changes / delivery mode 必须来自 persisted canonical package，而不是从 report 尝试信息里二次推导

### P4. Upstream Authority Loop Must Be Audited In Same Round

#### Problem

- 如果错误 runtime contract 已进入 `ExecutionContract`，authority corpus 仍可能把它喂回 `PRD/DESIGN` prompt 输入，形成循环强化。

#### Solution

- 在同轮实现里同步收紧 authority corpus 来源：
  - 只允许 binding contract metadata 进入 hard authority
  - 不允许 runtime ownership 从 soft prose、部署偏好或设计建议反向升级成 binding fact
- 文档 intake / stage artifact input resolver 必须与 contract extractor 的新来源口径保持一致。

### P5. Validation Must Not Become The Next False-Negative Blocker

#### Problem

- planning 和 continuation 收住之后，golden path 会继续进入 TEST。
- 如果 validation 侧还保留旧 wiring / resource 语义，implementation 可能已经正确，却被 TEST 误拒绝。

#### Solution

- validation 侧本轮先做一致性收口，不做新能力扩展：
  - resource check 只判断本地引用是否存在
  - runtime wiring check 只判断真实入口接线与 ownership 契约是否一致
  - 不再从旧的 inline-host / orphan-root 过时语义反向阻断合法 split package
- `TestExecutor` 与 `ExperienceFailureDispositionResolver` 对 implementation re-verification / patch target / override scope 的 producer 语义必须与新的 canonical repair package 保持一致。

## Final State

- outline 规划器能够稳定产出合法 shared-file boundary contract，不再靠 gate 连续打回逼模型试错。
- shared-file boundary contract 成为 planning、task package、coder、reviewer、boundary gate 的唯一边界真相源。
- implementation continuation 在协议层、流程层、产物层、resume 层都保留三分类，不再被 generic continue 覆盖。
- canonical repair package 在 `review/test -> retry feedback -> implementation_state -> continuation -> resume` 全链只有一个 machine owner。
- `ImplementationSnapshotRestorer` 不再从旧 report 语义或 narrowed retry scope 反向重建 continuation。
- authority corpus 不再把 soft design choice 或错误 runtime ownership 循环强化回文档阶段。
- validation 在 golden path 上只做与当前契约一致的检查，不再对已合法的 split runtime / resource layout 产生误拒绝。

## Removal Plan

本轮必须删除或封死：

- outline prompt 中对 shared-file boundary 的泛化描述和靠模型自行脑补的旧路径。
- 流程层把 implementation continuation 压成 blocked/not-blocked + `STAGE_CONTINUE` 的旧语义。
- concrete patch package 在 retry feedback、directive payload、implementation_state、resume 之间被 prose 化或清空回 `NONE` 的旧双轨。
- `resume restore` 从旧 report scope、旧 delivery mode 或旧 narrowed effectiveChanges 偷偷重建 continuation 的路径。
- authority corpus 中从 soft prose 反向强化 binding runtime contract 的旧入口。
- validation 侧对旧 wiring/resource 语义的隐式依赖。

## Joint-Change Scope

### Scope 1. Planning Boundary Contract Closure

- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationOutlinePromptBuilder.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanningPromptAssembler.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanner.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationOutlineGate.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanGate.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanCoverageAnalyzer.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanNormalizationSupport.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanGateInputBuilder.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationSubtaskDetailPromptBuilder.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationSubtaskDetailGate.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanningFeedbackRouter.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanningRepairSupport.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanningPayloadParser.java`

目标：

- 让 shared-file downstream boundary 成为 planning 模型可直接学习和输出的协议，而不是事后 gate 语言。
- 让 final gate / accepted-package completeness 命中后的 reroute 仍沿同一条 planning owner 链回到 outline 或具体 subtask detail，而不是生成第二套 detail 修复语义。

### Scope 2. Boundary Contract Propagation

- `src/main/java/devflow/agent/executor/subtask/TaskPackage.java`
- `src/main/java/devflow/agent/executor/implementation/render/TaskPackageAssembler.java`
- `src/main/java/devflow/agent/executor/editing/TaskPackageMarkdownRenderer.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskReviewPromptAssembler.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskBoundaryGate.java`

目标：

- 让 accepted outline 的 boundary contract 稳定进入 task package、coder、reviewer 与 deterministic rejection。

### Scope 3. Continuation Semantics Closure

- `src/main/java/devflow/agent/executor/gate/ImplementationStageGate.java`
- `src/main/java/devflow/agent/executor/gate/ImplementationStageStatus.java`
- `src/main/java/devflow/agent/protocol/ImplementationContinuationMode.java`
- `src/main/java/devflow/agent/protocol/ImplementationStageStatusPayload.java`
- `src/main/java/devflow/agent/executor/implementation/render/ImplementationStageStatusArtifactRenderer.java`
- `src/main/java/devflow/agent/executor/implementation/state/ImplementationStateCodec.java`
- `src/main/java/devflow/agent/orchestrator/StageContinuationContext.java`
- `src/main/java/devflow/agent/orchestrator/ImplementationProgressSupport.java`
- `src/main/java/devflow/agent/orchestrator/ImplementationProgressState.java`
- `src/main/java/devflow/agent/orchestrator/ImplementationRevisionFacts.java`
- `src/main/java/devflow/agent/orchestrator/StageProgressCoordinator.java`
- `src/main/java/devflow/agent/orchestrator/FlowController.java`
- `src/main/java/devflow/agent/orchestrator/FlowDecisionExecutor.java`
- `src/main/java/devflow/agent/orchestrator/StageTransitionSupport.java`
- `src/main/java/devflow/agent/orchestrator/StageEntryExecutor.java`
- `src/main/java/devflow/agent/loop/TransitionReason.java`

目标：

- 让 `MID_PLAN_CONTINUE / PATCH_CONTINUE / BLOCKED_EXHAUSTED_SUBTASK` 在流程层和产物层都成为单一语义。

### Scope 4. Canonical Repair Package / Resume Restore Closure

- `src/main/java/devflow/agent/executor/subtask/SubtaskRepairDirectiveResolver.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskRecoverySupport.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskVerificationSupport.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskVerificationOutcome.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskRevisionDirective.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskRetryFeedbackRenderer.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskExecutionState.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskAttemptStepExecutor.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskExecutor.java`
- `src/main/java/devflow/agent/executor/testing/TestExecutor.java`
- `src/main/java/devflow/agent/protocol/ExecutionDirectiveProtocol.java`
- `src/main/java/devflow/agent/protocol/ExecutionDirectivePayload.java`
- `src/main/java/devflow/agent/protocol/ExecutionDirectiveFeedbackSupport.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationDirectiveResolver.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationContextResolver.java`
- `src/main/java/devflow/agent/executor/implementation/ImplementationPlanRunner.java`
- `src/main/java/devflow/agent/orchestrator/StageContinuationNoteBuilder.java`
- `src/main/java/devflow/agent/orchestrator/StageRevisionRepairSupport.java`
- `src/main/java/devflow/agent/orchestrator/StageRevisionNoteBuilder.java`
- `src/main/java/devflow/agent/executor/implementation/state/ImplementationStateSnapshot.java`
- `src/main/java/devflow/agent/executor/implementation/state/ImplementationStateSnapshotSerializer.java`
- `src/main/java/devflow/agent/executor/implementation/state/ImplementationStateArtifactSupport.java`
- `src/main/java/devflow/agent/executor/implementation/state/ImplementationSnapshotRestorer.java`
- `src/main/java/devflow/agent/orchestrator/ImplementationContinuationSupport.java`
- `src/main/java/devflow/agent/executor/implementation/ImplementationResumePolicy.java`
- `src/main/java/devflow/agent/repair/RepairAgent.java`

目标：

- 让 canonical repair package 从 producer 到 persisted state 再到 resume 只有一个结构化真相源。
- 让 subtask 内部 `review.overrideChanges -> revisionDirective.retryChanges -> executionState.effectiveChanges` 只保留一条 direct carrier 链，不再同时保留两份 machine owner。
- 同轮收紧 `ExecutionDirectiveProtocol` 的 merge owner，避免后续再用“协议层可能吞 scope”解释同类问题。
- 封掉 reroute-to-repair note / repair brief 这条并行 directive producer 第二轨，避免 `StageContinuationNoteBuilder` 与 repair route 各自生成不同 machine package。

### Scope 5. Upstream Authority Audit

- `src/main/java/devflow/agent/context/ConstraintAuthoritySupport.java`
- `src/main/java/devflow/agent/context/ContractExtractor.java`
- `src/main/java/devflow/agent/context/ExecutionContract.java`
- `src/main/java/devflow/agent/review/ContractMetadataConsistencyGuard.java`
- `src/main/java/devflow/agent/artifact/DocumentStageIntake.java`
- `src/main/java/devflow/agent/artifact/StageArtifactInputResolver.java`

目标：

- 让 binding runtime contract 只来自受控结构化来源，不再被 authority corpus 循环放大。

### Scope 6. Validation-Side Consistency Audit

- `src/main/java/devflow/agent/executor/testing/TestExecutor.java`
- `src/main/java/devflow/agent/executor/testing/ExperienceFailureDispositionResolver.java`
- `src/main/java/devflow/agent/validation/ValidationExecutor.java`
- `src/main/java/devflow/agent/validation/WebRuntimeWiringValidationSupport.java`
- `src/main/java/devflow/agent/validation/WebResourceValidationSupport.java`

目标：

- 防止 implementation 真修对后，被旧 validation 语义误拒绝。
- 防止 TEST 侧即使拿到正确 evidence，仍被旧 disposition 映射错误地下推回 implementation patch。

## Execution Order

1. `Scope 1 + Scope 2`
   原因：这是 `v178` 的当前首因，不先打通，后续链路没有进入机会。
2. `Scope 3`
   原因：当前代码里已经存在 continuation flattening，必须与 planning 收口同步完成。
3. `Scope 4`
   原因：planning 与 flow 打通后，下一层最可能暴露的是 patch package / resume drift。
4. `Scope 5 + Scope 6`
   原因：这两条不是 `v178` 首因，但必须同轮审计，否则下一次集成只会把失败位置向后移动。
5. 全部完成后，再跑黄金路径集成测试。

## Regression Matrix

1. shared-file incremental outline 没写 downstream deferred boundary 时，outline planning 必须 deterministic fail。
2. shared-file incremental outline 正确声明 deferred boundary 时，outline planning 必须一次通过。
3. accepted boundary contract 必须稳定出现在 task package、review prompt 与 deterministic boundary gate 消费链。
4. final plan gate / detail completeness 若命中 shared-file boundary 问题，反馈必须稳定路由回正确 planning unit，而不是长出第二套 detail 修复轨。
5. `MID_PLAN_CONTINUE` 与 `PATCH_CONTINUE` 在 progress / transition / artifact / resume 层不能再混成同一个 generic continue。
6. canonical repair package 必须经过：
   - review/test producer
   - subtask verification outcome / revision directive
   - retry feedback
   - execution directive
   - implementation_state serialize / parse
   - reroute-to-repair note / repair brief
   - continuation context
   - resume restore
   后保持同一份结构化 patch facts。
7. `ExecutionDirectiveProtocol` 多段 block merge 不能吞掉 canonical override scope。
8. authority corpus 不能把 soft design choice 升级成 binding runtime contract。
9. validation 与 TEST disposition mapping 对合法 split runtime / local asset layout 不能误拒绝或误路由。
10. 黄金路径集成测试不再出现：
   - outline 三次都死在 shared-file boundary
   - continuation 在流程层被压平
   - patch package 在 attempt 间丢失
   - implementation 真修对后被 TEST 误拒绝

## Non-Goals

- 不顺手放松 repair-mode patch-first 工具约束。
- 不顺手扩新的产品能力。
- 不顺手重做 completed-plan owner resolver 的语义。
- 不顺手再开一套并行协议或兼容层。

## Closure Decision

- 这轮可以一次性收口，但前提是 6 个 scope 按执行顺序成组落地。
- 不能只修其中一层：
  - 只修 planning，不修 flow/continuation，patch 仍会在下一层被压平。
  - 只修 continuation，不修 planning，集成仍然会死在 outline。
  - 只修 repair package，不修 resume restore，persisted state 仍可能被还原坏掉。
  - 不审 authority/validation，只会把失败位置从 implementation 前移到文档阶段或 TEST 阶段。
- 当前阶段停在方案，不进入实现。

## Risks / Blockers

- 这轮最大风险不是“问题面不清”，而是如果拆成多轮局部修改，会再次形成半成品。
- `Scope 4` 如果不把 `ExecutionDirectiveProtocol` 一起纳入，就仍可能保留协议层吞 scope 的第二 owner。
- `Scope 5` 和 `Scope 6` 虽然不是首因，但若被排除到下一轮，会显著降低本轮集成通过概率。

## Implementation

- 本文档通过 review 前，不改代码。
- 通过后按 `Scope 1 -> Scope 2 -> Scope 3 -> Scope 4 -> Scope 5 -> Scope 6` 成组实现。
- 每个大 scope 完成后必须做：
  - targeted self-test
  - code review
  - 文档同步
- 全部 scope 完成后，才重新跑黄金路径集成测试。

## Completion Gate Result

- 当前结果：`Result B`
- 状态：`只输出完整收口方案，不提交半成品实现`
- 下一步：`review 本文档；review 通过后再进入实现`
