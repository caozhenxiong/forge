# Review V9: Subtask Repair Closure Plan

## Summary

- 当前不先继续盲跑集成测试。
- `v176` 的主阻塞已经不是 tool-loop 收尾，而是 `REVISION_REQUIRED -> repair` 这条链没有收敛成最小 patch scope。
- 第一子任务被驳回本身是合理的；真正的问题是驳回之后，系统仍把 repair 当成整子任务重做，导致反复 whole-file rewrite、内联回退和能力越界继续扩大。
- 本轮只收口子任务级 `review / verification -> revision directive -> retry feedback -> next attempt scope` 这条主链，不顺手扩到 planning 或 stage gate。

## Current Evidence

### v176 真实现象

- 第一子任务 `创建基础HTML结构与Canvas渲染界面` 连续两次 `REVISION_REQUIRED`。
- `tool-loop` 在第 12 轮已经能以 `declared-changes-satisfied` 正常结束，不再是上一轮的 completion bug。
- 当前产物中：
  - `index.html` 已正确接入 `src/app.js`
  - `src/app.js` 却已经提前实现了移动、旋转、碰撞、消行、得分、结束判定等后续能力
- 这说明：
  - review 驳回方向是对的
  - 失稳发生在驳回之后的 repair 续跑，而不是发生在最初编码或最终 completion

### 当前代码链上的根因

1. `SubtaskBoundaryGate` 只能表达“越界了”，但不能产出结构化最小修复范围。
2. `SubtaskVerificationSupport.normalizeScopedPatchReview()` 在 patch review 没有 override scope 时，会把范围自动扩回当前 `subtask.changes()`。
3. `SubtaskRunnableMilestoneGuard` 这类 producer 也仍会把 revision directive 回退成整子任务 change-set。
4. `SubtaskRetryFeedbackRenderer` 没把结构化 patch scope 写回 retry payload，导致 repair 文案里只有抽象 prose，没有执行边界。
5. `SubtaskExecutionState.applyRevisionDirective()` 只是被动吃上游结果，因此一旦上游给的是整包，下一轮 tool-loop 就继续重做整子任务。

## Target State

- 子任务级 `REVISION_REQUIRED` 只有两种自动续跑入口：
  - 已有 canonical runtime repair package 的 `PATCH_RUNTIME_WIRING`
  - 已有结构化最小文件范围的普通 `PATCH_EXISTING_IMPLEMENTATION`
- 任何缺少 canonical repair package 的 patch review 一律转 `REQUEST_HUMAN`，不能再自动扩回整子任务范围。
- boundary violation 的自动 repair 只允许作用于明确命中的 offending files，且这些文件必须属于当前 effective change-set。
- retry payload、execution state、next attempt scope 三者使用同一份 canonical repair package，不再各自拼装或重新放大范围。

## Implementation Changes

### 1. 把 boundary finding 补成 typed repair signal

- 扩展 `SubtaskBoundaryReviewPayload`：
  - 新增 `offendingPaths`
  - 只允许填写当前子任务相对路径
- `SubtaskReviewPromptAssembler` 补充 reviewer 约束：
  - 只有在有明确代码证据时才填写 `offendingPaths`
  - 只能填写当前子任务结构化 change-set 内文件
  - 不允许凭 prose 猜路径
- `StructuredReviewResult` 继续承载 `subtaskBoundary`，不新起平行协议。

### 2. 建立单一 owner，产出 canonical repair package

- 新增本地 resolver，作为唯一 owner，把以下输入收敛成 canonical repair package：
  - `ReviewResult`
  - `StructuredReviewResult.subtaskBoundary`
  - 当前 effective change-set
- resolver 规则写死：
  - `PATCH_RUNTIME_WIRING` 只能复用现有 runtime contract builder
  - boundary violation 只能取 `offendingPaths ∩ 当前 effective change-set`
  - scope 为空、路径越界、或无法结构化表达时，返回 `REQUEST_HUMAN`
  - 禁止再把空 scope 回退成 `subtask.changes()`
- 下列 producer 统一改为走这条 resolver：
  - `SubtaskVerificationSupport.normalizeScopedPatchReview()`
  - `SubtaskRunnableMilestoneGuard`
  - `TestExecutor.toImplementationVerificationOutcome()`

### 3. 真正收成 patch-first retry，而不是 prose-first retry

- `SubtaskRevisionDirective` 继续作为执行态 carrier，但 `retryChanges` 只能来自 canonical resolver。
- `SubtaskExecutionState.applyRevisionDirective()` 只消费 canonical repair package，不允许自行放大范围。
- `SubtaskRetryFeedbackRenderer` 必须把以下结构化字段写入 retry payload：
  - `fixMode`
  - `implementationPatchTarget`
  - `overrideChanges`
  - 禁止扩 scope 的修复约束
- repair continuation prompt 保持单入口，但明确：
  - 只修当前 repair package 内文件
  - 不要把已通过文件重新 whole rewrite
  - 不要把局部 patch 退化成整子任务重做

### 4. 补结构化可观测性

- 子任务验证结果进入 implementation 结构化状态时，至少持久化：
  - `reviewDecision`
  - `reasonCode`
  - `implementationPatchTarget`
  - `overrideChanges`
  - `revisionRoute`
- markdown 展示层继续派生，但真实诊断真相源必须是结构化状态，不再只留下泛化的 prose `continuationChangeRequest`。

## Explicit Non-Goals

- 不改 planning capability partition。
- 不改 accepted package completeness gate。
- 不改 stage-level continuation / stage gate。
- 不顺手处理其他非当前主阻塞的稳定性问题。

## Tests

1. boundary violation 最小修复范围
   - 当前 change-set 为 `index.html + src/app.js`
   - reviewer 标记 `src/app.js` 越界
   - 下一轮 `effectiveChanges` 只能保留 `src/app.js`

2. boundary payload 缺 scope
   - reviewer 说越界，但 `offendingPaths` 为空或越出当前 change-set
   - 结果必须转 `REQUEST_HUMAN`

3. runtime wiring 回归
   - 继续保持“只有 canonical runtime repair package 才能自动 patch”
   - 本轮改动不能把这条收紧打松

4. generic patch review 缺 scope
   - `PATCH_EXISTING_IMPLEMENTATION` 无 override scope
   - 不能回退为整子任务自动续跑

5. retry payload round-trip
   - canonical repair package 经过
     `verification -> SubtaskRevisionDirective -> SubtaskExecutionState -> next attempt`
     后仍保持同一文件范围

6. observability
   - 子任务驳回后，结构化状态里能直接看到
     `reasonCode / patchTarget / overrideChanges / revisionRoute`

## Assumptions

- 本轮默认采用最小闭环修复，不把问题重新抬高到 planning/stage 级重构。
- `offendingPaths` 使用当前子任务相对路径，写入前统一 normalize。
- 自动 repair 的 scope 只能来自 typed payload 或已有 runtime contract，不允许引入基于 prose、日志或 mutation 文本的 heuristics。
- 实现完成后先跑新增单元/回归，再重跑黄金路径集成测试。
