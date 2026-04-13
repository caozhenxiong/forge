# 架构整改执行清单

## 用途

这份文档只跟踪当前架构整改主线，不混写黄金路径集成验证，也不回收历史大清单。

规则：

- 每个 phase 下只保留当前还没完成的 checklist
- 每完成一项，直接打勾并补上证据
- 证据固定写：`commit / self-test / code review / docs`
- 如果 blocker 变化，先更新本文档，再继续改代码
- 本文档是当前唯一有效的架构整改 tracker

---

## Final State

完成态必须同时满足：

- `WorkflowEngine` 成为 CLI 唯一工作流门面
- `context -> orchestrator` 依赖消失，`domain` 成为共享模型层
- `executor` 按职责分层，不再是平铺大包
- implementation tool loop 的执行器、权限、上下文边界清晰
- generate 主链接入结构化上下文，`ContextCompactor` 按四层裁剪
- `StageProgressCoordinator` 只保留 orchestration

---

## Removal Plan

本轮必须删除：

- `WorkflowEngine` 无 `Path` 旧方法
- `DefaultWorkflowEngine` 中抛异常的伪实现
- CLI 对 `DefaultWorkflowEngine` 的直接依赖
- `ImplementationToolLoopExecutor` 对 `ForkJoinPool.commonPool()` 的隐式依赖
- generate 主链只接受字符串 compact 的旧入口
- `context` 对 `orchestrator` 域模型的直接依赖
- `executor` 根包平铺职责

---

## Phase Checklist

### Phase 0. 契约与可观测性修复

- [x] `ImplementationExecutor` 去工厂化，改为单构造器注入
- [x] `WorkflowEngine` 升级为完整 path-aware facade
- [x] CLI 改为依赖 `WorkflowEngine`
- [x] 删除 `DefaultWorkflowEngine` 无 `Path` 旧入口与伪实现
- [x] `SupervisorAgent` 两处静默异常改为 `warn` 日志
- [x] 新增 `SubtaskExecutionContext`，收掉 `SubtaskExecutor.executeSubtask(...)` 的离散参数入口
- [x] `ImplementationToolLoopExecutor` 显式接收 `ExecutorService`
- [x] `ImplementationExecutor` 持有并管理执行器生命周期
- [x] tool loop continuation prompt 回收到 `ImplementationToolPromptBuilder`
- [x] Phase 0 `self-test`
- [x] Phase 0 `code review`
- [x] 同步更新方案文档与本文档

### Phase 1. 提取 `domain`

- [x] 迁移 `StageType`
- [x] 迁移 `RunRecord`
- [x] 迁移 `RunStatus`
- [x] 迁移 `RunConfig`
- [x] 迁移 `GatePolicy`
- [x] 迁移 `StageExecution`
- [x] 迁移 `StageStatus`
- [x] 清理 `context -> orchestrator` 依赖
- [x] Phase 1 `compile`
- [x] Phase 1 `code review`
- [x] 同步更新方案文档与本文档

### Phase 2. `executor` 拆职责簇

- [x] 建立 `executor.llm`
- [x] 建立 `executor.context`
- [x] 建立 `executor.generation`
- [x] 建立 `executor.shell`
- [x] 建立 `executor.tools`
- [x] 建立 `executor.runtime`
- [x] 建立 `executor.gate`
- [ ] 建立 `executor.editing`
- [ ] 建立 `executor.patch`
- [x] 建立 `executor.testing`
- [x] 建立 `executor.subtask`
- [x] 建立 `executor.implementation`
- [x] `ImplementationToolLoopExecutor` 等 toolloop 组件落入 `executor.implementation.toolloop`
- [x] Phase 2 `compile/test`
- [x] Phase 2 `code review`
- [x] 同步更新方案文档与本文档

### Phase 3. `ContextCompactor` 结构化接线

- [ ] 新增结构化 generate request
- [ ] `LlmProvider.generate(...)` 主路径切到 request object
- [ ] `OllamaLlmProvider` 接入新主路径
- [ ] `OllamaGenerationExecutor` 接入结构化上下文
- [ ] `ContextCompactor` 改为四层裁剪
- [ ] 删除旧字符串 compact 主路径
- [ ] Phase 3 `self-test`
- [ ] Phase 3 `code review`
- [ ] 同步更新方案文档与本文档

### Phase 4. `StageProgressCoordinator` 瘦身

- [ ] 提取 payload converter / assembler
- [ ] 提取 file change / patch target 装配逻辑
- [ ] `StageProgressCoordinator` 收回纯 orchestration
- [ ] Phase 4 `self-test`
- [ ] Phase 4 `code review`
- [ ] 同步更新方案文档与本文档

---

## Per-phase Exit Criteria

每个 phase 结束都必须满足：

- 对应 checklist 已全部打勾
- `self-test` 或 `compile/test` 已完成
- `code review` 已完成
- 本文档已补齐证据
- 没有残留“后续再清理”的旧路径

---

## Current Status

- 当前阶段：`Phase 2 / 进行中`
- 当前 blocker：`Phase 2 剩余 root flat 职责已进一步收缩到 editing/patch；runtime/gate/testing 核心模型与执行链已经下沉，根包只剩消费这些结果的 renderer / orchestration 边界`
- 最近完成证据：`Phase 2 已完成 llm/context/generation/shell/tools/subtask/implementation/runtime/gate/testing 十个职责簇拆分；runtime ownership / wiring graph / completeness gate / stage gate / test evidence gate / testcase execution chain 已整体落位，主代码 compile 与全量测试已通过`

---

## Evidence Log

### Phase 0

- commit：`未提交（工作树已包含 Phase 0 完整改动）`
- self-test：`mvn -q -Dtest=DefaultWorkflowEngineTests,SupervisorAgentTests,ImplementationToolLoopExecutorTests,ImplementationToolPromptBuilderTests,ImplementationExecutorTests test`；`mvn -q test`
- code review：`已完成；聚焦 wiring、context 边界、tool executor 所有权与 CLI facade，无新增阻塞问题`
- docs：`ARCHITECTURE_REVIEW_INTEGRATED_PLAN.md`、本文档已同步

### Phase 1

- commit：`未提交（工作树已包含 Phase 1 完整改动）`
- self-test：`mvn -q -DskipTests compile`；`mvn -q test`
- code review：`已完成；确认旧 orchestrator 域模型文件已删除、旧 import 已清空、context 不再依赖 orchestrator，共享模型统一落到 domain`
- docs：`docs/current-state.md`、本文档已同步

### Phase 2

- commit：`待填写`
- self-test：`已完成当前 checkpoint：mvn -q -DskipTests compile；mvn -q -Dtest=ArchitectIntegrationCheckTests,ImplementationGateEngineTests,ImplementationStageGateTests,WebRuntimeWiringCheckTests,UiRuntimeContractResolverTests,ImplementationCompletenessCheckTests,ImplementationCompletenessGateTests,TestEvidenceGateTests,PlaywrightCaseExecutorTests,PlaywrightExecutionPolicyTests,TestCasePlannerTests,TestCasePlanSanitizerTests,TestCasePromptAssemblerTests,TestEvidenceCollectorTests,TestExecutorTests,TestPlanningPolicyTests,TestRunnerTests,TestToolSelectorTests,ImplementationExecutorTests test；mvn -q test`
- code review：`已完成当前 checkpoint：确认 executor 根包不再残留 runtime/gate/testing 核心类；testing 相关单测已迁到同包边界，根包只剩 editing/patch 与消费层 orchestration/renderer`
- docs：`docs/current-state.md、本文档已同步到 runtime + gate + testing checkpoint`

### Phase 3

- commit：`待填写`
- self-test：`待填写`
- code review：`待填写`
- docs：`待填写`

### Phase 4

- commit：`待填写`
- self-test：`待填写`
- code review：`待填写`
- docs：`待填写`

---

## Completion Gate

只有以下条件全部满足，才允许宣称本轮架构整改完成：

- `WorkflowEngine` 假接口彻底删除
- `context -> orchestrator` 依赖彻底消失
- `executor` 职责簇已落位
- tool loop 不再使用隐式 common pool
- generate 主链已切到结构化上下文输入
- `StageProgressCoordinator` 已收回纯 orchestration
- 5 个 phase 的证据全部补齐
