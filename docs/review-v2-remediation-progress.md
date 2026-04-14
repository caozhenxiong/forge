# Review V2 收口进度

## Purpose

这份文档是 `REVIEW_V2_REMEDIATION_PLAN.md` 的唯一执行 tracker。

规则：

- 只跟踪本轮 Claude Review V2 收口，不混写上一轮架构整改，也不混写黄金路径集成验证
- 每个 phase 开始前先更新本文档
- 每完成一项，直接打勾并补证据
- 证据固定写：`commit / self-test / code review / docs`
- blocker 变化时，先更新本文档，再继续改代码

## Final State

完成态必须同时满足：

- `executor` 根包只保留 10 个白名单类
- `devflow.agent.editing` 旧包已清空，全部迁入 `devflow.agent.editing.precise`
- `RuntimeSnapshot*` 已上移到 `executor.runtime`
- `WorkflowAction` 成为唯一流程动作枚举
- `LanguagePolicy` 成为唯一默认语言决策入口，`devflow.document.default-language` 已配置化
- main source 中 `System.getProperty("devflow...") == 0`
- `StageProgressCoordinator` 只保留 orchestration
- ArchUnit 守门测试已接入并通过

## Removal Plan

本轮必须删除：

- executor 根包中的剩余 `Implementation*` 平铺职责
- `devflow.agent.editing` 旧包路径
- `FlowAction` / `SupervisorAction`
- 所有 `System.getProperty("devflow...")` 主代码入口
- `code_review_review*.md` 命名
- `StageProgressCoordinator` 中 implementation continuation/block payload 拼装逻辑
- `TestExecutor`、`StageReviewer` 及同类类中的隐藏构造器工厂链

## Phase Checklist

### Phase 1. executor / editing / runtime 包边界收口

- [x] executor 根包剩余 `Implementation*` 类迁入 `executor.implementation.*`
- [x] `StructuredPatchSupport` / `StructuredPatchHunk` 迁入 `executor.patch`
- [x] `RuntimeWorkingSetPolicy` / `RuntimeWorkingSetResolver` 迁入 `executor.runtime`
- [x] `ScopedTaskPackageSupport` / `TaskPackageMarkdownRenderer` 迁入 `executor.implementation.render`
- [x] `RuntimeSnapshot*` 迁入 `executor.runtime`
- [x] `devflow.agent.editing` 整体迁入 `devflow.agent.editing.precise`
- [x] 更新 main/test import 与 package 声明
- [x] Phase 1 `self-test`
- [x] Phase 1 `code review`
- [x] 同步更新方案文档与本文档

### Phase 2. Spring 配置体系与默认语言配置化

- [x] `DevflowAgentApplication` 切换到 `@ConfigurationPropertiesScan`
- [x] 新增 `DocumentLanguageProperties`
- [x] 新增 implementation / patch / stage / subtask-review / test-planning / playwright / prompt-token-estimator / tool-permission / ollama client 的 typed properties
- [x] `pom.xml` 增加 `spring-boot-configuration-processor`
- [x] 新增最小 `application.yml`
- [x] 主代码中 `System.getProperty("devflow...")` 收敛为 0
- [x] `DocumentLanguage.detect(...)` 不再承担默认语言
- [x] `LanguagePolicy` 接管默认语言决策
- [x] Phase 2 `self-test`
- [x] Phase 2 `code review`
- [x] 同步更新方案文档与本文档

### Phase 3. TestExecutor / StageReviewer / 同类隐藏 `new` 去工厂化

- [x] `TestExecutor` 去工厂化
- [x] `StageReviewer` 去工厂化
- [x] `DocumentStructureGuard` 去工厂化
- [x] `DocumentReviewNormalizer` 去工厂化
- [x] `ImplementationReviewNormalizer` 去工厂化
- [x] `HtmlStructureRuntimeSignalResolver` 去工厂化
- [x] `SubtaskPerformanceGuidanceResolver` 去工厂化
- [x] `Phase 3` 追加清理 `QualityPlanFactory` / `ImplementationContextResolver` / `TestCasePlanner` / `SubtaskRuntimeWiringGuard` 的同类隐藏依赖链
- [x] Phase 3 `self-test`
- [x] Phase 3 `code review`
- [x] 同步更新方案文档与本文档

### Phase 4. WorkflowAction + StageArtifactNames + StageFlowPolicy + 命名收口

- [x] 合并 `FlowAction` / `SupervisorAction` 为 `domain.WorkflowAction`
- [x] 更新 `SupervisorDecision` / `FlowDecision` / controller / executor / sanitizer / fallback support / tests
- [x] `StageArtifactNames` 改用 `switch expression`
- [x] `StageFlowPolicy` 改用 `switch expression`
- [x] `code_review_review*.md` 改名为 `code_review_feedback*.md`
- [x] 运行态 direct `DocumentLanguage.detect(...)` 收敛为 0
- [x] Phase 4 `self-test`
- [x] Phase 4 `code review`
- [x] 同步更新方案文档与本文档

### Phase 5. StageProgressCoordinator 职责下沉 + ArchUnit 守门

- [x] 提取 implementation continuation / block / payload helper
- [x] `StageProgressCoordinator` 收回纯 orchestration
- [x] `pom.xml` 增加 `archunit-junit5`
- [x] 新增 ArchUnit 守门测试
- [x] Phase 5 `self-test`
- [x] Phase 5 `code review`
- [x] 同步更新方案文档与本文档

## Current Status

- 当前阶段：`Phase 1 - Phase 5 已完成，等待后续 commit / push / 集成测试`
- 当前 blocker：`无`
- 当前约束：`禁止兼容层、禁止双轨并存、禁止“后续再清理”`

## Evidence Log

### Phase 1

- commit：`当前工作区已完成包边界迁移，待统一 commit`
- self-test：`mvn -q -DskipTests compile`、`mvn -q -DskipTests test-compile` 通过
- code review：`find src/main/java/devflow/agent/executor -maxdepth 1 -type f | wc -l == 10`；`find src/main/java/devflow/agent/editing -maxdepth 1 -type f | wc -l == 0`
- docs：`REVIEW_V2_REMEDIATION_PLAN.md` 保持设计态；`docs/review-v2-remediation-progress.md` 已同步真实状态

### Phase 2

- commit：`当前工作区已完成配置化收口，待统一 commit`
- self-test：`mvn -q -DskipTests compile`、`mvn -q -DskipTests test-compile` 通过
- code review：`rg -n "System\\.getProperty|System\\.getBoolean|Boolean\\.getBoolean|Integer\\.getInteger|Long\\.getLong" src/main/java == 0`；`rg -n "DocumentLanguage\\.detect\\(" src/main/java == 0`
- docs：`src/main/resources/application.yml` 已纳入默认语言配置；tracker 已补 Phase 2 证据

### Phase 3

- commit：`当前工作区已完成 façade / support 去工厂化，待统一 commit`
- self-test：`mvn -q -Dtest=ArchitectureRulesTests,QualityPlanFactoryTests,TestCasePlannerTests,TestCasePromptAssemblerTests,ImplementationContextResolverTests,ImplementationPlanCoverageAnalyzerTests,ImplementationCompletenessGateTests,ImplementationExecutorTests,StageArtifactComposerTests,DefaultWorkflowEngineTests test` 通过
- code review：`quality / subtask / testing / implementation.planning` 主路径内 `new TreeSitterSupport()`、`new ContractExtractor()` 已清零；`QualityPlanFactory`、`TestCasePlanner`、`ImplementationContextResolver`、`SubtaskPerformanceGuidanceResolver`、`SubtaskRuntimeWiringGuard` 已改为显式依赖
- docs：tracker 已补 Phase 3 追加清理说明，不再把同类残留留到后续

### Phase 4

- commit：`当前工作区已完成 WorkflowAction / 命名收口，待统一 commit`
- self-test：`mvn -q -Dtest=ArchitectureRulesTests,DefaultWorkflowEngineTests test` 通过
- code review：`rg -n "FlowAction|SupervisorAction" src/main/java src/test/java == 0`；`StageArtifactNames` 仅保留 `code_review_feedback.md` / `code_review_feedback_history.md`
- docs：tracker 已同步 Phase 4 完成状态

### Phase 5

- commit：`当前工作区已完成 orchestration / ArchUnit 守门收口，待统一 commit`
- self-test：`ArchitectureRulesTests`、`DefaultWorkflowEngineTests`、`StageArtifactComposerTests` 通过
- code review：`executor root whitelist`、`quality -> executor.testing 依赖禁止`、`editing.precise -> executor 依赖禁止` 已由 ArchUnit 守门；`StageProgressCoordinator` 相关路径维持 orchestration 角色
- docs：tracker 已同步 Phase 5 完成状态

## Completion Gate

只有以下条件全部满足，才允许宣称本轮完成：

- [x] executor 根包源码数为 10
- [x] `devflow.agent.editing` 旧包源码数为 0
- [x] main source 中 `System.getProperty("devflow...") == 0`
- [x] main source 中 `executor.testing.RuntimeSnapshot*` 直接引用为 0
- [x] main source 中运行态 direct `DocumentLanguage.detect(...) == 0`
- [x] Spring façade/orchestrator 构造器不存在本轮收口范围内的子系统隐藏 `new` 链
- [x] ArchUnit 守门测试通过
- [x] `self-test + code review + 文档同步 + tracker 证据` 已全部补齐

结果：`PASS`
