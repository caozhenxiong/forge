# IMPLEMENTATION 阶段收口方案

## Purpose

本方案用于收口当前 `IMPLEMENTATION -> CODE_REVIEW -> CONTINUATION` 主链中的一类问题，目标不是修单个案例，而是删除这条链上的错误抽象和错误流转。

本方案严格遵守：

- 根目录 `AGENTS.md`
- `docs/engineering-agreements.md`

约束重点：

- 不引入兼容层、适配层、shim、fallback、临时 cap、heuristic patch
- 不保留新旧双轨并存
- 不允许 prose 猜意图驱动流程
- 不允许“先跑起来、后清理”
- 只有同类问题整体收口，才算完成

## Summary

当前集成失败不是单点 bug，而是同一问题族的四段断裂：

1. `IMPLEMENTATION` 阶段的 machine truth 不单一，`stageReady=false` 仍可能流入后续阶段。
2. HTML 入口与 companion runtime 的所有权没有在批准前绑定进结构化契约，导致实现先放过、review 再打回。
3. implementation review 会产出不可续跑的半结构化结果，例如 `REWORK + implementationPatchTarget=NONE`。
4. tool loop 的完成语义过宽，assistant prose 或 shell 逃逸失败没有被当成确定性失败处理。

这四条必须一次联动收口；拆开修会继续出现“前面放行、后面打回、planner 死锁”的半成品状态。

## Current Root Causes

### 1. 阶段真相源不单一

- `implementation_state.json` 本应是 `IMPLEMENTATION` 唯一 machine truth。
- 实际链路里仍存在“结构化状态未完成，但阶段继续推进”的泄漏。
- 结果是 `planCompleted=false`、`stageReady=false` 时，系统仍可能进入 `CODE_REVIEW`。

### 2. 运行时所有权绑定过晚

- HTML 入口文件进入子任务 scope 时，没有强制绑定 `runtimeOwnership`。
- `ImplementationMutationContractGuard` 只能在字段存在时校验，导致 `runtimeOwnership=null` 仍可能通过前置链路。
- review 才发现“外提脚本未接线”或“内联与外提双轨并存”，此时修复语义已经被拖晚。

### 3. review 到 continuation 的协议不闭合

- implementation review 对可修复问题仍可能输出 `REVISION_REQUIRED + REWORK + implementationPatchTarget=NONE`。
- 这类结果不能指导 continuation 做 patch 级修复。
- planner 被迫在受保护入口文件上重新规划，最后死在 `index.html` 整页 `REWORK` 限制。

### 4. tool loop 的 terminal 条件不严

- assistant 返回 prose，但声明变更未满足时，链路仍可能被误判为“可以结束”。
- unsupported shell 写文件或逃逸尝试，没有被及时转成结构化失败证据。
- 结果是 attempt 结束原因和下一步修复意图都不清晰。

## Final State

完成态必须同时满足：

1. `IMPLEMENTATION` 是否允许出阶段，只由 `implementation_state.json` 的结构化状态决定。
2. `implementation.md` 只做展示，不再参与任何 machine flow。
3. 只要 `planCompleted=false` 或 `stageReady=false`，系统只能：
   - 进入结构化 continuation
   - 或进入 block / human review
   不能进入正常 `CODE_REVIEW`。
4. 入口 HTML 文件一旦进入批准后的 change scope，必须带完整 runtime contract：
   - `runtimeOwnership`
   - companion script 路径
   - runtime wiring 约束
   - inline script 约束
5. implementation review 对可修复问题只能产出结构化 patch continuation，不允许再出现 `REWORK + NONE`。
6. continuation planner 只能生成 patch 级修复请求；如果修入口文件，也必须是受控 patch scope，不能整页 `REWORK`。
7. tool loop 只有在声明变更满足、结构校验通过、契约校验通过时才能结束。
8. assistant prose-only 响应、unsupported shell 写文件、空 ownership 等情况，必须落成结构化失败原因。

## Removal Plan

本轮必须删除以下错误路径，不能保留为“暂时兼容”：

1. 删除任何依赖 `implementation.md` 或 review prose 来判断 implementation 是否完成的主链逻辑。
2. 删除 implementation review 中 `REWORK + implementationPatchTarget=NONE` 仍被视为合法结果的路径。
3. 删除入口 HTML 以 `runtimeOwnership=null` 进入已批准 change scope 的路径。
4. 删除 continuation 对现有 HTML 入口走整页 `REWORK` 的退路。
5. 删除 tool loop 中“assistant 返回 prose 即可视为完成”的宽松终止逻辑。
6. 删除 unsupported shell 写文件失败后仍继续主链推进的宽松行为。

## Joint-Change Scope

本轮必须一起改，不允许只改其中一半：

### 1. 阶段流转真相源

- `ImplementationStateArtifactSupport`
- `ImplementationProgressSupport`
- `ImplementationStageGate`
- `StageProgressCoordinator`

### 2. 运行时所有权前移绑定

- 生成和批准 `FileChange` 的实现链路
- `ImplementationMutationContractGuard`
- `SubtaskVerificationSupport`
- `HtmlEntryRuntimeOwnershipInspector`

### 3. review 与 continuation 契约

- implementation review prompt
- implementation review structured result / normalizer
- continuation constraint builder
- implementation planner change gate

### 4. tool loop 终止与失败证据

- `ImplementationToolLoopExecutor`
- subtask attempt state / terminality 判定
- unsupported shell/tool 写入失败事件落盘

### 5. 文档与测试

- implementation 相关单测
- 至少一条当前黄金路径集成测试
- `docs/current-state.md`
- 本轮 tracker

## Public Interfaces And Type Changes

本轮需要显式调整的公开结构如下：

### 1. Implementation 阶段进度结果

将 `IMPLEMENTATION` 对 orchestrator 暴露的状态统一为明确三态：

- `READY`
- `CONTINUE`
- `BLOCKED`

上层只消费该结构化结果，不允许再自行拼条件。

### 2. Approved change scope

入口 HTML 变更必须携带完整 runtime contract。最少包含：

- `runtimeOwnership`
- runtime script 路径或缺省为无 companion
- 是否允许 inline host
- wiring 约束

入口 HTML 若缺失这些字段，直接视为非法 scope，而不是交给后置 gate 猜测。

### 3. Implementation review 结构化结果

review 结果必须显式给出：

- repair action
- patch target
- 是否存在 override changes
- fix mode

并禁止非法组合继续流入 planner。

### 4. Attempt failure reason

subtask / tool loop 失败原因要落为结构化 reason code，至少覆盖：

- 声明变更未满足
- runtime ownership 缺失
- runtime wiring 不一致
- unsupported shell write
- terminal response 非法

## Implementation

### Phase 1. 锁死 IMPLEMENTATION 唯一真相源

目标：让 `IMPLEMENTATION` 是否完成只由结构化状态决定。

实施：

1. 收口 `ImplementationProgressSupport` 输出，统一为 `READY / CONTINUE / BLOCKED`。
2. `StageProgressCoordinator` 只消费上述结果，不再从 markdown、review prose 或空结果推断下一步。
3. `ImplementationStageGate` 只负责从 snapshot 计算三态与 continuation payload，不再让上层重复组合条件。
4. 如果 `planCompleted=false` 或 `stageReady=false`，则禁止进入正常 `CODE_REVIEW`。

完成标志：

- 不存在 `stageReady=false` 却仍流入 `CODE_REVIEW` 的路径。

### Phase 2. 把运行时所有权前移成批准前契约

目标：让 runtime ownership 在子任务批准前就成为结构化事实，而不是 review 后置发现。

实施：

1. 入口 HTML 进入 approved change scope 时，强制要求 runtime contract 完整。
2. `ImplementationMutationContractGuard` 改为：
   - 入口 HTML 在 scope 中但缺少 runtime contract，直接失败
   - 不再接受 `runtimeOwnership=null` 的宽松路径
3. `SubtaskVerificationSupport` 在 review 前做 deterministic runtime wiring check。
4. `HtmlEntryRuntimeOwnershipInspector` 与 mutation / verification 共享同一 contract 结构，不再各自猜测。

完成标志：

- 不存在 `runtimeOwnership=null` 的入口 HTML 已批准变更。
- 外提脚本未接线、内联与外提双轨并存，在 review 前就能被确定性挡下。

### Phase 3. 收口 review -> continuation 协议

目标：review 对 implementation 的打回必须可直接驱动 patch continuation。

实施：

1. 收紧 implementation review 的结构化 schema，禁止可修复问题输出 `REWORK + NONE`。
2. `ImplementationReviewNormalizer` 对非法组合直接判为协议错误，不把脏结果继续喂给 planner。
3. continuation builder 只接受结构化 patch request。
4. 如需修入口 HTML，也必须以受控 patch target 表达，不允许退化为整页 `REWORK`。

完成标志：

- 对当前这类 runtime wiring/实现缺口问题，review 只能给出 patch continuation。
- continuation planner 不再因为 `index.html` 整页 `REWORK` 被拒。

### Phase 4. 收口 tool loop 完成语义

目标：子任务 attempt 的结束必须建立在确定性条件上。

实施：

1. `ImplementationToolLoopExecutor` 的 terminal 条件改为：
   - 声明变更满足
   - 结构校验通过
   - 契约校验通过
2. assistant 无 tool call 但仍未满足声明变更时，直接判定 attempt failure。
3. unsupported shell 写文件或同类逃逸尝试，落结构化事件和失败 reason。
4. failure evidence 进入 repair/review 可见面，避免再次靠 prose 反推问题。

完成标志：

- assistant prose-only 响应不会再被误判为完成。
- shell 逃逸失败不会静默滑过。

### Phase 5. 文档、tracker、守门

目标：确保本轮收口结果可验证、可复盘。

实施：

1. 更新 `docs/current-state.md`，写明：
   - implementation 唯一真相源
   - runtime ownership contract
   - review continuation 协议
2. 新增或更新本轮 tracker，按 phase 记录：
   - commit
   - self-test
   - code review
   - docs
3. 结束前再做一次 code review，专项复扫：
   - `REWORK + NONE`
   - `runtimeOwnership=null`
   - markdown/prose 驱动流转
   - HTML 整页 `REWORK`

## Test Plan

本轮必须覆盖以下测试：

### 1. 阶段真相源

- `implementation_state.json` 显示 `stageReady=false` 时，绝不进入 `CODE_REVIEW`
- markdown 再“像完成”，也不能影响 machine flow

### 2. 运行时所有权

- 入口 HTML 进入 scope 但 `runtimeOwnership` 缺失时，直接失败
- `EXTERNAL_COMPANION` 下存在外提脚本但未接线，verification 失败
- `INLINE_HOST` 下继续保留 companion runtime，verification 失败

### 3. review 协议

- implementation 可修复问题不能输出 `REWORK + implementationPatchTarget=NONE`
- illegal review combination 会被 normalizer 拦截

### 4. continuation

- runtime wiring 修复走 patch continuation，而不是整页重写
- continuation 仅修当前 scope，不重开整轮 planning

### 5. tool loop

- assistant prose-only 且声明变更未满足时，attempt 明确失败
- unsupported shell 写文件会记录结构化错误证据

### 6. 集成

- 重跑当前黄金路径，验证：
  - implementation 不再错误流入 code review
  - companion runtime 接线正确
  - 最终产物可运行
  - 日志中失败原因和 repair 信号结构化可读

## Assumptions

1. 本轮只收 implementation 主线，不顺手扩 unrelated review-v5 主题。
2. runtime ownership 规则用通用 contract 表达，不引入任何“俄罗斯方块/单 HTML/单案例”规则。
3. review 与 continuation 继续走结构化协议，不回退到 prose 猜意图。
4. 黄金路径仍以当前网页产出链验证，但设计必须对同类网页产出普适。

## Closure Decision

本轮可以一次性收口，且必须一次性收口。

原因：

- 四个根因属于同一失败族。
- 只修其中一段，会继续出现新的半成品路径。
- 当前问题已经证明“先局部跑起来、后面再补”不可接受。

如果执行过程中发现任一 phase 不能按最终态直接落地，应停止实现，回到方案层；不得提交半成品主链。

## Completion Gate Result

只有同时满足以下条件，本轮才算成功：

1. 不存在 `stageReady=false` 仍进入 `CODE_REVIEW` 的路径。
2. 不存在入口 HTML 以 `runtimeOwnership=null` 进入已批准 scope。
3. 不存在 implementation review 输出 `REWORK + implementationPatchTarget=NONE` 的路径。
4. 不存在 continuation 通过整页 `REWORK index.html` 修复当前问题的退路。
5. 不存在 assistant prose-only 响应在声明变更未满足时被当成完成。
6. 不存在 unsupported shell 写文件失败后仍继续主链推进的宽松行为。
7. 已完成 `self-test + code review + 集成测试 + 文档更新`。

未同时满足以上条件时，本轮一律视为未完成。
