# Review V7 Closure Follow-up Findings

## Scope

这份文档只记录对以下实现提交的 follow-up review 结论：

- `a206fc2 implement planning and subtask boundary closure`
- `a001408 implement runtime repair package round-trip`
- `db1d10a implement repair mode permission enforcement`
- `bd49682 lock repair reroute consistency regressions`

目标：

- 只指出当前实现里仍然没有完全收口的边界
- 不重复已经确认收口的问题
- 让后续修复可以直接对着文件改

说明：

- 本次为静态代码 review
- 未重新跑编译和测试

## Findings

### 1. capability partition 还没有真正落成 planning 阶段的确定性 owner

位置：

- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanNormalizationSupport.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationOutlineGate.java`
- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanCoverageAnalyzer.java`

当前问题：

- `ImplementationPlanNormalizationSupport.normalize()` 只对 `ownedCapabilities` / `deferredCapabilities` 做了去空和去重。
- outline gate 和 final plan gate 目前只是把这些字段并进 coverage 语料，没有做 capability owner 的确定性校验。
- 因此下面几种坏 partition 现在都还能进入执行：
  - 同一个 capability 同时出现在当前 subtask 的 `ownedCapabilities` 和 `deferredCapabilities`
  - 同一个 capability 同时被多个 subtasks 声明为 `ownedCapabilities`
  - 前序 subtask 的 `deferredCapabilities` 与后序 subtask 的 `ownedCapabilities` 不形成稳定交接，而只是自由文本碰巧相似

为什么这是问题：

- 本轮 closure 的目标之一，是让 `SubtaskBoundaryGate` 消费 canonical capability partition，而不是继续靠 prose 推断“谁拥有哪个能力”。
- 如果 planning 没有把 owner 锁死，review typed payload 即使标了 `implementsDeferredCapabilities` / `implementsForeignCapabilities`，本地 gate 也没有稳定事实源可依赖。
- 这会让 boundary gate 继续停留在“审阅器主观判断”，而不是“planner 已确定 owner，本地只做 deterministic 驳回”。

建议：

- 在 planning gate 增加 capability partition 的硬校验，而不只是 normalize。
- 最低要求是：
  - `ownedCapabilities` 与 `deferredCapabilities` 必须互斥
  - 同一 capability 不能被多个 subtasks 同时声明为 owned
  - 后续子任务若接手某项 deferred capability，必须形成唯一 owner，而不是多处漂移

### 2. accepted package completeness 仍然会放过 mixed runtime-root 场景

位置：

- `src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanChangeGate.java`

当前问题：

- `ScopePaths.requiresHostEntryPatch()` 当前逻辑是：
  - 先收集当前 scope 里的 runtime paths
  - 如果其中“任意一个”已经在 `wiredRuntimePaths` 中，就直接 `return false`
- 这会放过一种混合坏包：
  - 当前 subtask 同时改了一个已经 wired 的 runtime root
  - 又新增了一个新的 unwired runtime root
  - 但没有携带 host HTML patch

为什么这是问题：

- 本轮 gate 的本意是“只要当前 package 里出现新的 runtime root，且 host 还没接线，就必须同包携带 host patch”。
- 现在的实现把“scope 里存在已接线 root”和“scope 里不存在新 root”混成了同一件事。
- 结果就是 mixed runtime-root package 仍可能以前一个已接线 root 为掩护穿过 planning gate。

建议：

- `requiresHostEntryPatch()` 不应因为 scope 中存在一个已接线 root 就提前返回 `false`。
- 正确口径应是：
  - 只要 scope 中存在任意一个当前未被 `wiredRuntimePaths` 覆盖的 runtime root
  - 且 scope 本身又没有包含 host HTML patch
  - 就必须判为不完整 package
- 需要补一条回归测试覆盖 mixed-root 反例。

### 3. subtask retry scope 对 `PATCH_RUNTIME_WIRING` 仍可能回退到旧 accepted package

位置：

- `src/main/java/devflow/agent/executor/subtask/SubtaskVerificationSupport.java`
- `src/main/java/devflow/agent/executor/subtask/SubtaskExecutor.java`

当前问题：

- `SubtaskVerificationSupport.normalizeScopedPatchReview()` 对所有 concrete patch target 都用了同一条兜底：
  - 只要 review 是 PATCH
  - `implementationPatchTarget().concretePatch() == true`
  - 且 `overrideChanges` 为空
  - 就直接回填当前 `subtask.changes()`
- 这里没有把 `PATCH_RUNTIME_WIRING` 排除掉。
- `SubtaskExecutor` 后续会把这个 `revisionDirective` 应用到 `executionState`，作为下一轮 retry 的真实 scope。

为什么这是问题：

- 本轮方案要求 `PATCH_RUNTIME_WIRING` 的 retry scope 必须来自 canonical runtime repair package，而不是继续沿用当前 subtask 的旧 accepted package。
- 当前如果 wiring 问题不是由 `SubtaskRuntimeWiringGuard` 这条 deterministic 分支产出，而是由 reviewer 分支产出，就会重新落回“拿当前 subtask changes 当 retry scope”的旧路径。
- 这会把 runtime repair package 的唯一 owner 又从 canonical contract 拉回到 subtask 原始 scope，形成双轨。

建议：

- `normalizeScopedPatchReview()` 只应为 `PATCH_EXISTING_IMPLEMENTATION` 做“空 overrideChanges -> 回填当前 subtask scope”的兜底。
- 对 `PATCH_RUNTIME_WIRING`：
  - 要么要求上游必须已经给出 canonical runtime repair package
  - 要么直接转 `REQUEST_HUMAN`
  - 不能回填当前 subtask 的旧 accepted package

### 4. repair-mode read-only shell deny 仍然丢失结构化 `pathIntents`

位置：

- `src/main/java/devflow/agent/executor/shell/ShellCommandAnalyzer.java`
- `src/main/java/devflow/agent/executor/tools/BashTool.java`

当前问题：

- `ShellCommandAnalyzer.analyze()` 在 `writeSegments == 0 && !context.allowReadOnlyShell()` 这条 repair-mode deny 分支里，返回的是空 `pathIntents`。
- `BashTool.diagnosticPath()` 后续又是基于 `decision.declaredTargetPaths()` 来落 diagnostics 路径。
- 结果就是像 `cat app.js` 这种 repair-mode read-only deny，虽然命令里已经有稳定目标路径，最后 diagnostics 仍然会退回空路径。

为什么这是问题：

- 本轮修复目标之一是：deny 类 shell 决策要保留已解析的 `pathIntents`，让 diagnostics 稳定落到具体路径。
- 现在只有一部分 deny 分支保留了 `pathIntents`，repair-mode read-only deny 这条分支还是把结构化路径丢掉了。
- 这会让 repair/resume 模式下最常见的一类拒绝，继续退化成 `(tool-loop)` 级别的模糊诊断。

建议：

- 在进入 `REPAIR_MODE_READ_ONLY_SHELL_DENIED` 分支前，复用已解析出的 path intents，而不是返回空列表。
- 需要补一条测试，锁死：
  - repair mode
  - 只读命令
  - deny
  - diagnostics 仍能稳定落到具体目标路径

## Conclusion

当前实现的大方向仍然是对的，已经明显比前几轮更接近方案本意：

- planning runtime facts 已不再走目录扫描 fallback
- runtime repair package 已进入 `implementation_state` 协议链
- repair-mode permission 已开始从 prompt 约束转向工具层 owner
- run-state consistency 这一轮没有再看到明显旧回归

但以上 4 个问题仍然说明，这轮 closure 还没有完全收口：

- planning 侧的 canonical capability owner 还不够硬
- accepted package completeness 还有 mixed-root 漏口
- runtime wiring retry scope 还可能回退到旧 accepted package
- repair-mode shell deny 还没有全链保留结构化路径

建议修复优先级：

1. capability partition gate
2. `PATCH_RUNTIME_WIRING` retry scope 收口
3. accepted package completeness mixed-root 补漏
4. repair-mode shell deny diagnostics 收口
