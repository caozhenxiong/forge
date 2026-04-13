# 架构评审整合方案

## 背景

这份文档整合了两份输入：

- `REVIEW.md`
- `ARCHITECTURE_REFACTOR.md`

目标不是机械照抄 review 建议，而是把真实问题、需要立即调整的结构、可以后移的事项收敛成一份可执行方案。

---

## 总结结论

这轮需要纳入整改的主线有 5 条：

1. `context ↔ orchestrator` 解耦
2. `executor` 大包拆边界
3. `WorkflowEngine` 真契约化
4. tool loop 并发执行器显式化
5. `ContextCompactor` 四层上下文真正接入 generate 主链

这轮明确先不做：

- `sandbox / OS 级隔离`

原因：它属于安全能力升级，不是当前主链架构收口的第一阻塞。

---

## 对现有两份方案的评审

### `REVIEW.md` 中需要保留的点

- `WorkflowEngine` 接口是假接口，这是真问题
- `ImplementationToolLoopExecutor` 使用默认 common pool，这是真问题
- `SupervisorAgent` 静默吞异常，这是真问题
- `ContextCompactor` 仍然是字符串截断，这是真问题
- `context ↔ orchestrator` 双向耦合，这是真问题
- `executor` 包无边界膨胀，这是真问题
- `StageProgressCoordinator` 职责过多，这是真问题，但优先级靠后

### `ARCHITECTURE_REFACTOR.md` 中方向正确的点

- 提取 `domain` 包来切断 `context` 对 `orchestrator` 的模型依赖
- 给 `executor` 做职责化子包拆分
- 把 `WorkflowEngine` 契约修正并让 CLI 依赖接口
- 给 tool loop 的并发执行指定显式执行器

### `ARCHITECTURE_REFACTOR.md` 中需要修正的点

#### 1. `Step 3a` 还是半截

文档里只给 `WorkflowEngine` 增加了 4 个 path-aware 方法，但 CLI 真实还依赖：

- `initialize(Path)`
- `createRun(Path, goal, constraints)`
- `find(Path, runId)`

所以 `WorkflowEngine` 必须升级成完整 facade，而不是只补 4 个方法。

#### 2. `Step 3c` 只加静态线程池不够

真正的问题不是“漏传 executor 参数”，而是：

- 谁创建执行器
- 谁持有执行器
- 谁关闭执行器
- 测试如何注入执行器

因此应该由 `ImplementationExecutor` 显式拥有并管理执行器生命周期，`ImplementationToolLoopExecutor` 只接收依赖。

#### 3. `Step 2` 不是纯机械改动

当前 `executor` 根包里有大量 package-private 顶层类。拆到子包后会触发大量可见性调整，不只是 move 文件和改 import。

#### 4. `Step 2` 的 `tools/` 边界不够干净

`ImplementationToolLoopExecutor`、`ImplementationToolContext` 这类类不是 generic tool 定义，而是 implementation orchestration 的一部分，不应该落进 `tools/` 子包。

#### 5. 文档漏掉了 `ContextCompactor` 主链接线

这条不能再后置。四层上下文已经存在，但 generate 主链仍只消费 `systemPrompt/userPrompt` 字符串，导致预算压缩继续丢语义。

---

## 最终态

整改完成后的最终态应是：

- `WorkflowEngine` 成为 CLI 唯一工作流门面，不再存在会抛 `UnsupportedOperationException` 的伪接口
- `context` 和 `orchestrator` 通过 `domain` 单向汇合
- `executor` 不再是 426 个类平铺的大包，而是按职责分层
- implementation tool loop 的工具、权限、上下文、执行控制边界清晰
- generate 主链接收结构化上下文输入，`ContextCompactor` 基于四层上下文裁剪
- `StageProgressCoordinator` 只做 orchestration，不再承载 payload 转换细节

---

## 必须删除的旧路径

- `WorkflowEngine` 的无 `Path` 旧方法
- `DefaultWorkflowEngine` 中那 4 个抛异常的假实现
- CLI 对 `DefaultWorkflowEngine` 具体类的依赖
- `ImplementationToolLoopExecutor` 对 `ForkJoinPool.commonPool()` 的隐式依赖
- generate 主链只接受字符串 compact 的旧入口
- `context` 对 `orchestrator` 域模型的直接依赖
- `executor` 根包里职责混杂的平铺组织

不删除这些旧路径，就不算收口。

---

## 联动修改范围

### 1. `WorkflowEngine` 收口

涉及：

- `orchestrator/WorkflowEngine.java`
- `orchestrator/DefaultWorkflowEngine.java`
- `interfaceadapter/cli/DevflowCliRunner.java`
- `interfaceadapter/cli/CliRunCommandHandler.java`

### 2. `domain` 提取

建议迁移的核心模型：

- `StageType`
- `RunRecord`
- `RunStatus`
- `RunConfig`
- `GatePolicy`
- `StageExecution`
- `StageStatus`

目标依赖关系：

- `context -> domain`
- `orchestrator -> domain`
- `orchestrator -> context`

禁止继续存在：

- `context -> orchestrator`

### 3. `executor` 边界拆分

建议保留这些职责子包方向：

- `executor.llm`
- `executor.context`
- `executor.generation`
- `executor.patch`
- `executor.editing`
- `executor.testing`
- `executor.shell`
- `executor.runtime`
- `executor.gate`
- `executor.tools`
- `executor.implementation`
- `executor.subtask`

关键边界调整：

- `tools/` 只放 generic tool contract、tool implementation、permission、registry
- `ImplementationToolLoopExecutor`
- `ImplementationToolContext`
- `ImplementationToolResultBudgetManager`
- `ImplementationToolSessionState`

这些应留在 `implementation/` 或 `implementation.toolloop/`，不能继续混在 `tools/`。

### 4. `ContextCompactor` 接线

涉及：

- `LlmProvider`
- `OllamaLlmProvider`
- `OllamaGenerationExecutor`
- `ContextCompactor`
- generate 调用点

### 5. `StageProgressCoordinator` 瘦身

目标：

- 保留 orchestration
- 下沉 payload 转换、文件变更组装、patch target 推导等细节

---

## 分阶段执行顺序

不建议并行乱改，建议按以下顺序推进：

### Phase 0：契约和可观测性修复

- `WorkflowEngine` 升级成完整 facade：
  - `initialize(Path)`
  - `createRun(Path, goal, constraints)`
  - `find(Path, runId)`
  - `startRun(Path, runId)`
  - `resumeRun(Path, runId)`
  - `approveStage(Path, runId, stageType, reviewer)`
  - `rejectStage(Path, runId, stageType, reviewer, reason)`
- CLI 改依赖接口
- `SupervisorAgent` 两处静默异常改 `warn` 日志
- `ImplementationToolLoopExecutor` 改为显式接收 `ExecutorService`
- `ImplementationExecutor` 持有并管理执行器生命周期

### Phase 1：提取 `domain`

把被多包依赖的纯模型从 `orchestrator` 挪到 `domain`，消除 `context -> orchestrator`。

### Phase 2：`executor` 拆职责簇

按低耦合到高耦合顺序推进，而不是任意搬：

1. `llm`
2. `context`
3. `generation`
4. `shell`
5. `tools`
6. `runtime`
7. `gate`
8. `editing`
9. `patch`
10. `testing`
11. `subtask`
12. `implementation`

原因：`implementation` 和 `subtask` 依赖面最大，应最后收。

### Phase 3：`ContextCompactor` 结构化接线

最终态要求：

- 新增结构化 generate request
- `LlmProvider.generate(...)` 主路径改接 request object
- request 能承载 `ProjectedContext` 或 `ContextViews`
- `OllamaGenerationExecutor` 基于结构化上下文压缩
- `ContextCompactor` 按四层优先级裁剪：
  - `Trace`
  - `Evidence`
  - `Working`
  - `Durable`
- 删除 provider 主路径上的旧字符串 compact 入口

### Phase 4：`StageProgressCoordinator` 收缩

前面边界稳定后，再把它压回纯 orchestration。

---

## 本轮不做的事项

只明确后移一条：

- `sandbox / OS 级隔离`

这不是否认问题，而是当前不纳入这一轮主链整改。

---

## 验证策略

每个阶段都单独验证，不等最后一起炸。

### Phase 0

```bash
mvn -q -Dtest=DefaultWorkflowEngineTests,SupervisorAgentTests,ImplementationToolLoopExecutorTests test
```

### Phase 1

```bash
mvn -q compile
```

### Phase 2

- 每拆完一个职责簇跑一次 `mvn -q compile`
- 阶段末跑 `mvn -q test`

### Phase 3

- 补 `ContextCompactorTests`
- 补 generate 主链测试

### Phase 4

- 跑 orchestrator 相关测试
- 再做一次 code review

---

## 最终判断

这轮不能再按“quick fix + 局部搬家”做。

正确做法是：

- 先收契约和显式依赖
- 再收 domain 边界
- 再收 executor 结构
- 再收 context compactor 主链
- 最后压 coordinator 职责

如果跳过中间任一层，后面 bug 仍会反复牵动多处代码，Claude 指出的“架构设计有问题”也不会真正被解决。
