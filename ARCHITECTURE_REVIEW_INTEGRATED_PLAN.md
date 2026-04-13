# 架构整改执行方案

## Summary

这份文档是当前唯一有效的架构整改方案，整合自：

- `REVIEW.md`
- `ARCHITECTURE_REFACTOR.md`
- 已完成的本地代码现状核对

本轮只收 5 条主线：

1. `WorkflowEngine` 真契约化
2. `context ↔ orchestrator` 通过 `domain` 解耦
3. `executor` 按职责拆边界
4. tool loop 并发执行器显式化
5. `ContextCompactor` 四层上下文接入 generate 主链

本轮明确不做：

- `sandbox / OS 级隔离`

理由：它属于安全能力升级，不是当前主链架构收口的第一阻塞。

---

## Final State

整改完成后，主链必须同时满足以下条件：

- `WorkflowEngine` 成为 CLI 唯一工作流门面，不再存在会抛 `UnsupportedOperationException` 的伪接口
- `context` 不再依赖 `orchestrator` 域模型；`context` 与 `orchestrator` 统一依赖 `domain`
- `executor` 不再是 426 个类平铺的大包，而是按职责分层
- implementation tool loop 的工具定义、权限、上下文、执行控制边界清晰
- generate 主链接收结构化上下文输入，`ContextCompactor` 基于四层上下文裁剪
- `StageProgressCoordinator` 只做 orchestration，不再承载 payload 转换细节

---

## Removal Plan

本轮必须同步删除以下旧路径：

- `WorkflowEngine` 的无 `Path` 旧方法
- `DefaultWorkflowEngine` 中那 4 个抛异常的假实现
- CLI 对 `DefaultWorkflowEngine` 具体类的依赖
- `ImplementationToolLoopExecutor` 对 `ForkJoinPool.commonPool()` 的隐式依赖
- generate 主链只接受字符串 compact 的旧入口
- `context` 对 `orchestrator` 域模型的直接依赖
- `executor` 根包里职责混杂的平铺组织

只要这些旧路径还在，本轮就不能宣称收口完成。

---

## Review Alignment

### 需要纳入本轮的 review 点

- `WorkflowEngine` 假接口
- tool loop 默认 common pool
- `SupervisorAgent` 静默吞异常
- `ContextCompactor` 仍是字符串截断
- `context ↔ orchestrator` 双向耦合
- `executor` 包无边界膨胀
- `StageProgressCoordinator` 职责过多

### 对 `ARCHITECTURE_REFACTOR.md` 的修正

需要保留其主方向，但修正以下落点：

1. `WorkflowEngine` 不能只补 4 个 path-aware 方法，必须成为完整 facade
2. tool executor 不能只加静态线程池，必须定义创建、持有、关闭与测试注入方式
3. `executor` 拆包不是纯机械 move，根包存在大量 package-private 顶层类，拆分会伴随可见性收敛
4. `tools/` 只承载 generic tool contract/implementation；`ImplementationToolLoopExecutor`、`ImplementationToolContext` 等 implementation orchestration 组件必须留在 `implementation`
5. 原文档漏掉了 `ContextCompactor` 主链接线，这条必须纳入本轮
6. `ImplementationExecutor` 当前仍是构造器里的装配工厂，这条必须在 Phase 0 收掉，不能带着工厂式 `new` 链进入后续拆包
7. `SubtaskExecutor.executeSubtask(...)` 当前仍是 16 参数离散入口，必须补 `SubtaskExecutionContext`，不能只在 attempt runner 一侧有 context 包装
8. tool loop continuation prompt 不能继续硬编码在执行器里，必须回收到 `ImplementationToolPromptBuilder`

---

## Key Changes

### Phase 0：契约与可观测性修复

这一 phase 只收契约真相源和低风险可观测性问题。

#### `WorkflowEngine` 完整 facade

接口固定为：

- `initialize(Path projectPath)`
- `createRun(Path projectPath, String goal, String constraints)`
- `find(Path projectPath, UUID runId)`
- `startRun(Path projectPath, UUID runId)`
- `resumeRun(Path projectPath, UUID runId)`
- `approveStage(Path projectPath, UUID runId, StageType stageType, String reviewer)`
- `rejectStage(Path projectPath, UUID runId, StageType stageType, String reviewer, String reason)`

要求：

- CLI 全部改依赖 `WorkflowEngine`
- 删除 `DefaultWorkflowEngine` 中无 `Path` 的旧入口
- 不保留兼容层，不保留双接口并存

#### `SupervisorAgent` 异常可观测化

要求：

- 两处静默 `catch (Exception ignored)` 改成 `warn`
- 保留原 fallback 行为
- 日志至少带上 `stage` 或 `attempt` 维度

#### tool loop 显式执行器

要求：

- `ImplementationToolLoopExecutor` 显式接收 `ExecutorService`
- `ImplementationExecutor` 创建、持有并关闭执行器
- 测试显式注入执行器
- 不再使用 `ForkJoinPool.commonPool()`

#### `ImplementationExecutor` 去工厂化

要求：

- `ImplementationExecutor` 收成单构造器注入
- 不再通过链式构造器 + `@Autowired(required = false)` 维持多种运行模式
- 不再在构造器内部手工 `new` 一串 implementation 组件
- implementation 依赖装配下沉到明确的 wiring / configuration 层
- 测试可以按协作者粒度注入 double，而不是被迫走整套真实装配

#### `SubtaskExecutionContext`

要求：

- `SubtaskExecutor.executeSubtask(...)` 不再暴露 16 个离散参数
- 新增 execution 级 context，承载：
  - `persistentRepairFeedback`
  - `deliveryPolicy`
  - `initialExecutionState`
  - 其余当前 execution 级不变量
- `SubtaskAttemptContext` 继续只负责单次 attempt 的不变量
- execution context 与 attempt context 的边界必须清晰，禁止职责重叠

#### continuation prompt 归位

要求：

- `initializeTranscript(...)` 不再内联续跑 prompt 字符串
- `ImplementationToolPromptBuilder` 提供 continuation prompt 组装入口
- tool loop 执行器只负责在合适时机附加 prompt，不负责决定 prompt 文案本身

### Phase 1：提取 `domain`

把被多包依赖的纯模型从 `orchestrator` 挪到 `domain`：

- `StageType`
- `RunRecord`
- `RunStatus`
- `RunConfig`
- `GatePolicy`
- `StageExecution`
- `StageStatus`

完成后依赖关系必须变成：

- `context -> domain`
- `orchestrator -> domain`
- `orchestrator -> context`

禁止继续存在：

- `context -> orchestrator`

### Phase 2：`executor` 拆职责簇

目标子包固定为：

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
- `executor.subtask`
- `executor.implementation`

关键边界要求：

- `tools` 只放 generic tool contract、tool implementation、permission、registry
- `ImplementationToolLoopExecutor`
- `ImplementationToolContext`
- `ImplementationToolResultBudgetManager`
- `ImplementationToolSessionState`

以上 4 类放入 `executor.implementation.toolloop`

执行顺序固定为低耦合到高耦合：

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

可见性策略固定为：

- 先成簇移动 package-private 类型，尽量保持默认可见性
- 只有跨簇稳定契约需要暴露时，才提升为 `public`
- 禁止为了“方便搬家”批量放大可见性

### Phase 3：`ContextCompactor` 结构化接线

最终态固定为：

- 新增结构化 generate request，例如 `LlmGenerateRequest`
- `LlmProvider.generate(...)` 主路径改为消费 request object
- request 必须能承载 `ProjectedContext` 或 `ContextViews`
- `OllamaLlmProvider` 与 `OllamaGenerationExecutor` 统一走新 request 主链
- `ContextCompactor` 基于四层上下文裁剪，而不是对拼好的长字符串做 `truncateMiddle`
- 裁剪优先级固定为：
  - `Trace`
  - `Evidence`
  - `Working`
  - `Durable`
- 删除 provider 主路径上的旧字符串 compact 入口

### Phase 4：`StageProgressCoordinator` 瘦身

目标：

- coordinator 只保留阶段推进、调用顺序、状态协调
- payload converter / assembler 下沉到独立组件
- file change / patch target 这类内容装配逻辑从 coordinator 中拆出

---

## Progress Tracking

本轮进度统一由：

- `docs/architecture-refactor-work-items.md`

进行跟踪。

规则：

- 这是本轮唯一有效的架构整改 tracker
- 每个 phase 的 checklist、证据、blocker 都只写这里
- 不把架构整改 checklist 混写进 `docs/active-work-items.md`
- 如果 blocker 变化，先更新 tracker，再继续改代码

---

## Test Plan

测试节奏固定为“阶段内分段验证”，不等所有 phase 改完再统一发现问题。

### Phase 0

```bash
mvn -q -Dtest=DefaultWorkflowEngineTests,SupervisorAgentTests,ImplementationToolLoopExecutorTests test
```

### Phase 1

```bash
mvn -q compile
```

重点验证：

- `context -> orchestrator` 依赖已消失
- `domain` 迁移后编译通过

### Phase 2

- 每拆完一个职责簇跑一次 `mvn -q compile`
- phase 末尾跑 `mvn -q test`
- phase 末尾做一次 code review

review 重点：

- 可见性是否被不必要放大
- `tools` / `implementation` 边界是否重新混了

### Phase 3

- 补 `ContextCompactor` 相关单测
- 补 generate 主链测试
- 跑 `mvn -q test`

### Phase 4

- 跑 orchestrator 相关测试
- 再做一次 code review

所有 phase 的退出条件固定为：

- `self-test` 已完成
- `code review` 已完成
- 方案文档与 tracker 已同步

---

## Assumptions

- 本轮以本文档为唯一架构方案根文档，不再新增第三份根目录架构方案
- `docs/architecture-refactor-work-items.md` 是新建的专用进度追踪文档
- `sandbox` 不纳入本轮
- 本轮默认不新增第三方依赖；只有在某个 phase 证明现有边界无法收口时，才重新做 build-vs-buy 判断
- 在全部 5 个 phase 完成前，不进入新的黄金路径集成测试主线

---

## Completion Gate

只有以下条件全部满足，才允许宣称本轮架构整改完成：

- `WorkflowEngine` 假接口彻底删除，CLI 只依赖真 facade
- `context -> orchestrator` 依赖彻底消失
- `executor` 根包不再承担平铺职责，职责簇已落位
- tool loop 不再依赖隐式 common pool
- generate 主链已切到结构化上下文输入，旧字符串 compact 主路径已删除
- `StageProgressCoordinator` 已收回纯 orchestration
- 各 phase 的 `self-test + code review + docs sync` 证据已全部写入 tracker
