# Claude Review 收口方案 V2

## Review digest

### 1. 问题面

- `executor` 根包仍残留大量 implementation 规划、状态、渲染类，边界没有完全收口。
- `devflow.*` 运行时策略仍大量通过 `System.getProperty()` 读取，绕开 Spring 配置体系。
- `TestExecutor`、`StageReviewer` 以及部分 review/quality/subtask 类仍存在构造器工厂模式，影响可测试性和装配清晰度。
- `SupervisorAction` / `FlowAction` 双枚举、`StageArtifactNames` / `StageFlowPolicy` if 链、`StageProgressCoordinator` payload 拼装，说明流程契约还没有完全收成单一真相源。

### 2. 最终态

- `executor` 根包只保留 facade、wiring、configuration 和跨阶段共享 DTO；实现域类全部进入明确子包。
- 所有 `devflow.*` 配置都进入 Spring `@ConfigurationProperties` 体系，主代码不再直接读 JVM system properties。
- Spring 管理类只做协调，不在构造器里 `new` 子系统依赖。
- 流程面只有一套动作枚举、一套阶段文件命名规则、一套阶段流转规则。
- orchestration 只编排，不承担 implementation 特有 payload 转换和 review result 拼装。

### 3. 联动修改

- 包边界收口要和错放类归位、`RuntimeSnapshot*` 上移、`editing.precise` 重命名一起完成。
- 配置体系收口要和 `@ConfigurationPropertiesScan`、typed properties、消费方注入改造一起完成。
- 可测试性收口要和 `TestExecutor` / `StageReviewer` / 同类隐藏 `new` 一起完成。
- 流程契约收口要和 `WorkflowAction`、`StageArtifactNames`、`StageFlowPolicy`、`DocumentLanguage.detect()` 一起完成。
- `StageProgressCoordinator` 职责下沉要和 implementation continuation/block helper 一起完成。

### 4. 验证门槛

- executor 根包源码数收敛到 10。
- main source 中 `System.getProperty("devflow...") == 0`。
- main source 中 `executor.testing.RuntimeSnapshot*` 直接引用为 0。
- Spring façade/orchestrator 构造器不再存在子系统 `new` 链。
- `self-test + code review + 文档同步` 完成前，不进入集成测试。

## Summary

- 这轮按 `AGENTS.md` 和 `docs/engineering-agreements.md` 做“同类问题一次收口”，不做逐点修补。
- 必须一起完成的主线有 5 条：`executor` 边界收敛、Spring 配置体系收敛、构造器工厂去除、流程动作/阶段映射单一化、`StageProgressCoordinator` 职责下沉。
- 本轮纳入的低风险顺手项只有与同一结构面直接相关的两条：`StageArtifactNames` 命名修正、`DocumentLanguage.detect()` 空输入默认值修正。
- 本轮不混入 `TestCaseBehaviorRepairSupport` 拆策略、`AgentTurnLoop` tracing、`ContextCompactor` 语义摘要器；那是另外三类问题，混做会再次形成半成品。

## Final state

- `devflow.agent.executor` 根包只保留 10 个类型：`ImplementationExecutor`、`ImplementationExecutionBundle`、`ImplementationProgressSink`、`ImplementationExecutorConfiguration`、`ImplementationExecutorWiring`、`FileChange`、`SelfCheckResult`、`ChangeAction`、`DeliveryMode`、`DeliveryPolicyEnvelope`。
- 其余 `Implementation*` 和相关 helper 全部进入 `executor.implementation` 体系，并按 `planning`、`state`、`render`、`toolloop` 分层；`executor` 根包不再残留规划/状态/渲染类。
- `devflow.agent.editing` 重命名为 `devflow.agent.editing.precise`，作为唯一“精确编辑内核”包；`executor.editing` 仅保留编排/策略层，单向依赖 `editing.precise`。
- `RuntimeSnapshot`、`RuntimeSnapshotCaptureStatus`、`RuntimeSnapshotFailureCode` 上移到 `executor.runtime`；`quality` 不再依赖 `executor.testing`。
- `SupervisorAction` 和 `FlowAction` 合并为 `domain.WorkflowAction`；流程面只保留这一套动作枚举。
- 所有 `devflow.*` 运行时策略改为 Spring `@ConfigurationProperties` 绑定；主代码中不再直接读取 `System.getProperty("devflow...")`。
- `TestExecutor`、`StageReviewer` 及 review/quality/subtask 中被点名的 Spring 管理类都变成薄协调器，构造器里不再 `new` 子系统依赖。
- `StageProgressCoordinator` 只负责 orchestration，不再承担 payload 转换、implementation continuation/block 映射、`ReviewResult` 拼装。
- `StageArtifactNames` 改为穷举 `switch expression`；CODE_REVIEW 的 review 产物改名为 `code_review_feedback.md` / `code_review_feedback_history.md`。
- `DocumentLanguage.detect()` 在无可检测人类语言时走系统 locale 对应默认语言，而不是写死 `ZH`。

## Removal plan

- 删除 `FlowAction`、`SupervisorAction`、`FlowController.mapAction()`。
- 删除主代码中所有 `System.getProperty("devflow...")` 读取和对应静态 helper/`defaults()` 入口。
- 删除 `TestExecutor`、`StageReviewer`、`DocumentStructureGuard`、`DocumentReviewNormalizer`、`ImplementationReviewNormalizer`、`HtmlStructureRuntimeSignalResolver`、`SubtaskPerformanceGuidanceResolver` 等类里的隐藏构造器工厂链。
- 删除 executor 根包中的剩余 `Implementation*` 留存，不保留旧包别名、桥接类、fallback import。
- 删除源代码/测试/文档中的 `code_review_review*.md` 命名，不做运行时兼容读取。
- 从 `StageProgressCoordinator` 删除 payload 映射和 implementation-stage 专属拼装逻辑，抽到专门 helper 后原地移除旧方法。

## Joint-change scope

- 边界收口必须一起改：
  - executor 根包剩余类迁移
  - `tools`/`editing` 错放类归位
  - `editing.precise` 重命名
  - `RuntimeSnapshot*` 上移到 `executor.runtime`
- 配置收口必须一起改：
  - `DevflowAgentApplication` 改为 `@ConfigurationPropertiesScan`
  - 新增分域 typed properties
  - 加 `spring-boot-configuration-processor`
  - 所有消费方改为注入配置对象，不再静态读 JVM 属性
- 可测试性收口必须一起改：
  - `TestExecutor`
  - `StageReviewer`
  - review/quality/subtask 中同类隐藏 `new`
  - 对应单测全部改为注入 double 或 wiring bean
- 流程/协议收口必须一起改：
  - `WorkflowAction`
  - `StageArtifactNames` / `StageFlowPolicy` 的 `switch expression`
  - `code_review_feedback*.md`
  - `DocumentLanguage.detect()`
- orchestration 收口必须一起改：
  - `StageProgressCoordinator`
  - implementation continuation / block / payload mapper
  - 对应 stage progress 测试

## Closure decision

- 这轮可以完整收口，但只能按固定顺序执行，不能穿插别的问题族。
- 固定顺序：
  1. executor/package ownership 收口
  2. Spring config 收口
  3. wiring/testability 收口
  4. workflow/protocol 收口
  5. `StageProgressCoordinator` 职责下沉
  6. 架构守门测试 + 文档同步
- 不允许中途用兼容层、双轨路径、旧包 fallback、旧文件名 fallback 来“先跑起来”。

## Implementation

- `executor` 收口：
  - 将 executor 根包剩余 `Implementation*` 类按职责迁入 `executor.implementation.planning`、`executor.implementation.state`、`executor.implementation.render`、`executor.implementation.toolloop`。
  - `StructuredPatchSupport` / `StructuredPatchHunk` 移到 `executor.patch`。
  - `RuntimeWorkingSetPolicy` / `RuntimeWorkingSetResolver` 移到 `executor.runtime`。
  - `ScopedTaskPackageSupport` / `TaskPackageMarkdownRenderer` 移到 `executor.implementation.render`。
- Spring 配置：
  - 保留现有 `devflow.*` key，不改外部配置面。
  - 新增分域 properties：implementation、patch、stage、subtask-review、test-planning、playwright、prompt-token-estimator、tool-permission、ollama client。
  - 默认值保留在 typed properties 归一化逻辑中；`application.yml` 只放仓库级默认覆盖项，不复制整套字面量。
- 去工厂化：
  - `TestExecutor` 改为仅接收已装配 collaborator。
  - `StageReviewer` 改为注入 parser / normalizer / turn executor / contract gate 依赖。
  - 其余被点名类全部改成 bean 注入或 package-private collaborator，不再在 Spring façade/orchestrator 里构造子系统。
- 流程/协议：
  - 引入 `domain.WorkflowAction`，同步更新 `SupervisorDecision`、`FlowDecision`、fallback support、sanitizer、controller、executor、测试。
  - `StageArtifactNames` 与 `StageFlowPolicy` 改成穷举 `switch expression`。
  - `DocumentLanguage.detect()` 用系统 locale 兜底。
- orchestration：
  - 提取 implementation-stage continuation/block/payload 转换 helper，让 `StageProgressCoordinator` 只保留“读产物 → review → supervisor → flow → apply”主流程。
- 防回退：
  - 用 ArchUnit，不自造 grep 守门脚本。
  - 新增规则：
    - executor 根包白名单
    - `quality` 不得依赖 `executor.testing`
    - `editing.precise` 不得依赖 `executor.*`
    - main source 不得调用 `System.getProperty("devflow...")`

## Test plan

- `mvn -q -DskipTests compile`
- `mvn -q -DskipTests test-compile`
- 定向单测：
  - `StageReviewerTests`
  - `TestExecutorTests`
  - `DefaultWorkflowEngineTests`
  - `FlowControllerTests`
  - `StageProgressCoordinatorTests`
  - 新增 properties 绑定测试
  - 新增 ArchUnit 边界测试
- 完成门槛：
  - executor 根包源码数为 10
  - main source 中 `System.getProperty("devflow...") == 0`
  - main source 中 `executor.testing.RuntimeSnapshot*` 直接引用为 0
  - Spring façade/orchestrator 构造器中不再存在子系统 `new` 链
  - `self-test + code review + 文档更新` 完成后，才能进入集成测试

## Assumptions

- 旧运行目录里的 `code_review_review*.md` 不做运行时兼容；需要的话离线清理或重跑生成。
- 外部配置 key 保持不变，只替换读取机制。
- 本轮明确不包含 `TestCaseBehaviorRepairSupport` 拆分、`AgentTurnLoop` tracing、`ContextCompactor` 语义摘要器；它们需要单独立项，不与本轮结构收口混做。
