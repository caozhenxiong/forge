# Claude Review 收口方案 V3

## Summary

- 本轮按 `AGENTS.md` 与 `docs/engineering-agreements.md` 的强约束执行，只收 `REVIEW_V3.md` 中的 `P1 + P2`，不做兼容层、不保留双轨、不用“先能跑后清理”的过渡实现。
- 方案产物固定为 2 份：
  - 设计方案：`REVIEW_V3_REMEDIATION_PLAN.md`
  - 进度追踪：`docs/review-v3-remediation-progress.md`
- 本轮目标不是继续零散修补，而是把 orchestration / supervisor / context / document pipeline 这几条已经暴露出结构性问题的主线一次性收口。
- `REVIEW_V3.md` 只作为外部 review 输入，不直接承担执行 tracker 角色。

## Scope

本轮纳入：

- `REVIEW_V3.md` 中全部 `P1`
- `REVIEW_V3.md` 中全部 `P2`
- `P3` 中仅纳入 3 条直接保护本轮改造成果的守门项：
  - `SupervisorAgent` catch 变量命名修正
  - `FlowController` 分隔符字面量提取
  - ArchUnit 增加 `System.getProperty(String)` 守门

本轮不纳入：

- `TestCaseBehaviorRepairSupport` 大拆分
- `ContextCompactor` 语义摘要器替换
- Micrometer / tracing / 可观测性增强

## Review Item Mapping

### P1

- `requireStage()` 三处重复
  - 收口动作：统一提取到 `StageStatusSupport`
  - 删除位置：
    - `StageRevisionSupport`
    - `StageTransitionSupport`
    - `StageProgressCoordinator`
- `StageProgressCoordinator` payload 转换未下沉
  - 收口动作：引入 `ImplementationContinuationSupport`
  - 删除位置：
    - `toFileChange()`
    - `requiredContinuationField()`
    - `implementationPatchTarget()`
    - `implementationReasonCode()`
- `continueStage()` 10 参数
  - 收口动作：引入 `StageContinuationContext`
  - 变更位置：
    - `StageTransitionSupport.continueStage(...)`
    - `FlowDecisionExecutor.continueStage(...)`

### P2

- `StageRevisionSupport` 双构造器 + 隐藏 `new`
  - 收口动作：只保留一个显式注入构造器
- `SupervisorDecisionSanitizer` 内部创建 `SupervisorPayloadNormalizer`
  - 收口动作：转为 Spring 显式注入
- `FlowController.mapReason()` if 链
  - 收口动作：改为 `switch expression`
- `ValidationStrategyPlanner` 静默吞异常
  - 收口动作：保留 deterministic fallback，补齐日志，不改决策语义
- `ContextProjector` 职责过多
  - 收口动作：拆为 reader / resolver / summary assembler / assembler 四段
- `AnalysisDocumentComposer` / `PrdDocumentComposer` / `DesignDocumentComposer` 高度重复
  - 收口动作：引入统一模板骨架，三阶段退化为差异策略
- `TestExecutor` 14 构造参数
  - 收口动作：按 planning / run / evidence 三组聚合依赖

### P3

- `SupervisorAgent` catch 变量命名误导
  - 收口动作：`ignored -> ex`
- `FlowController` `" | "` 字面量散落
  - 收口动作：提取常量
- ArchUnit 缺少 `System.getProperty` 守门
  - 收口动作：新增架构规则，防止配置化回退

## Final State

完成态必须同时满足：

- `StageProgressCoordinator` 只保留 orchestration 主流程，不再自行解析 implementation continuation payload，也不再本地拼 `ReviewResult`
- `StageContinuationContext` 成为 implementation continuation 的唯一上下文对象
- `ImplementationContinuationSupport` 成为 continuation payload 到 domain object 的唯一转换入口
- `StageStatusSupport` 成为 `requireStage()` 的唯一共享入口
- `StageRevisionSupport`、`SupervisorDecisionSanitizer`、`ContextProjector`、`DocumentStageComposer` 不再保留本轮收口范围内的隐藏 `new` 或双构造器
- 文档阶段三条 composer 主链合并为一个模板骨架 + 三个阶段策略，不再复制 intake / generate / post-process / upsert 流程
- `TestExecutor` 构造器不再平铺 14 个依赖，而是按职责组装
- `main source` 不得新增 `System.getProperty(String)` 直接读取
- tracker 只在代码、自测、review 完成后更新为完成

## Key Changes

### 1. Orchestration 收口

- 新增 `StageContinuationContext`，固定字段为：
  - `summary`
  - `changeRequest`
  - `evidence`
  - `actionItems`
  - `overrideChanges`
  - `implementationPatchTarget`
  - `reasonCode`
- 新增 `ImplementationContinuationSupport`，职责固定为：
  - 校验 `ImplementationStageStatusPayload` 的 continuation 必填字段
  - 把 `FileChangePayload` 转成 `FileChange`
  - 输出 `StageContinuationContext`
  - 用同一份 context 生成 block-human 路径用的 `ReviewResult`
- `StageProgressCoordinator` 只保留：
  - 读取 implementation state
  - 判断 `stageReady` / `continuationMode`
  - 调用 `ImplementationContinuationSupport`
  - 生成 `TransitionDecision`
  - 委托 `FlowDecisionExecutor`
- 从 `StageProgressCoordinator` 删除并禁止回流：
  - `toFileChange()`
  - `requiredContinuationField()`
  - `implementationPatchTarget()`
  - `implementationReasonCode()`
  - 本地 `requireStage()`
- `FlowDecisionExecutor.continueStage(...)` 和 `StageTransitionSupport.continueStage(...)` 改为接收 `StageContinuationContext`
- 新增 `StageContinuationNoteBuilder`，专门负责 continuation note 的 `ExecutionDirectivePayload` 渲染

### 2. Stage Revision / Supervisor / Flow 收口

- `StageRevisionSupport` 只保留一个显式注入构造器
- `StageRevisionSupport` 不再内部 `new`：
  - `SupervisorGuidanceRenderer`
  - `StageRevisionRepairSupport`
  - `StageRevisionNoteBuilder`
- 新增 `OrchestratorConfiguration` 显式装配：
  - `StageStatusSupport`
  - `StageRevisionSupport`
  - `StageContinuationNoteBuilder`
  - `ImplementationContinuationSupport`
  - `EventLogStore`
- `SupervisorPayloadNormalizer` 改为显式 bean
- `SupervisorDecisionSanitizer` 改为构造注入 `SupervisorPayloadNormalizer`
- 新增 `SupervisorConfiguration`，统一装配 supervisor 相关协作者
- `SupervisorAgent` 中 `catch (Exception ignored)` 改为 `catch (Exception ex)`
- `FlowController.mapReason()` 改为 `switch expression`
- `appendToolSummary()` 的 `" | "` 提取为常量

### 3. Validation / TestExecutor 收口

- `ValidationStrategyPlanner` 保持 deterministic fallback 语义不变
- 为 fallback 触发增加日志，不再静默吞异常
- 引入 3 组测试依赖对象，放在 `executor.testing` 包内：
  - `TestPlanningComponents`
  - `TestRunComponents`
  - `TestEvidenceComponents`
- `TestExecutor` 构造器改为：
  - `TestPlanningComponents`
  - `TestRunComponents`
  - `TestEvidenceComponents`
  - `ContractExtractor`
  - `LanguagePolicy`
- `TestExecutionConfiguration` 统一装配这 3 组依赖，测试 support / harness 同步改签

### 4. ContextProjector 职责拆分

- `ContextProjector.project()` 拆成 4 个确定性步骤：
  - `ContextProjectionArtifactReader`
  - `ContextProjectionContractResolver`
  - `ContextProjectionSummaryAssembler`
  - `ContextProjectionAssembler`
- 新增 3 个中间 record：
  - `ContextProjectionArtifacts`
  - `ContextProjectionContractBundle`
  - `ContextProjectionSummaries`
- `ContextProjector` 只负责串联这 4 步，不再内部 `new ContextProjectionArtifactReader()`，也不保留 convenience ctor

### 5. Document Composer 模板化收口

- 删除三套重复实现的文档阶段 composer 主链，不保留“旧类继续跑、新模板旁挂”的双轨
- 引入模板骨架 `DocumentCompositionTemplate`，固定流程为：
  - resolve language / upstream artifacts
  - build authoritative metadata
  - build `DocumentDraftContext`
  - build prompt
  - generate
  - stabilize source/contract metadata
  - merge draft
  - strip machine blocks
  - sanitize constraint escalation
  - upsert machine blocks
- 用 3 个阶段策略继承模板骨架：
  - `AnalysisDocumentComposition`
  - `PrdDocumentComposition`
  - `DesignDocumentComposition`
- `DocumentStageComposer` 退化为纯路由器，只持有 3 个策略，不再内部 `new intake/postProcessor/promptAssembler/generationSupport/三套 composer`
- 新增 `ArtifactCompositionConfiguration`，显式装配共享支撑
- 当前 `AnalysisDocumentComposer` / `PrdDocumentComposer` / `DesignDocumentComposer` 删除，不保留兼容入口

### 6. Docs / Tracker 规则

- 新建本轮专属文件：
  - `REVIEW_V3_REMEDIATION_PLAN.md`
  - `docs/review-v3-remediation-progress.md`
- tracker 规则固定：
  - 先改代码，再自测，再 code review，最后更新 tracker
  - 只有“旧 helper 已删 + 新 support 已接线 + 测试覆盖通过”同时满足后，条目才能打勾
- `REVIEW_V3_REMEDIATION_PLAN.md` 只描述设计，不记录执行进度；执行进度只写入 tracker

## Phase Plan

### Phase 1. orchestration continuation 收口

- 提取 `StageContinuationContext`
- 提取 `ImplementationContinuationSupport`
- 提取 `StageContinuationNoteBuilder`
- 删掉 `StageProgressCoordinator` 内 continuation payload helper
- 把 `requireStage()` 统一收口到 `StageStatusSupport`

### Phase 2. revision / supervisor / flow 依赖注入收口

- `StageRevisionSupport` 去双构造器和隐藏 `new`
- `SupervisorDecisionSanitizer` 注入 `SupervisorPayloadNormalizer`
- `SupervisorAgent` catch 变量修正
- `FlowController.mapReason()` 改 `switch expression`
- `FlowController` 分隔符字面量提取常量

### Phase 3. validation / testing 结构收口

- `ValidationStrategyPlanner` 增加 fallback 日志
- `TestExecutor` 依赖按 planning / run / evidence 分组
- `TestExecutionConfiguration` 同步重接线
- 定向测试与 harness 同步改签

### Phase 4. context / document pipeline 收口

- `ContextProjector` 按四段拆分
- 引入中间 record
- 三套 document composer 收敛为模板骨架 + 三个策略
- `DocumentStageComposer` 退化为纯路由器

### Phase 5. 守门与文档对齐

- 新增 `System.getProperty(String)` ArchUnit 规则
- 完成 code review
- 更新 `docs/review-v3-remediation-progress.md`

## Test Plan

- 编译验证：
  - `mvn -q -DskipTests compile`
  - `mvn -q -DskipTests test-compile`
- orchestration 定向测试：
  - `StageProgressCoordinatorTests`
  - `StageTransitionSupportTests`
  - `StageStatusSupportTests`
  - `FlowControllerTests`
  - `DefaultWorkflowEngineTests`
- supervisor / validation / testing：
  - `SupervisorAgentTests`
  - `SupervisorFallbackPolicyTests`
  - `ValidationStrategyPlannerTests`
  - `TestExecutorTests`
- context / artifact：
  - `ContextProjectorTests`
  - `StageArtifactComposerTests`
  - `DocumentCompositionTemplate` 等效三阶段策略测试
- 架构守门：
  - 现有 3 条 ArchUnit 继续保留
  - 新增 `main code must not call System.getProperty(String)` 规则

## Completion Gate

只有以下条件全部满足，才允许宣称本轮完成：

- [ ] `StageProgressCoordinator` 中不再存在 continuation payload 解析 helper
- [ ] `StageTransitionSupport.continueStage()` 不再透传长参数链，改由 `StageContinuationContext` 驱动
- [ ] `requireStage()` 只剩 `StageStatusSupport` 一个实现
- [ ] `StageRevisionSupport`、`SupervisorDecisionSanitizer`、`ContextProjector`、`DocumentStageComposer` 不再保留本轮收口范围内的隐藏 `new` / 双构造器
- [ ] `TestExecutor` 构造器已完成职责分组
- [ ] `ValidationStrategyPlanner` fallback 异常可观测
- [ ] 三阶段文档 composer 已收敛到模板骨架 + 策略
- [ ] `System.getProperty(String)` ArchUnit 守门已接入
- [ ] `self-test + code review + 文档同步 + tracker 证据` 全部完成

## Assumptions

- 本轮 scope 固定为 `P1 + 全部 P2 + 3 条直接守门型 P3`，不扩展到 `TestCaseBehaviorRepairSupport` 和 `ContextCompactor`
- 本轮不保留旧 composer / 旧构造器 / 旧 helper 作为兼容路径；新骨架接入时同步删除旧骨架
- tracker 必须新建 `V3` 文件，不覆盖 `V2` 执行记录
- 本轮是结构收口，不是集成验证；方案评审通过后才进入代码修改与测试
