# Review V3 收口进度

## Purpose

这份文档是 `REVIEW_V3_REMEDIATION_PLAN.md` 的唯一执行 tracker。

规则：

- 只跟踪本轮 `REVIEW_V3` 收口，不混写 `V2` 收尾，也不混写集成测试结果
- 每个 phase 开始前先更新本文档
- 每完成一项，直接打勾并补证据
- 证据固定写：`commit / self-test / code review / docs`
- blocker 变化时，先更新本文档，再继续改代码

## Final State

完成态必须同时满足：

- `StageProgressCoordinator` 只保留 orchestration 主流程
- `StageContinuationContext` 成为唯一 continuation 上下文对象
- `ImplementationContinuationSupport` 成为 continuation payload 唯一转换入口
- `StageStatusSupport` 成为 `requireStage()` 唯一共享入口
- `StageRevisionSupport`、`SupervisorDecisionSanitizer`、`ContextProjector`、`DocumentStageComposer` 不再保留本轮收口范围内的隐藏 `new` / 双构造器
- `TestExecutor` 构造器完成职责分组
- 三阶段文档 composer 已收敛到模板骨架 + 策略
- `System.getProperty(String)` ArchUnit 守门已接入

## Removal Plan

本轮必须删除：

- `StageProgressCoordinator` 中 continuation payload 解析 helper
- `StageRevisionSupport` 的双构造器与内部 `new` 链
- `SupervisorDecisionSanitizer` 内部创建 `SupervisorPayloadNormalizer` 的路径
- `ContextProjector` 中读 artifact / 解析 contract / summarize / assemble 混杂的单体实现
- 三套重复的 document composer 主链
- `TestExecutor` 14 参数平铺注入
- `FlowController.mapReason()` 的 if 链与 `" | "` 字面量散落

## Phase Checklist

### Phase 1. orchestration continuation 收口

- [x] 提取 `StageContinuationContext`
- [x] 提取 `ImplementationContinuationSupport`
- [x] 提取 `StageContinuationNoteBuilder`
- [x] 删除 `StageProgressCoordinator` 中 continuation payload helper
- [x] `requireStage()` 统一收口到 `StageStatusSupport`
- [x] Phase 1 `self-test`
- [x] Phase 1 `code review`
- [x] 同步更新方案文档与本文档

### Phase 2. revision / supervisor / flow 依赖注入收口

- [x] `StageRevisionSupport` 去双构造器和隐藏 `new`
- [x] `StageRevisionRepairSupport` / `StageRevisionNoteBuilder` 从 `StageRevisionSupport` 构造器里移出，并在 `OrchestratorConfiguration` 中显式 bean 化
- [x] `SupervisorDecisionSanitizer` 注入 `SupervisorPayloadNormalizer`
- [x] `SupervisorAgent` catch 变量改为 `ex`
- [x] `FlowController.mapReason()` 改为 `switch expression`
- [x] `FlowController` 分隔符字面量提取常量
- [x] Phase 2 `self-test`
- [x] Phase 2 `code review`
- [x] 同步更新方案文档与本文档

### Phase 3. validation / testing 结构收口

- [x] `ValidationStrategyPlanner` 增加 deterministic fallback 日志
- [x] `TestExecutor` 依赖按 planning / run / evidence 分组
- [x] `TestExecutionConfiguration` 同步重接线
- [x] 测试 support / harness 同步改签
- [x] Phase 3 `self-test`
- [x] Phase 3 `code review`
- [x] 同步更新方案文档与本文档

### Phase 4. context / document pipeline 收口

- [x] `ContextProjector` 拆为 reader / resolver / summary assembler / assembler
- [x] 引入 `ContextProjectionArtifacts` / `ContextProjectionContractBundle` / `ContextProjectionSummaries`
- [x] document composer 固定收敛为 `DocumentCompositionTemplate + DocumentCompositionStrategy` 组合模式
- [x] `DocumentStageComposer` 退化为纯路由器
- [x] Phase 4 `self-test`
- [x] Phase 4 `code review`
- [x] 同步更新方案文档与本文档

### Phase 5. 守门与文档对齐

- [x] 新增 `System.getProperty(String)` ArchUnit 规则
- [x] 完成本轮 code review
- [x] tracker 与代码状态对齐
- [x] Phase 5 `self-test`
- [x] Phase 5 `code review`
- [x] 同步更新方案文档与本文档

## Current Status

- 当前阶段：`Phase 1-5 completed`
- 当前 blocker：`无`
- 当前约束：`禁止兼容层、禁止双轨并存、禁止“后续再清理”`

## Evidence Log

### Phase 1

- commit：`当前工作区已完成 continuation 收口，待统一 commit`
- self-test：`mvn -q -Dtest=StageProgressCoordinatorTests,StageTransitionSupportTests,StageEntryExecutorTests,DefaultWorkflowEngineTests test` 通过；`mvn -q -DskipTests test-compile` 通过
- code review：`StageProgressCoordinator` 的 continuation helper 已清零；`requireStage()` 只剩 `StageStatusSupport` 一个实现入口；`continueStage()` 已改为 `StageContinuationContext`
- docs：`本文档已提升为唯一 tracker，并补充 Phase 2 wiring 显式装配要求与 Phase 4 组合式 document composition 约束`

### Phase 2

- commit：`当前工作区已完成 revision / supervisor / flow 收口，待统一 commit`
- self-test：`mvn -q -Dtest=StageProgressCoordinatorTests,StageTransitionSupportTests,StageEntryExecutorTests,DefaultWorkflowEngineTests,SupervisorAgentTests,FlowControllerTests test` 通过；`mvn -q -DskipTests test-compile` 通过
- code review：`StageRevisionSupport` 已收成单构造器；`SupervisorGuidanceRenderer` / `StageRevisionNoteBuilder` / `SupervisorPayloadNormalizer` 只在配置类里显式装配；`SupervisorAgent` 已无 `ignored` catch；`FlowController` 已改为穷举 `switch`
- docs：`tracker 已同步 Phase 1-2 完成状态`

### Phase 3

- commit：`当前工作区已完成 validation / testing 结构收口，待统一 commit`
- self-test：`mvn -q -DskipTests test-compile` 通过；`mvn -q -Dtest=ValidationStrategyPlannerTests,TestExecutorTests test` 通过
- code review：`TestExecutor` 已改为 `TestPlanningComponents/TestRunComponents/TestEvidenceComponents` 三组注入；旧 14 依赖构造入口已删除；`ValidationStrategyPlanner` 已输出 deterministic fallback warn 日志
- docs：`tracker 已同步 Phase 3 完成状态`

### Phase 4

- commit：`当前工作区已完成 context / document pipeline 收口，待统一 commit`
- self-test：`mvn -q -DskipTests test-compile` 通过；`mvn -q -Dtest=StageArtifactComposerTests,DefaultWorkflowEngineTests,SupervisorAgentTests,StageProgressCoordinatorTests test` 通过；`mvn -q -Dtest=StageArtifactComposerTests#prdComposeKeepsValidationMetadataAndDropsUnsourcedThresholds test` 通过`
- code review：`旧 Analysis/Prd/Design composer 引用已清零；新的 context/document collaborators 只在 configuration 中装配；`ContextProjector` 与 `DocumentStageComposer` 已无隐藏 new；PRD/Design 的 persisted contract 投影已恢复为 sanitize 后提取，和旧实现顺序一致`
- docs：`tracker 已同步 Phase 4 完成状态`

### Phase 5

- commit：`当前工作区已完成 ArchUnit 守门与 tracker 对齐，待统一 commit`
- self-test：`rg -n "System\\.getProperty\\(" src/main/java` 返回 0；`mvn -q -Dtest=ArchitectureRulesTests test` 通过；`mvn -q -DskipTests test-compile` 通过`
- code review：`ArchitectureRulesTests` 已从 3 条扩为 4 条规则；新增 main code 禁止 `System.getProperty(String)` 守门；`src/main/java` 中 `System.getProperty` 残留为 0`
- docs：`tracker 已同步 Phase 5 完成状态，并将 completion gate 全部对齐`

## Completion Gate

只有以下条件全部满足，才允许宣称本轮完成：

- [x] `StageProgressCoordinator` 中不再存在 continuation payload 解析 helper
- [x] `StageTransitionSupport.continueStage()` 已改为 `StageContinuationContext`
- [x] `requireStage()` 只剩 `StageStatusSupport` 一个实现
- [x] `StageRevisionSupport`、`SupervisorDecisionSanitizer`、`ContextProjector`、`DocumentStageComposer` 不再保留本轮收口范围内的隐藏 `new` / 双构造器
- [x] `TestExecutor` 构造器已完成职责分组
- [x] `ValidationStrategyPlanner` fallback 异常可观测
- [x] 三阶段 document composer 已收敛到模板骨架 + 策略
- [x] `System.getProperty(String)` ArchUnit 守门已接入
- [x] `self-test + code review + 文档同步 + tracker 证据` 已全部补齐

结果：`DONE`
