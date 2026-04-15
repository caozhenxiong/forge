# Review V13: High-Risk Convergence Plan

## Summary

- 这份文档只处理当前 reviewer 已明确指出、且与 `v180` 集成失败直接相关的风险分层。
- 本轮不继续改代码，不跑集成测试；先把 `high-risk / medium-risk` 问题族收成正式方案，作为后续 code review 和实现的唯一入口。
- 当前结论不是“再试一次”，而是“先把第一阻塞链文档化并过审，再决定实现”。

## Final State

本轮完成态必须同时满足下面 4 条高风险闭环：

1. fresh implementation 子任务不会因为上游 prose note 非空而误入 repair mode。
2. repair / continuation 回合的 existing-file completion 只认当前或恢复后的 canonical mutation history，并且要求当前文件状态等于该 history 的 latest terminal state；不允许 tool-loop 以“文件存在”或“猜测当前工作区语义已正确”判定完成。
3. runtime planning / runtime graph / ownership inspector 对 root-relative runtime path 使用同一套路径归一规则，不再出现“一层已接线、另一层 unreachable”的事实冲突。
4. planning runtime facts 不再通过目录扫描 sibling/orphan runtime root 推导事实，只允许基于：
   - explicit runtime contract
   - accepted / continuation scoped contract
   - 当前 HTML 已观察到的结构化 wiring facts
   - 对于 brand-new runtime root，outline 不再猜测；统一由 detail 阶段的 `runtimeScriptRole` 结构化声明并校验

完成后，系统对当前问题族的单一语义应是：

- `fresh implementation` 与 `repair implementation` 的进入条件单一且显式，并且只有一个 canonical repair predicate owner
- `existing file patch round` 不会零 mutation 假完成；existing-file assistant-only completion 只能建立在当前/恢复后的 canonical mutation history 上，且当前文件状态必须等于该 history 的 latest terminal state
- `/index.app.js`、`/src/game.js` 这类 root-relative 引用在 planning / runtime / ownership 上事实一致
- planning 不再把未接线 sibling script 当成当前 runtime root 事实
- outline 与 detail 的 runtime root 边界单一：
  - outline 只对已知 root / accepted root 做 completeness gate
  - brand-new root 统一由 detail 的 `runtimeScriptRole` 校验

## Removal Plan

本轮必须删除或封死下面这些旧语义：

- `ImplementationToolLoopExecutor.isRepairMode(...)` 中“只要 feedback 非空就进入 repair mode”的旧判定。
- `ImplementationToolLoopExecutor.writeSatisfied(...)` 中“repair mode 下 existing file 只要存在即 satisfied”的旧判定。
- `PlanningRuntimeFactsResolver.resolveCurrentRuntimeRoots(...)` 通过目录树扫描所有 runtime script 生成 root facts 的旧路径。
- `RuntimeScriptGraphInspector` / `HtmlEntryRuntimeOwnershipInspector` 对 `/foo.js` 这类 root-relative 路径直接 `basePath.resolve(...)` 的旧解析方式。
- `planning` 继续把 orphan / sibling runtime script 作为当前 HTML 已成立事实的旧测试口径。

本轮不允许：

- 用 fallback 或 heuristic patch 掩盖上述问题
- 继续保留“先扫描目录树，再在下游 gate 修正”的双轨
- 把 fresh implementation 的 prose note 继续当 repair 触发器

## Joint-Change Scope

这 4 条 high-risk 必须联动修改，否则会形成半成品。

### Scope 1. Fresh vs Repair Mode Boundary

- [ImplementationPlanRunner.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/ImplementationPlanRunner.java)
- [SubtaskExecutionContext.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskExecutionContext.java)
- [SubtaskExecutionState.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskExecutionState.java)
- [ImplementationResumePolicy.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/ImplementationResumePolicy.java)
- [ImplementationStateSnapshotSerializer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/state/ImplementationStateSnapshotSerializer.java)
- [ImplementationSnapshotRestorer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/state/ImplementationSnapshotRestorer.java)
- [ImplementationToolLoopExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolLoopExecutor.java)
- [ImplementationToolPermissionPolicy.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/tools/ImplementationToolPermissionPolicy.java)

### Scope 2. Existing-File Closure Semantics

- [ImplementationToolLoopExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolLoopExecutor.java)
- [ImplementationToolSessionState.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolSessionState.java)
- [ImplementationStateSnapshotSerializer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/state/ImplementationStateSnapshotSerializer.java)
- [ImplementationSnapshotRestorer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/state/ImplementationSnapshotRestorer.java)
- [ImplementationToolLoopExecutorTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/ImplementationToolLoopExecutorTests.java)

### Scope 3. Runtime Path Consistency

- [PlanningRuntimeFactsResolver.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/PlanningRuntimeFactsResolver.java)
- [RuntimeScriptGraphInspector.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/runtime/RuntimeScriptGraphInspector.java)
- [HtmlEntryRuntimeOwnershipInspector.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/runtime/HtmlEntryRuntimeOwnershipInspector.java)
- [PlanningRuntimeFactsResolverTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/implementation/planning/PlanningRuntimeFactsResolverTests.java)

### Scope 4. Planning Runtime Facts Boundary

- [PlanningRuntimeFactsResolver.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/PlanningRuntimeFactsResolver.java)
- [ImplementationOutlineGate.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationOutlineGate.java)
- [ImplementationPlanChangeGate.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanChangeGate.java)
- [ImplementationSubtaskDetailGate.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationSubtaskDetailGate.java)
- [PlanningRuntimeFactsResolverTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/implementation/planning/PlanningRuntimeFactsResolverTests.java)
- 如有必要，再补 planning gate regression tests

### Out Of Scope For This Round

下面这些问题是真实存在的，但不是当前第一阻塞链，不在本轮实现范围：

- `TestExecutor` targeted reverification 在 passed + unresolved cases 下继续产出 patch review 的 medium 风险
- `ownedCapabilities=[] && deferredCapabilities=[]` 的空 owner contract medium 风险
- 更大范围的 shared-file path-level capability ownership 细化
- 更换整套 editing primitive / diff engine

这些只能记录，不在本轮顺手实现。

## High-Risk Findings

### H1. Fresh implementation 被误判成 repair mode

#### Evidence

- [StageTransitionSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/StageTransitionSupport.java)
  会把非空 prose guidance 带入 implementation 入口。
- [ImplementationPlanRunner.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/ImplementationPlanRunner.java)
  会把这份 note 合回 `sharedFeedback`。
- [SubtaskExecutionContext.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskExecutionContext.java)
  首轮会创建默认 `SubtaskExecutionState`，不是 `null executionState`。
- [ImplementationToolLoopExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolLoopExecutor.java)
  当前只要 `feedback != blank` 就返回 `repairMode=true`。

#### Impact

- fresh subtask 第一轮就套用了 repair semantics
- repair-mode 会隐藏 Bash、改变 whole-file rewrite 语义、改变 closure 语义
- 实际上把“正常实现第一轮”错误降格成“修复轮”

#### Required Fix

- repair mode 的 owner 必须单一且结构化，并达到可执行口径：
  - canonical repair predicate 必须挂在 `SubtaskExecutionState`
  - 或由单一 resolver 只从 `SubtaskExecutionState` 解析
- `ImplementationToolLoopExecutor` 与 `ImplementationToolPermissionPolicy` 只能消费同一份 canonical repair predicate，不能各自保留 if/heuristic 判定
- 如果 canonical repair predicate 挂在 `SubtaskExecutionState`，则 `resume / snapshot serialize / snapshot restore` 必须同步承载该语义，不能只改 live execution 分支
- 单纯 prose note / supervisor guidance / stage transition prose 不能触发 repair mode

### H2. Existing-file closure 过宽，可能零 mutation 假完成

#### Evidence

- [ImplementationToolLoopExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolLoopExecutor.java)
  的 `writeSatisfied(...)` 现在在 `workspaceStateClosure=true` 时，只要文件存在就返回 `true`
- 这条判定同时被 assistant-only 收口和 max-turn 收口复用

#### Impact

- existing file patch round 即使没修对，也可能因为文件存在而被当成 declared changes satisfied
- 叠加 H1 之后，会直接制造 fresh subtask 的空转假完成

#### Required Fix

- `workspaceStateClosure` 不能再让 tool-loop 猜“当前工作区语义是否已经正确”
- existing file 的 assistant-only completion 只能建立在：
  - 当前 `ImplementationToolSessionState` 中已存在的 canonical mutation history
  - 或 restore 后仍然存在于 tool session state 的 canonical mutation history
- 且必须同时满足：
  - 当前文件状态与该 canonical mutation history 的 latest `afterExists / afterHash` 一致
  - 不能只因为“history 存在”就允许 completion
- 对 existing file：
  - 若没有 canonical mutation history，则 zero-mutation existing-file completion 一律失败
  - 若当前文件状态已经偏离 latest terminal state，则即使 mutation history 存在也必须失败
  - 不允许再保留“workspace already correct + zero mutation”这种 tool-loop 层无法稳定判真的正例

### H3. Runtime graph 对 root-relative 路径事实不一致

#### Evidence

- [PlanningRuntimeFactsResolver.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/PlanningRuntimeFactsResolver.java)
  已支持 `/foo.js` -> project-relative path
- [RuntimeScriptGraphInspector.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/runtime/RuntimeScriptGraphInspector.java)
  仍直接 `basePath.resolve(rawRef)`
- [HtmlEntryRuntimeOwnershipInspector.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/runtime/HtmlEntryRuntimeOwnershipInspector.java)
  的 import 解析仍是同样旧语义

#### Impact

- planning 侧和 runtime / ownership 侧会对同一引用得出不同事实
- 这会持续制造：
  - “planning 认为已 reachable”
  - “ownership / wiring gate 认为 unreachable or orphan”

#### Required Fix

- runtime path normalization 必须单一：
  - root-relative path 一律先归一成 project-relative candidate
  - planning / runtime graph / ownership inspector 全部复用同一规则

### H4. Planning 仍在扫描 sibling/orphan runtime script 生成 facts

#### Evidence

- [PlanningRuntimeFactsResolver.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/PlanningRuntimeFactsResolver.java)
  当前仍会调用 `resolveCurrentRuntimeRoots()` 通过目录树扫描 root scripts
- [PlanningRuntimeFactsResolverTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/implementation/planning/PlanningRuntimeFactsResolverTests.java)
  目前还锁着“inline host 未引用 sibling `index.app.js` 但 runtimeRootPaths 仍包含它”的旧语义

#### Impact

- planning 还在把“当前 HTML 根本没接线的 sibling/orphan script”当成事实
- 会持续制造：
  - 错误 accepted package
  - 错误 runtime ownership
  - 错误后续 patch / review 目标

#### Required Fix

- planning runtime facts 只允许来自：
  - explicit contract
  - accepted / continuation scoped runtime contract
  - 当前 HTML 已观察到的 structured wiring facts
- 禁止再通过目录扫描 sibling/orphan script 推事实
- outline / detail 的边界必须写死：
  - outline 只对已知 root / accepted root 做 completeness gate
  - `reachable anchor + brand-new runtime root + no host patch` 这类场景，不在 outline 用 heuristic 判定
  - brand-new runtime root 统一下沉到 detail，并由 `runtimeScriptRole` 作为唯一结构化入口
- 本轮不为 outline 额外引入第二套 runtime role schema

## Medium-Risk Findings

### M1. `TestExecutor` 可能在 `passed + unresolved target` 下继续产出 patch review

这条 reviewer 结论成立，但当前 `v180` 还没走到 TEST，因此不应和 high-risk 主线混做一轮。

处理策略：

- 本轮记录，不实现
- 等 high-risk 主线收完后，再决定是否单独出 `v14`

### M2. capability partition 仍允许空 owner contract

这条 reviewer 结论也成立，但它更像 boundary review 和 planning quality 的次级放大器，不是 `v180` 的直接首因。

处理策略：

- 本轮记录，不实现
- 后续如果继续收 planning 边界，再单列 scope

## Deferred Medium-Risk Backlog

这两条中风险不是“不处理”，而是“本轮不并入 high-risk 主线实现”。后续是否升级，按下面规则执行。

### Backlog Item A. `TestExecutor targeted reverification drift`

- 对应问题：`M1`
- 当前状态：记录，不纳入 `v13` 实现
- 升级条件：
  - high-risk 4 条已收口，但集成仍在 `TEST / targeted reverification` 阶段被打回
  - 或 code review / logs 明确显示 `passed + unresolved target` 仍被错误翻译成 patch review
- 下一轮入口：
  - 单独起 `v14` 或后续独立方案
  - 只改 `TestExecutor / ExperienceFailureDispositionResolver / targeted reverification mapping` 这条链

### Backlog Item B. `Empty owner contract / weak capability partition`

- 对应问题：`M2`
- 当前状态：记录，不纳入 `v13` 实现
- 升级条件：
  - high-risk 4 条已收口后，planning / boundary review 仍出现空 owner contract 子任务
  - 或 reviewer 明确指出 capability partition 仍允许无 owner / 无 deferred contract 的 plan 混入执行
- 下一轮入口：
  - 单独起 planning-boundary 收口方案
  - 只改 `capability partition / owner contract / boundary review` 相关链路

### Backlog Discipline

- `M1/M2` 在本轮只做记录，不允许顺手并入实现
- 只有当 high-risk 主线收完、且有新的 reviewer 证据或集成日志证据时，才允许升级
- 升级后必须单独成文档、单独过审，不得直接混进 `v13`

## Problem / Solution Map

### High-Risk 优先级

1. `H1 fresh->repair semantic drift`
2. `H2 existing-file false closure`
3. `H3 root-relative runtime fact drift`
4. `H4 planning directory-scan facts`

### Why This Order

- 不先修 `H1/H2`，fresh implementation 会继续被错误套上 repair 语义，并制造空转/假完成。
- 不修 `H3/H4`，planning 与 runtime/ownership 会继续生产冲突事实，后面就算实现链修对，也会被错事实重新打回。

## Implementation Order

### Phase 1. Fresh vs Repair Boundary

- 明确 repair mode 的唯一进入条件
- 明确 canonical repair predicate 的唯一 owner
- 把 canonical repair predicate 接进 resume / snapshot serialize / snapshot restore
- 去掉 prose note 触发 repair mode 的旧语义
- 补 tool-loop / permission policy 消费同一 repair predicate 的 regression
- 补 fresh implementation 首轮不会误入 repair mode 的 regression

### Phase 2. Existing-File Closure

- 改 `writeSatisfied(...)` / related completion logic
- 把 completion owner 收回当前/恢复后的 `toolSessionState.mutationRecords`
- 把 current file state 与 latest mutation terminal state 的一致性校验接入 closure
- 补 “existing broken file + zero mutation must fail” 的反例回归
- 补 “restored mutation history 存在且当前状态等于 latest terminal state 时 assistant-only 可闭合” 的正例回归
- 补 “mutation history 存在但当前状态已偏离 latest terminal state 时仍必须失败” 的反例回归
- 删除 “workspace already correct + zero mutation” 正例口径

### Phase 3. Root-Relative Runtime Normalization

- 抽单一路径归一逻辑
- planning / runtime graph / ownership inspector 统一使用
- 补 `/index.app.js` / `/src/game.js` 类型回归

### Phase 4. Planning Runtime Facts Boundary

- 删掉目录扫描 sibling/orphan script 生成 runtime root facts 的旧路径
- 明确 outline 只拦已知 root，brand-new root 下沉到 detail `runtimeScriptRole`
- 改测试口径，让 facts 只来自已成立 contract / 当前 HTML 观察到的 wiring
- 补 accepted package completeness regression

## Regression Matrix

### Required High-Risk Regressions

- fresh implementation prose note 非空，但第一轮 `repairMode=false`
- tool loop / permission policy 对同一 execution state 得出一致的 `repairMode` / `repair permission` 结论
- resumed execution / restored execution state 继续保留同一 canonical repair predicate 语义
- existing broken file + no mutation => declared changes not satisfied
- restored canonical mutation history present + current state matches latest terminal state => existing-file assistant-only completion allowed
- canonical mutation history present + current state drifted from latest terminal state => declared changes not satisfied
- `/index.app.js` root-relative script 在 planning/runtime/ownership 三处归一结果一致
- inline host 未引用 sibling runtime script 时，planning runtime facts 不再把 sibling script 当成 runtime root
- reachable anchor + brand-new runtime root + no host patch 不在 outline 用 heuristic 通过；必须下沉到 detail `runtimeScriptRole`

### Required Safety Regressions

- existing approved repair-mode paths 不被放松
- current runtime wiring canonical package tests 继续通过
- current continuation / patch-owner tests 继续通过

## Closure Decision

可以一次性收口，但只能限于这 4 条 `high-risk`。

本轮不能做成两段：

- 先修 H1/H2，H3/H4 后面再看
- 或先修 runtime facts，fresh/repair 语义以后再收

因为这两组问题共同组成了当前“为什么一直不过”的第一阻塞链：

- H1/H2 负责制造 fresh implementation 的空转 / 假完成
- H3/H4 负责制造 planning/runtime 的错事实

任一组留着，集成都仍然可能继续失败。

## Non-Goals

- 不在本轮重构全部 planning boundary 体系
- 不在本轮引入新的 edit primitive / diff engine
- 不在本轮处理 `TestExecutor` 的 medium 风险
- 不在本轮处理 path-level capability ownership

## Review Questions

请 reviewer 只审下面 4 个问题，不扩散：

1. `H1/H2/H3/H4` 的优先级排序是否正确
2. `Final State` 是否足够单一，没有留下第二条 owner / fallback 路径
3. `Joint-Change Scope` 是否漏掉真实 owner
4. `Regression Matrix` 是否已经能钉住这轮问题族，避免再次回退
