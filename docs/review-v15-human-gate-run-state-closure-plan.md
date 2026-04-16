# Review V15 Human Gate / Run-State Closure Plan

## Summary

- 这份方案只处理 `2026-04-16` 黄金路径集成运行 `c8019e26-05b0-4426-8fa1-688503c2d2a2` 暴露出的真实主问题。
- 当前主问题不是“测试没有给出方向”，而是：
  - implementation 已明确 `BLOCKED_EXHAUSTED_SUBTASK`
  - test 已明确 `REJECTED + PATCH`
  - 但人工批准链仍把当前阶段写成 `APPROVED`
  - 最终 [run.json](/home/linus/workspace/tetris_test_itest_v182/.devflow/runs/c8019e26-05b0-4426-8fa1-688503c2d2a2/run.json) 被落成 `COMPLETED + APPROVED`
- 这属于工作流真相源与人工 gate 语义冲突，不是业务实现已经通过。
- 本轮目标不是顺手补俄罗斯方块逻辑，而是先把“human gate / repair route / final run-state”这条单链收口，让后续红绿结果可信。

## Final State

本轮完成态必须同时满足：

1. `AWAITING_HUMAN_REVIEW` 不再默认等价于“等待人工批准当前阶段通过”。
2. human gate 只有一套结构化语义，并且显式区分至少两类意图：
   - `APPROVE_STAGE_GATE`
   - `CONFIRM_REPAIR_ROUTE`
3. `IMPLEMENTATION_BLOCKED_EXHAUSTED_SUBTASK`、`REJECTED + ROUTE_TO_REPAIR`、`REJECTED + PATCH` 进入人工处理后，批准动作只能恢复 repair / reroute，不能把当前阶段写成 `APPROVED`。
4. `run.json`、`events.log`、`transition_decision.md`、阶段 report 对同一轮结果只保留一套最终事实：
   - 如果走 repair：当前 run 仍是 `IN_PROGRESS` 或显式 `BLOCKED`
   - 如果走 complete：所有产物都必须显式是 `APPROVED / COMPLETED`
   - 不允许再出现 “events/report 说 REJECTED，run.json 说 APPROVED”。
5. test / code review 给出的 canonical repair package 必须能穿过 human gate，进入下一轮 implementation；不能在人工批准点丢失。

本轮收口后，`v182` 这类真实链路应变成：

- implementation blocked exhausted subtask
- human gate 记录为 `CONFIRM_REPAIR_ROUTE`
- human approve 后回到 implementation repair
- 保留 test / review 产出的结构化 patch scope
- run 不得被写成 completed

## Removal Plan

本轮必须删除或封死下面这些旧语义：

- `approveHumanReview()` = “无论为什么等人工，一律把当前阶段标成 APPROVED，再进下一阶段”的旧路径。
- `CliRunCommandHandler` 对任何 `AWAITING_HUMAN_REVIEW` 都直接调用统一 `approveStage()` 并默认视为“阶段通过”的旧语义。
- human gate 只在 `StageExecution.reviewDecision/reviewSummary/changeRequest` 上留 prose，不保留结构化 repair routing 的旧状态。
- `TEST/CODE_REVIEW/IMPLEMENTATION` 已经 `REJECTED / BLOCKED`，但后续人工批准还能把 `run.json` 收尾成 `COMPLETED + APPROVED` 的旧路径。
- 通过重新读取 markdown report / transition artifact 作为 fallback 来恢复 repair route 的思路。

不允许的修法：

- 不允许只改 `events.log` 文案，保留错误状态机。
- 不允许只改 `run.json` 收尾，保留错误 human approve 入口。
- 不允许在 approval 时“猜”应该是 approve 还是 repair。
- 不允许从 `test_report.md` / `code_review.md` 反向解析 repair package 作为主路径。

## Joint-Change Scope

下面这些 owner 必须联动修改，否则一定会留下第二轨：

### Scope 1. Human Gate Intent Protocol

- [StageExecution.java](/home/linus/workspace/forge/src/main/java/devflow/agent/domain/StageExecution.java)
- [StageStatus.java](/home/linus/workspace/forge/src/main/java/devflow/agent/domain/StageStatus.java)
- `RunRecord` / `run.json` 对应序列化链
- 新增或显式引入：
  - `HumanReviewIntent`
  - `HumanReviewResolutionContext`

### Scope 2. Human Approval Lifecycle

- [WorkflowRunLifecycleSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/WorkflowRunLifecycleSupport.java)
- [StageTransitionSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/StageTransitionSupport.java)
- [StageStatusSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/StageStatusSupport.java)
- [StageRevisionSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/StageRevisionSupport.java)
- [FlowDecisionExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/FlowDecisionExecutor.java)

### Scope 3. Repair Context Persistence

- [StageProgressCoordinator.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/StageProgressCoordinator.java)
- [ImplementationProgressSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/ImplementationProgressSupport.java)
- [ImplementationContinuationSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/ImplementationContinuationSupport.java)
- [StageRevisionRepairSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/StageRevisionRepairSupport.java)
- [StageRevisionNoteBuilder.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/StageRevisionNoteBuilder.java)

### Scope 4. Artifact / Final State Consistency

- [StageProgressArtifactSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/StageProgressArtifactSupport.java)
- [WorkflowArtifactRenderer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/WorkflowArtifactRenderer.java)
- [WorkflowEventMessages.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/WorkflowEventMessages.java)
- [TransitionReason.java](/home/linus/workspace/forge/src/main/java/devflow/agent/loop/TransitionReason.java)

### Scope 5. CLI / Autopilot Human Handling

- [CliRunCommandHandler.java](/home/linus/workspace/forge/src/main/java/devflow/agent/interfaceadapter/cli/CliRunCommandHandler.java)
- [CliOutputRenderer.java](/home/linus/workspace/forge/src/main/java/devflow/agent/interfaceadapter/cli/CliOutputRenderer.java)

### Scope 6. Regression Tests

- `WorkflowRunLifecycleSupportTests`
- `StageStatusSupportTests`
- `StageTransitionSupportTests`
- `FlowDecisionExecutorTests`
- `StageProgressCoordinatorTests`
- `DefaultWorkflowEngineTests`
- 必要时补 CLI 层回归

## Closure Decision

这轮可以一次性收口，前提是把问题限定在 human gate / run-state / repair route 单链上，不顺手扩到业务实现。

原因：

- 问题族已经收敛到一条明确责任链：
  - `StageProgressCoordinator.blockImplementationForHuman(...)`
  - `WorkflowRunLifecycleSupport.approveStage(...)`
  - `StageTransitionSupport.approveHumanReview(...)`
  - `StageStatusSupport.approveHumanReview(...)`
- 当前错误不是“很多分散 bug”，而是同一条批准入口被拿来处理两种不同语义。
- 这条问题可以通过引入单一 `HumanReviewIntent` / `HumanReviewResolutionContext` 一次性消掉旧路径。

如果 reviewer 不接受“在 run-state 中显式持久化 human gate intent / repair context”，那本轮就不该实现，应停在文档阶段；因为不持久化就只能回退到读 markdown/fallback 猜语义。

## Problem -> Solution

### Problem 1. Human approval 把“阶段 gate 批准”和“repair route 确认”混成了一个动作

#### Evidence

- `2026-04-16T13:53:54Z`
  [events.log](/home/linus/workspace/tetris_test_itest_v182/.devflow/runs/c8019e26-05b0-4426-8fa1-688503c2d2a2/events.log)
  记录：
  - `阶段｜等待人工处理｜阶段=IMPLEMENTATION｜目标阶段=IMPLEMENTATION｜原因=IMPLEMENTATION_BLOCKED_EXHAUSTED_SUBTASK`
  - 紧接着 `阶段｜人工批准｜阶段=IMPLEMENTATION`
  - 然后直接进入 `CODE_REVIEW`
- 代码上：
  [WorkflowRunLifecycleSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/WorkflowRunLifecycleSupport.java)
  的 `approveStage()` 无差别调用
  [StageTransitionSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/StageTransitionSupport.java)
  的 `approveHumanReview()`
- 而
  [StageStatusSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/orchestrator/StageStatusSupport.java)
  的 `approveHumanReview()` 当前固定做两件事：
  - 把当前 stage 写成 `APPROVED`
  - 按 `stageFlowPolicy.nextStage(stageType)` 进入下一个阶段或完成 run

#### Why This Is Wrong

- `APPROVE_STAGE_GATE` 的正确含义是：当前阶段产物被人工认可，可以前进。
- 但 `IMPLEMENTATION_BLOCKED_EXHAUSTED_SUBTASK`、`REJECTED + ROUTE_TO_REPAIR` 的正确含义是：当前阶段没有通过，只是等待人工确认 repair。
- 这两者不可能共用同一个批准动作。

#### Solution

- 在 run-state 协议里引入单一结构化 owner：
  - `HumanReviewIntent`
  - `HumanReviewResolutionContext`
- 至少区分两类 intent：
  - `APPROVE_STAGE_GATE`
  - `CONFIRM_REPAIR_ROUTE`
- `approveStage()` 不再直接等价于“当前 stage APPROVED”。
- `approveStage()` 必须先读取当前 stage 的 `HumanReviewResolutionContext`：
  - `APPROVE_STAGE_GATE` -> 才允许走 `approve current stage + next stage`
  - `CONFIRM_REPAIR_ROUTE` -> 只能走 `reroute/continue implementation repair`

### Problem 2. Human gate 当前无法单一持有 canonical repair package

#### Evidence

- 这轮 [test_report.md](/home/linus/workspace/tetris_test_itest_v182/.devflow/runs/c8019e26-05b0-4426-8fa1-688503c2d2a2/test_report.md)
  明确给出了结构化 repair package：
  - `fixMode=PATCH`
  - `implementationPatchTarget=PATCH_EXISTING_IMPLEMENTATION`
  - `overrideChanges=[src/game.js]`
- 但当前 [StageExecution.java](/home/linus/workspace/forge/src/main/java/devflow/agent/domain/StageExecution.java)
  只持有：
  - `reviewDecision`
  - `reviewSummary`
  - `changeRequest`
- 也就是说，一旦进入 human gate，run-state 本身并不持有这份 canonical patch package。

#### Why This Is Wrong

- human approve 之后如果要恢复 repair，系统必须知道：
  - 修哪一阶段
  - 用什么 fix mode
  - patch target 是什么
  - concrete overrideChanges 是什么
- 这些信息如果不进入 run-state，就只能靠：
  - 读 markdown report
  - 读 transition artifact
  - 或重新猜测
- 这三种都违反单一真相源要求。

#### Solution

- `HumanReviewResolutionContext` 必须持有完整结构化 repair routing：
  - `targetStage`
  - `fixMode`
  - `implementationPatchTarget`
  - `overrideChanges`
  - `reviewDecision`
  - `summary/changeRequest/evidence`
  - 如果来源是 implementation blocked，还要持有 `continuationMode`
- 这份 context 必须进 run-state 主链，不允许只放在 markdown artifact。
- `CONFIRM_REPAIR_ROUTE` 被批准后，下一轮 implementation 只能消费这份 context，不能再从 report 重新构造。

### Problem 3. Final state 被 human approve 覆写，导致 `run.json` 与 report / events 冲突

#### Evidence

- [test_report.md](/home/linus/workspace/tetris_test_itest_v182/.devflow/runs/c8019e26-05b0-4426-8fa1-688503c2d2a2/test_report.md)
  明确写：
  - `决策：REJECTED`
  - `fixMode: PATCH`
  - `revisionRoute: ROUTE_TO_REPAIR_TARGET`
- [transition_decision.md](/home/linus/workspace/tetris_test_itest_v182/.devflow/runs/c8019e26-05b0-4426-8fa1-688503c2d2a2/transition_decision.md)
  明确写：
  - `supervisorAction: ROUTE_TO_REPAIR`
- 但 [run.json](/home/linus/workspace/tetris_test_itest_v182/.devflow/runs/c8019e26-05b0-4426-8fa1-688503c2d2a2/run.json)
  最终写成：
  - `status: COMPLETED`
  - `TEST: APPROVED`
  - 全阶段 `APPROVED`

#### Why This Is Wrong

- 这不是展示问题，而是 final truth 被写坏。
- 一旦 `run.json` 不可信，后续 resume / audit / review 都会失真。

#### Solution

- final-state 收尾必须只走一条链：
  - `FlowDecisionExecutor`
  - `StageTransitionSupport`
  - `StageStatusSupport`
  - `StageProgressArtifactSupport`
  - `WorkflowRunLifecycleSupport`
- 对 `CONFIRM_REPAIR_ROUTE`：
  - 当前阶段不能被写成 `APPROVED`
  - run 不能被写成 `COMPLETED`
  - events / transition artifact / run.json 都必须仍然指向 repair route
- 只有 `APPROVE_STAGE_GATE` 才允许：
  - stage -> `APPROVED`
  - run -> `COMPLETED` 或进入 next stage

### Problem 4. CLI / autopilot 目前会对任何 `AWAITING_HUMAN_REVIEW` 做盲批准

#### Evidence

- [CliRunCommandHandler.java](/home/linus/workspace/forge/src/main/java/devflow/agent/interfaceadapter/cli/CliRunCommandHandler.java)
  当前只检查 `StageStatus.AWAITING_HUMAN_REVIEW`，然后统一调用 `approveStage(...)`
- 它并不知道当前等人工的是：
  - gate approval
  - 还是 repair confirmation

#### Why This Is Wrong

- 即使后端把 `approveStage()` 修成分支语义，CLI 不展示当前 intent，也会继续误导人。
- autopilot 日志里会继续出现“人工批准 TEST/IMPLEMENTATION”，但实际上不是批准通过，只是确认 repair。

#### Solution

- CLI summary / output 必须显示当前 `HumanReviewIntent`
- autopilot 可以继续自动处理，但日志与输出必须区分：
  - `人工批准通过`
  - `人工确认修复回流`
- 不允许继续输出会误导人的统一“人工批准”语义。

### Problem 5. 这轮也暴露了真实业务失败，但它不是当前工作流主问题

#### Evidence

- [test_report.md](/home/linus/workspace/tetris_test_itest_v182/.devflow/runs/c8019e26-05b0-4426-8fa1-688503c2d2a2/test_report.md)
  真实失败证据是：
  - `FAIL ASSERT_CANVAS_HASH_CHANGED`
  - 点击开始后 `#game-board` 没有可观察变化

#### Why It Is Secondary In This Round

- 这是个真实业务 gap，需要修。
- 但在当前状态机未收口前，就算把 `src/game.js` 修好，run-level 绿/红也仍然不可信。

#### Solution

- 本轮只做工作流收口，不顺手补业务代码。
- 收口完成后，必须保留这份 test 产出的 canonical patch package：
  - `PATCH_EXISTING_IMPLEMENTATION`
  - `overrideChanges=[src/game.js]`
- 下一轮 implementation repair 再只修这条业务问题。

## Implementation Order

1. 先改 `run-state protocol`，把 `HumanReviewIntent / HumanReviewResolutionContext` 明确挂进唯一事实源。
2. 再改 `WorkflowRunLifecycleSupport / StageTransitionSupport / StageStatusSupport`，把 human approve 分成两条确定性动作。
3. 再改 `StageProgressCoordinator / StageRevisionSupport / StageRevisionRepairSupport`，确保 blocked implementation / rejected test 都能产出同一份 human resolution context。
4. 再改 `StageProgressArtifactSupport / WorkflowEventMessages / CLI`，把人类可见产物与最终状态对齐。
5. 最后补回归，证明：
   - blocked implementation approve 不会进 code review
   - rejected test + route_to_repair approve 不会 complete run
   - canonical repair package 能跨 human gate 进入 implementation

## Regression Matrix

至少补下面 6 类回归：

1. `IMPLEMENTATION_BLOCKED_EXHAUSTED_SUBTASK` -> human confirm -> re-enter implementation
   不能进入 `CODE_REVIEW`
2. `TEST REJECTED + PATCH + ROUTE_TO_REPAIR_TARGET` -> human confirm
   run 不得 `COMPLETED`
3. `CODE_REVIEW REJECTED + REWORK + ROUTE_TO_REPAIR` -> human confirm
   当前 stage 不得写成 `APPROVED`
4. `AGENT_PLUS_HUMAN` 正常 gate approval
   仍然可以人工批准并进入 next stage
5. `run.json` / `events.log` / `transition_decision.md` / report
   对 repair route 保持一致
6. `canonical repair package`
   经过 human gate 后仍能进入 implementation resume，不丢 `patch target / overrideChanges`

## Risks / Blockers

- 最大 blocker 只有一个：如果 reviewer 不接受把 human gate intent 持久化进 run-state，就没有单一主链，后续实现必然退回 artifact/fallback。
- 本轮不应把范围扩到俄罗斯方块业务修复，否则会把“状态机收口”与“业务 patch”混成两条主线。
- 本轮不接受兼容写法：
  - 旧 `approveHumanReview()` 语义保留
  - 新增一个 `approveRepairRoute()` 但 CLI 继续走旧入口
  - 从 report 反向读取 patch package 作为兜底

## Out Of Scope

本轮不做：

- `src/game.js` 的业务修复
- 测试用例设计策略调整
- runtime wiring / tool-loop 的其他问题族
- 更大范围的 implementation/test/state 重构

## Advisory Notes

下面这些是基于多轮方案 / 代码 review 得出的系统性建议，用来解释“为什么集成测试到现在仍然一次都没跑通”。

- 这部分不是 `v15` 当前必做 scope，也不是要求本轮顺手实现。
- 它的作用是给后续细化方案提供统一判断，避免问题再次被拆成孤立 patch。

### Systemic Diagnosis

当前集成测试长期不过，我的总体判断不是“还差最后一个业务 bug”，而是下面 5 条系统性原因叠加：

1. 端到端还没有形成单一 canonical execution contract。
   从 `planning -> task package -> tool loop -> subtask verification -> repair/resume -> human gate -> final run-state`，很多环节都持有一份“近似同义”的执行语义，但不是同一份 owner / carrier / persistence 链。
   所以局部看每层都“有 contract”，串起来却仍会漂移。
2. 上游 gate 历史上长期允许坏 package 进入执行，后面再靠 verifier / repair 止血。
   这也是为什么之前经常出现“accepted package 语义上需要 companion，但结构上并不完整”的情况。
   集成测试测的是闭环，不是单点兜底；前面放坏包，后面一定会在别处炸开。
3. `repair / resume` 还不是稳定保真的 continuation 协议。
   它仍然是最容易丢失语义、重新 materialize scope、重新解释 patch target 的一层。
   只要 repair package 不能被单一路径保真地穿过 resume / re-enter / tool loop，集成就会持续出现“局部修了、下一轮又漂”的现象。
4. `human gate / repair route / final run-state` 直到现在都还没有完全收口成单链。
   这正是 `v15` 这轮聚焦的问题。
   在这条链彻底收完之前，即使业务 patch 偶尔修对，最终 `run.json` 仍可能把失败回合写成 `APPROVED / COMPLETED`，导致红绿结果本身不可信。
5. 系统复杂度已经明显超过当前协议化程度。
   Forge 现在不是单一 coder loop，而是叠了 planning、subtask、review、supervisor、repair、human gate、artifact、run-state 多层跃迁。
   这套系统只有在每一层 owner / carrier / serializer / enforcer 都写死时才稳定；只要有一层仍然靠 prose、fallback 或隐式 helper 续命，黄金路径集成就会继续暴露新的未收口面。

### Suggested Priority After V15

如果 `v15` 收口后要继续推进，我建议后续优先级固定成下面 5 层，而不是继续分散打补丁：

1. `human gate / repair route / run-state` 单链彻底收口
2. `repair / resume` 的 canonical contract 保真
3. `planning accepted package` 不再放坏包
4. `subtask boundary + completeness` 全部改成 deterministic gate
5. `tool-loop closure` 只认 canonical execution contract，不再依赖轮次副产物兜底

### Why This Matters

- 这 5 条建议不是为了扩 scope，而是为了统一判断标准：
  - 不再把问题误认为“只是俄罗斯方块业务没修好”
  - 不再把问题拆成孤立的单点 bug
  - 不再把后置止血层误判成主因已经修复
- 如果后续细化方案没有对齐这条系统性主线，集成测试即使偶尔变绿，也不能视为可信收口。

## Review Focus

请 reviewer 重点只审下面 5 点：

1. 这份方案是否已经把 `v182` 的真实主问题定义准，不再把业务失败和状态机失败混为一谈
2. `HumanReviewIntent / HumanReviewResolutionContext` 是否足够作为唯一 owner，避免回退到 markdown/fallback
3. `approveStage()` 是否必须拆成“批准阶段”与“确认 repair”两类语义
4. `run.json / events.log / transition_decision / report` 的一致性 owner 是否已经列全
5. 这份方案是否仍然遵守 `AGENTS.md` 与 `engineering-agreements.md`，没有兼容层、fallback、双轨并存
