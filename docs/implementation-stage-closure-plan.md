# IMPLEMENTATION v172 收口方案

## Purpose

这份文档只处理 `2026-04-14` 黄金路径集成测试 `tetris_test_itest_v172` 暴露出的当前真实未闭合问题。

约束来源：

- 根目录 `AGENTS.md`
- `docs/engineering-agreements.md`
- `docs/current-state.md`
- `docs/active-work-items.md`

本方案不是重开旧整改，也不是补过渡层。它只收当前这轮集成失败真正暴露出的规划分工塌陷、运行时接线包不完整、实现续跑不收敛这三个问题族。

## Latest Evidence

本轮失败证据来自：

- `/home/linus/workspace/tetris_test_itest_v172.itest.log`
- `/home/linus/workspace/tetris_test_itest_v172/.devflow/runs/25615d68-9413-4726-964e-5d50cc4f7446/events.log`
- `/home/linus/workspace/tetris_test_itest_v172/.devflow/runs/25615d68-9413-4726-964e-5d50cc4f7446/implementation_state.json`
- `/home/linus/workspace/tetris_test_itest_v172/.devflow/runs/25615d68-9413-4726-964e-5d50cc4f7446/implementation_stage_status.md`
- `/home/linus/workspace/tetris_test_itest_v172/.devflow/runs/25615d68-9413-4726-964e-5d50cc4f7446/task_packages.md`
- 当前产物：
  - `/home/linus/workspace/tetris_test_itest_v172/index.html`
  - `/home/linus/workspace/tetris_test_itest_v172/src/board.js`
  - `/home/linus/workspace/tetris_test_itest_v172/src/engine.js`

本轮已证实的事实：

1. `ANALYSIS / PRD / DESIGN` 的输出预算正常，不存在“小 cap 把代码截断”的主因。
2. 第一轮 `IMPLEMENTATION` 在 `2026-04-14 14:35:56` 结束时并未通过，而是触发了 `STAGE_CONTINUE` 并重进 `IMPLEMENTATION` 第 2 次阶段尝试。
3. 当前失败也不是“流程没有继续到 implementation”或“stage continue 基础路由失效”。`StageProgressCoordinator` 和 `FlowController` 已经把流程留在 `IMPLEMENTATION` 内部继续执行。
4. 当前第二个子任务的 accepted change-set 只有 `src/engine.js`、`src/board.js`，但执行中反复尝试修改 `index.html` 以接入这两个文件。
5. 当前不是单点语法错误，而是：
   - skeleton 子任务越权实现了完整功能
   - runtime split package 不完整
   - stage continue 没有携带当前失败子任务的 canonical repair package
   - tool loop 在 repair 场景下不收敛

## 已排除项（不要重做）

以下项不是当前主根因，本轮不得重开：

1. `implementation_state.json` 单一真相源
2. `STAGE_CONTINUE` / `StageProgressCoordinator` 基础路由
3. continuation blocked review 落盘的前一轮整改
4. token 预算整体过低
5. 老的 orphan runtime wiring 基础修复
6. planning detail 回填 runtime metadata

这些项已经在代码和状态文档里收口。本轮如果再围绕这些点重做，只会把问题面重新扩散。

## Current Problems

### P1. skeleton 子任务分工塌了，入口壳子任务错误吞掉了全部业务能力

当前证据：

- `implementation_state.json` 中，第一个子任务 `初始化HTML入口文件并引入基础资源` 的 `ownedCapabilities` 已覆盖全部核心能力。
- `task_packages.md` 中，这个 skeleton 子任务的“当前负责能力”同样覆盖：
  - 方块下落与移动控制
  - 方块旋转与碰撞检测
  - 行消除与计分机制
  - 游戏暂停与继续功能
  - 游戏结束判定
- 实际执行结果中，第一个子任务直接在 `index.html` 内联了完整俄罗斯方块逻辑，并且被评审 `APPROVED`。

这说明当前问题不是“模型偶然写多了”，而是：

- 规划阶段就把 skeleton 子任务的能力边界放宽了
- task package 也把这个错误边界继续传给 coder 和 reviewer
- 结果第一子任务直接提前完成了后续子任务本该负责的能力

### P2. runtime split 被拆到了不同子任务里，但后续子任务没有拿到宿主入口修改契约

当前证据：

- 子任务 2 `实现游戏引擎核心逻辑` 的 accepted change-set 只有：
  - `src/engine.js`
  - `src/board.js`
- 子任务 1 已经把 `index.html` 写成完整内联运行时。
- 子任务 2 在执行过程中反复尝试把 `index.html` 改为接入 `src/board.js` / `src/engine.js`，但这不是它明确拿到的结构化文件契约。

这说明当前问题不是“模型不知道要改入口页”，而是：

- accepted package 自身是不完整的
- 新 runtime root 文件和宿主接线没有被放进同一 execution package
- coder 只能在执行时临时发现需要改 `index.html`，然后越出当前 accepted change-set

### P3. 子任务评审只看能不能跑，没有按任务包边界拦截 deferred capability 越权实现

当前证据：

- 第一个 skeleton 子任务声明的文件契约只有 `index.html` 和 `src/style.css`。
- 但它实际已经实现了完整游戏逻辑，并提前覆盖了后续子任务能力。
- 当前实现评审仍给出：
  - `APPROVED`
  - `HTML入口文件创建完成，资源引用正确，页面可加载显示游戏界面。`

这说明当前问题不是“verification 全坏了”，而是：

- reviewer 会做 smoke / wiring / syntax 检查
- 但没有把“是否越权实现了 deferred capabilities”作为硬性驳回条件
- 只要页面能打开，越界实现也会被放过

### P4. `STAGE_CONTINUE` 只保留了“还有哪些子任务没做完”，没有保留当前失败子任务的 repair package

当前证据：

- 第一轮 `IMPLEMENTATION` 结束后，`implementation_stage_status.md` 只有：
  - `continuationMode=CONTINUE_SUBTASKS`
  - `continuationPatchTarget=NONE`
  - `continuationOverrideChanges=[]`
- 同时它只保留：
  - `未完成子任务：实现游戏引擎核心逻辑；集成用户输入处理与游戏状态管理`
- 第二轮 `IMPLEMENTATION` 只是“复用旧计划”，然后重新从同一个子任务泛化重试。

这说明当前问题不是“系统不会继续跑”，而是：

- system 知道要继续 implementation
- 但没有把当前失败子任务的 canonical scope、material mutations、失败证据做成一个可恢复的 repair package
- 于是下一轮只能重新给同一个子任务宽泛重试，导致重复同类失败动作

### P5. repair 场景下的 tool loop 没收敛到 patch-first，继续在已有文件上做整块重写和无效 shell 读

当前证据：

- 第二轮 `IMPLEMENTATION` 继续出现：
  - 多次 `Read index.html`
  - `cat index.html`
  - `head -n 20 index.html`
  - 整块 `Write src/board.js`
  - 整块 `Edit index.html`
- 其中 `head -n 20 ...` 被 `UNSUPPORTED_SHELL_COMMAND` 拒绝。
- 对 `src/board.js` / `src/engine.js` 的写入多次失败，说明它仍在把已有文件当成 fresh rewrite 处理。

这说明当前问题不只是“模型偶发乱调用工具”，而是：

- 第二轮 repair 入口没有强制切换到 patch-first 模式
- 允许它继续对已有文件做整块重写
- 允许它继续浪费回合在 shell 只读命令上

## Final State

完成态必须同时满足：

1. skeleton 子任务不再默认拥有后续 gameplay capability，也不能在 task package 中吞掉全部业务能力。
2. 当子任务要引入新的 runtime root 文件，而当前宿主入口尚未接线时，同一 accepted package 必须同步携带宿主入口 patch；不完整 package 在 planning 阶段即被打回。
3. subtask review 不再只返回被取平的 `ReviewResult`；子任务级 boundary finding 必须先保留在结构化 review payload 中，再由 deterministic gate 消费，并最终映射回 `ReviewResult`。不得把 implementation-subtask 专属 boundary 语义直接散入全局 review 主协议。
4. `TaskPackage.alignToSubtask()` 不再把旧 task package 中的 capability fallback 重新灌回当前 subtask；planning 收紧后的 capability partition 必须原样流到 coder / reviewer 输入。
5. `CONTINUE_SUBTASKS` 不再只保留“未完成子任务列表”，还必须保留当前失败子任务的 canonical repair package。
6. implementation 第 2 次阶段尝试必须从 repair package 恢复，而不是重新给同一子任务一个宽泛 execution package。
7. repair/resume 模式下的 tool permission 由单一 permission policy 决定：Bash 不再承担只读探索，repair mode 只允许在当前 canonical file contract 内做 patch-first 编辑，不再继续对已有文件做 fresh `Write`，也不再浪费回合做 `cat/head` 这类 shell 只读读取。
8. `events.log`、阶段 artifact、`run.json` 的最终状态必须一致；`REJECTED + ROUTE_TO_REPAIR` 不得再被后续收尾链覆写成 `APPROVED / COMPLETED`。
9. 本轮不引入兼容层、fallback、heuristic patch，也不新增第二套 planning/runtime 协议。

## Removal Plan

本轮必须删除或封死以下错误路径：

1. skeleton 子任务默认吞掉全部 `ownedCapabilities` 的旧规划路径。
2. “新增 runtime 文件，但宿主接线后面再说”的不完整 accepted package 路径。
3. subtask structured review 在 `.result()` 处被取平、boundary 语义直接丢失的旧路径。
4. `TaskPackage.alignToSubtask()` 通过 `emptyAware(...)` 把旧 capability 灌回当前 subtask 的旧 fallback 路径。
5. 只看 smoke / syntax、忽略 deferred capability 越权的旧 subtask approval 路径。
6. `CONTINUE_SUBTASKS` 只保留未完成列表、不保留当前 subtask repair package 的旧续跑路径。
7. repair mode 下对已有文件继续走 fresh `Write` 的旧执行路径。
8. repair mode 下继续使用 `cat/head` 这类 shell 只读命令的旧执行路径。
9. `REJECTED + ROUTE_TO_REPAIR` 之后仍可能把 run/stage 状态落成 `APPROVED / COMPLETED` 的旧收尾路径。

## Joint-Change Scope

这轮如果进入实现，必须一起改以下联动面，否则一定会留下半成品：

### 1. planning input / wiring / gate 链

- `ImplementationPlanNormalizationSupport`
- `ImplementationPlanGateInput`
- `ImplementationPlanGateInputBuilder`
- `PlanningRequest`
- `ImplementationPlanningWiring`
- `ImplementationPlanner`
- `ImplementationOutlineGate`
- `ImplementationPlanCoverageAnalyzer`
- `ImplementationPlanChangeGate`
- `ImplementationSubtaskDetailGate`

要求：

- capability partition 的 canonical owner 只允许在 planning 链产生
- `ImplementationPlanGateInputBuilder` 是 gate 输入的唯一展平装配点；新的 planning runtime facts 不得绕过 builder 散落到 gate 或 planner
- accepted package completeness gate 必须复用单一 planning runtime facts 输入
- planning gate 不得自行新增目录扫描、root-script 猜测或 companion 文件名 fallback

### 2. task package / coder 输入链

- `TaskPackage`
- `TaskPackageAssembler`
- `TaskPackageMarkdownRenderer`
- `ImplementationToolPromptBuilder`

要求：

- planning 已收紧的 capability partition 必须原样透传
- `TaskPackage.alignToSubtask()` 不得再把旧 capability 灌回当前 subtask
- coder prompt、task package、subtask review 输入必须消费同一份 canonical capability partition

### 3. subtask structured review / boundary gate 链

- `LlmProvider`
- `StructuredReviewResult`
- `SubtaskReviewPromptAssembler`
- `SubtaskVerificationSupport`
- `OllamaStructuredReviewExecutor`

要求：

- boundary finding 的 typed carrier 必须显式定义；不得停留在 ad hoc JSON 或 prose 约定
- typed carrier 必须显式挂在真实协议类型边界上；当前子任务 review 的结构化入口至少要落在 `StructuredReviewResult` 对应层
- 如果现有 `StructuredReviewResult` 无法干净承载 subtask-only payload，应新增专用 typed result，而不是退回 executor 层 ad hoc JSON
- `LlmProvider` 必须能跨 provider 类型边界承载这份 subtask-only typed payload；不能只在 executor 层临时拼 ad hoc JSON
- `SubtaskVerificationSupport` 必须先消费这份 typed payload，再做 deterministic boundary gate，最后再映射回 `ReviewResult`
- 最终再映射回 `ReviewResult`
- 不直接把 implementation-subtask 专属 boundary 字段散入全局 `ReviewResult` 主协议
- 也不把 subtask boundary 语义硬塞进通用 `ReviewSemantics`，避免全局 review 协议被 implementation 子任务语义污染

### 4. runtime repair package / resume 链

- `ImplementationArtifactPersister`
- `ImplementationStageStatus`
- `ImplementationStateSnapshotSerializer`
- `ImplementationStateSnapshot`
- `ImplementationStateCodec`
- `ImplementationStateArtifactSupport`
- `ImplementationProgressSupport`
- `ImplementationContinuationSupport`
- `ImplementationStageStatusPayload`
- `SubtaskRuntimeWiringGuard`
- `RuntimeWiringRetryChangeFactory`
- `SubtaskRevisionDirective`
- `ImplementationResumePolicy`
- `ImplementationStageStatusArtifactRenderer`

要求：

- implementation resume 的机器事实源只允许来自 `implementation_state` 及其结构化读写链
- canonical repair package 必须真正进入 `ImplementationStageStatus -> ImplementationStateSnapshotSerializer -> ImplementationStateSnapshot -> ImplementationStateCodec -> implementation_state.json` 这条写侧协议链
- continuation / resume 的 typed carrier 必须显式落在真实协议边界上；当前至少要覆盖 `ImplementationStageStatusPayload`
- 如果现有 continuation payload 无法干净承载 current subtask canonical repair package，应新增专用 typed payload，而不是继续依赖 markdown 展示物或 support 层临时拼装
- `implementation_stage_status.md` 只是派生展示物，不得再被当成 resume / continuation 的真实 owner
- 子任务级 `PATCH_RUNTIME_WIRING` 必须直接复用 `RuntimeWiringRetryChangeFactory`
- 不允许在 `SubtaskRuntimeWiringGuard` 自己再拼第二套 runtime repair package
- current subtask canonical repair package 必须先写入 structured payload，再经过 persist / render / parse / continuation expand 全链消费

### 5. repair-mode permission / tool loop 链

- `ImplementationToolPermissionPolicy`
- `ImplementationToolPermissionContext`
- `ImplementationToolLoopExecutor`
- `ImplementationToolContext`
- `ToolExecutionContext`
- `FileEditTool`
- `FileWriteTool`
- `BashTool`
- `ShellCommandAnalyzer`

要求：

- repair/resume 模式必须进入 permission policy 的单一 owner
- tool loop 不得再靠 prompt 文案约束 Bash 只读探索
- 已有文件禁止 fresh rewrite、whole-file rewrite 与 shell read 收敛必须覆盖 permission / tool-context / tool implementation 全链
- 不允许只在 permission policy 引入 repair/resume mode，而具体工具仍按旧 `deliveryMode == REWORK` 分支放行 whole rewrite
- `BashTool` 必须和 analyzer / permission / context 一起收口，确保 shell deny payload、pathIntents diagnostics、执行前 `assertShellWriteTargets(...)`、执行后 mutation 校验都同步进入 repair-mode patch-first 约束

### 6. run-state consistency 链

- `StageProgressCoordinator`
- `FlowController`
- `FlowDecisionExecutor`
- `StageTransitionSupport`
- `StageRevisionSupport`
- `StageRevisionRepairSupport`
- `StageProgressArtifactSupport`
- `StageEntryExecutor`
- `StageStatusSupport`

要求：

- review reject、supervisor repair route、最终 run/stage 落盘必须走同一条收尾链
- `StageRevisionSupport` 负责 repair reroute 的 review history、event log、revision note 和 re-entry owner；不得把这条链留在 scope 之外
- `StageProgressArtifactSupport` 负责 transition artifact 与相关 event 的真实落盘；不得留下“状态正确但 artifact / event 口径漂移”的尾巴
- `StageEntryExecutor` 负责 repair reroute 后的 stage re-entry、attempt 推进和 artifactPath 持久化；不得把最终 re-entry owner 留在 scope 之外
- 不允许上层已进入 repair，而底层仍把 run/stage 收成 approved/completed
- `events.log`、artifact、`run.json` 必须保持一致

## Closure Decision

可以一次性收口，但前提是本轮只做同一问题族：

- skeleton 分工塌陷
- runtime split package 不完整
- subtask boundary review 不可信
- stage continue 缺 repair package
- repair mode tool loop 不收敛
- run-state consistency 漂移

如果把范围再扩到 token 预算、旧 continuation routing 基础收口、runtime metadata 规划协议等已收口项，就会再次偏离主线。

## Implementation Order

实现顺序固定为：

1. planning input / wiring / gate 链
2. task package / coder 输入链
3. subtask structured review / boundary gate 链
4. runtime repair package / resume 链
5. repair-mode permission / tool loop 链
6. run-state consistency 链
7. `self-test + code review`
8. 黄金路径集成测试

说明：

- 第 1 步和第 2 步必须一起完成，否则 planning 收紧会被旧 task package fallback 重新污染。
- 第 3 步必须落到真实结构化 payload owner，不能只改 prompt prose。
- 第 4 步必须复用现有 `RuntimeWiringRetryChangeFactory`，禁止子任务级再造一套 builder；同时必须把 canonical repair package 真正写入 `implementation_state` 的写侧协议链。
- 第 5 步必须把 repair/resume mode 送进 permission policy，不能只改 analyzer 名单。
- 第 6 步必须连同 `FlowDecisionExecutor / StageTransitionSupport / StageRevisionSupport / StageRevisionRepairSupport / StageProgressArtifactSupport / StageEntryExecutor / StageStatusSupport` 一起收，不能只改展示层、coordinator 或状态表层落盘。

## Problem-to-Solution Mapping

### P1 对应方案：把 skeleton 子任务的能力边界收回到壳体级，不再吞后续业务能力

做法：

1. `SKELETON` 子任务不再允许默认拥有后续子任务的业务能力。
2. 对于“只搭入口页和基础样式”的 runnable milestone，`ownedCapabilities` 允许为空，或仅保留与当前文件契约完全一致的最小能力。
3. `ImplementationPlanNormalizationSupport` 不再把空 `ownedCapabilities` 回退成 `acceptanceCriteria`。
4. `TaskPackage.alignToSubtask()` 不再通过 `emptyAware(...)` 把旧 task package 里的 capability 重新灌回当前 subtask。
5. `task_packages.md`、coder prompt、subtask review 必须消费同一份 capability partition，不能一处说是壳体任务，一处又让它负责全部 gameplay capability。
6. 第一子任务只负责：
   - 页面壳体
   - 基础样式
   - 最小 bootstrapping
7. `CAP-1 ~ CAP-5` 这类 gameplay capability 必须继续留给后续子任务按计划落地。

目标：

- skeleton 子任务不再越权实现完整产品
- 后续子任务的责任边界重新成立

### P2 对应方案：runtime split package 必须在 accepted 前自带宿主入口 patch

做法：

1. 新增 package completeness gate：
   - 如果某个子任务引入新的 runtime root 文件，而当前宿主入口并未接入它，那么同一子任务必须同时拥有宿主 HTML patch。
2. planning 链必须先拿到单一 `PlanningRuntimeFacts` 等价输入，不能让每个 gate 各自读取 workspace 或各自重建 HTML 事实。
3. 这份 planning runtime facts 的装配 owner 只允许在 planning wiring 层，不允许散落到各个 gate。
4. 这条判断只依赖：
   - 当前 accepted change-set
   - 当前项目已有宿主入口事实
   - 且这些 facts 的合法来源只能是：
     - explicit host contract
     - 当前 accepted scope
     - 当前 HTML 已观察到的 structured wiring facts
5. 不允许把“新增 `src/engine.js` / `src/board.js`”和“接线 `index.html`”拆到不同 execution package。
6. 显式禁止把目录扫描、root-script 猜测、基于 companion 文件名的 fallback 推断作为 package completeness gate 的事实来源。
7. 这条规则不回灌 planning detail 的 runtime metadata，只在 accepted package 完整性层判断。

目标：

- coder 不再在执行阶段临时发现需要改 `index.html`
- runtime split 从 package 层就是完整可执行的

### P3 对应方案：subtask review 显式拦截 deferred capability 越权实现

做法：

1. subtask review 增加 package-boundary review：
   - 如果产物实现了当前子任务 `deferredCapabilities`
   - 或实现了其他子任务 `ownedCapabilities`
   - 必须驳回
2. 当前子任务 review 不再在 `SubtaskVerificationSupport` 中直接取平 `.result()`；必须先保留结构化 payload，再由 deterministic boundary gate 消费。
3. 结构化 boundary finding 的 owner 只在子任务 review payload 内，不直接扩散到全局 `ReviewResult` 主协议。
4. 对 skeleton 子任务的通过标准收紧为：
   - 页面壳体、样式、容器、最小 bootstrapping 可通过
   - 完整游戏状态机、输入处理、计分/消行、游戏结束逻辑属于越权实现，必须打回
5. smoke / syntax / wiring 仍保留，但不再能覆盖 capability 越权问题。

目标：

- 把“页面能打开”与“任务包边界正确”拆开
- skeleton 不再靠偷跑后续能力获得通过

### P4 对应方案：`CONTINUE_SUBTASKS` 必须落当前失败子任务的 canonical repair package

做法：

1. `CONTINUE_SUBTASKS` 不再只表示“还有子任务没完成”，还必须携带当前失败子任务的 repair package。
2. repair package 至少包含：
   - 当前子任务 canonical file contract
   - 最近一次 accepted/effective change-set
   - material mutations
   - tool failure diagnostics
   - 下一轮必须遵守的 patch-only boundary
3. 子任务级 `PATCH_RUNTIME_WIRING` 直接复用 `RuntimeWiringRetryChangeFactory` 生成 canonical change-set，不允许在 `SubtaskRuntimeWiringGuard` 再造第二套 builder。
4. repair package 的 canonical scope 硬上限只能来自当前 subtask 的 accepted/effective structured change-set。
5. material mutations、tool failure diagnostics 只能作为：
   - 当前 scope 内的失败证据
   - 当前 scope 内的修复优先级信号
   - 当前 scope 内的 resume 提示
   它们不能扩张 scope，更不能把本轮越界触达路径反向转正成下一轮 repair package 的合法范围。
6. 如果当前 subtask 无法从 accepted/effective structured change-set 合成安全的 canonical repair package，必须直接 `BLOCK_STAGE`，不能退化成只携带 unfinished subtasks 的半结构化 `CONTINUE_SUBTASKS`。
7. 第二轮 implementation 尝试直接从这份 repair package 恢复，而不是重新给同一子任务宽泛 execution package。
8. 该 package 的 owner 单一化：
   - stage roll-up 负责落盘
   - implementation resume 只消费，不得重新猜当前修复范围

目标：

- 把第二轮 implementation 从“泛化重试”改成“当前失败子任务的结构化续跑”
- 不再重复第一轮失败模式

### P5 对应方案：repair mode 切到 patch-first，禁止已有文件 fresh rewrite 和 shell 只读读取

做法：

1. 当前子任务进入 repair/resume 模式后，已有文件优先：
   - `Read`
   - 精确 `Edit`
2. repair/resume 模式必须进入 `ImplementationToolPermissionPolicy` 的单一 owner，不允许只靠 prompt prose 约束。
3. `ImplementationToolLoopExecutor` 必须把 repair/resume mode 显式传入 permission policy；不能继续只传 owned paths。
4. 不允许继续把已有文件当成 fresh `Write`。
5. 对已有文件的整文件 body replace 也视为 rewrite，即使调用形式是 `Edit` 而不是 `Write`；repair/resume 只能做 scope 内的局部 patch。
6. deny 决策必须保留 analyzer 已解析出的 `pathIntents`，diagnostics 尽量落到具体路径，而不是退回 `(tool-loop)` 级别的泛化证据。
7. shell 只读读取统一收敛到结构化文件工具，不再依赖命令字符串 heuristics 去猜用户意图。
8. `UNSUPPORTED_SHELL_COMMAND` 一旦出现，续跑提示必须直接把等价动作转成结构化文件工具，不再继续消耗回合试 shell。
9. patch-first 约束只在 repair/resume 生效，不影响真正的新文件创建场景。

目标：

- repair mode 的 tool loop 收敛到局部修补
- 避免回合继续浪费在重写和无效 shell 读上

## 回归验证矩阵

### R1. skeleton capability boundary

验证点：

- skeleton 子任务只允许交付页面壳体、样式、容器、最小 bootstrapping。
- skeleton 子任务不得提前实现后续 gameplay capability。
- 如果 skeleton 产物实现了当前子任务 `deferredCapabilities`，或实现了其他子任务 `ownedCapabilities`，subtask review 必须显式驳回。

期望结果：

- skeleton capability boundary 被 review 和回归测试双重锁死。
- “页面能打开”不再掩盖 capability 越权实现。

### R2. runtime split package completeness

验证点：

- accepted package 一旦引入新的 runtime root 文件，必须同时携带宿主入口 patch。
- package completeness gate 只允许使用以下事实来源：
  - explicit host contract
  - 当前 accepted scope
  - 当前 HTML 已观察到的 structured wiring facts
- 明确禁止目录扫描、root-script 猜测、companion 文件名 fallback 等猜测式来源。
- coder 不得在执行阶段临时越出 accepted package 去补宿主接线。

期望结果：

- runtime split package 在进入执行前就是完整可运行的。
- host-entry 接线缺失在 package 层即被拦下，而不是拖到 implementation 末尾。

### R3. CONTINUE_SUBTASKS repair package scope clamp

验证点：

- `CONTINUE_SUBTASKS` 必须携带当前失败子任务的 canonical repair package。
- 子任务级 `PATCH_RUNTIME_WIRING` 必须复用 `RuntimeWiringRetryChangeFactory`，不能再单独拼 package。
- repair package 的 canonical scope 上限只能来自当前 subtask 的 accepted/effective structured change-set。
- material mutations、tool failure diagnostics 只能作为当前 scope 内的证据、优先级与 resume 提示，不能扩张 scope。
- 如果无法从 accepted/effective structured change-set 合成安全 repair package，必须 `BLOCK_STAGE`，不能退化成半结构化 `CONTINUE_SUBTASKS`。
- 下一轮 implementation 必须直接消费该 repair package，而不是重新放宽 execution package。

期望结果：

- continuation repair 保持 patch-only、scope-clamped。
- 本轮越界路径不会被反向转正成下一轮合法修复范围。

### R4. shell deny pathIntents diagnostics

验证点：

- shell deny 决策必须保留 analyzer 已解析出的 `pathIntents`。
- diagnostics 应优先落到结构化具体路径，而不是退回 `(tool-loop)` 级别泛化证据。
- 不新增命令字符串 heuristics；路径信息只能来自 analyzer 已成功解析的结构化 intent。
- repair/resume 下对已有文件的整文件替换同样视为 rewrite，不因使用 `Edit` 形式而放行。
- 对无法解析路径的拒绝场景，空路径仍然是合法结果，但不能伪造目标文件。

期望结果：

- shell deny 失败证据可稳定指向具体文件。
- 排障和后续 repair package 聚合继续基于结构化路径，而不是基于命令文本猜意图。

### R5. tool-level full Read / stale Read invariants

验证点：

- 未 full Read 就对已有文件执行 `Edit` / `Write` 必须拒绝。
- 文件在 read 之后发生漂移，继续 `Edit` / `Write` 必须拒绝。
- shell 写已有文件也必须经过同样的 fresh-read 校验，而不是绕过工具级不变量。
- repair/resume 的 patch-first 收紧不得把现有 full Read / stale Read 工具防线打松。

期望结果：

- patch-first 约束停留在工具级不变量，而不是 prompt 建议。
- `Edit / Write / Bash` 对已有文件保持同一套 fresh-read 防线。

### R6. typed payload round-trip

验证点：

- subtask review 的 typed payload 必须经过 `LlmProvider -> StructuredReviewResult -> verification gate` 后仍可被 deterministic boundary gate 消费。
- canonical repair package 必须经过 `ImplementationStageStatus -> ImplementationStateSnapshotSerializer -> implementation_state.json -> ImplementationStateArtifactSupport -> ImplementationContinuationSupport` 后仍保持结构化。
- 中途不得 flatten 回 prose，也不得退回 support 层临时拼装。

期望结果：

- subtask structured review 和 continuation payload 都真正穿过真实协议边界。
- typed payload round-trip 可被单测与集成回归同时锁死。

### R7. run-state consistency

验证点：

- `TEST` 或 `CODE_REVIEW` 产物为 `REJECTED` 且 supervisor 动作为 `ROUTE_TO_REPAIR` 时，`run.json` 不得再被写成 `COMPLETED + APPROVED`。
- `FlowDecisionExecutor`、`StageTransitionSupport`、`StageRevisionSupport`、`StageRevisionRepairSupport`、`StageProgressArtifactSupport`、`StageEntryExecutor`、`StageStatusSupport` 必须对同一份 repair 决策达成一致。
- reroute event、revision note、repair brief 与最终 stage re-entry 必须来自同一条 repair reroute 链，不允许一部分已进入 repair、一部分仍落成 approved/completed。
- `events.log`、阶段 artifact、`run.json` 三者必须指向同一最终状态。

期望结果：

- repair route 的结构化决策不会在状态落盘链上丢失或被覆写。
- run/stage 最终状态重新成为可信真相源。
