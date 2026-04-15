# Review V12: Patch-Continue Convergence Plan

## Summary

- 这轮不继续盲跑集成测试，也不先改代码。
- `v179` 的黄金路径失败，已经把当前第一阻塞重新钉在 `IMPLEMENTATION patch continue 不收敛`，不是 planning、runtime ownership 旧双轨，也不是 Bash 基础设施本身坏掉。
- 最新 reviewer 的 4 条 finding 与本次集成日志是同一问题族：
  - tool loop 把“当前轮必须再产生 mutation”当成 closure 条件
  - implementation resume 让 persisted `PATCH_CONTINUE` 吃掉当前 reroute 的新 patch package
  - TEST targeted reverification 还会复用过时 patch scope
  - stage gate 会把空 scope / 错 scope 静默放大成整份 `allowedScope`
- 本文档把集成现象和 review finding 收成一条统一方案，作为下一轮实现与 review 的唯一入口。

## Scope Boundary

### 本轮明确不重做的已收口项

- 不重开 `planning boundary contract` 与 `continuation 三分类协议` 的 `v11` 基线整改。
- 不重开 `runtime contract resolver` 的目录扫描 / orphan root 旧问题。
- 不重开 `completed-plan PATCH owner resolver` 已落地链路。
- 不重开 `Bash deny pathIntents diagnostics` 已落地链路。
- 不顺手扩新的产品能力，也不重做整套 validation 架构。

### 本轮真正要收的 5 条问题链

1. `PATCH_CONTINUE` / subtask retry 的 closure 仍依赖“本轮 mutation”，而不是工作区当前终态。
2. 当前 reroute / TEST 生成的新 canonical patch package，仍可能被 persisted continuation 或上轮 failure scope 覆盖掉。
3. review / TEST / stage gate 提供的 patch scope 仍可能被静默放大，而不是 fail-fast。
4. repair-mode tool surface 与真实可执行权限不一致，模型还看得到会被拒绝的 Bash / whole-file rewrite 路径。
5. runnable milestone 的批准证据仍然过弱，静态 shell 或弱占位页可能过早进入后续链路。

## Current Evidence

### v179 直接证据：当前首因是 patch loop 不收敛

- 项目：`/home/linus/workspace/tetris_test_itest_v179`
- run id：`8d44b9a8-1669-494d-9f2d-f716990dc7cf`
- 关键产物：
  - `/home/linus/workspace/tetris_test_itest_v179/.devflow/runs/8d44b9a8-1669-494d-9f2d-f716990dc7cf/events.log`
  - `/home/linus/workspace/tetris_test_itest_v179/.devflow/runs/8d44b9a8-1669-494d-9f2d-f716990dc7cf/run.json`
  - `/home/linus/workspace/tetris_test_itest_v179/.devflow/runs/8d44b9a8-1669-494d-9f2d-f716990dc7cf/implementation_state.json`
  - `/home/linus/workspace/tetris_test_itest_v179/index.html`

直接现象：

- `ANALYSIS / PRD / DESIGN` 一次通过。
- `IMPLEMENTATION` planning 已通过，失败不在 outline。
- `subtask-1` 写出的是非常弱的 `index.html` 壳，但仍被批准进入下一子任务。
- `subtask-2` 在已有 `index.html` 上连续尝试：
  - whole-file `Edit`
  - `replace_all Edit`
  - whole-file `Write`
  - Bash 读写替代
- 这些动作都被拒绝后，tool loop 没有稳定收缩到“读取现有文件 -> 局部 Edit”，而是反复走整文件重写和 Bash 回退。
- `IMPLEMENTATION` 随后被重开到下一次 `PATCH_CONTINUE` 尝试，问题面没有改变。
- 磁盘上的 `index.html` 一直停留在弱壳状态，没有真正进入可玩的俄罗斯方块实现。

结论：

- 当前首因不是“模型随机发挥差”，而是系统把 repair round 暴露成一条不可能收口的执行面：
  - whole-file rewrite 被禁止
  - Bash 在 repair mode 下大多也会被禁止
  - 但 closure 语义和 prompt 仍在鼓励模型继续尝试这些路径

### Bash 被拒绝不是偶发故障，而是当前权限面与工具面不一致

- 当前实现明确会拒绝两类 repair-mode Bash：
  - read-only shell exploration
  - 对已有文件的 shell write / overwrite
- 同时，不在 deterministic allow-list 里的命令也会直接报 `UNSUPPORTED_SHELL_COMMAND`。
- 所以集成日志里出现的 `cat ... | head`、`echo > index.html` 被拒，不是 shell 本身坏掉，而是：
  - repair mode 不允许这类 Bash
  - 但模型仍能看到 Bash，并把它当成失败后的可用回退

结论：

- 这不是 Bash 子系统 bug，而是 repair-mode tool surface 设计还没有和权限约束完全对齐。

### 最新 reviewer finding 与本次集成现象的重合关系

#### R1. Tool Loop Closure 仍要求本轮必须有 mutation

- `ImplementationToolLoopExecutor.writeSatisfied()/deleteSatisfied()` 现在按“当前轮 mutation 是否存在”判定完成。
- 这会直接制造一个不可能收敛的状态：
  - 上一轮已经把某个文件修到正确终态
  - 当前 canonical package 里还带着这个文件
  - 当前轮不再改它
  - 系统却会继续判定“声明文件未满足”

判断：

- 这是本次集成失败的直接放大器，不修这条，`PATCH_CONTINUE` 很容易稳定循环。

#### R2. Fresh Patch Package 可能被 Persisted Continuation 吃掉

- `ImplementationResumePolicy` 当前会优先吃 persisted `PATCH_CONTINUE`。
- 如果 `TEST / CODE_REVIEW` 这次刚生成一份新的 canonical patch package，但上一轮 state 里还留着旧 `PATCH_CONTINUE`，resume 仍可能沿旧 scope 修旧问题。

判断：

- 这和本次集成的“同一问题一直重来，但 scope 不刷新”高度一致，是第二层直接放大器。

#### R3. TEST Targeted Reverification 仍可能回用旧 scope

- `TestExecutor` 当前 targeted reverification 在当前 failure 没有 `overrideChanges` 时，仍可回退用上一轮 `previousFailure.overrideChanges()`。
- 这会把旧 patch scope 重新塞回 implementation。

判断：

- 这条未必是 `v179` 第一跳就触发的原因，但它会让后续 `TEST -> implementation` 循环稳定卡在过时路径上。

#### R4. Stage Gate 还会把空 scope / 错 scope 扩成 allowedScope

- `ImplementationStageGate.canonicalOverrideChanges()` 当前对空的或完全不相交的 `overrideChanges`，会退回整份 `allowedScope`。

判断：

- 这会把本来应该 fail-fast 的协议错误，静默变成“自动扩大 patch 范围继续修”，是本次循环长不死的重要放大器。

### 次级但同轮必须收住的质量问题

- `subtask-1` 的弱占位页被批准，说明 runnable milestone 证据仍然过弱。
- 对当前这类“可玩的网页版”任务，如果 milestone guard 只看 smoke/resource/syntax，而不要求更强的当前能力证据，后续子任务就会从一份过弱壳子继续推进。

结论：

- 这不是本次循环的唯一根因，但它和当前 patch-continue 不收敛属于同一条质量链。
- 如果本轮只修 loop，不收紧 runnable milestone，下一轮仍可能在更早位置重新堆出弱骨架。

## Problem / Solution Map

### P1. Closure 必须从“本轮有 mutation”改成“当前工作区满足 canonical package”

#### Problem

- 当前 closure 语义把 repair 回合错误建模成“每一轮都要继续动手”。
- 对 continuation / retry 来说，正确语义应当是：
  - 当前 canonical patch package 涉及的文件，工作区是否已经到达目标终态
  - 而不是“本轮是否又生成了新的 mutation record”

#### Solution

- `ImplementationToolLoopExecutor` 的 declared-change completion 必须改成 workspace-state 判定：
  - 基于当前文件存在性、当前内容、当前结构化 patch 终态判断是否满足
  - 不再把“本轮 mutation record 非空”作为必要条件
- 如果文件已经在正确终态，本轮允许零 mutation 收口。
- 如果文件未达终态，才继续要求实际工具动作。

### P2. 最新 canonical patch package 必须压过 persisted continuation、feedback merge 残留和上轮 failure

#### Problem

- 当前系统里至少还有三条旧语义会抢最新 patch package：
  - `ImplementationResumePolicy` 优先 persisted continuation
  - intra-stage feedback merge 仍可能把旧 concrete package 保留下来
  - `TestExecutor` 在 targeted reverification 中回退到 `previousFailure.overrideChanges()` / 旧 patch target

#### Solution

- 统一成单一规则：
  - 当前 reroute note / 当前 TEST / 当前 review 产出的 canonical patch package 优先级最高
  - persisted continuation 只在当前轮没有新 concrete package 时才允许生效
  - feedback merge 在 fresh feedback 没有 concrete package 时，不得把旧 concrete package 继续偷带进下一轮
- active round 的 patch package owner 必须单一：
  - stage-level fresh package 只允许沿 `revision note -> directive parser -> resumed execution state` 进入 implementation
  - 一旦进入当前 active subtask，唯一 machine owner 立刻切换为 `SubtaskExecutionState.effectiveChanges`
  - `SubtaskRevisionDirective` 是唯一把 fresh patch package 写入当前 active execution state 的结构化入口
  - `TaskPackage` / scoped task package 只允许作为从当前 active scope 派生出来的 coder/reviewer 视图，不再承担 machine owner
  - feedback channel 只承载 prose 与辅助约束；fresh feedback 没有 concrete package 时，merge 结果允许不带 package
- revision note producer / parser / feedback merge / resume 必须使用同一份 canonical patch package 口径：
  - revision note 负责落盘当前 fresh package
  - directive parser 只解析这份 package，不再从其他路径补第二份 owner
  - feedback merge 不再承担 active patch package owner，不能把 base concrete package 复活
  - resume 只消费收口后的 canonical package
- `TestExecutor` 不再回退使用上轮 `overrideChanges` 或 patch target。
- 当前 failure 如果没有安全 canonical scope：
  - 直接 `REQUEST_HUMAN` / `BLOCK_STAGE`
  - 不允许拿旧 scope 冒充当前 scope

### P3. 空 scope / 错 scope 不能再静默扩成 allowedScope

#### Problem

- 当前 stage gate 对空的或完全不相交的 patch scope，会扩成整份 `allowedScope`。
- 这会把协议错误伪装成“自动扩大 patch 范围继续修”。

#### Solution

- `ImplementationStageGate` 必须改成 fail-fast：
  - proposed scope 为空
  - proposed scope 与 allowed scope 完全不相交
  - proposed scope 需要 canonical package 但当前无法安全解析
  - 以上场景都不得自动扩范围
- 结果只能是：
  - `BLOCK_STAGE`
  - `REQUEST_HUMAN`
  - 或当前 producer 自己重新给出正确 canonical package

### P4. Repair-Mode Tool Surface 必须和真实权限严格对齐

#### Problem

- 当前 repair mode 里，模型还能看到 Bash 和 whole-file rewrite 这类高概率会被拒绝的路径。
- 结果就是：
  - whole-file `Edit/Write` 被拒
  - Bash 又被拒
  - 但 prompt / tool surface 没有把模型稳定压回“Read + localized Edit”

#### Solution

- repair mode 只暴露当前 truly-legal 的工具策略：
  - 优先 `Read`
  - 再 `Edit` 局部 patch
  - `Write` 只用于新文件或明确允许的 whole rewrite
  - Bash 不再作为 repair-mode 的伪回退路径暴露给模型
- whole-file rewrite rejection 必须返回结构化、可执行的 localized-edit 指引，而不是只说“不能这么做”。
- `ImplementationToolPromptBuilder`、permission policy、tool registry / loop policy 必须使用同一口径。

### P5. Runnable Milestone 必须有更强的当前能力证据

#### Problem

- 当前 runnable milestone 仍可能批准弱壳页面。
- 这会让 implementation 从一份过弱的中间产物继续 patch，放大后续 repair 负担。

#### Solution

- `SubtaskRunnableMilestoneGuard` 与 `SubtaskVerificationSupport` 必须提高 runnable milestone 的放行门槛：
  - 不只是 smoke/resource/syntax 通过
  - 还要有当前 milestone 对应的运行态证据
  - 对“可玩的网页版”类任务，必须证明当前 milestone 的核心互动面已经存在，而不是只剩静态骨架
- 如果当前证据不足：
  - 直接在当前子任务内打回
  - 不让弱壳进入下一子任务

## Final State

- repair / continuation 回合的 closure 只看“当前工作区是否满足 canonical patch package”，不再要求本轮必须再产生 mutation。
- 当前 reroute note / TEST / review 产出的 canonical patch package 成为实现续跑的唯一最高优先级真相源。
- 当前 active subtask 的 patch package machine owner 固定为 `SubtaskExecutionState.effectiveChanges`；`TaskPackage` 只保留派生视图职责。
- 空 scope、错 scope、无法解析的 scope 不再被自动放大成整份 `allowedScope`。
- repair-mode tool surface 与真实权限严格一致；模型不会再被引导去尝试注定被拒的 Bash 或 whole-file rewrite 路径。
- runnable milestone 不再批准弱占位页；只有当前里程碑的运行态证据达标，才允许推进下一子任务。
- 黄金路径重跑时，不再出现：
  - `subtask-2` 在已有 `index.html` 上反复 whole-file rewrite
  - Bash 作为 repair-mode 伪回退反复被拒
  - 新 patch package 被旧 continuation 或旧 test scope 吃掉
  - 空 scope 被静默扩成整个子任务范围继续自动续跑

## Removal Plan

本轮必须删除或封死：

- `ImplementationToolLoopExecutor` 中“declared changes satisfied 必须来自本轮 mutation”的旧语义。
- `ImplementationResumePolicy` 中“persisted PATCH_CONTINUE 优先于当前 reroute patch package”的旧语义。
- `ExecutionDirectivePayload` / `ExecutionDirectiveFeedbackSupport` / `ImplementationPlanRunner` / `SubtaskRecoverySupport` 中“旧 concrete package 可在 feedback merge 后继续残留”的旧语义。
- feedback channel / merged feedback 继续承担 active patch package owner 的旧语义。
- `TestExecutor` 中对 `previousFailure.overrideChanges()` / 旧 patch target 的回退复用。
- `ImplementationStageGate` 中“空 scope / 完全不相交 scope 自动退回 allowedScope”的旧 fallback。
- repair mode 中只会被拒绝的 Bash / whole-file rewrite 伪可用路径。
- runnable milestone 对弱壳页面的过早放行路径。

## Joint-Change Scope

### Scope 1. Tool Loop Convergence Closure

- `src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolLoopExecutor.java`
- `src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolPromptBuilder.java`
- `src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolContext.java`
- `src/main/java/devflow/agent/executor/tools/ImplementationToolPermissionPolicy.java`
- `src/main/java/devflow/agent/executor/tools/ImplementationToolRegistry.java`
- `src/main/java/devflow/agent/executor/tools/BashTool.java`
- `src/main/java/devflow/agent/executor/tools/FileEditTool.java`
- `src/main/java/devflow/agent/executor/tools/FileWriteTool.java`
- `src/main/java/devflow/agent/executor/tools/ToolExecutionContext.java`

收口要求：

- completion 判定改为 workspace-state based
- repair-mode 工具面与权限面对齐
- whole-file rewrite reject 返回 localized-edit 指引
- Bash 不再作为 repair-mode 伪回退路径

### Scope 2. Canonical Patch Package Precedence Closure

- `src/main/java/devflow/agent/orchestrator/StageRevisionRepairSupport.java`
- `src/main/java/devflow/agent/orchestrator/StageRevisionNoteBuilder.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationDirectiveResolver.java`
- `src/main/java/devflow/agent/protocol/ExecutionDirectivePayload.java`
- `src/main/java/devflow/agent/protocol/ExecutionDirectiveFeedbackSupport.java`
- `src/main/java/devflow/agent/executor/implementation/ImplementationPlanRunner.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskRecoverySupport.java`
- `src/main/java/devflow/agent/executor/implementation/ImplementationResumePolicy.java`
- `src/main/java/devflow/agent/executor/implementation/CoderTurnCoordinator.java`
- `src/main/java/devflow/agent/artifact/ImplementationStageComposer.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskRevisionDirective.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskExecutionState.java`
- `src/main/java/devflow/agent/executor/subtask/TaskPackage.java`

收口要求：

- 当前 reroute note 的 concrete patch package 优先于 persisted continuation
- revision note 生成、directive 解析、feedback merge、resume 消费必须使用同一份 fresh canonical package
- 当前 active subtask 的 patch package owner 固定为 `SubtaskExecutionState.effectiveChanges`
- `SubtaskRevisionDirective` 是 active execution state 的唯一结构化写入口
- `TaskPackage` 只从当前 active scope 派生，不再承担 machine owner
- feedback merge 不得在 fresh feedback 缺少 concrete package 时复活 base concrete package；fresh feedback 没有 package 时，merge 结果允许不带 package
- persisted continuation 只在当前轮没有新 concrete package 时才允许生效
- resume 不能继续沿用旧 patch scope 修旧问题

### Scope 3. Stage Gate Scope Discipline Closure

- `src/main/java/devflow/agent/executor/gate/ImplementationStageGate.java`
- `src/main/java/devflow/agent/executor/testing/TestExecutor.java`
- `src/main/java/devflow/agent/executor/testing/ExperienceFailureDispositionResolver.java`

收口要求：

- 空 scope / 完全不相交 scope 直接 fail-fast
- TEST targeted reverification 不再回退复用上轮 concrete scope
- 只有当前轮安全 canonical scope 才允许自动 patch continue

### Scope 4. Runnable Milestone Quality Closure

- `src/main/java/devflow/agent/executor/subtask/SubtaskVerificationSupport.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskRunnableMilestoneGuard.java`
- `src/main/java/devflow/agent/executor/subtask/ImplementationSelfCheckReviewResolver.java`
- `src/main/java/devflow/agent/executor/testing/TestExecutor.java`
- `src/main/java/devflow/agent/validation/ValidationExecutor.java`
- `src/main/java/devflow/agent/validation/WebRuntimeWiringValidationSupport.java`
- `src/main/java/devflow/agent/validation/WebResourceValidationSupport.java`

收口要求：

- runnable milestone 不再只靠 smoke/resource/syntax 放行
- `TestExecutor` 对 runnable milestone 的 functional verification 触发条件和验证面必须同步收紧
- 当前 milestone 的运行态能力证据必须达标
- 弱骨架必须在当前子任务内被打回

### Scope 5. Regression Matrix

必须一起补的回归：

- `src/test/java/devflow/agent/executor/ImplementationToolLoopExecutorTests.java`
  - 已满足终态但本轮零 mutation 的 `PATCH_CONTINUE` 允许收口
  - existing-file patch round 不再因为“本轮没再动手”被误判失败
- `src/test/java/devflow/agent/executor/FileEditToolTests.java`
  - whole-file rewrite reject 返回 localized-edit 可执行反馈
- `src/test/java/devflow/agent/executor/FileWriteToolTests.java`
  - repair round 下 existing-file overwrite 不被当成隐性回退
- `src/test/java/devflow/agent/executor/BashToolTests.java`
- `src/test/java/devflow/agent/executor/implementation/toolloop/BashToolFailureDiagnosticsTests.java`
  - repair-mode Bash 不再承担伪回退角色
- `src/test/java/devflow/agent/protocol/ExecutionDirectiveFeedbackSupportTests.java`
  - fresh feedback 缺少 concrete package 时，不再复活 base concrete package，active scope 由 execution state 持有
- `src/test/java/devflow/agent/executor/implementation/ImplementationPlanRunnerTests.java`
  - intra-stage feedback merge 不再把旧 concrete patch package 带入后续 active subtask
- `src/test/java/devflow/agent/executor/ImplementationResumePolicyTests.java`
  - 当前 reroute patch package 覆盖 persisted continuation
- `src/test/java/devflow/agent/executor/SubtaskExecutionStateTests.java`
  - `SubtaskRevisionDirective` 写入的 active patch package 只由 `effectiveChanges` 持有
- `src/test/java/devflow/agent/executor/subtask/TaskPackageTests.java`
  - scoped task package 只从当前 active scope 派生，不从 merged feedback 回捞 concrete package
- 新增 revision note render / parse round-trip 回归
  - fresh patch package 经 `StageRevisionNoteBuilder -> ImplementationDirectiveResolver` 后仍保持同一份 machine truth
- `src/test/java/devflow/agent/executor/ImplementationStageGateTests.java`
  - 空 scope / 完全不相交 scope 不再扩成 allowedScope
- `src/test/java/devflow/agent/executor/testing/TestExecutorTests.java`
  - runnable milestone 会触发更强 functional verification，而不是仅凭弱壳页面通过
- `src/test/java/devflow/agent/executor/testing/ExperienceFailureDispositionResolverTests.java`
  - targeted reverification 不再回退旧 scope
- `src/test/java/devflow/agent/executor/subtask/SubtaskVerificationSupportTests.java`
- `src/test/java/devflow/agent/executor/ImplementationSelfCheckReviewResolverTests.java`
  - runnable milestone 对弱骨架的误放行被锁死
- 黄金路径集成回归
  - 同一类 `index.html` existing-file patch round 必须收敛到 localized edit，不再 whole-file rewrite + Bash 循环

## Closure Decision

- 这轮可以一次性收口。
- 但前提是 `Scope 1 ~ Scope 4` 必须一起做，不能拆成“先修 tool loop，再看 resume / TEST / stage gate / milestone”。
- 如果只改单点，会留下同类问题的第二轨：
  - 只修 tool loop，不修 resume / TEST scope，旧 patch package 仍会回来
  - 只修 gate，不修 tool surface，模型仍会在 repair mode 里重复不可能路径
  - 只修 loop，不修 milestone，弱壳仍会过早进入后续子任务

## Risks / Blockers

- 本轮目标是收掉“implementation patch continue 不收敛”这一类系统性问题，不是保证模型永远不会写错业务代码。
- 如果这轮按本文档完整实现后，黄金路径仍不过，下一批真正值得优先查的是：
  - `ImplementationSnapshotRestorer` / resume restore drift
  - flow layer 是否仍把具体 continuation 压平
  - validation side false negative
- 这些不是当前首因，本轮不应抢跑重构。

## Implementation

### Phase 1. 改 closure owner

- 把 `ImplementationToolLoopExecutor` 的 declared-change satisfaction 改成 workspace-state 判定。
- 删除“本轮必须有 mutation 才能结束”的语义。

### Phase 2. 改 patch precedence

- 调整 `ImplementationResumePolicy`，让当前 reroute note 的 concrete patch package 高于 persisted continuation。
- `CoderTurnCoordinator` / `ImplementationStageComposer` 只负责把当前 package 传进来，不再保留旧优先级第二轨。

### Phase 3. 改 scope clamp

- `ImplementationStageGate` 对空 scope / 错 scope 直接 fail-fast。
- `TestExecutor` 和 `ExperienceFailureDispositionResolver` 删除旧 scope fallback。

### Phase 4. 改 repair-mode tool surface

- 收紧 repair mode 下的 tool visibility 和 prompt 语义。
- 将 existing-file patch 的主路径明确压回 `Read -> localized Edit`。
- whole-file rewrite reject 统一返回结构化局部修复指引。

### Phase 5. 改 runnable milestone gate

- 提升 runnable milestone 的放行证据要求。
- 对当前任务类型，禁止静态壳页面作为 runnable milestone 通过。

### Phase 6. 回归验证

- 先跑 Scope 1 ~ Scope 4 对应单测。
- 再跑黄金路径集成测试。
- 只要还出现：
  - whole-file rewrite loop
  - Bash repair fallback loop
  - stale patch scope reuse
  - empty-scope widening
  - weak placeholder milestone approval
  任何一条，都不能宣称收口完成。

## Completion Gate Result

- 当前结果：`PLAN_READY_FOR_REVIEW`
- 进入实现前必须满足：
  - 方案 review 通过
  - `Scope 1 ~ Scope 4` owner 无遗漏
  - regression matrix 已确认
- 只有在 `Scope 1 ~ Scope 5` 全部完成、单测通过、黄金路径重跑通过后，才允许宣称这条问题族已收口。
