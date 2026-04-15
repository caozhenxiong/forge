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

- [ ] 明确 canonical repair predicate 的唯一 owner
- [ ] `ImplementationPlanRunner` / `SubtaskExecutionContext` 不再把 prose note 误转成 repair 语义
- [ ] `SubtaskExecutionState` 成为 repair predicate 的唯一状态 owner，或由单一 resolver 只从它解析
- [ ] `ImplementationResumePolicy` / `ImplementationStateSnapshotSerializer` / `ImplementationSnapshotRestorer` 与 live execution 共享同一 repair predicate 语义
- [ ] `ImplementationToolLoopExecutor` / `ImplementationToolPermissionPolicy` 只消费同一 canonical repair predicate，不再各自保留 heuristic
- [ ] Scope 1 `self-test`
- [ ] Scope 1 `code review`
- [ ] Scope 1 `docs`

### Scope 2. Existing-File Closure Semantics

- [ ] `writeSatisfied(...)` / closure logic 不再把“文件存在”视为 completion
- [ ] existing-file assistant-only completion 只认当前/恢复后的 canonical mutation history
- [ ] existing-file assistant-only completion 还必须校验当前文件状态等于 latest terminal state
- [ ] current state 已偏离 latest terminal state 时，即使 history 存在也必须失败
- [ ] `ImplementationToolSessionState` / snapshot serialize / restore 保持同一 mutation history 语义
- [ ] Scope 2 `self-test`
- [ ] Scope 2 `code review`
- [ ] Scope 2 `docs`

### Scope 3. Runtime Path Consistency

- [ ] 抽单一 root-relative runtime path normalization 规则
- [ ] `PlanningRuntimeFactsResolver` / `RuntimeScriptGraphInspector` / `HtmlEntryRuntimeOwnershipInspector` 统一使用同一规则
- [ ] `PlanningRuntimeFactsResolverTests` 覆盖 planning 侧 root-relative 场景
- [ ] `WebRuntimeWiringCheckTests` 覆盖 runtime/ownership 侧 root-relative script / import 场景
- [ ] `planning/runtime/ownership` 对同一路径事实不再分裂
- [ ] Scope 3 `self-test`
- [ ] Scope 3 `code review`
- [ ] Scope 3 `docs`

### Scope 4. Planning Runtime Facts Boundary

- [ ] 删除 `resolveCurrentRuntimeRoots()` 这类目录扫描 sibling/orphan runtime roots 的事实来源
- [ ] planning facts 只允许来自 explicit contract / accepted continuation contract / 当前 HTML 已观察到的 wiring facts
- [ ] `ImplementationOutlineGate` 只对已知 root / accepted root 做 completeness gate
- [ ] `reachable anchor + brand-new runtime root + no host patch` 不在 outline 用 heuristic 判定
- [ ] brand-new runtime root 统一下沉到 detail `runtimeScriptRole`
- [ ] `ImplementationPlanChangeGate` / `ImplementationSubtaskDetailGate` 对 outline/detail 边界保持同一语义
- [ ] Scope 4 `self-test`
- [ ] Scope 4 `code review`
- [ ] Scope 4 `docs`

### Scope 5. Regression Matrix

- [ ] `R1` fresh implementation prose note 非空，但第一轮 `repairMode=false`
- [ ] `R2` tool loop / permission policy 对同一 execution state 得出一致的 repair 结论
- [ ] `R3` resumed / restored execution state 继续保留同一 canonical repair predicate 语义
- [ ] `R4` existing broken file + no mutation => declared changes not satisfied
- [ ] `R5` canonical mutation history 存在且 current state matches latest terminal state => assistant-only completion allowed
- [ ] `R6` canonical mutation history 存在但 current state drifted => declared changes not satisfied
- [ ] `R7` `/index.app.js` 或等价 root-relative path 在 planning/runtime/ownership 三处归一结果一致
- [ ] `R8` `WebRuntimeWiringCheckTests` 覆盖 root-relative runtime script / import，不再让 runtime/ownership 侧回退
- [ ] `R9` inline host 未引用 sibling runtime script 时，planning 不再把 sibling script 当成 runtime root；brand-new root 只能在 detail `runtimeScriptRole` 处结构化进入
- [ ] Scope 5 `self-test`
- [ ] Scope 5 `code review`
- [ ] Scope 5 `docs`

## Current Status

- 当前阶段：`READY_FOR_TRACKER_REVIEW`
- 当前 blocker：`等待 tracker 审阅通过，尚未进入实现`
- 当前约束：`禁止兼容层、禁止双轨并存、禁止 fallback、禁止“后续再清理”`

## Evidence Log

### Scope 1

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`tracker 已创建，待审阅`

### Scope 2

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`tracker 已创建，待审阅`

### Scope 3

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`tracker 已创建，待审阅`

### Scope 4

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`tracker 已创建，待审阅`

### Scope 5

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`tracker 已创建，待审阅`

## Deferred Backlog Guard

下面两条只允许留在 backlog，不允许并入本轮 tracker：

- `M1` `TestExecutor targeted reverification drift`
- `M2` `Empty owner contract / weak capability partition`

只有当 `v13 high-risk` 收完且有新的 code review / 执行证据时，才允许单独起下一轮方案。

## Completion Gate

- [ ] `S1` fresh vs repair boundary 单轨收口
- [ ] `S2` existing-file closure 只认 canonical mutation terminal state
- [ ] `S3` root-relative runtime path 在 planning/runtime/ownership 单轨收口
- [ ] `S4` planning runtime facts boundary 在 outline/detail 单轨收口
- [ ] `R1 ~ R9` 全部补齐
- [ ] `self-test + code review + docs` 全部补齐

结果：`NOT_STARTED`

## Review Focus

请 reviewer 重点只审下面 4 点：

1. 这份 tracker 是否与 `review-v13-high-risk-convergence-plan.md` 完全对齐，没有漏 owner
2. Scope 1 ~ 4 的 checklist 是否已经足够约束实现顺序
3. `R1 ~ R9` 是否足够钉住本轮 high-risk，不会再让问题半收口
4. 是否还有不该进入本轮 tracker 的范围漂移
