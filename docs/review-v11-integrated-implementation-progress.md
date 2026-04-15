# Review V11 Integrated Implementation Progress

## Purpose

这份文档是 [review-v11-integrated-implementation-closure-plan.md](/home/linus/workspace/forge/docs/review-v11-integrated-implementation-closure-plan.md) 的唯一执行 tracker。

它只负责：

- 把 `v11` 的 6 个 scope 拆成可打勾待办
- 记录当前执行状态与 blocker
- 为每个 scope 记录固定证据：
  `commit / self-test / code review / docs`
- 确保后续实现严格按 `v11` 顺序推进，而不是边跑边偏

## Rules

- 只跟踪 `review-v11` 这一轮 integrated closure，不混写其他整改
- 每个 scope 开始前先更新本文档
- 每完成一项，立即打勾
- 如果 blocker 或执行顺序变化，先更新本文档，再继续改代码
- 不允许把“后续再清理”写进 checklist
- 只有本轮同类问题整体收口，才允许标记 scope 完成

## Final State

完成态必须同时满足：

- `S1` planning boundary contract 在 outline/detail/final-gate/reroute 单轨收口
- `S2` accepted boundary contract 稳定进入 task package、coder、reviewer、deterministic boundary gate
- `S3` implementation continuation 在协议层、流程层、产物层保留三分类，不再被 generic continue 压平
- `S4` canonical repair package 在 subtask / retry / directive / implementation_state / reroute / resume 全链只有一个 machine truth source
- `S5` authority corpus 不再把 soft prose 或错误 runtime contract 循环强化回文档阶段
- `S6` validation 与 TEST disposition mapping 不再对合法实现误拒绝或误路由
- `R1 ~ R10` 全部有对应回归
- 完成 `self-test + code review + docs`
- 然后再跑黄金路径集成测试

## Removal Plan

本轮必须删除或封死：

- outline prompt 中对 shared-file boundary 的泛化表述
- planning final gate 命中后生成第二套 detail 修复语义的路径
- implementation continuation 在流程层被折叠成 blocked/not-blocked + `STAGE_CONTINUE`
- repair package 在 `review / retry / directive / implementation_state / reroute / resume` 之间的多 owner 路径
- `resume restore` 从旧 report / narrowed scope / 旧 delivery mode 反推 continuation 的路径
- authority corpus 从 soft prose 反向强化 binding runtime contract 的路径
- validation / disposition mapping 对合法 split runtime 或资源布局的误拒绝路径

## Scope Checklist

### Scope 1. Planning Boundary Contract Closure

- [x] 把 shared-file downstream boundary 规则显式写进 `ImplementationOutlinePromptBuilder`
- [x] `ImplementationPlanningPromptAssembler` / `ImplementationPlanner` 只围绕同一份 planning boundary contract 组织重试
- [x] `ImplementationOutlineGate` / `ImplementationPlanGate` / `ImplementationPlanCoverageAnalyzer` 对 shared-file boundary 使用同一套结构化口径
- [x] `ImplementationSubtaskDetailPromptBuilder` / `ImplementationSubtaskDetailGate` 与 outline 使用同一份 boundary contract
- [x] `ImplementationPlanningFeedbackRouter` 把 final gate / accepted-package completeness 问题稳定路由回 outline 或具体 subtask detail
- [x] `ImplementationPlanningRepairSupport` / `ImplementationPlanningPayloadParser` 不再生成第二套 detail 修复语义
- [x] Scope 1 `self-test`
- [x] Scope 1 `code review`
- [x] Scope 1 `docs`

### Scope 2. Boundary Contract Propagation

- [x] `TaskPackage` 与 accepted outline 的 boundary contract 对齐
- [x] `TaskPackageAssembler` / `TaskPackageMarkdownRenderer` 只消费 canonical boundary contract
- [x] `SubtaskReviewPromptAssembler` 展示当前/后续能力边界与禁止提前实现约束
- [x] `SubtaskBoundaryGate` 对 boundary contract 形成 deterministic rejection
- [x] Scope 2 `self-test`
- [x] Scope 2 `code review`
- [x] Scope 2 `docs`

### Scope 3. Continuation Semantics Closure

- [x] `ImplementationStageGate` / `ImplementationStageStatus` 不再把 exhausted subtask 与 patch continuation 混成 generic continue
- [x] `ImplementationContinuationMode` / `ImplementationStageStatusPayload` / `ImplementationStateCodec` 对三分类保持单一协议
- [x] `ImplementationStageStatusArtifactRenderer` / `StageContinuationContext` 对三分类保持同一 artifact/context bridge，不再在桥接层退回旧二元语义
- [x] `ImplementationProgressSupport` / `ImplementationProgressState` / `ImplementationRevisionFacts` 不再只按 blocked/not-blocked 处理
- [x] `StageProgressCoordinator` / `FlowController` / `FlowDecisionExecutor` / `StageTransitionSupport` / `StageEntryExecutor` 对三分类保持单一路由
- [x] `TransitionReason` 不再用单一 `STAGE_CONTINUE` 覆盖所有 implementation continuation
- [x] Scope 3 `self-test`
- [x] Scope 3 `code review`
- [x] Scope 3 `docs`

### Scope 4. Canonical Repair Package / Resume Restore Closure

- [ ] `SubtaskVerificationSupport` / `SubtaskVerificationOutcome` / `SubtaskRevisionDirective` / `SubtaskExecutionState` 只保留 `review.overrideChanges -> revisionDirective.retryChanges -> executionState.effectiveChanges` 一条 direct carrier 链
- [ ] `SubtaskRepairDirectiveResolver` / `SubtaskRecoverySupport` / `SubtaskRetryFeedbackRenderer` 不再把 repair package prose 化或清空回 `NONE`
- [ ] `TestExecutor` 与 subtask producer 共享 canonical repair package 口径
- [ ] `ExecutionDirectiveProtocol` / `ExecutionDirectivePayload` / `ExecutionDirectiveFeedbackSupport` 不再在 merge 中吞 scope
- [ ] `ImplementationDirectiveResolver` / `ImplementationContextResolver` / `ImplementationPlanRunner` 对 repair package 只消费 canonical owner
- [ ] `StageContinuationNoteBuilder` / `StageRevisionRepairSupport` / `StageRevisionNoteBuilder` / `RepairAgent` 不再形成 reroute-to-repair 的并行 directive producer 第二轨
- [ ] `ImplementationStateSnapshot` / `ImplementationStateSnapshotSerializer` / `ImplementationStateArtifactSupport` 让 canonical repair package 进入 `implementation_state` 单一真相源
- [ ] `SubtaskAttemptStepExecutor` / `SubtaskExecutor` / `ImplementationContinuationSupport` 作为 repair package 的执行消费链与 continuation bridge，不再游离在 canonical owner 之外
- [ ] `ImplementationSnapshotRestorer` / `ImplementationResumePolicy` 不再从旧 report / narrowed scope / 旧 delivery mode 反推 continuation
- [ ] Scope 4 `self-test`
- [ ] Scope 4 `code review`
- [ ] Scope 4 `docs`

### Scope 5. Upstream Authority Audit

- [ ] `ConstraintAuthoritySupport` 只拼装受控 binding authority，不再放大 soft prose
- [ ] `ContractExtractor` / `ExecutionContract` / `ContractMetadataConsistencyGuard` 对 binding runtime contract 使用单一来源
- [ ] `DocumentStageIntake` / `StageArtifactInputResolver` 不再把错误 runtime contract 循环喂回文档阶段
- [ ] Scope 5 `self-test`
- [ ] Scope 5 `code review`
- [ ] Scope 5 `docs`

### Scope 6. Validation-Side Consistency Audit

- [ ] `ValidationExecutor` 只按当前契约执行 validation，不再依赖旧 wiring/resource 语义
- [ ] `WebRuntimeWiringValidationSupport` 对合法 split runtime 不误拒绝
- [ ] `WebResourceValidationSupport` 只判断真实缺失资源，不额外引入旧布局假设
- [ ] `ExperienceFailureDispositionResolver` 不再把正确 evidence 误映射成 implementation patch 请求
- [ ] `TestExecutor` 的 implementation re-verification 与 disposition mapping 保持同一语义
- [ ] Scope 6 `self-test`
- [ ] Scope 6 `code review`
- [ ] Scope 6 `docs`

### Scope 7. Regression Matrix

- [ ] `R1` shared-file incremental outline 无 deferred boundary 时 deterministic fail
- [ ] `R2` shared-file incremental outline 正确声明 deferred boundary 时一次通过
- [ ] `R3` accepted boundary contract 稳定进入 task package / review prompt / boundary gate
- [ ] `R4` final gate / detail completeness 命中 shared-file boundary 时稳定路由回正确 planning unit
- [ ] `R5` `MID_PLAN_CONTINUE` 与 `PATCH_CONTINUE` 不再混成 generic continue
- [ ] `R6` canonical repair package 经过 subtask verification outcome / revision directive / retry feedback / directive / `implementation_state` / reroute / resume 后保持结构化
- [ ] `R7` `ExecutionDirectiveProtocol` merge 不吞 canonical override scope
- [ ] `R8` authority corpus 不再把 soft design choice 升级成 binding runtime contract
- [ ] `R9` validation 与 TEST disposition mapping 对合法 split runtime / local asset layout 不误拒绝或误路由
- [ ] `R10` 黄金路径集成测试不再复现 `v178` 及其后续 continuation / repair / TEST 误拒绝问题
- [ ] Scope 7 `self-test`
- [ ] Scope 7 `code review`
- [ ] Scope 7 `docs`

### Scope 8. Golden Path Integration

- [ ] 重跑黄金路径集成测试
- [ ] 如果失败，先把 blocker 与证据更新到本文档
- [ ] 如果通过，补齐最终 commit / self-test / code review / docs 证据

## Current Status

- 当前阶段：`SCOPE_3_COMPLETED_PENDING_SCOPE_4`
- 当前 blocker：`无`
- 当前约束：`禁止兼容层、禁止双轨并存、禁止“后续再清理”`

## Evidence Log

### Scope 1

- commit：`e2ca4f8 Close planning boundary contract scope 1`
- self-test：`mvn -q -Dtest=ImplementationPlannerTests,ImplementationPlanGateTests,ImplementationPlanCoverageAnalyzerTests,ImplementationPlanningFeedbackRouterTests,ImplementationPlanningPayloadParserTests,ImplementationPlanNormalizationSupportTests,ImplementationPlanningPromptBuilderTests test`
- code review：`本地静态自审通过；未发现 Scope 1 新双轨或 fallback`
- docs：`tracker 已回填 Scope 1 状态与证据`

### Scope 2

- commit：`ed6c76e Propagate accepted boundary contract through scope 2`
- self-test：`mvn -q -Dtest=TaskPackageTests,TaskPackageAssemblerTests,TaskPackageMarkdownRendererTests,SubtaskReviewPromptAssemblerTests,SubtaskBoundaryGateTests test`
- code review：`本地静态自审通过；未发现 accepted boundary contract 第二轨`
- docs：`tracker 已回填 Scope 2 状态与证据`

### Scope 3

- commit：`待本次提交回填`
- self-test：`mvn -q -Dtest=FlowControllerTests,StageProgressCoordinatorTests,ImplementationContinuationSupportTests,StageTransitionSupportTests,StageStatusSupportTests test`
- code review：`本地静态自审通过；implementation continuation 不再走 generic STAGE_CONTINUE 活路径`
- docs：`tracker 已回填 Scope 3 状态与证据`

### Scope 4

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

### Scope 5

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

### Scope 6

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

### Scope 7

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

### Scope 8

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

## Completion Gate

- [ ] `S1` planning boundary contract 已在 outline/detail/final-gate/reroute 单轨收口
- [ ] `S2` accepted boundary contract 已稳定进入 task package、coder、reviewer、boundary gate
- [ ] `S3` implementation continuation 已保留三分类，不再被 generic continue 压平
- [ ] `S4` canonical repair package 已在 subtask / retry / directive / implementation_state / reroute / resume 全链保持单一真相源
- [ ] `S5` authority corpus 已不再循环强化错误 runtime contract
- [ ] `S6` validation 与 TEST disposition mapping 已不再误拒绝或误路由
- [ ] `R1 ~ R10` 已全部补齐
- [ ] `self-test + code review + docs` 已全部补齐
- [ ] 黄金路径集成测试已通过

结果：`NOT_STARTED`
