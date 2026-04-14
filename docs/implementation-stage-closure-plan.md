# IMPLEMENTATION v171 收口方案

## Purpose

这份文档只处理 `2026-04-14` 黄金路径集成测试 `tetris_test_itest_v171` 暴露出的**当前真实未闭合问题**。

约束来源：

- 根目录 `AGENTS.md`
- `docs/engineering-agreements.md`
- `docs/current-state.md`
- `docs/active-work-items.md`

本方案不是重开旧整改，也不是补过渡层。它只收当前这次集成失败真正暴露出的协议冲突与执行不收敛问题。

## Latest Evidence

本轮失败证据来自：

- `/home/linus/workspace/tetris_test_itest_v171.itest.log`
- `/home/linus/workspace/tetris_test_itest_v171/.devflow/runs/59876204-72fb-49ec-aa5e-fd17ca6f5a6f/events.log`
- `/home/linus/workspace/tetris_test_itest_v171/.devflow/runs/59876204-72fb-49ec-aa5e-fd17ca6f5a6f/implementation_progress.md`
- `/home/linus/workspace/tetris_test_itest_v171/.devflow/runs/59876204-72fb-49ec-aa5e-fd17ca6f5a6f/implementation_stage_status.md`
- `/home/linus/workspace/tetris_test_itest_v171/.devflow/runs/59876204-72fb-49ec-aa5e-fd17ca6f5a6f/implementation_diagnostics.md`
- `/home/linus/workspace/tetris_test_itest_v171/.devflow/runs/59876204-72fb-49ec-aa5e-fd17ca6f5a6f/code_review_feedback.md`
- `/home/linus/workspace/tetris_test_itest_v171/.devflow/runs/59876204-72fb-49ec-aa5e-fd17ca6f5a6f/transition_decision.md`

本轮已证实的事实：

1. `ANALYSIS / PRD / DESIGN` 的输出预算正常，不存在“小 cap 把代码截断”的主因。
2. 当前失败也不是老的“外提脚本未接线”问题。最终产物里 `index.html` 已接入 `src/game.js`。
3. 当前失败发生在 `IMPLEMENTATION -> CODE_REVIEW -> IMPLEMENTATION` 的续跑主链。
4. 当前失败不是单点语法错误，而是**repair scope 丢失 + continuation 语义冲突 + tool loop 不收敛**叠加造成。

## 已排除项（不要重做）

以下项不是当前主根因，本轮不得重开：

1. `implementation_state.json` 单一真相源
2. prose-only success 判定收紧
3. planning detail 回填 runtime metadata
4. runtime wiring orphan script 基础修复
5. token 预算整体过低

这些项已经在代码和状态文档里收口。本轮如果再围绕这些点重做，只会把问题面重新扩散。

## Current Problems

### P1. 子任务失败后，阶段汇总拿到了 patch 目标，但没有拿到结构化 repair scope

当前证据：

- `implementation_progress.md` 显示：
  - `continuationMode=BLOCK_STAGE`
  - `continuationPatchTarget=PATCH_EXISTING_IMPLEMENTATION`
  - `continuationOverrideChanges=[]`
- 同时失败子任务是明确的：`实现行消除与计分系统`
- 当前子任务的有效文件边界并不模糊，至少已经包含 `src/game.js`，并且产物里也确实发生了对应修改

这说明当前问题不是“没有 patch 目标”，而是：

- 系统已经知道这是 `PATCH_EXISTING_IMPLEMENTATION`
- 但没有把当前失败子任务的结构化文件范围稳定带出来
- 后续链路只能看到“需要 patch”，却拿不到“补哪里”

这会直接导致后续续跑语义漂移。

### P2. CODE_REVIEW 打回后的续跑语义，与 implementation continuation 语义冲突

当前证据：

- `CODE_REVIEW` 给出 `REVISION_REQUIRED`
- `fixMode=REWORK`
- `implementationPatchTarget=NONE`
- `overrideChanges=[]`
- supervisor 决策为 `RETRY_STAGE`
- 回流后重新进入 `IMPLEMENTATION outline`

但同一轮 implementation 自己的状态又明确写着：

- 当前实现还没有完成
- 当前需要 patch
- 只是没有结构化 scope，所以自动续跑被阻断

这两个语义放在一起是冲突的：

- implementation 侧说“当前 stage 还在 patch continuation 语义里”
- code review / supervisor 侧却把它重新抬成“整阶段 REWORK 重开 outline”

最终结果就是系统从一个边界清晰的 repair 问题，被抬回成了一个重新规划问题。

### P3. fresh outline 规则与 host-entry rewrite guard 在 continuation repair 上互相打架

当前证据：

- `events.log` 连续 3 次驳回 outline，核心原因一致：
  - `Continuation 计划不能对现有 HTML 入口使用 REWORK/整页重写: index.html`
- 同时驳回原因还要求：
  - runnable milestone 必须直接覆盖 HTML 入口

这说明当前有两套本来各自合理、但不该在同一条 continuation repair 链路里同时生效的规则：

1. fresh outline 规则：
   - 可运行里程碑必须直接覆盖 HTML 入口
2. continuation repair 规则：
   - 对现有 host entry 不能走整页 REWORK / 重写

当系统错误地把当前 bounded repair 抬回 fresh outline 后，这两条规则就发生正面冲突，最后 planner 被卡死。

### P4. implementation tool loop 在 bounded repair 场景下不收敛

当前证据：

- 第 3 个子任务最终以 `tool loop exceeded max turns without a terminal assistant response` 结束
- 中间出现了多次无关或非法动作：
  - `python3 -m http.server 8000`
  - `head -20 ...`
  - `echo ... > STATUS.txt`
- `implementation_diagnostics.md` 已经记录了结构化失败证据，但执行主链仍然没有在当前 scope 内收敛成：
  - 继续 patch
  - 或明确失败并保留 scope

这说明问题不只是“模型偶发乱调用工具”，而是：

- bounded repair 子任务在接近回合上限时
- 缺少一个稳定的收口出口
- 系统允许它消耗完 turns，却没有把当前 material changes、失败证据、当前文件范围合成一个可继续的 repair package

## Final State

完成态必须同时满足：

1. 子任务在 tool loop 内失败时，如果已经存在可判定的当前 repair scope，则阶段汇总必须输出结构化 `overrideChanges`，不能再出现“`PATCH_EXISTING_IMPLEMENTATION` 但 scope 为空”的半结构化状态。
2. `CODE_REVIEW` 打回当前 implementation 时，若当前 run 已存在未完成 subtask 或 stage-level patch scope，则只能沿当前 continuation 语义回流，不允许重新抬成 fresh outline `REWORK`。
3. fresh outline 的“覆盖 HTML 入口”规则，只能用于真正的新一轮 implementation planning；continuation repair 不再复用这套规则。
4. host-entry rewrite guard 继续保留，不放松。
5. tool loop 子任务在 bounded repair 场景下，必须在回合耗尽前收敛成以下二选一：
   - 成功完成并提交终态
   - 失败，但留下可直接续跑的结构化 repair package
6. `overrideChanges` 的 canonical scope 只能来自当前 subtask 的 accepted/effective structured change-set；material mutations、tool failures、diagnostics 只能作为该 scope 内的证据与优先级信息，不能扩张 scope，更不能把越界路径合法化进后续 repair。
7. “continuation repair 是否升级回 fresh outline”的流程判定 owner 只能有一个：`FlowController`。`StageProgressCoordinator` 只负责提供结构化输入，`FlowDecisionExecutor` 只负责执行既定动作，不得再次重解释。
8. 本轮不引入兼容层、fallback、heuristic patch，也不新增第二套 repair scope 推导体系或第二个流程判定 owner。

## Removal Plan

本轮必须删除或封死以下错误路径：

1. `PATCH_EXISTING_IMPLEMENTATION` 与空 `overrideChanges` 并存后仍继续流入后续阶段决策的路径。
2. `CODE_REVIEW` 在当前 implementation 已存在 continuation 语义时，仍然回流为 fresh outline `REWORK` 的路径。
3. fresh outline 的入口覆盖规则在 continuation repair 中误生效的路径。
4. tool loop 在 bounded repair 子任务中，允许“有 material edits + 有结构化失败证据 + 无 terminal 收口结果”直接耗尽的路径。
5. 任何用越界 material mutation、越界 tool failure 路径去扩张 `overrideChanges` 的路径。
6. review / supervisor / planner 各自重新解释“是否需要 fresh outline”的多 owner 判定路径。

## Joint-Change Scope

这轮如果进入实现，必须一起改以下联动面，否则一定会留下半成品：

### 1. repair scope 产出链

- subtask 失败汇总
- implementation stage roll-up
- continuation stage status / progress 产物
- patch continuation scope builder

### 2. stage 回流语义链

- code review 结果消费
- supervisor / transition 决策
- implementation retry 入口
- outline planner 选择逻辑

### 3. planner / guard 边界

- fresh outline 契约校验
- continuation repair 包构建
- host-entry rewrite guard 的生效范围

### 4. tool loop 收口链

- subtask attempt terminalization
- tool failure 证据汇总
- material mutation ledger
- bounded repair 失败时的 repair package 合成

## Closure Decision

可以一次性收口，但前提是本轮只做**同一问题族**：

- repair scope 丢失
- continuation 语义冲突
- bounded repair tool loop 不收敛

如果把范围再扩到 token 预算、runtime ownership 基础协议、planning detail 结构等已收口项，就会再次偏离主线。

## Problem-to-Solution Mapping

### P1 对应方案：repair scope 从失败子任务直接物化，不再等后置链路猜

做法：

1. 当子任务失败时，若当前 subtask 已有确定的 `effectiveChanges` 或等价结构化文件范围，则直接以该范围生成 stage-level continuation `overrideChanges`。
2. 该物化动作只使用当前 run 内已有结构化信息：
   - 当前 subtask 文件契约
   - 当前 accepted/effective change-set
   - 当前 attempt material mutations
3. `overrideChanges` 的硬上限固定为当前 subtask 的 accepted/effective structured change-set。
4. material mutations、tool failures、diagnostics 只能用于：
   - 证明该 scope 内哪些文件确实被触达
   - 标记该 scope 内的修复优先级
   - 解释当前失败原因
   它们不能扩张 scope，也不能把越界路径、非法写入目标、被拒绝工具目标转正成 continuation scope。
5. 不允许用 prose 猜路径，也不允许退回文件名 heuristics。
6. 如果当前 run 内确实无法推出安全 scope，则保持 `BLOCK_STAGE`，但不得再生成“有 patch target、无 patch scope、还可继续自动回流”的混合状态。

目标：

- repair scope 的 owner 单一化
- stage status 与 continuation scope 保持一致

### P2 对应方案：review 打回时优先续跑当前 implementation，而不是重开 outline

做法：

1. `FlowController` 是“continuation repair 是否升级回 fresh outline”的唯一判定 owner。
2. `StageProgressCoordinator` 只负责把结构化输入送进 `FlowController`，至少包括：
   - 当前 implementation 是否仍有未完成 subtask
   - 当前 stage 是否已有 continuation package
   - 当前 stage 是否已有结构化 patch scope
   - review / supervisor 给出的修订模式与目标阶段
3. `FlowDecisionExecutor` 只执行 `FlowController` 已给出的动作，不得重新解释 continuation 还是 replan。
4. 当 `CODE_REVIEW` 面向的是一个尚未完成、且已有 continuation patch 语义的 implementation run 时，回流必须优先复用当前 run 的 continuation package。
5. 只有以下条件同时成立时，`FlowController` 才允许把 continuation repair 升级回 fresh outline：
   - 当前 implementation 没有未完成 subtask
   - 当前 stage 不存在可继续消费的 continuation package
   - 当前 stage 不存在结构化 patch scope
   - review / supervisor 的结构化结论表明当前问题需要 stage replan，而不是局部修复
6. `REWORK` 保持合法，但它不再默认等价于“重新规划 outline”。对于当前 implementation run，`REWORK` 也可以落到 continuation repair。

目标：

- 把“review 打回”重新收敛到当前 run 的 repair 语义里
- 避免 bounded repair 被升级成 stage replan

### P3 对应方案：把 fresh outline 与 continuation repair 明确拆成两套入口

做法：

1. fresh outline 继续使用现有“可运行里程碑必须覆盖入口/运行表面”的规则。
2. continuation repair 改为消费结构化 repair package，不再重新生成 fresh outline。
3. host-entry rewrite guard 只负责约束 continuation repair 不得做整页重写；它不再与 fresh outline 规则在同一调用链里互相打架。
4. planner 选择逻辑必须先判断当前是：
   - fresh implementation planning
   - 还是 continuation repair resume

目标：

- 两套规则各守各的边界
- 不再出现“既要求直接覆盖 HTML 入口，又禁止对现有 HTML 入口做 REWORK”的自冲突

### P4 对应方案：给 bounded repair tool loop 增加确定性的失败收口

做法：

1. 当前子任务存在 material edits 或结构化失败证据时，tool loop 不能只以“无 terminal assistant response”裸失败结束。
2. 在接近回合上限或触发明显不可继续条件时，执行器必须产出结构化终态：
   - 要么成功完成
   - 要么失败并输出当前 repair package
3. repair package 只允许复用现有结构化 owner：
   - 当前 subtask 文件契约
   - material mutation ledger
   - `ToolFailureCode`
   - 当前 accepted/effective change-set
4. 不新增“临时自动修复层”或“猜意图补丁层”。

目标：

- bounded repair 子任务失败后仍可直接续跑
- 不再把 turns 耗尽变成结构化信息丢失

## Risks / Blockers

### 风险 1

如果当前实现里 repair scope 的 owner 还不单一，本轮改动时容易再次出现：

- stage status 一套 scope
- review 一套 scope
- resume 一套 scope

这会直接违反本轮收口目标。

### 风险 2

如果只改 stage roll-up，不改 review / supervisor / planner 选择逻辑，那么 repair scope 即使补出来，仍可能被后续阶段重新抬成 fresh outline，问题会原样复发。

### 风险 3

如果只改 tool loop 的终态判定，不把失败产物接到 continuation package 上，当前问题会从“卡死在 outline”变成“卡死在人工阻断”，仍然不算收口。

## Implementation

### Phase 1. 锁定 scope owner

目标：

- 把子任务失败后的 repair scope 单点物化

实施：

1. 明确当前 run 中 repair scope 的 canonical owner。
2. 子任务失败后由该 owner 直接生成 stage-level continuation scope。
3. 封死“patch target 已知但 scope 为空仍继续自动回流”的路径。

### Phase 2. 修正 review -> implementation 回流语义

目标：

- review 打回后优先沿当前 continuation 语义续跑

实施：

1. 调整 code review 结果消费与 supervisor/transition 决策。
2. 有 continuation package 时直接回到 continuation repair。
3. 无 continuation package 且确需重规划时，才进入 fresh outline。

### Phase 3. 拆开 fresh outline 与 continuation repair

目标：

- 让入口覆盖规则与 host-entry rewrite guard 不再同链冲突

实施：

1. 把 planner 入口分成 fresh planning 与 continuation repair 两类。
2. continuation repair 直接消费结构化 repair package。
3. fresh outline 校验不再对 continuation repair 生效。
4. planner 不再自行决定是否从 continuation 升级到 fresh outline；它只消费 `FlowController` 已确定的入口类型。

### Phase 4. 收口 bounded repair tool loop

目标：

- turns 耗尽时仍能留下可继续的结构化状态

实施：

1. 在 subtask attempt terminalization 处补确定性失败收口。
2. 把 material mutations 与 tool failures 合成 continuation repair package 的证据层，但不得突破 accepted/effective structured change-set 的 scope 上限。
3. 不允许再以“无 terminal response”裸失败丢失当前 scope。

## Test Plan

### 1. repair scope 物化

- 子任务失败但已有 material edits 时，`implementation_stage_status.md` 必须带结构化 `overrideChanges`
- 不再出现 `PATCH_EXISTING_IMPLEMENTATION + overrideChanges=[]` 的自动续跑语义

### 2. review 回流语义

- code review 打回未完成 implementation 时，优先走 continuation repair
- 不再直接回到 fresh outline
- `FlowController` 作为唯一 owner 做出是否升级为 fresh outline 的判定
- `StageProgressCoordinator` 与 `FlowDecisionExecutor` 不再各自重解释

### 3. planner / guard 边界

- fresh outline 继续要求 runnable milestone 覆盖入口
- continuation repair 不再触发该校验
- host-entry rewrite guard 仍然有效

### 4. tool loop 收口

- bounded repair 子任务在 turn budget 内若无法完成，必须输出结构化 repair package
- 非法 shell / scope violation 等证据会进入 repair package，而不是只留日志
- repair package 的 `overrideChanges` 不得包含越界路径；越界路径只能作为失败证据，不能转正进 scope

### 5. 黄金路径回归

- 重新跑网页版俄罗斯方块 case
- 验证 `IMPLEMENTATION -> CODE_REVIEW -> IMPLEMENTATION` 不再因续跑语义冲突而卡死

## Completion Gate Result

当前结果：`方案完成，可进入实现；代码尚未开始`

本轮成功标准：

1. 不重开已完成整改
2. 不新增 fallback / shim / heuristic patch
3. repair scope、review 回流、planner 入口、tool loop 收口四条链一次性一起收住
4. 重新跑黄金路径集成测试，失败若仍存在，必须是新问题，而不是这四条链上的原问题复发
