# Review V14 Planning / Tool-Loop Progress

## Purpose

这份文档是 [review-v14-planning-toolloop-closure-plan.md](/home/linus/workspace/forge/docs/review-v14-planning-toolloop-closure-plan.md) 的唯一执行 tracker。

它只负责：

- 把 `v14` 的两条主线拆成可打勾 checklist
- 记录当前执行状态、blocker、完成门槛
- 固定实现顺序，避免做成 gate-only 或 tool-only 的半收口
- 为后续 `self-test / code review / docs` 留下证据位

## Rules

- 只跟踪 `review-v14` 这一轮 planning / tool-loop closure，不混写其他整改
- 本 tracker 通过审阅后，才进入代码实现
- Scope 1 与 Scope 2 必须一起实现收口，不允许只改其中一半
- 同一方案内，代码全部实现完成后再一次性提交，不分段提交半成品
- 每个 scope 开始前先更新本文档
- 每完成一项，立即打勾并补证据
- 如果 blocker 或 scope 变化，先更新本文档，再继续改代码
- 不允许把“后续再清理”写进 checklist
- code review 通过之前，不允许进入黄金路径集成测试

## Final State

完成态必须同时满足：

- `S1` outline producer / retry / gate 对 capability partition 与 shared-file deferred boundary 只有一套边界语义
- `S2` live execution file contract 只在 attempt 入口现算一次，并成为 task package / prompt / permission / tools / closure 的唯一语义来源
- `S3` planning-time `FileChange.action` 不再直接充当 live execution 语义
- `S4` existing-file completion 只认当前 round 的成功 mutation evidence，且当前文件状态必须等于 latest terminal state
- `R1 ~ R8` 全部有对应回归
- 完成 `self-test + code review + docs`
- reviewer 通过后，才允许重新进入黄金路径集成测试

## Removal Plan

本轮必须删除或封死：

- outline prompt / retry 只做 prose 提醒，但 deterministic gate 才真正知道 capability boundary 的旧状态
- shared-file full defer 只在 gate 层成立，producer / retry 不共享同一结构语义的旧路径
- planning-time `FileChange.action=WRITE` 直接驱动 live existing-file execution 的旧语义
- `TaskPackage` / prompt / permission / tools / closure 各自保留一套文件执行语义的双轨
- existing file 的 whole-file `Write/Edit` 失败后仍可能被记成 declared change satisfied 的旧路径
- 只要文件存在、或只要历史 mutation 存在，就允许 existing-file assistant-only completion 的宽判定

## Scope Checklist

### Scope 1. Outline Capability Partition Closure

- [ ] `ImplementationOutlinePromptBuilder` 把 capability partition / shared-file full defer 收成明确结构 contract
- [ ] `ImplementationPlanningPromptAssembler` / `ImplementationPlanningRepairSupport` 把 deterministic gate 产出的结构性问题原样回注到 retry prompt
- [ ] `ImplementationPlanner` / `ImplementationPlanningFeedbackRouter` 不再把同类失败降成一般性 prose repair
- [ ] `ImplementationPlanCoverageAnalyzer` / `ImplementationOutlineGate` / `ImplementationPlanGate` 对 owner overlap 与 full defer 使用同一套边界语义
- [ ] outline producer 不再稳定产出 overlap owner / partial defer 这两类 deterministic illegal shape
- [ ] `R1` 同一 capability 被两个 subtasks 同时声明为 `ownedCapabilities` => fail
- [ ] `R2` shared-file future owner 的 `ownedCapabilities` 只 defer 一部分 => fail
- [ ] `R3` shared-file future owner 的完整能力集被 defer => pass
- [ ] Scope 1 `self-test`
- [ ] Scope 1 `code review`
- [ ] Scope 1 `docs`

### Scope 2. Live Execution File Contract Closure

- [ ] 引入单一 execution file contract materializer，只基于 accepted/effective structured change-set + live workspace state 产出当前 attempt contract
- [ ] 明确 materialized contract 不进入 snapshot/state 持久化链；resume / retry 重新进入 attempt 时必须现算
- [ ] `TaskPackage` / `TaskPackageAssembler` / `TaskPackageMarkdownRenderer` 只展示 materialized contract，不再直接渲染 raw `subtask.changes()`
- [ ] `ImplementationToolPromptBuilder` 的 `Current File Contracts` 只展示 materialized contract
- [ ] `ImplementationToolPermissionContext` / `ToolExecutionContext` / `ImplementationToolContext` / `ImplementationMutationContractGuard` 只消费 materialized contract，不再直接吃 raw `scopedChanges`
- [ ] `ImplementationToolPermissionPolicy` / `FileWriteTool` / `FileEditTool` 对 existing file 统一执行 `patch-existing` 语义；whole-file rewrite 仅显式 `REWORK` 允许
- [ ] `ImplementationToolLoopExecutor` 的 declared-changes closure 只认当前 round 成功 mutation evidence
- [ ] existing-file assistant-only completion 还必须校验当前文件状态等于 latest terminal state
- [ ] failed `Write/Edit` 不再生成 completion evidence
- [ ] `R4` live attempt 遇到 planning-time `WRITE` 但文件已由前序 subtask 创建 => materialize 为 `patch-existing`
- [ ] `R5` existing file + whole-file `Write` denied + no successful patch mutation => declared changes not satisfied
- [ ] `R6` existing file + successful targeted patch + current state matches latest terminal state => declared changes satisfied
- [ ] `R7` failed `Write/Edit` 不生成 completion evidence
- [ ] `R8` `TaskPackage` / prompt 中 existing file 不再继续展示为 raw `WRITE whole-file` contract
- [ ] Scope 2 `self-test`
- [ ] Scope 2 `code review`
- [ ] Scope 2 `docs`

### Scope 3. Final Verification Gate

- [ ] Scope 1 与 Scope 2 代码全部完成
- [ ] 只跑 deterministic unit / gate tests，不跑集成测试
- [ ] 完成一次完整 code review
- [ ] 更新 `docs/current-state.md`
- [ ] 更新 `docs/active-work-items.md`
- [ ] reviewer 审核通过后，才允许进入黄金路径集成测试

## Current Status

- 当前阶段：`TRACKER_PENDING_REVIEW`
- 当前 blocker：`无新的代码 blocker；等待 reviewer 确认 tracker 是否与 v14 方案完全对齐`
- 当前执行方案： [review-v14-planning-toolloop-closure-plan.md](/home/linus/workspace/forge/docs/review-v14-planning-toolloop-closure-plan.md)
- 当前约束：`禁止兼容层、禁止双轨并存、禁止 fallback、禁止“后续再清理”、禁止 review 前集成测试`

## Evidence Log

### Scope 1

- commit：`待提交`
- self-test：`待执行`
- code review：`待执行`
- docs：`tracker 已创建，等待 reviewer 审阅`

### Scope 2

- commit：`待提交`
- self-test：`待执行`
- code review：`待执行`
- docs：`tracker 已创建，等待 reviewer 审阅`

### Scope 3

- commit：`待提交`
- self-test：`待执行`
- code review：`待执行`
- docs：`tracker 已创建，等待 reviewer 审阅`

## Completion Gate

- [ ] `S1` outline capability boundary 单轨收口
- [ ] `S2` live execution file contract 单轨收口
- [ ] `S3` planning-time action 不再直连 live execution semantics
- [ ] `S4` existing-file closure 只认当前 round 成功 mutation evidence + latest terminal state
- [ ] `R1 ~ R8` 全部补齐
- [ ] `self-test + code review + docs` 全部补齐
- [ ] reviewer 通过后才进入黄金路径集成测试

结果：`PENDING_TRACKER_REVIEW`

## Review Focus

请 reviewer 重点只审下面 5 点：

1. 这份 tracker 是否与 `review-v14-planning-toolloop-closure-plan.md` 完全对齐，没有漏 owner
2. Scope 1 是否已经覆盖 outline producer / retry / gate 的完整 owner 链，不会落成 gate-only 修法
3. Scope 2 是否已经覆盖 task package / prompt / permission / tools / closure 的完整 owner 链，不会落成上层单轨、底层双轨
4. “materialized contract attempt 入口现算、不持久化到 snapshot/state” 这条边界是否已经在 tracker 中写死
5. 这份 tracker 是否仍然遵守 `AGENTS.md` 与 `engineering-agreements.md`，没有引入 fallback、过渡层或“后续再清理”
