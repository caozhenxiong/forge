# Review V8: Tool Loop Declared-Changes Closure Note

## Purpose

这份文档只解释本次提交 `dbbc80c` 的代码改动：

- 改了什么
- 这次改动要解决的具体问题是什么
- 为什么改在这里
- 验证结果是什么
- 这次没有解决什么

它不是新一轮大方案文档，只对应这次已经落地的代码修改。

## Problem

`v175` 集成测试暴露出的主问题不是 planning、runtime wiring 或 shell deny 本身，而是：

- 第一个 `INCREMENTAL` 子任务已经把 `index.html` / `src/app.js` 写出来了
- 但 `ImplementationToolLoopExecutor` 仍然要求模型必须主动给出 terminal assistant response
- 如果模型没有停手，而是继续做无意义的 `Read / Glob / Bash / Write`
- tool loop 会在 `maxToolTurns` 后报错，进入重试

也就是说，系统当时的成功判定是：

- `tool loop success == 模型主动停手`

而不是：

- `tool loop success == 当前 subtask 的声明文件交付已经满足`

这会导致一种错误状态：

- 真实文件已经满足当前 subtask 的交付契约
- 但因为模型没有及时停手，系统仍把这轮执行判成失败

这就是 `v175` 中第一子任务反复重试的直接原因。

## Root Cause

根因在 [ImplementationToolLoopExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolLoopExecutor.java)：

- `assertDeclaredChangesSatisfied(...)` 只在 `assistant-only terminal response` 分支使用
- 如果模型一直在调用工具，没有给 terminal assistant response
- 即便声明文件交付已经满足，tool loop 也只能在回合数耗尽后抛 `tool loop exceeded max turns without a terminal assistant response`

换句话说，声明交付契约只是“assistant-only 结束时的校验”，不是“tool loop 的统一完成条件”。

## Final State

本次改动后的完成条件收口为：

- 如果模型给出 terminal assistant response：
  仍然必须通过声明交付校验
- 如果模型没有给 terminal assistant response，但 tool loop 已跑到 `maxToolTurns`：
  只要声明交付已经满足，也允许结束当前 subtask
- 只有在声明交付未满足时，才继续报失败

这次没有引入 fallback、兼容层或第二套成功语义；只是把同一份声明交付校验，扩展成 tool loop 的统一完成门槛。

## Code Changes

### 1. `ImplementationToolLoopExecutor`

修改文件：

- [ImplementationToolLoopExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/implementation/toolloop/ImplementationToolLoopExecutor.java)

主要改动：

- 抽出 `collectUnsatisfiedDeclaredChanges(...)`
  - 把“哪些声明文件交付还没满足”的计算收成单点
- 保留原有 `assistant-only` 分支的 declared-changes 校验
  - 语义没放松
- 新增 `maxToolTurns` 结束时的 declared-changes 收口逻辑
  - 如果 `unsatisfiedChanges` 为空，则直接结束当前 subtask
  - 并写事件：`声明交付已满足｜结束=declared-changes-satisfied`
- 只有在 `unsatisfiedChanges` 非空时，才继续抛 `VALIDATION_FAILED`

### 2. Regression Test

修改文件：

- [ImplementationToolLoopExecutorTests.java](/home/linus/workspace/forge/src/test/java/devflow/agent/executor/ImplementationToolLoopExecutorTests.java)

新增回归：

- `toolLoopFinishesWhenDeclaredWriteIsSatisfiedAtTurnLimit`

这条测试锁住的边界是：

- 模型在唯一一轮里只做一次 `Write`
- 没有再给 terminal assistant response
- 但因为声明文件交付已经满足，tool loop 也必须成功结束

## Why This Owner

这次问题的 owner 必须是 `ImplementationToolLoopExecutor`，不是别处：

- 不是 `FileWriteTool` 的问题
  - `Write` 已经成功落盘
- 不是 `ImplementationStageGate` 的问题
  - 阶段 gate 发生在更后面
- 不是 `SubtaskVerificationSupport` 的问题
  - review 打回是后续问题，不是这次的首因

真正出错的位置就是：

- tool loop 已经拿到了正确文件结果
- 但没有把“声明交付已满足”视为结束条件

所以必须改在 tool loop owner，而不是在 review / stage gate 上再兜一层。

## Validation

本次本地验证：

- `mvn -q -Dtest=ImplementationToolLoopExecutorTests test`
- `mvn -q -Dtest=ImplementationToolLoopExecutorTests,ImplementationStageGateTests test`
- `mvn -q -DskipTests package`

说明：

- 更大一轮里 `ImplementationExecutorTests.runtimeWiringContinuationReusesPreviousPlanWithoutReplanning` 仍然会在 `ImplementationResumePolicy` 先报旧异常
- 那条不是这次改动引入的新问题，也不在本次收口范围内

## Integration Evidence

本次重新跑了黄金路径集成：

- 项目目录：`/home/linus/workspace/tetris_test_itest_v176`
- run id：`88babec8-79e0-4431-9c34-cafbb4997d4d`

从 [events.log](/home/linus/workspace/tetris_test_itest_v176/.devflow/runs/88babec8-79e0-4431-9c34-cafbb4997d4d/events.log) 可以确认两点：

### 已解决

- 第一子任务正常创建了 `index.html`
- 第一子任务正常创建了 `src/app.js`
- 在第二次尝试的第 `12/12` 轮，系统明确落了：
  - `实现阶段｜tool-loop｜声明交付已满足｜子任务=创建基础HTML结构与Canvas渲染界面｜轮次=12/12｜结束=declared-changes-satisfied`

这说明本次修复已经生效：

- 旧的“文件已满足但 tool loop 仍失败重试”主问题已收住

### 尚未解决

集成仍未跑通，但主阻塞已经变化：

- 第一子任务不再卡在 tool loop 收敛
- 新的主阻塞变成：
  - 子任务 review 连续 `REVISION_REQUIRED`
  - 打回后的 repair 仍在尝试整文件重写、空 `old_string` edit、甚至内联脚本回退

也就是说，当前新的问题面是：

- `subtask review / revision feedback -> patch-first repair` 这条链

而不是这次已经修掉的：

- `tool loop completion condition`

## Not Solved In This Commit

本次提交 **没有** 处理以下问题：

- 子任务 review 为什么连续打回第一个 runnable milestone
- revision feedback 为什么没有稳定收敛成局部 patch
- repair 模式下为什么还会反复尝试整文件 rewrite / inline script 回退
- `ImplementationResumePolicy` 上那条历史 runtime wiring continuation 测试异常

这些属于下一轮问题，不属于这次 commit 的 owner。

## Reviewer Focus

review 时请重点看三件事：

1. 是否把 `declared-changes-satisfied` 变成了 tool loop 的统一完成门槛，而不是新的 fallback
2. 是否保持了 assistant-only completion 的原有严格校验，没有放松“口头完成”路径
3. 是否只在 `unsatisfiedChanges` 为空时结束，没有把真正未完成的 subtask 错判为成功

## Commit

- `dbbc80c` `close tool loop when declared changes are satisfied`
