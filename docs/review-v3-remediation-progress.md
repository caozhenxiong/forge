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

- [ ] 提取 `StageContinuationContext`
- [ ] 提取 `ImplementationContinuationSupport`
- [ ] 提取 `StageContinuationNoteBuilder`
- [ ] 删除 `StageProgressCoordinator` 中 continuation payload helper
- [ ] `requireStage()` 统一收口到 `StageStatusSupport`
- [ ] Phase 1 `self-test`
- [ ] Phase 1 `code review`
- [ ] 同步更新方案文档与本文档

### Phase 2. revision / supervisor / flow 依赖注入收口

- [ ] `StageRevisionSupport` 去双构造器和隐藏 `new`
- [ ] `SupervisorDecisionSanitizer` 注入 `SupervisorPayloadNormalizer`
- [ ] `SupervisorAgent` catch 变量改为 `ex`
- [ ] `FlowController.mapReason()` 改为 `switch expression`
- [ ] `FlowController` 分隔符字面量提取常量
- [ ] Phase 2 `self-test`
- [ ] Phase 2 `code review`
- [ ] 同步更新方案文档与本文档

### Phase 3. validation / testing 结构收口

- [ ] `ValidationStrategyPlanner` 增加 deterministic fallback 日志
- [ ] `TestExecutor` 依赖按 planning / run / evidence 分组
- [ ] `TestExecutionConfiguration` 同步重接线
- [ ] 测试 support / harness 同步改签
- [ ] Phase 3 `self-test`
- [ ] Phase 3 `code review`
- [ ] 同步更新方案文档与本文档

### Phase 4. context / document pipeline 收口

- [ ] `ContextProjector` 拆为 reader / resolver / summary assembler / assembler
- [ ] 引入 `ContextProjectionArtifacts` / `ContextProjectionContractBundle` / `ContextProjectionSummaries`
- [ ] document composer 收敛为模板骨架 + 三个阶段策略
- [ ] `DocumentStageComposer` 退化为纯路由器
- [ ] Phase 4 `self-test`
- [ ] Phase 4 `code review`
- [ ] 同步更新方案文档与本文档

### Phase 5. 守门与文档对齐

- [ ] 新增 `System.getProperty(String)` ArchUnit 规则
- [ ] 完成本轮 code review
- [ ] tracker 与代码状态对齐
- [ ] Phase 5 `self-test`
- [ ] Phase 5 `code review`
- [ ] 同步更新方案文档与本文档

## Current Status

- 当前阶段：`Planning complete, pending Claude review`
- 当前 blocker：`无`
- 当前约束：`禁止兼容层、禁止双轨并存、禁止“后续再清理”`

## Evidence Log

### Phase 1

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`REVIEW_V3_REMEDIATION_PLAN.md` 与本文档已创建

### Phase 2

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

### Phase 3

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

### Phase 4

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

### Phase 5

- commit：`待开始`
- self-test：`待开始`
- code review：`待开始`
- docs：`待开始`

## Completion Gate

只有以下条件全部满足，才允许宣称本轮完成：

- [ ] `StageProgressCoordinator` 中不再存在 continuation payload 解析 helper
- [ ] `StageTransitionSupport.continueStage()` 已改为 `StageContinuationContext`
- [ ] `requireStage()` 只剩 `StageStatusSupport` 一个实现
- [ ] `StageRevisionSupport`、`SupervisorDecisionSanitizer`、`ContextProjector`、`DocumentStageComposer` 不再保留本轮收口范围内的隐藏 `new` / 双构造器
- [ ] `TestExecutor` 构造器已完成职责分组
- [ ] `ValidationStrategyPlanner` fallback 异常可观测
- [ ] 三阶段 document composer 已收敛到模板骨架 + 策略
- [ ] `System.getProperty(String)` ArchUnit 守门已接入
- [ ] `self-test + code review + 文档同步 + tracker 证据` 已全部补齐

结果：`IN_PROGRESS`
