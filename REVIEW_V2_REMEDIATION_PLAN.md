# Claude Review 收口方案 V2.2

## Summary

- 本轮仍按 `AGENTS.md` 和 `docs/engineering-agreements.md` 做一次性收口，不做逐点修补，不留兼容层。
- 方案产物固定为 2 份：
  - 设计方案：`REVIEW_V2_REMEDIATION_PLAN.md`
  - 进度追踪：`docs/review-v2-remediation-progress.md`
- 不复用 `docs/architecture-refactor-work-items.md`。那份文档属于上一轮架构整改主线，混写会让证据、阶段和 blocker 失真。
- `DocumentLanguage` 默认语言改为配置项，不依赖 JVM locale；ArchUnit 依赖和架构守门测试显式纳入本轮。

## Final state

- `executor` 根包只保留 10 个类型：`ImplementationExecutor`、`ImplementationExecutionBundle`、`ImplementationProgressSink`、`ImplementationExecutorConfiguration`、`ImplementationExecutorWiring`、`FileChange`、`SelfCheckResult`、`ChangeAction`、`DeliveryMode`、`DeliveryPolicyEnvelope`。
- `devflow.agent.editing` 整体迁入 `devflow.agent.editing.precise`，旧包路径删除；`executor.editing` 只保留编排/策略层，单向依赖 `editing.precise`。
- `RuntimeSnapshot`、`RuntimeSnapshotCaptureStatus`、`RuntimeSnapshotFailureCode` 位于 `executor.runtime`；`quality` 不再依赖 `executor.testing`。
- 流程动作只保留 `domain.WorkflowAction`；`FlowAction`、`SupervisorAction` 删除。
- 阶段文件命名和阶段推进规则都改成穷举 `switch expression`。
- 默认文档语言由 `devflow.document.default-language` 决定，默认值固定为 `ZH`；`LanguagePolicy` 是唯一默认语言决策入口。
- 所有 `devflow.*` 运行时策略进入 Spring `@ConfigurationProperties` 体系；主代码中不再直接读取 `System.getProperty("devflow...")`。
- `TestExecutor`、`StageReviewer` 以及同类 Spring façade 不再在构造器里 `new` 子系统依赖。
- `StageProgressCoordinator` 只保留 orchestration 主流程，不再拼 implementation continuation/block payload 或 `ReviewResult`。

## Key changes

- 包边界收口：
  - executor 根包剩余 `Implementation*` 类迁入 `executor.implementation.planning`、`state`、`render`、`toolloop`
  - `StructuredPatchSupport` / `StructuredPatchHunk` 迁入 `executor.patch`
  - `RuntimeWorkingSetPolicy` / `RuntimeWorkingSetResolver` 迁入 `executor.runtime`
  - `ScopedTaskPackageSupport` / `TaskPackageMarkdownRenderer` 迁入 `executor.implementation.render`
  - `devflow.agent.editing -> devflow.agent.editing.precise` 与上述迁移同批完成，不单拆
- 配置体系收口：
  - `DevflowAgentApplication` 切到 `@ConfigurationPropertiesScan`
  - 新增 `DocumentLanguageProperties`，前缀 `devflow.document`，字段 `defaultLanguage`
  - 新增分域 typed properties：implementation、patch、stage、subtask-review、test-planning、playwright、prompt-token-estimator、tool-permission、ollama client
  - `pom.xml` 新增 `spring-boot-configuration-processor` 和 `com.tngtech.archunit:archunit-junit5:1.4.1` 的 test 依赖
  - 仓库新增最小 `application.yml`，只显式声明 `devflow.document.default-language: zh`；其余技术默认值继续保留在 properties 归一化逻辑里
- 语言决策收口：
  - `DocumentLanguage.detect(...)` 只负责文本信号检测，不再承担默认值
  - 14 个主代码 direct `detect(...)` 调用点统一改为 `LanguagePolicy.resolve(...)` 或由上层传入已解析语言
  - 不允许“部分 resolve、部分 detect fallback”双轨并存
- 去工厂化与流程收口：
  - `TestExecutor`、`StageReviewer`、`DocumentStructureGuard`、`DocumentReviewNormalizer`、`ImplementationReviewNormalizer`、`HtmlStructureRuntimeSignalResolver`、`SubtaskPerformanceGuidanceResolver` 一起去工厂化
  - 引入 `domain.WorkflowAction`，同步更新 `SupervisorDecision`、`FlowDecision`、fallback support、sanitizer、controller、executor、测试
  - `StageArtifactNames` 与 `StageFlowPolicy` 改为 `switch expression`
  - `code_review_review*.md` 改为 `code_review_feedback*.md`，不做运行时兼容读取
  - 提取 implementation continuation/block/payload helper，`StageProgressCoordinator` 只保留“读产物 -> review -> supervisor -> flow -> apply”

## Progress tracking

- 独立 tracker 文件固定为：`docs/review-v2-remediation-progress.md`
- 该文件是本轮唯一有效的进度追踪文档，结构固定为：
  - `Purpose`
  - `Final State`
  - `Removal Plan`
  - `Phase Checklist`
  - `Current Status`
  - `Evidence Log`
  - `Completion Gate`
- `Phase Checklist` 固定 5 个 phase：
  1. executor / editing / runtime 包边界收口
  2. Spring 配置体系与默认语言配置化
  3. TestExecutor / StageReviewer / 同类隐藏 `new` 去工厂化
  4. WorkflowAction + StageArtifactNames + StageFlowPolicy + 命名收口
  5. StageProgressCoordinator 职责下沉 + ArchUnit 守门
- 每个 phase 必须记录：
  - checklist 项
  - blocker
  - `commit / self-test / code review / docs` 四类证据
- 工作流要求：
  - 开始每个 phase 前先更新 tracker，把当前 phase 标为进行中
  - phase 完成后立即打勾并补证据
  - blocker 变化时先更新 tracker，再继续改代码
  - 没有更新 tracker，不得宣称 phase 完成
- `REVIEW_V2_REMEDIATION_PLAN.md` 只描述设计，不记录执行进度；进度只写入 tracker，不双写

## Test plan

- 编译验证：
  - `mvn -q -DskipTests compile`
  - `mvn -q -DskipTests test-compile`
- 定向单测：
  - `StageReviewerTests`
  - `TestExecutorTests`
  - `DefaultWorkflowEngineTests`
  - `FlowControllerTests`
  - `StageProgressCoordinatorTests`
  - `LanguagePolicyTests`
  - `DocumentLanguageTests`
  - properties 绑定测试
  - ArchUnit 架构测试
- 架构守门规则：
  - executor 根包白名单固定为 10 个类
  - `quality` 不得依赖 `executor.testing`
  - `editing.precise` 不得依赖 `executor.*`
  - main source 不得调用 `System.getProperty("devflow...")`
- 量化完成门槛：
  - executor 根包源码数为 10
  - main source 中 `System.getProperty("devflow...") == 0`
  - main source 中 `executor.testing.RuntimeSnapshot*` 直接引用为 0
  - main source 中运行态 direct `DocumentLanguage.detect(...) == 0`
  - `devflow.agent.editing` 旧包源码数为 0
  - Spring façade/orchestrator 构造器中不存在子系统 `new` 链
  - `self-test + code review + 文档同步 + tracker 证据` 完成后，才能进入集成测试

## Assumptions

- `devflow.document.default-language` 默认值固定为 `ZH`，只通过配置覆盖，不依赖部署环境 locale。
- 旧运行目录里的 `code_review_review*.md` 不做运行时兼容；需要时离线清理或重跑。
- 本轮不纳入 `TestCaseBehaviorRepairSupport` 拆分、`AgentTurnLoop` tracing、`ContextCompactor` 语义摘要器；这三项继续单独立题，不和本轮结构收口混做。
- 任何旧包路径、旧文件名、旧 `System.getProperty` 入口、旧默认语言 fallback 只要残留，都视为未完成。
