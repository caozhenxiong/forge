# Review V14: Planning / Tool-Loop Closure Plan

## Summary

- 这份方案只处理当前黄金路径集成里已经明确暴露出的两条第一阻塞链。
- 它不继续扩到 runtime wiring、TEST false negative、review routing 等其他问题面。
- 当前要收的是：
  1. `v181` 暴露的 `outline capability partition / shared-file deferred boundary` 失稳
  2. `v180` 暴露的 `planning-time file change` 与 `live execution contract` 双轨分裂，以及由此导致的 existing-file whole rewrite 空转与假收口

## Evidence

### E1. `v181`：失败发生在 `IMPLEMENTATION / OUTLINE`

- 项目：`/home/linus/workspace/tetris_test_itest_v181`
- run：`30a342ea-f5d5-47f7-9811-3aa5ae27f56e`
- 证据：
  - [events.log](/home/linus/workspace/tetris_test_itest_v181/.devflow/runs/30a342ea-f5d5-47f7-9811-3aa5ae27f56e/events.log)
  - [attempt-1 raw outline](/home/linus/workspace/tetris_test_itest_v181/.devflow/runs/30a342ea-f5d5-47f7-9811-3aa5ae27f56e/implementation_planning_outline-outline.raw.attempt-1.txt)
  - [attempt-2 raw outline](/home/linus/workspace/tetris_test_itest_v181/.devflow/runs/30a342ea-f5d5-47f7-9811-3aa5ae27f56e/implementation_planning_outline-outline.raw.attempt-2.txt)
  - [attempt-3 raw outline](/home/linus/workspace/tetris_test_itest_v181/.devflow/runs/30a342ea-f5d5-47f7-9811-3aa5ae27f56e/implementation_planning_outline-outline.raw.attempt-3.txt)

当前事实：

- outline 连续 3 次都被 `capability partition` 打回
- 模型反复产出：
  - 同一 capability 被多个 subtask 同时声明为 `ownedCapabilities`
  - 共享 `index.html` / `src/game.js` 时，没有把后续 owner 的完整能力集写入 `deferredCapabilities`
- 这说明当前问题不是 coder、runtime wiring 或 test，而是 outline producer 本身仍会稳定产出 deterministic gate 明确禁止的结构

### E2. `v180`：失败发生在 implementation 执行期

- 项目：`/home/linus/workspace/tetris_test_itest_v180`
- run：`fc9dbc0a-12d7-457b-8313-78d548951d3d`
- 证据：
  - [events.log](/home/linus/workspace/tetris_test_itest_v180/.devflow/runs/fc9dbc0a-12d7-457b-8313-78d548951d3d/events.log)
  - [task_packages.md](/home/linus/workspace/tetris_test_itest_v180/.devflow/runs/fc9dbc0a-12d7-457b-8313-78d548951d3d/task_packages.md)
  - [subtask-2 accepted detail](/home/linus/workspace/tetris_test_itest_v180/.devflow/runs/fc9dbc0a-12d7-457b-8313-78d548951d3d/implementation_planning_subtask-subtask-2.accepted.json)
  - [implementation_state.json](/home/linus/workspace/tetris_test_itest_v180/.devflow/runs/fc9dbc0a-12d7-457b-8313-78d548951d3d/implementation_state.json)

当前事实：

- `subtask-2` 的 accepted detail 直接把 `index.html` 和 `src/game.js` 都声明成了 `WRITE`
- 进入执行时，`index.html` 已经被 `subtask-1` 创建，所以 `subtask-2` 实际面对的是 existing file
- tool loop 随后反复：
  - 对 existing `index.html` 发起 whole-file `Write/Edit`
  - 被工具侧拒绝
  - 最后又把回合收成 `declared-changes-satisfied`
- 这说明 `v180` 的根因不是单点工具 bug，而是：
  - planning-time `FileChange.action`
  - live workspace state
  - tool permission / prompt
  - closure 判定
  这四段没有共享同一份 execution file contract

## Final State

本轮完成态必须同时满足下面 2 条闭环。

### FS1. Outline capability boundary 单轨收口

- outline producer 只能产出一类合法结构：
  - 每个 capability 在 outline 中只能有一个 current owner
  - 如果共享文件会被后续子任务继续修改，当前 subtask 必须把每个 downstream owner 的完整 `ownedCapabilities` 写进 `deferredCapabilities`
  - 如果做不到，就必须在 outline 层重新拆分 targetPaths，而不是靠 prose 模糊过去
- outline / final plan gate / feedback reroute / retry prompt 必须共享同一套边界语义
- 不允许继续出现“gate 知道什么叫 shared-file full defer，但 outline prompt / retry feedback 没把这条规则压死”的双轨

### FS2. Existing-file execution contract 单轨收口

- planning-time `FileChange.action` 不能再直接充当 live tool-loop 的执行语义
- 进入每个 subtask attempt 时，系统必须先基于：
  - accepted structured change-set
  - 当前 workspace 文件存在性
  - 当前 subtask execution state
  产出唯一一份 canonical execution file contract
- 这份 contract 至少要把文件分成：
  - `create-new`
  - `patch-existing`
  - `delete`
- 下游只有这一份 contract：
  - `TaskPackage / Current File Contracts`
  - tool permission
  - `Write/Edit` whole-file 限制
  - declared-changes closure
  才允许消费
- 对 existing file：
  - whole-file rewrite 只有显式 `REWORK` 才允许
  - `PATCH / INCREMENTAL` 下只允许局部 patch
  - failed `Write/Edit` 绝不能再被记成 declared change satisfied

## Removal Plan

本轮必须同步删除或封死下面这些旧语义。

### RP1. Planning 侧

- outline prompt 里只“提醒” shared-file full defer，但 producer 仍可稳定产出 overlap owner 的旧语义
- final gate 知道 capability boundary，但 outline retry 仍允许模型反复输出同类非法结构的旧路径
- shared-file boundary 只在 deterministic gate 层成立，outline producer / retry feedback 没把这条边界变成硬约束的旧状态

### RP2. Execution 侧

- 把 planning-time `FileChange.action=WRITE` 直接拿来驱动 live existing-file execution 的旧语义
- `TaskPackage` / coder prompt 不区分 `create-new` 与 `patch-existing`，只展示原始 `action=WRITE`
- tool loop 在 whole-file `Write/Edit` 失败后，仍可能把子任务收成 `declared-changes-satisfied` 的旧语义
- existing file 的成功判定仍允许脱离当前 round 的真实成功 mutation evidence 的旧路径

## Joint-Change Scope

这两条问题不能拆开做。只修 planning，不修 live execution contract，`v180` 会原样复发；只修 tool-loop，不修 outline producer，`v181` 仍会在 coder 前被打死。

### Scope 1. Outline Capability Partition Closure

- [ImplementationOutlinePromptBuilder.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationOutlinePromptBuilder.java)
- [PlanningBoundaryContractPromptSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/PlanningBoundaryContractPromptSupport.java)
- [ImplementationPlanningPayloadParser.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanningPayloadParser.java)
- [ImplementationPlanner.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanner.java)
- [ImplementationPlanningFeedbackRouter.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanningFeedbackRouter.java)
- [ImplementationPlanCoverageAnalyzer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanCoverageAnalyzer.java)
- [ImplementationOutlineGate.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationOutlineGate.java)
- [ImplementationPlanGate.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/planning/ImplementationPlanGate.java)
- [ImplementationPlanGateTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/ImplementationPlanGateTests.java)
- planning outline / gate regression tests

### Scope 2. Live Execution File Contract Closure

- [SubtaskExecutionState.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskExecutionState.java)
- [SubtaskExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/SubtaskExecutor.java)
- [TaskPackage.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/subtask/TaskPackage.java)
- [ImplementationToolPromptBuilder.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolPromptBuilder.java)
- [ImplementationToolLoopExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolLoopExecutor.java)
- [ImplementationToolPermissionPolicy.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/tools/ImplementationToolPermissionPolicy.java)
- [FileWriteTool.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/tools/FileWriteTool.java)
- [FileEditTool.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/tools/FileEditTool.java)
- tool-loop / existing-file closure regression tests

## Problem / Solution Map

### P1. Outline producer 仍会反复产出 deterministic gate 明确禁止的 capability partition

#### Problem

- `ImplementationPlanCoverageAnalyzer` 已经能精确指出 overlap owner 与 shared-file full defer 缺口
- 但 `ImplementationOutlinePromptBuilder` / `PlanningBoundaryContractPromptSupport` 目前只是“规则提醒”，没有把 illegal shape 压成更强的 producer contract
- `ImplementationPlanner` / `ImplementationPlanningFeedbackRouter` 的重试链也还没有明确把这类失败收成“outline boundary contract 重规划”，而不是一般性 prose 修修补补

#### Required Fix

- outline prompt 必须把 capability partition 收成明确结构 contract，而不是泛泛提示
- outline retry feedback 必须直接回注 deterministic gate 产出的结构性问题，不再让模型用重新措辞逃过同一约束
- final gate 与 outline gate 的 capability boundary 语义必须保持同一套 wording / owner 规则
- 如果 shared-file future boundary 需要完整 defer，就在 prompt 和 retry contract 里明确写死，不允许只靠 gate 末端兜底

### P2. planning-time `WRITE` 与 live existing-file execution 语义分裂

#### Problem

- `v180` 证明 accepted detail 里的 `WRITE index.html` 在 planning 时看似合法
- 但执行到该 subtask 时，`index.html` 已经由前序 subtask 创建，live 语义已经不是 `create-new`
- 当前链路仍把 planning-time action 直接带进：
  - `TaskPackage`
  - coder prompt
  - tool loop declared change closure
- 这会把“未来会存在的共享文件”错误地继续当成 `whole-file WRITE` 目标

#### Required Fix

- 新增单一的 execution file contract materializer / resolver
- 它只做一件事：
  - 接收 structured change-set + live workspace state
  - 产出当前 attempt 唯一有效的 `create-new / patch-existing / delete` contract
- 之后：
  - `TaskPackage` 只展示 materialized contract
  - `Current File Contracts` 只展示 materialized contract
  - tool permission 与 closure 只消费 materialized contract
- 不允许再让 raw `FileChange.action` 直接控制 live existing-file semantics

### P3. failed Write/Edit 与 declared-changes-satisfied 没有走同一条证据链

#### Problem

- `v180` 中 repeated `Write/Edit` 明确失败
- 但 tool loop 后段仍能把子任务记为 `declared-changes-satisfied`
- 这说明 completion gate 没有以“当前 round 的成功 mutation evidence + current state”作为唯一证据

#### Required Fix

- 对每个 materialized contract：
  - `create-new` 只认当前 round 成功 create/update mutation
  - `patch-existing` 只认当前 round 成功 patch mutation，且 current state 与 latest terminal state 一致
  - `delete` 只认当前 round 成功 delete mutation
- failed tool call 不得生成可被 closure 复用的成功证据
- 对 existing file：
  - 没有当前 round 成功 mutation，就不允许 assistant-only completion
  - 不允许再保留“工具都失败了，但 declared changes 还是 satisfied”的口子

## Implementation Order

### Phase 1. Outline Capability Partition Closure

- 收紧 outline producer contract
- 收紧 outline retry feedback owner
- 让 outline gate / final gate / feedback router 使用同一边界语义
- 补 planning regressions

### Phase 2. Live Execution File Contract Closure

- 引入单一 execution file contract materializer
- 让 `TaskPackage` / prompt / tool permission / closure 共用它
- 删除 raw `FileChange.action` 直连 live execution semantics 的旧路径
- 补 existing-file / failed-write / assistant-only regressions

### Phase 3. Self-Test And Review

- 只跑 deterministic unit / gate tests
- 做一次 code review
- 更新 `docs/current-state.md` / `docs/active-work-items.md`
- reviewer 通过后，才允许重新进入黄金路径集成测试

## Regression Matrix

- `R1` outline 中同一 capability 被两个 subtasks 同时声明为 `ownedCapabilities` => fail
- `R2` shared-file future owner 的 `ownedCapabilities` 只 defer 一部分 => fail
- `R3` shared-file future owner 的完整能力集被 defer => pass
- `R4` live attempt 遇到 planning-time `WRITE` 但文件已由前序 subtask 创建 => materialize 为 `patch-existing`
- `R5` existing file + whole-file `Write` denied + no successful patch mutation => declared changes not satisfied
- `R6` existing file + successful targeted patch + current state matches latest terminal state => declared changes satisfied
- `R7` failed `Write/Edit` 不生成 completion evidence
- `R8` `TaskPackage` / prompt 中 existing file 不再继续展示为 raw `WRITE whole-file` contract

## Out Of Scope For This Round

下面问题是真实存在的，但不是当前两条第一阻塞链，不在本轮一并实现：

- runtime wiring continuation / ownership 的后续稳定性
- TEST / CODE_REVIEW false negative
- shared-file capability ownership 升级到 path 级协议
- 更大范围的 diff engine / editing primitive 替换

## Closure Decision

可以一次性收口，但前提是必须按上面两条主线一起改。

原因：

- `v181` 如果不先修 outline producer，下一轮仍然到不了 coder
- `v180` 如果不同时修 live execution contract，下一轮即使通过 outline，也会再次掉进 existing-file whole rewrite / false closure
- 这两条一起改，仍然属于同一层 implementation closure，不需要扩到新的架构重构

## Risks / Blockers

- 如果只改 gate，不改 outline prompt / retry owner，会继续出现“deterministic gate 很严格，但模型一直稳定撞墙”的半收口
- 如果只改 tool-loop closure，不改 execution file contract materialization，会继续出现“planning-time WRITE 与 live existing-file patch 语义冲突”的双轨
- 如果只改 prompt，不改 tool permission / closure owner，会继续出现“模型被提示 patch-existing，但系统内部仍按 raw WRITE 判定”的第二轨

## Review Questions

请 reviewer 重点只审下面 5 点：

1. `v181` 这条是否已经被准确收敛成 outline capability partition / shared-file deferred boundary 问题，而不是 runtime wiring 或 coder 问题
2. `v180` 这条是否已经准确识别为 `planning-time FileChange` 与 `live execution file contract` 双轨分裂，而不是单纯 closure 小 bug
3. Scope 1 是否覆盖了 outline producer / gate / reroute 的完整 owner 链，没有只改末端 gate
4. Scope 2 是否真正把 prompt / permission / tools / closure 统一到同一份 materialized execution contract，而不是继续双轨
5. 这份方案是否仍然遵守 `AGENTS.md` 与 `engineering-agreements.md`，没有引入 fallback、过渡层或“后续再清理”
