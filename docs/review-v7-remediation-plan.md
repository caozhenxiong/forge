# Review V7 收口修复方案

## Purpose

这份文档只处理 [docs/review-v7-closure-follow-up-findings.md](/home/linus/workspace/forge/docs/review-v7-closure-follow-up-findings.md) 提出的 4 个未收口问题。

目标：

- 不重开已完成整改
- 只修当前 review 已证实的收口缺口
- 修完后再恢复黄金路径集成测试

约束来源：

- 根目录 `AGENTS.md`
- `docs/engineering-agreements.md`
- `docs/implementation-stage-closure-plan.md`
- `docs/implementation-stage-closure-execution.md`

## Findings To Fix

### F1. capability partition 还没有变成 planning 阶段的确定性 owner

现状：

- [ImplementationPlanNormalizationSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanNormalizationSupport.java)
- [ImplementationOutlineGate.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationOutlineGate.java)
- [ImplementationPlanCoverageAnalyzer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanCoverageAnalyzer.java)

当前只做 capability 清洗与 coverage 检查，没有把 capability owner 锁成确定性约束。

需要修成：

- `ownedCapabilities` 与 `deferredCapabilities` 必须互斥
- 同一 capability 不能被多个 subtasks 同时声明为 `owned`
- 某 capability 一旦被前序子任务声明为 deferred，后续只能出现一个稳定接手 owner
- gate 本地直接基于 canonical partition 驳回，不再留给 reviewer prose 解释

### F2. accepted package completeness 对 mixed runtime-root 仍有漏口

现状：

- [ImplementationPlanChangeGate.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanChangeGate.java)

`requiresHostEntryPatch()` 只要发现 scope 中有一个已经 wired 的 runtime root，就提前放过整个 package。

需要修成：

- 只要 scope 内存在任意一个当前未被 `wiredRuntimePaths` 覆盖的 runtime root
- 且当前 scope 又没有 host HTML patch
- 就必须打回
- 不能让“已接线 root”掩护“新增未接线 root”

### F3. `PATCH_RUNTIME_WIRING` retry scope 仍可能回退到旧 accepted package

现状：

- [SubtaskVerificationSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskVerificationSupport.java)
- [SubtaskExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskExecutor.java)

`normalizeScopedPatchReview()` 现在对所有 concrete patch target 共享“空 overrideChanges 时回填 `subtask.changes()`”逻辑。

需要修成：

- `PATCH_EXISTING_IMPLEMENTATION` 才允许用当前 subtask scope 做 canonical 回填
- `PATCH_RUNTIME_WIRING` 不允许回落到旧 accepted package
- 对 runtime wiring：
  - 要么上游已经给出 canonical runtime repair package
  - 要么直接走 `REQUEST_HUMAN`
- retry scope 的唯一 owner 继续保持在 runtime repair package 链，而不是 reviewer patch fallback

### F4. repair-mode read-only shell deny 仍会丢 `pathIntents`

现状：

- [ShellCommandAnalyzer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/shell/ShellCommandAnalyzer.java)
- [BashTool.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/tools/BashTool.java)

`REPAIR_MODE_READ_ONLY_SHELL_DENIED` 分支返回空 `pathIntents`，导致 diagnostics 仍会退回空路径。

需要修成：

- repair/resume 模式下，对只读 shell 的 deny 继续保留 analyzer 已经解析出的 `pathIntents`
- diagnostics 必须优先落到结构化目标路径
- 不新增命令字符串 heuristics，只复用 analyzer 已经拿到的结构化路径

## Final State

完成态必须同时满足：

1. capability partition 的 canonical owner 只由 planning gate 链生成，并在本地以确定性规则校验。
2. mixed runtime-root package 只要包含未接线 runtime root 且缺少 host patch，就会在 planning 阶段被打回。
3. `PATCH_RUNTIME_WIRING` 的 retry scope 只允许来自 canonical runtime repair package，不再回填旧 accepted package。
4. repair-mode read-only shell deny 会保留结构化 `pathIntents`，Bash diagnostics 能稳定落到具体路径。
5. Phase 8 黄金路径集成测试在以上 4 条收口完成前不得恢复。

## Removal Plan

本轮必须删除或封死以下旧路径：

1. 只做 capability sanitize、不做 owner 校验的 planning 路径。
2. mixed runtime-root package 被“已有 wired root”掩护放过的 planning 路径。
3. `PATCH_RUNTIME_WIRING` 通过 reviewer fallback 回填 `subtask.changes()` 的旧路径。
4. repair-mode read-only shell deny 返回空 `pathIntents` 的旧路径。

## Joint-Change Scope

这 4 个问题必须按链路成组修改，不能只改单点。

### Scope 1. capability partition gate

涉及：

- [ImplementationPlanNormalizationSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanNormalizationSupport.java)
- [ImplementationPlanGateInputBuilder.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanGateInputBuilder.java)
- [ImplementationOutlineGate.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationOutlineGate.java)
- [ImplementationPlanGate.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanGate.java)
- [ImplementationPlanCoverageAnalyzer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanCoverageAnalyzer.java)
- 相关 planning gate tests

要求：

- normalization 继续只负责清洗，不偷偷修正 owner 冲突
- owner 冲突必须由 gate 明确失败
- `ImplementationPlanGateInputBuilder` 继续作为 final assembled plan gate 输入的唯一展平装配点
- `ImplementationPlanGate` 必须和 outline gate 一样消费同一份 canonical capability partition，而不是只靠 flatten 后的 coverage 语料放行
- canonical capability partition 只保留一套 owner 解释

### Scope 2. runtime package completeness gate

涉及：

- [ImplementationPlanChangeGate.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanChangeGate.java)
- 相关 planning gate tests

要求：

- `requiresHostEntryPatch()` 不能再用“命中任意 wired root 就直接放过”的逻辑
- mixed runtime-root 反例必须有单测锁死

### Scope 3. runtime wiring retry scope

涉及：

- [SubtaskVerificationSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskVerificationSupport.java)
- [SubtaskExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskExecutor.java)
- [TestExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/testing/TestExecutor.java)
- runtime wiring / subtask verification tests

要求：

- `PATCH_RUNTIME_WIRING` 与 `PATCH_EXISTING_IMPLEMENTATION` 分开处理
- runtime wiring retry scope 继续只认 canonical runtime repair package
- `TestExecutor` 产出的 `PATCH_RUNTIME_WIRING` 也必须走同一条 canonical runtime repair package 约束
- 测试侧若没有 canonical runtime repair package，必须转 `REQUEST_HUMAN`
- 不允许 reviewer fallback 再造第二套 patch scope

### Scope 4. repair-mode shell diagnostics

涉及：

- [ShellCommandAnalyzer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/shell/ShellCommandAnalyzer.java)
- [BashTool.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/tools/BashTool.java)
- bash diagnostics tests

要求：

- deny 分支复用已解析 `pathIntents`
- diagnostics 继续只消费结构化路径
- 不新增文本猜测逻辑

## Closure Decision

这轮可以一次性收口。

原因：

- 4 个问题都属于当前 closure 主线的已存在 owner 链补漏
- 不需要引入新协议层，也不需要新增兼容层
- 每条问题都有明确 owner 和回归测试入口

因此本轮应直接按最终态修，不做过渡实现，不保留双轨。

## Implementation Order

1. capability partition gate
2. `PATCH_RUNTIME_WIRING` retry scope 收口
3. accepted package completeness mixed-root 补漏
4. repair-mode shell deny diagnostics 收口
5. 回归测试补齐
6. 通过后再恢复 Phase 8 集成测试

## Regression Matrix

本轮至少补齐以下回归：

- capability partition:
  - `ownedCapabilities` 与 `deferredCapabilities` 重叠时失败
  - 同一 capability 被多个 subtasks 同时声明为 owned 时失败
  - deferred capability 没有形成唯一后续 owner 时失败
  - final assembled plan 进入 `ImplementationPlanGate` 时仍会按 canonical capability partition 驳回，不允许只在 outline gate 收紧
- mixed runtime-root completeness:
  - scope 同时包含一个 wired root 和一个 unwired root，但没有 host patch 时失败
- runtime wiring retry scope:
  - reviewer 给出 `PATCH_RUNTIME_WIRING` 且 `overrideChanges` 为空时，不能回填 `subtask.changes()`
  - 无 canonical runtime repair package 时应转 `REQUEST_HUMAN`
  - 测试侧产出 `PATCH_RUNTIME_WIRING` 且缺少 canonical runtime repair package 时，也必须转 `REQUEST_HUMAN`
- repair-mode shell diagnostics:
  - repair mode 下 `cat app.js` 被 deny，但 diagnostics 仍落到 `app.js`

## Risks / blockers

当前没有架构级 blocker。

唯一风险在执行层：

- 如果只改 gate，不补对应单测，这轮仍可能在集成前回松
- 如果把 `PATCH_RUNTIME_WIRING` 继续混进通用 patch fallback，会重新长出双轨

## Completion Gate Result

当前状态：`PLAN_READY_NOT_IMPLEMENTED`

通过标准：

- 4 条 review finding 全部有代码 owner 对应修复
- 对应回归单测补齐并通过
- `docs/implementation-stage-closure-execution.md` 恢复到可进入 Phase 8 的状态
