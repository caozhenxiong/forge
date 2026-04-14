# Review V6 Continuation Routing Findings

## Scope

这份文档只记录对提交 `31224d1 fix: close implementation continuation routing` 的 review 结论。

目标：

- 不重做整轮方案
- 只指出当前实现里仍未收紧的边界
- 让后续修复能直接对着文件改

## Findings

### 1. `ImplementationStageGate` 只按路径夹断 scope，但没有回落到 canonical `FileChange`

位置：

- `src/main/java/devflow/agent/executor/gate/ImplementationStageGate.java`

当前问题：

- `canonicalOverrideChanges()` 只检查 reviewer 提交的 `FileChange.path` 是否落在 `allowedScope` 内。
- 一旦路径命中，它保留的是 reviewer 原始 `FileChange`，不是当前 subtask 的 canonical change。
- 这意味着下一轮 continuation 虽然不能把新路径混进来，但仍然可以在已允许路径上偷偷改：
  - `action`
  - `editScope`
  - `runtimeOwnership`
  - `hostHtmlPatchRequired`

为什么这是问题：

- 本轮方案要求 `overrideChanges` 的 canonical scope 和结构化 change 来源只能来自当前 subtask 的 accepted/effective structured change-set。
- 现在实现只收紧了“路径集合”，没有收紧“结构化 change 元数据”。
- 这些字段后续会进入 continuation directive、repair note 和 resume 执行链，不只是展示信息。

建议：

- `canonicalOverrideChanges()` 在路径命中时，应优先返回 `allowedScope` 中对应的 canonical `FileChange`。
- 如果确实还想保留 reviewer 的局部语义，只保留在 prose/evidence 层，不要继续信任 reviewer 提交的结构化 change metadata。

### 2. implementation blocked 语义只改了 transition summary，没有同步到实际落盘的 `ReviewResult`

位置：

- `src/main/java/devflow/agent/orchestrator/FlowController.java`
- `src/main/java/devflow/agent/orchestrator/FlowDecisionExecutor.java`
- `src/main/java/devflow/agent/orchestrator/StageStatusSupport.java`

当前问题：

- `FlowController.decide()` 在 implementation continuation 被阻断时，会把动作改成 `REQUEST_HUMAN_REVIEW`，并把 transition summary 改成 blocked continuation 的摘要。
- 但 `FlowDecisionExecutor.apply()` 在 `REQUEST_HUMAN_REVIEW` 分支里，仍然把原始 `reviewResult` 传给 `blockForHumanReview()`。
- `StageStatusSupport.blockForHumanReview()` 最终落盘的 stage review 仍来自原始 review，而不是 blocked continuation context。

为什么这是问题：

- transition artifact 和最终 `run.json` / stage state 会说两套不同的话。
- 人工接手时，状态里看不到真正导致阻断的 implementation continuation 原因，例如“缺少结构化 patch scope，不能自动续跑”。
- 这会削弱本轮“FlowController 是唯一流程 owner”的收口效果，因为真正落盘的语义仍然来自旧 review。

建议：

- 当 `FlowController` 决定把 implementation continuation 改判为 `REQUEST_HUMAN_REVIEW` 时，应同时产出一份 canonical blocked review 语义。
- `FlowDecisionExecutor` 在该分支里应落盘这份 blocked review，而不是继续落盘原始 `reviewResult`。
- 最低要求是让 stage state、transition artifact、human review 入口三者对同一个阻断原因达成一致。

## Conclusion

当前实现主方向是对的：

- `FlowController` 已开始承担唯一流程 owner
- `ImplementationStageGate` 已开始夹断 continuation scope

但以上两点还没有完全收口。如果不修：

- scope 会继续在 metadata 层松动
- blocked implementation 的真实原因不会稳定落盘

这两项建议都属于本轮 continuation routing 收口范围内，应在同一轮内补齐。
