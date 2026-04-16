# Review V13 High-Risk Progress

## Purpose

这份文档是 [review-v13-high-risk-convergence-plan.md](/home/linus/workspace/forge/docs/review-v13-high-risk-convergence-plan.md) 的唯一执行 tracker。

它只负责：

- 把 `v13` 的 4 条 high-risk 主线拆成可打勾 checklist
- 记录当前执行状态、blocker、完成门槛
- 约束实现顺序，避免实现时再次扩 scope
- 为后续 `self-test / code review / docs` 留下固定证据位

## Rules

- 只跟踪 `review-v13` 这一轮 high-risk closure，不混写其他整改
- `M1 / M2` 只保留在 backlog，不允许顺手并入本 tracker
- 每个 scope 开始前先更新本文档
- 每完成一项，立即打勾并补证据
- 如果 blocker 或 scope 变化，先更新本文档，再继续改代码
- 不允许把“后续再清理”写进 checklist
- 只有同类问题整体收口，才允许标记 scope 完成

## Final State

完成态必须同时满足：

- `S1` fresh implementation 与 repair implementation 的进入条件只有一个 canonical repair predicate owner
- `S2` existing-file assistant-only completion 只认 canonical mutation history，且当前文件状态必须等于 latest terminal state
- `S3` planning / runtime graph / ownership inspector 对 root-relative runtime path 使用同一套归一规则
- `S4` planning runtime facts 不再扫描 sibling/orphan runtime scripts；outline 只拦已知 root，brand-new root 下沉到 detail `runtimeScriptRole`
- `R1 ~ R9` 全部有对应回归
- 完成 `self-test + code review + docs`

## Removal Plan

本轮必须删除或封死：

- `feedback != blank => repairMode=true` 的旧语义
- `existing file exists => write satisfied` 的旧语义
- `mutation history 存在 => assistant-only completion allowed` 的宽判定
- runtime graph / ownership inspector 对 root-relative path 的旧解析方式
- planning 通过目录扫描 sibling/orphan runtime roots 推事实的旧路径
- outline 对 brand-new runtime root 的 heuristic 判定

## Scope Checklist

### Scope 1. Fresh vs Repair Mode Boundary

- [x] 明确 canonical repair predicate 的唯一 owner
- [x] `ImplementationPlanRunner` / `SubtaskExecutionContext` 不再把 prose note 误转成 repair 语义
- [x] `SubtaskExecutionState` 成为 repair predicate 的唯一状态 owner，或由单一 resolver 只从它解析
- [x] `ImplementationResumePolicy` / `ImplementationStateSnapshotSerializer` / `ImplementationSnapshotRestorer` 与 live execution 共享同一 repair predicate 语义
- [x] `ImplementationToolLoopExecutor` / `ImplementationToolPermissionPolicy` 只消费同一 canonical repair predicate，不再各自保留 heuristic
- [x] Scope 1 `self-test`
- [x] Scope 1 `code review`
- [x] Scope 1 `docs`

### Scope 2. Existing-File Closure Semantics

- [x] `writeSatisfied(...)` / closure logic 不再把“文件存在”视为 completion
- [x] existing-file assistant-only completion 只认当前/恢复后的 canonical mutation history
- [x] existing-file assistant-only completion 还必须校验当前文件状态等于 latest terminal state
- [x] current state 已偏离 latest terminal state 时，即使 history 存在也必须失败
- [x] `ImplementationToolSessionState` / snapshot serialize / restore 保持同一 mutation history 语义
- [x] Scope 2 `self-test`
- [x] Scope 2 `code review`
- [x] Scope 2 `docs`

### Scope 3. Runtime Path Consistency

- [x] 抽单一 root-relative runtime path normalization 规则
- [x] `PlanningRuntimeFactsResolver` / `RuntimeScriptGraphInspector` / `HtmlEntryRuntimeOwnershipInspector` 统一使用同一规则
- [x] `PlanningRuntimeFactsResolverTests` 覆盖 planning 侧 root-relative 场景
- [x] `WebRuntimeWiringCheckTests` 覆盖 runtime/ownership 侧 root-relative script / import 场景
- [x] `planning/runtime/ownership` 对同一路径事实不再分裂
- [x] Scope 3 `self-test`
- [x] Scope 3 `code review`
- [x] Scope 3 `docs`

### Scope 4. Planning Runtime Facts Boundary

- [x] 删除 `resolveCurrentRuntimeRoots()` 这类目录扫描 sibling/orphan runtime roots 的事实来源
- [x] planning facts 只允许来自 explicit contract / accepted continuation contract / 当前 HTML 已观察到的 wiring facts
- [x] `ImplementationOutlineGate` 只对已知 root / accepted root 做 completeness gate
- [x] `reachable anchor + brand-new runtime root + no host patch` 不在 outline 用 heuristic 判定
- [x] brand-new runtime root 统一下沉到 detail `runtimeScriptRole`
- [x] `ImplementationPlanChangeGate` / `ImplementationSubtaskDetailGate` 对 outline/detail 边界保持同一语义
- [x] Scope 4 `self-test`
- [x] Scope 4 `code review`
- [x] Scope 4 `docs`

### Scope 5. Regression Matrix

- [x] `R1` fresh implementation prose note 非空，但第一轮 `repairMode=false`
- [x] `R2` tool loop / permission policy 对同一 execution state 得出一致的 repair 结论
- [x] `R3` resumed / restored execution state 继续保留同一 canonical repair predicate 语义
- [x] `R4` existing broken file + no mutation => declared changes not satisfied
- [x] `R5` canonical mutation history 存在且 current state matches latest terminal state => assistant-only completion allowed
- [x] `R6` canonical mutation history 存在但 current state drifted => declared changes not satisfied
- [x] `R7` `/index.app.js` 或等价 root-relative path 在 planning/runtime/ownership 三处归一结果一致
- [x] `R8` `WebRuntimeWiringCheckTests` 覆盖 root-relative runtime script / import，不再让 runtime/ownership 侧回退
- [x] `R9` inline host 未引用 sibling runtime script 时，planning 不再把 sibling script 当成 runtime root；brand-new root 只能在 detail `runtimeScriptRole` 处结构化进入
- [x] Scope 5 `self-test`
- [x] Scope 5 `code review`
- [x] Scope 5 `docs`

## Current Status

- 当前阶段：`REVIEW_FIXES_REQUIRED`
- 当前 blocker：`存在 3 条最新代码 review 阻塞：repair round 复用旧 mutation history、detail gate 误杀合法 runtime package、WEB_RESOURCE_LINK_CHECK 仍误判 root-relative 资源`
- 当前约束：`禁止兼容层、禁止双轨并存、禁止 fallback、禁止“后续再清理”`

## Latest Reviewer Findings

以下结论基于最新代码静态 review，当前状态不能按 `PASSED_FOR_SINGLE_COMMIT` 继续推进：

1. `repair / reopen` 轮次还在复用上一轮的 `mutation history`，会导致“本轮没改代码也能被判定收口”。
   落点：`src/main/java/devflow/agent/executor/implementation/ImplementationResumePolicy.java`、`src/main/java/devflow/agent/executor/subtask/SubtaskExecutionState.java`、`src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolLoopExecutor.java`
   问题：新的 repair round 只清 transcript，不清旧 `mutationRecords`；而 existing-file closure 现在接受“current state matches latest terminal state”，所以 reopened patch / revision retry 在当前轮零新 mutation 时，仍可能直接 assistant-only 收口。

2. planning detail gate 现在会误杀合法的“首轮 runtime externalization 完整包”。
   落点：`src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanChangeGate.java`
   问题：`runtimeScriptRole=LEAF` 目前只认“当前图里已有 reachable anchor”，不认“同包里新增的 ROOT + host patch”；因此 `index.html host patch + index.app.js(ROOT) + src/engine.js(LEAF)` 这类本应合法的一次性完整 package 会被 detail gate 误判非法。

3. root-relative 资源路径在自测链路里还没统一，合法产物会被 `WEB_RESOURCE_LINK_CHECK` 误判失败。
   落点：`src/main/java/devflow/agent/validation/WebResourceValidationSupport.java`、`src/main/java/devflow/agent/validation/ValidationExecutor.java`
   问题：runtime graph / ownership 已开始支持 `"/js/app.js"` 这类 root-relative 引用，但资源校验仍按 html 所在目录拼接路径，导致合法 root-relative script / stylesheet 仍可能被判 missing resource。

修复顺序建议固定为：

1. 先修第 1 条，确保 repair / reopen 的收口证据只来自当前 round
2. 再修第 2 条，避免 planning 把正确 runtime package 挡在入口外
3. 最后修第 3 条，统一 self-check 对 root-relative 资源的解析

## Evidence Log

### Scope 1

- commit：`待提交`
- self-test：`mvn -q -Dtest=SubtaskExecutionStateTests,ImplementationStateSnapshotSerializerTests,ImplementationResumePolicyTests,ImplementationToolPermissionPolicyTests,ImplementationToolLoopExecutorTests,devflow.agent.executor.implementation.planning.ImplementationPlanChangeGateTests,devflow.agent.executor.implementation.planning.PlanningRuntimeFactsResolverTests,ImplementationPlanGateTests,ImplementationSubtaskDetailGateTests,WebRuntimeWiringCheckTests test`
- code review：`已完成；repair predicate 已收回 SubtaskExecutionState，tool-loop / permission policy 不再各自保留 heuristic`
- docs：`tracker 已更新到可提交态`

### Scope 2

- commit：`待提交`
- self-test：`同 Scope 1`
- code review：`已完成；closure 只认 canonical mutation terminal state，未保留 “file exists => satisfied” 旧语义`
- docs：`tracker 已更新到可提交态`

### Scope 3

- commit：`待提交`
- self-test：`同 Scope 1`
- code review：`已完成；planning/runtime/ownership 统一改为同一 root-relative 归一规则`
- docs：`tracker 已更新到可提交态`

### Scope 4

- commit：`待提交`
- self-test：`同 Scope 1`
- code review：`已完成；planning 不再扫描 sibling/orphan runtime root facts，brand-new root 明确下沉到 detail runtimeScriptRole`
- docs：`tracker 已更新到可提交态`

### Scope 5

- commit：`待提交`
- self-test：`同 Scope 1`
- code review：`已完成；R1~R9 回归全部补齐且 targeted self-test 通过`
- docs：`tracker 已更新到可提交态`

## Deferred Backlog Guard

下面两条只允许留在 backlog，不允许并入本轮 tracker：

- `M1` `TestExecutor targeted reverification drift`
- `M2` `Empty owner contract / weak capability partition`

只有当 `v13 high-risk` 收完且有新的 code review / 执行证据时，才允许单独起下一轮方案。

## Completion Gate

- [x] `S1` fresh vs repair boundary 单轨收口
- [ ] `S2` existing-file closure 只认 canonical mutation terminal state
- [x] `S3` root-relative runtime path 在 planning/runtime/ownership 单轨收口
- [ ] `S4` planning runtime facts boundary 在 outline/detail 单轨收口
- [ ] `R1 ~ R9` 全部补齐
- [ ] `self-test + code review + docs` 全部补齐

结果：`REVIEW_BLOCKED`

## Review Focus

请 reviewer 重点只审下面 4 点：

1. 这份 tracker 是否与 `review-v13-high-risk-convergence-plan.md` 完全对齐，没有漏 owner
2. Scope 1 ~ 4 的 checklist 是否已经足够约束实现顺序
3. `R1 ~ R9` 是否足够钉住本轮 high-risk，不会再让问题半收口
4. 是否还有不该进入本轮 tracker 的范围漂移
