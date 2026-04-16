# Review V16: Repair Carrier / Patch Scope Closure Plan

## Summary

- 这轮只处理当前第一阻塞链：`review/test -> canonical repair package -> retry feedback -> next attempt`。
- 不继续扩到 `ArtifactContextSanitizer`、outline runtime split、`ImplementationResumePolicy` 残留 guard 等其他问题面。
- 当前要收的是两条高风险同类问题：
  1. 普通 `PATCH` 在没有显式安全 scope 时，仍会静默扩回整个当前 `allowedScope`
  2. canonical repair package 进入 retry feedback channel 后被主动抹掉，下一轮又退回 prose-first repair
- 这两条如果不一起改，repair 就会继续表现成：
  - 最小 patch 被放大成整子任务重做
  - whole-file rewrite / 越界修复重新出现
  - 结构化 patch package 在 retry / retry-merge 后消失
  - 下一轮 implementation 继续沿 prose guidance 漂移

## Scope Boundary

### 本轮明确不重做的内容

- 不重开 `ArtifactContextSanitizer` 对 analysis/prd intake 的清洗语义
- 不重开 `ImplementationPlanChangeGate` 的 brand-new runtime root / outline host patch 协议
- 不重开 `ImplementationResumePolicy` 那条 “patch target 已确定但 scope 为空” 的残留 guard
- 不重开 `human gate / run-state` 语义
- 不顺手重做 `planning runtime facts`、`runtime ownership`、`document intake`

### 本轮真正要收的内容

1. `PATCH_EXISTING_IMPLEMENTATION` 的 canonical scope owner
2. active repair retry 的 canonical package carrier
3. retry feedback / feedback merge / next attempt 三处是否继续消费同一份 package

## Current Evidence

### E1. `PATCH` 在缺少显式 scope 时仍会静默扩回整个子任务

- 当前代码：
  [SubtaskRepairDirectiveResolver.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskRepairDirectiveResolver.java)
- 关键行为：
  - `resolveWithinSubtask(...)` 在 `review.overrideChanges()` 为空时，直接把当前 `allowedScope` 当成 canonical patch scope
  - 对应位置：`return patchOutcome(withOverrideChanges(review, allowedScope), allowedScope);`

这意味着：

- review/test 只要给出 `FixMode.PATCH`
- 但没有给出结构化文件范围
- 系统就会自动把 repair 扩成整份当前子任务 `effectiveChanges`

这会直接重新制造：

- whole-file rewrite
- 越界改动
- repair scope 漂移

### E2. canonical repair package 在 retry feedback channel 中被主动抹掉

- 当前代码：
  - [SubtaskRetryFeedbackRenderer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskRetryFeedbackRenderer.java)
  - [ExecutionDirectiveFeedbackSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/protocol/ExecutionDirectiveFeedbackSupport.java)
  - [ImplementationPlanRunner.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/ImplementationPlanRunner.java)
  - [SubtaskRecoverySupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskRecoverySupport.java)

当前事实：

- `SubtaskRetryFeedbackRenderer` 构造 retry payload 时，直接写入：
  - `implementationPatchTarget = null`
  - `overrideChanges = []`
- `ExecutionDirectiveFeedbackSupport.feedbackPayload(...)` 和 `repairBriefOnly(...)` 又继续把 concrete patch package 清成：
  - `implementationPatchTarget = null`
  - `overrideChanges = []`
- `ImplementationPlanRunner` 与 `SubtaskRecoverySupport` 真实使用这条 feedback channel 做 merge / persistent feedback 滚动

这意味着：

- 上游即使已经算出 canonical repair package
- 只要进入 retry feedback channel
- 下一轮仍可能只剩 prose summary / changeRequest
- active repair retry 重新退化成 prose-first repair

### E3. `v183` 的集成失败与上述两条问题族一致

- 项目：`/home/linus/workspace/tetris_test_itest_v183`
- run：`b90b680c-3732-4732-ae37-f36a9090ef95`
- 关键产物：
  - [events.log](/home/linus/workspace/tetris_test_itest_v183/.devflow/runs/b90b680c-3732-4732-ae37-f36a9090ef95/events.log)
  - [implementation_state.json](/home/linus/workspace/tetris_test_itest_v183/.devflow/runs/b90b680c-3732-4732-ae37-f36a9090ef95/implementation_state.json)

直接现象：

- `subtask-2` 修复回合里持续尝试 existing file whole-file rewrite
- patch scope 没有稳定收缩成当前最小修补范围
- `attempt=1` 子任务耗尽后触发 `PATCH`
- `attempt=2` 没有形成足够窄、结构正确的 repair outline，又掉回 capability partition / shared-file boundary 失败

结论：

- 当前主阻塞不是 human gate，也不是单纯 planning
- 而是 repair package 在 `verification -> retry feedback -> next attempt` 这一段没有稳定保持结构化最小范围

## Final State

本轮完成态必须同时满足下面 5 条闭环：

1. 普通 `PATCH_EXISTING_IMPLEMENTATION` 的自动续跑只允许两类 scope 来源：
   - review/test 显式给出的结构化 `overrideChanges`
   - 确定性 current-scope producer 明确给出的当前 owner scope
2. generic patch review 如果没有安全 canonical scope，结果必须是：
   - `REQUEST_HUMAN`
   - 或等价的显式阻断
   - 绝不能再自动扩成整份 `allowedScope`
3. 当前 active subtask 的 machine-truth patch package 只有一个：
   - fresh repair package 只能通过 `SubtaskRevisionDirective.patch(...)` 进入当前 active execution state
   - 进入当前轮后，唯一 machine owner 立刻切到 `SubtaskExecutionState.effectiveChanges`
4. retry feedback channel 不能主动抹掉当前 active retry 仍需消费的 concrete package，但它也不能成为第二个 owner：
   - `TaskPackage` / retry feedback / prose summary 只允许承载派生视图
   - 不能反过来重新决定 active patch scope
5. later-subtask repair brief 继续保持无 concrete package：
   - 当前 active retry 可以携带当前 canonical package
   - 后续 subtasks 只接收 repair brief / prose constraints
   - 不允许通过 merge 复活旧轮次、旧阶段的 concrete package

完成后，系统对这一类问题的唯一语义应是：

- active repair package 由 `SubtaskRevisionDirective -> SubtaskExecutionState.effectiveChanges` 单链持有
- retry feedback 可以携带当前 active repair 的结构化 package，但只是 derived carrier，不是 owner
- generic patch review / test 如果没有安全 scope，直接阻断，不再偷回整个子任务

## Removal Plan

本轮必须删除或封死下面这些旧语义：

- `SubtaskRepairDirectiveResolver` 中“overrideChanges 为空就扩回 `allowedScope`”的旧 fallback
- `SubtaskRetryFeedbackRenderer` 中主动把 `implementationPatchTarget` / `overrideChanges` 清空的旧语义
- `ExecutionDirectiveFeedbackSupport.feedbackPayload(...)` 中统一抹掉 concrete patch package 的旧语义
- `ExecutionDirectiveFeedbackSupport.repairBriefOnly(...)` 与 active retry feedback 共用一套 “drop package” 处理的旧语义
- 现有测试里把“retry feedback 必须丢失 concrete patch package”当成正确行为的旧预期

本轮不允许：

- 通过 `allowedScope` fallback 继续保主链能跑
- 把 feedback channel 升格成新的 machine owner
- 为了保留 later-subtask repair brief 旧行为，而继续让 active retry 丢包
- 用 prose summary / changeRequest 重新猜 patch scope

## Joint-Change Scope

这轮必须一起改下面这些 owner；只改其中一半，一定会形成半成品。

### Scope 1. Canonical Patch Scope Owner

- [SubtaskRepairDirectiveResolver.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskRepairDirectiveResolver.java)
- [SubtaskVerificationSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskVerificationSupport.java)
- [TestExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/testing/TestExecutor.java)
- [SubtaskRunnableMilestoneGuard.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskRunnableMilestoneGuard.java)
- [SubtaskRuntimeWiringGuard.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskRuntimeWiringGuard.java)

### Scope 2. Active Retry Carrier

- [SubtaskExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskExecutor.java)
- [SubtaskRetryFeedbackRenderer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskRetryFeedbackRenderer.java)
- [ExecutionDirectivePayload.java](/home/linus/workspace/forge/src/main/java/devflow/agent/protocol/ExecutionDirectivePayload.java)
- [ExecutionDirectiveFeedbackSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/protocol/ExecutionDirectiveFeedbackSupport.java)
- [ImplementationPlanRunner.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/ImplementationPlanRunner.java)
- [SubtaskRecoverySupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskRecoverySupport.java)

### Scope 3. Active Owner / Derived View Boundary

- [SubtaskRevisionDirective.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskRevisionDirective.java)
- [SubtaskExecutionState.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskExecutionState.java)
- [TaskPackage.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/TaskPackage.java)

说明：

- `TaskPackage` 在本轮不是 machine owner，只是 reviewer / coder 视图边界的一部分
- 如果代码实现发现 `TaskPackage` 不需要改，也必须在 review 里明确证明它仍然只消费当前 active scope 的派生结果，没有重新承担 owner 角色

### Scope 4. Regression Tests

- [SubtaskRepairDirectiveResolverTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/subtask/SubtaskRepairDirectiveResolverTests.java)
- [SubtaskVerificationSupportTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/subtask/SubtaskVerificationSupportTests.java)
- [SubtaskRetryFeedbackRendererTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/subtask/SubtaskRetryFeedbackRendererTests.java)
- 新增或显式扩展 `ExecutionDirectivePayload` owner regressions
  - 目标：直接锁死 `ExecutionDirectivePayload.mergeCanonicalPatchPackage(...)` 的 no-revive 语义
  - 不允许只靠 `ExecutionDirectiveFeedbackSupportTests` 间接覆盖
- [ExecutionDirectiveFeedbackSupportTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/protocol/ExecutionDirectiveFeedbackSupportTests.java)
- [TestExecutorTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/testing/TestExecutorTests.java)
- 新增或显式扩展 `SubtaskExecutor` owner regressions
  - 目标：直接锁死 `attempt N -> attempt N+1` 保留 active concrete package，而 later-subtask 仍只拿 repair brief
  - 不允许只靠 `ImplementationPlanRunnerTests` 间接覆盖
- [ImplementationPlanRunnerTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/implementation/ImplementationPlanRunnerTests.java)
- [SubtaskExecutionStateTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/SubtaskExecutionStateTests.java)

## Problem / Solution Map

### P1. Generic `PATCH` review 在无 scope 时静默扩成整个当前子任务

#### Problem

- 现在 `SubtaskRepairDirectiveResolver` 把两种完全不同的情况混成了一种：
  - 明确 current-scope producer 的 deterministic patch
  - generic review/test 只说 `FixMode.PATCH` 但没有结构化 scope
- 结果是 generic patch 也能偷回整个 `allowedScope`

#### Solution

- 把 automatic patch scope 来源收成两类且仅两类：
  1. 显式 `overrideChanges`
  2. 明确的 deterministic current-scope producer
- `resolveCurrentScopePatch(...)` 继续只服务 deterministic current-scope producer
- `resolveStructuredPatch(...)` / `resolveExplicitPatch(...)` 在没有显式安全 scope 时，必须返回：
  - `REQUEST_HUMAN`
  - 或等价阻断 review
- 删除 `resolveWithinSubtask(...)` 中对 generic `PATCH` 的 `allowedScope` fallback

### P2. Active repair package 进入 retry feedback channel 后被主动清空

#### Problem

- `SubtaskRetryFeedbackRenderer` 和 `ExecutionDirectiveFeedbackSupport` 当前把 active retry feedback 与 later-subtask repair brief 混成一套 “drop concrete package” 行为
- 这会让：
  - active retry 丢失当前 canonical package
  - next attempt 只剩 prose-first repair

#### Solution

- 把两种 feedback 语义拆开，但不引入第二个 owner：
  - active retry feedback：允许携带当前 canonical package
  - repair brief feedback：继续不携带 concrete package
- `ExecutionDirectivePayload.mergeCanonicalPatchPackage(...)` 是 no-revive 规则的协议 owner：
  - fresh feedback 没有 concrete package 时，不能回退复活 base concrete package
  - active retry 与 repair brief 的差异，只能建立在这条协议 owner 之上，不允许在包装层重新各写一套复活逻辑
- `ExecutionDirectiveFeedbackSupport` 只负责：
  - 保留 active retry 当前轮需要消费的 concrete package
  - 不复活旧轮次/旧阶段 concrete package
  - repair brief 继续 strip package
- `SubtaskRetryFeedbackRenderer` 必须把当前 verification 的：
  - `fixMode`
  - `implementationPatchTarget`
  - `overrideChanges`
  一并写入 active retry payload

### P3. feedback channel 不能重新变成 machine owner

#### Problem

- 如果只把 concrete package “重新塞回 feedback”，但不定义 owner 边界，很容易从 “丢包” 走到 “feedback 成了第二个 owner”

#### Solution

- 明确写死 owner 链：
  - fresh package 进入当前 active subtask 时，唯一 machine owner 立刻切到 `SubtaskRevisionDirective -> SubtaskExecutionState.effectiveChanges`
  - retry feedback 只是 derived carrier
  - `TaskPackage` 也是 derived view
- `ExecutionDirectivePayload.mergeCanonicalPatchPackage(...)` 不能在 fresh feedback 没 concrete package 时复活 base package
- `ExecutionDirectiveFeedbackSupport.merge(...)` 只能复用上述协议 owner，不允许再在 support 层长第二套 revive/fallback 逻辑
- `SubtaskExecutor` 必须与 `ImplementationPlanRunner` 一起对齐：
  - `ImplementationPlanRunner` 负责“子任务之间”的 carrier 边界
  - `SubtaskExecutor` 负责“同一子任务 attempt N -> attempt N+1” 的 carrier 滚动
  - 两处都必须保持：当前 active attempt 保留 active concrete package，而 later-subtask 仍只拿 repair brief
- `ImplementationPlanRunner` / `SubtaskRecoverySupport` 只在 active retry 仍属于当前 subtask 时保留当前 package
- later-subtask 路径只滚动 repair brief，不滚动 active concrete package

## Implementation Order

### Phase 1. 锁死 canonical patch scope owner

- 删除 generic `PATCH -> allowedScope` fallback
- 保留 deterministic current-scope producer 的专用入口
- review/test 侧无安全 scope 时统一阻断

### Phase 2. 锁死 active retry carrier

- `SubtaskRetryFeedbackRenderer` 补回 concrete patch package
- `ExecutionDirectivePayload.mergeCanonicalPatchPackage(...)` 收成唯一 no-revive 协议 owner
- `ExecutionDirectiveFeedbackSupport` 区分 active retry feedback 与 repair brief feedback
- `SubtaskExecutor` / `ImplementationPlanRunner` 一起对齐 attempt 内与子任务间的 carrier 滚动
- 删除现有 “drop concrete patch package” 预期测试

### Phase 3. 对齐 merge / next-attempt 消费链

- `ImplementationPlanRunner` / `SubtaskRecoverySupport` 只在 active retry 上保留当前 package
- later-subtask 只看 repair brief
- 证明 `TaskPackage` / `SubtaskExecutionState` 没有长出第二个 owner

### Phase 4. 补回归

- patch review 缺 scope 不再自动扩范围
- active retry feedback 保留当前 concrete package
- repair brief 继续不带 concrete package
- `ExecutionDirectivePayload.mergeCanonicalPatchPackage(...)` 不复活旧 package
- 新增或显式扩展 `ExecutionDirectivePayload` owner regressions，直接钉住 no-revive 规则，不只测包装层
- `SubtaskExecutor` 锁死 `attempt N -> attempt N+1` 保留 active concrete package，而 later-subtask 仍只拿 repair brief
- 新增或显式扩展 `SubtaskExecutor` owner regressions，直接钉住 same-subtask attempt carrier，不只测 runner 层
- next attempt 继续消费同一 active canonical package

## Explicit Non-Goals

- 不修 `ArtifactContextSanitizer`
- 不修 `ImplementationPlanChangeGate` brand-new runtime root / outline host patch
- 不修 `ImplementationResumePolicy` 那条残留 “empty scope patch continuation” guard
- 不顺手改 human gate、run-state、document intake

## Closure Decision

这轮可以一次性收口，前提是只守住当前问题族，不往外扩。

理由：

- 当前主阻塞链已经很清楚，就是 `patch scope owner + retry carrier`
- 两条高风险共享同一条 consumer 链：`SubtaskVerificationSupport / TestExecutor -> SubtaskRepairDirectiveResolver -> retry feedback -> ImplementationPlanRunner / SubtaskRecoverySupport -> next attempt`
- 这轮如果混入 `ArtifactContextSanitizer` 或 outline runtime split，就会重新变成多主线整改，review 会失焦

如果 reviewer 认为：

- active retry feedback 也不应携带 concrete package
- 或 deterministic current-scope producer 不应保留专用入口

那本轮不能直接写代码，必须先停在方案阶段重新定 owner；否则一定会做成双轨。

## Risks / Blockers

### R1. 把 feedback channel 错做成第二个 machine owner

避免方式：

- 文档和实现都必须明确：
  - owner 仍是 `SubtaskRevisionDirective -> SubtaskExecutionState.effectiveChanges`
  - feedback 只是 derived carrier

### R2. later-subtask repair brief 误带 active concrete package

避免方式：

- `repairBriefFeedback(...)` 继续 strip package
- later-subtask 路径只滚动 brief，不滚动 active package

### R3. deterministic current-scope producer 与 generic patch review 再次混淆

避免方式：

- 明确区分：
  - `resolveCurrentScopePatch(...)` 只给确定性 current-scope producer
  - `resolveStructuredPatch(...)` / `resolveExplicitPatch(...)` 必须显式拿到安全 canonical scope

## Review Focus

请 reviewer 重点只审下面 5 点：

1. 这份方案是否把本轮问题族收在单一链路内，没有把 `ArtifactContextSanitizer` 或 outline runtime split 混进来
2. `PATCH` 无 scope 时是否已经被明确收成 fail-fast / request-human，而不是继续 fallback 到 `allowedScope`
3. active retry feedback 与 later-subtask repair brief 是否已经被明确区分，且没有重新长出第二个 owner
4. `SubtaskRevisionDirective -> SubtaskExecutionState.effectiveChanges` 是否已经被明确写成唯一 machine owner 链
5. joint scope 是否已经覆盖真实 producer / carrier / merge / consumer / tests，没有遗漏 call-site owner
