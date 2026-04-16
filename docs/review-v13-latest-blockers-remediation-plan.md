# Review V13: Latest Blockers Remediation Plan

## Summary

- 这份文档只处理 `596ae49` 中新增的 3 条 latest blocker。
- 它不是新的大范围重构方案，而是对 `review-v13-high-risk-convergence-plan.md` 的补充收口。
- 本轮只修：
  1. repair / reopen round 复用旧 `mutation history`
  2. detail gate 误杀合法 runtime externalization 完整包
  3. `WEB_RESOURCE_LINK_CHECK` 误判 root-relative 本地资源
- 本轮不处理：
  - `M1 TestExecutor targeted reverification drift`
  - `M2 Empty owner contract / weak capability partition`
  - 工作区里那 5 个 prompt / tool guidance 的未提交本地改动
  - 集成测试

## Latest Reviewer Addendum

基于最新一轮 review，这份 remediation plan 还必须额外钉死两条边界：

1. Scope A 不能只覆盖 `reopen / revision retry`。
   `generation failure -> recovery retry`、`file-scoped retry` 也是新的 repair round 入口，必须与 completed-plan reopen / revision retry 一起走同一个 “start new repair round” owner。
2. Scope C 的 regression 不能只写成泛泛的 “validation 侧对应测试”。
   这条 blocker 的真实 owner 是 `WEB_RESOURCE_LINK_CHECK` 在 `ValidationExecutor` 的执行入口，所以必须明确挂到 `ValidationExecutorTests`；`WebRuntimeWiringCheckTests` 只能作为共享 root-relative helper 被复用时的并行回归，不是主测试 owner。

## Final State

本轮完成后，`v13` 的剩余 blocker 必须同时满足：

1. repair / reopen round 的 closure 只接受当前 round 的 canonical mutation evidence。
   旧 round 的 `mutationRecords` 不能被下一轮继续拿来当 assistant-only completion 证据。
2. planning detail gate 对 runtime externalization 只有一条合法性语义：
   - `ROOT` 仍要求同包带 `host HTML patch`
   - `LEAF` 可以依赖两类 anchor：
     - 当前已 reachable 的 runtime anchor
     - 同包新引入且已显式声明 `runtimeScriptRole=ROOT` 的 package-local anchor
3. `WEB_RESOURCE_LINK_CHECK` 与 planning/runtime/ownership 使用同一套 root-relative 本地资源归一规则。
   `"/js/app.js"`、`"/styles/app.css"` 这类路径不能在 validation 侧再次被解释成相对 html 父目录。

## Removal Plan

本轮必须删除或封死下面 3 条旧语义：

- 新 repair / reopen round 继续复用上一轮 `ImplementationToolSessionState.mutationRecords`。
- `ImplementationPlanChangeGate` 中 `LEAF` 只能依赖当前 `reachableRuntimePaths`，不能依赖同包新 `ROOT` 的旧规则。
- `WebResourceValidationSupport` 自己拼接 `html parent + rawRef` 的 root-relative 资源解析逻辑。

本轮不允许：

- 用“保留旧 mutation history 但额外加一个判断”继续做双轨
- 在 detail gate 里加新的 fallback / heuristic 特判掩盖 anchor 语义
- 在 validation 侧再造第二套 root-relative 解析 helper

## Joint-Change Scope

### Scope A. Current-Round Mutation Evidence

- [SubtaskExecutionState.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskExecutionState.java)
- [SubtaskExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskExecutor.java)
- [ImplementationResumePolicy.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/ImplementationResumePolicy.java)
- [ImplementationToolLoopExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolLoopExecutor.java)
- [ImplementationToolSessionState.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolSessionState.java)
- [ImplementationStateSnapshotSerializer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/state/ImplementationStateSnapshotSerializer.java)
- [ImplementationSnapshotRestorer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/state/ImplementationSnapshotRestorer.java)
- [ImplementationToolLoopExecutorTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/ImplementationToolLoopExecutorTests.java)
- [ImplementationResumePolicyTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/ImplementationResumePolicyTests.java)
- [SubtaskExecutionStateTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/SubtaskExecutionStateTests.java)
- `SubtaskExecutor` / attempt runner 级 retry-path regression（如当前没有直接 owner 测试，则本轮新增）

### Scope B. Runtime Externalization Package Legality

- [ImplementationPlanChangeGate.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanChangeGate.java)
- [ImplementationSubtaskDetailGate.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationSubtaskDetailGate.java)
- [ImplementationPlanGateTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/ImplementationPlanGateTests.java)
- [ImplementationSubtaskDetailGateTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/ImplementationSubtaskDetailGateTests.java)

### Scope C. Validation Root-Relative Resource Resolution

- [WebResourceValidationSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/validation/WebResourceValidationSupport.java)
- [ValidationExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/validation/ValidationExecutor.java)
- [ValidationExecutorTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/validation/ValidationExecutorTests.java)
- [RuntimeScriptGraphInspector.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/runtime/RuntimeScriptGraphInspector.java)
- [PlanningRuntimeFactsResolver.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/PlanningRuntimeFactsResolver.java)
- [WebRuntimeWiringCheckTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/WebRuntimeWiringCheckTests.java)（仅当共享 helper 影响 runtime/ownership 侧时并行补回归，不作为主测试 owner）

## Problem / Solution Map

### B1. Repair / reopen round 复用旧 mutation history

#### Problem

- `ImplementationResumePolicy` 在 reopen / revision retry 时只清 transcript，不清 `mutationRecords`。
- `SubtaskExecutor` 在 `generation failure -> applyFileScopedGenerationFailure() -> withRecoveryPolicy()` 这条 recovery retry 链上，也会开启新的 repair round。
- `ImplementationToolLoopExecutor` 的 existing-file closure 又已经变成“只要 current state matches latest terminal state 即可收口”。
- 两者叠加后，新 round 可能在零新 mutation 的情况下直接 assistant-only 收口。

#### Required Fix

- 进入任意新 repair round 时，必须显式重建当前 round 的 `ImplementationToolSessionState`。
- “新 repair round” 的 owner 范围必须一次收齐：
  - completed-plan reopen
  - revision retry
  - generation recovery retry
  - file-scoped retry 后的下一轮执行
- 新 round 允许继承：
  - read ledger
  - result replacement state
  - diagnostics（如必要）
- 新 round 不允许继承：
  - `mutationRecords`
  - 上一轮 transcript
- `ImplementationToolLoopExecutor` 继续只消费“当前 round session”中的 `mutationRecords`，不再有第二条证据路径。

#### Regression

- reopened patch round + current file matches old terminal state + zero new mutation => `NO_MATERIAL_CHANGE`
- generation recovery retry starts a new round with cleared mutation history
- restored same-round session still preserves current round mutation history

### B2. Detail gate 误杀合法 runtime externalization 完整包

#### Problem

- `LEAF` 当前只认 `reachableRuntimePaths` 里的 anchor。
- 这会把 `index.html host patch + index.app.js(ROOT) + src/engine.js(LEAF)` 这种一次性完整 package 误判成“没有 reachable anchor”。

#### Required Fix

- package-level anchor 必须统一为：
  - `reachableRuntimePaths`
  - `+ scopedRuntimePaths where runtimeScriptRole == ROOT`
- `LEAF` 只要命中 package-level anchor 即合法。
- `ROOT` 仍保留现有硬约束：
  - 新 `ROOT` 必须同包带 `host HTML patch`
- 不新增新字段，不新增 fallback。

#### Regression

- `host patch + ROOT + LEAF` => pass
- `LEAF` without reachable anchor and without package `ROOT` => fail
- `ROOT` without host patch => fail

### B3. `WEB_RESOURCE_LINK_CHECK` 误判 root-relative 本地资源

#### Problem

- planning/runtime/ownership 已开始支持 `"/js/app.js"` 这类 root-relative 路径。
- `WebResourceValidationSupport` 仍按 `html parent + rawRef` 解析资源。
- 合法产物在 self-check 里仍会被打成 missing resource。

#### Required Fix

- validation 侧必须复用与 runtime graph 同一套 root-relative 本地资源归一规则。
- `ValidationExecutor` 只保留 owner wiring，不新增第二套规则。
- `WebResourceValidationSupport` 不能再自己解释 root-relative 路径。
- Scope C 的主 regression owner 必须落在 `ValidationExecutorTests`，因为 blocker 命中的是 `WEB_RESOURCE_LINK_CHECK` 真正执行入口。

#### Regression

- `ValidationExecutorTests`: `src="/js/game-engine.js"` pass
- `ValidationExecutorTests`: `href="/styles/app.css"` pass
- 真正缺失的 root-relative 本地资源 still fail

## Implementation Order

### Phase 1. Current-Round Mutation Evidence

- 在 `SubtaskExecutionState` 或单一 helper 上提供“开始新 repair round”入口
- `ImplementationResumePolicy` 的 reopen / revision retry 统一走这条入口
- `SubtaskExecutor` 的 generation recovery retry / file-scoped retry 也统一走这条入口
- 删除“新 round 继续复用旧 mutationRecords”的旧路径
- 补 Scope A regression

### Phase 2. Runtime Externalization Package Legality

- 修改 `ImplementationPlanChangeGate` 的 package-level anchor 判定
- 确认 `ImplementationSubtaskDetailGate` 只消费这一份结构化语义
- 补 Scope B regression

### Phase 3. Validation Root-Relative Resource Resolution

- 抽 / 复用单一 root-relative 本地资源解析规则
- 接入 `WebResourceValidationSupport`
- 补 Scope C regression

## Regression Matrix

- `RB1` new repair round reopens existing file but carries zero new mutation => fail, not assistant-only success
- `RB2` generation recovery retry starts a new round with cleared mutation history
- `RB3` same-round restore keeps current round mutation history => pass
- `RB4` `host HTML patch + ROOT + LEAF` runtime externalization package => pass
- `RB5` `LEAF` without reachable anchor and without package `ROOT` => fail
- `RB6` new `ROOT` without host patch => fail
- `RB7` `ValidationExecutorTests` passes root-relative script reference
- `RB8` `ValidationExecutorTests` passes root-relative stylesheet reference
- `RB9` missing root-relative local asset still fails deterministically

## Self-Test Gate

本轮实现完成后，只允许先做：

- targeted unit / deterministic gate tests
- code review
- docs update

不允许在 reviewer 未过审前直接跑集成测试。

## Closure Decision

可以一次性收口。

原因：

- 3 条 blocker 都属于 `v13` 现有 owner 链上的剩余缺口，不需要扩出新架构
- 三条之间耦合是单向的，不会形成新的大范围联动面：
  - Scope A 修 repair evidence owner
  - Scope B 修 detail legality rule
  - Scope C 修 validation resource normalization

本轮不需要扩大到新的 `v14`，但必须一次把这 3 条都收完。

## Review Questions

请 reviewer 只审下面 4 点：

1. Scope A 是否真正删除了“新 round 复用旧 mutation history”的旧路径
   包括 completed-plan reopen、revision retry、generation recovery retry、file-scoped retry 这四类入口
2. Scope B 的 package-level anchor 语义是否足够精确，没有重新长出 heuristic
3. Scope C 是否真正复用了单一 root-relative 解析规则，而不是 validation 自己再保留一套
   并且主 regression owner 是否明确落在 `ValidationExecutorTests`
4. 这份 remediation plan 是否仍然严格限制在 3 条 latest blocker，没有范围漂移
