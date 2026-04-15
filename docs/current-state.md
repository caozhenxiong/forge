# Forge 当前状态

## 目的

这份文档只维护**当前代码与业务现状**，不再混历史路线讨论。

如果要判断：

- 当前主流程是什么
- 当前编码内核做到什么程度
- 当前主线还剩什么
- 当前下一步该先做什么

先看这里。

## 项目定位

`Forge` 是一个面向研发流程的编程 agent 内核。它的目标不是单轮代码生成，而是把一条完整研发链做成可追踪、可审阅、可打回、可恢复的工作流：

- `ANALYSIS`
- `PRD`
- `DESIGN`
- `IMPLEMENTATION`
- `CODE_REVIEW`
- `TEST`

## 当前业务主流程

当前对外仍然是六阶段工作流：

1. `ANALYSIS`
2. `PRD`
3. `DESIGN`
4. `IMPLEMENTATION`
5. `CODE_REVIEW`
6. `TEST`

阶段名没有变化，但 `IMPLEMENTATION / REVIEW / TEST` 的内部约束已经明显强化：

- implementation 已切到 Claude 风格 `tool loop`
- review 已切到结构化 contract-first
- test 已切到结构化证据主路径

## 当前编码内核现状

截至 `2026-04-14`，已经完成的事实是：

- implementation 子任务执行主链已经切到 `assistant -> tool_use -> tool_result -> assistant`
- `Read / Edit / Write / Delete / Glob / Grep / Bash` 已进入主链
- Bash 写入子集已收紧到单段、可建模的简单命令；`sed/tee` 和泛化“只读命令 + 重定向落盘”已移出主链，文件内容变更统一回到 `Read/Edit/Write/Delete`
- tool loop 的终态成功判定已收紧：如果当前 subtask 声明的文件交付契约未被工具真实落盘满足，纯 assistant prose 不再被当作成功，而是直接以 `NO_MATERIAL_CHANGE` 在执行阶段失败
- implementation planning 的 subtask detail 已收敛成 Claude 风格最小协议，只允许 `path / action / reason`
- `transcript / readFileState / tool-result replacement / mutation / diagnostics` 已收进单一 session state
- implementation 的 live control flow 现在只认 `implementation_state.json`；`stageReady / continuationMode / continuation 文本 / overrideChanges` 都由上游 gate 一次产出，读取侧与 coordinator 不再二次推导
- accepted change-set 已进入 implementation coder 的确定性边界：新本地依赖只能引用当前 owned files 或项目里已存在资产
- HTML runtime ownership 仍由执行链与 verifier 校验，但已经不再由 planning detail 预判或填写
- runtime wiring 续跑 scope 已改成显式携带 `host html + companion runtime roots`，不再只给一个宿主 HTML 让 coder 自己猜 companion 路径
- implementation 侧 host entry runtime contract 已补成单一 resolver，当前只对已明确进入 host-entry 语义的 HTML 生效，并按 `continuation scope > accepted change-set > 当前 host HTML 已观察到的 wiring facts` 产出 canonical contract；guard / validator 不再各自重建，也不再通过目录扫描推断 sibling runtime roots
- coder prompt 已显式展示 `Current File Contracts`，并且当前 attempt 使用的 task package 会和 `executionState.effectiveChanges()` 对齐，不再出现“writable files 已缩窄，但 Current Subtask 仍展示旧 owned files”的双轨提示
- PRD 的低权重条目已从正文承诺区收束到 `Source Metadata`；`推断 / 建议 / 设计选择 / 待确认问题` 不再进入 `PRODUCT_CONTRACT` 的 capability / acceptance 投影
- snapshot / restore 只恢复确定性会话状态，不再跨 attempt 恢复 transcript
- implementation 阶段只保留一份 `contract gate` 真相源，progress / report / state snapshot / resume 共用同一结果
- completed-plan 的 `PATCH` 续跑会直接回到 owning subtask，以 patch-only 方式继续，不再追加 synthetic continuation subtask
- `PATCH_EXISTING_IMPLEMENTATION` 的 stage artifact / parser / continuation note / resume 主链已经统一成结构化 `overrideChanges` 协议，不再允许空 scope 静默续跑
- `implementation_stage_status.md` / `worker_results.md` / `implementation_diagnostics.md` 已降为派生展示物，不再参与 continuation、review intake 或 stage progress 判定
- unsupported shell write 与其他 Bash 工具失败已开始复用 `ToolFailureCode` 进入 event log / implementation diagnostics / snapshot / review summary，不再只剩一条非结构化日志
- 子任务级 patch review 若未显式给出 scope，会先归一到当前 subtask 的 effective change-set；如果仍没有安全 scope，会直接阻断到人工，不再伪造 auto-patch
- implementation verification 遇到 `TEST_PLAN_DEFECT`、`RUNTIME_PROBE_INVALID` 这类非实现问题时，不会再被静默放过；当前会直接阻断到人工，避免带着无效测试证据继续推进实现
- implementation stage roll-up 已能保留 `ROUTE_TO_REPAIR_TARGET` 的结构化 repair scope，不再把它降级成泛化“未完成子任务”
- 编码主链只认 `chat/tool` provider，不再走 `generate -> chat` 桥接
- 仓库级单测 `mvn -q test` 已通过

这说明：

- “旧逐文件生成主入口”这条主线已经退出 implementation 核心
- 当前问题不再是“要不要切 tool loop”
- `Claude` 风格编码内核四段基础设施已经在代码层完成收口
- 下一步不再是继续补基础骨架，而是做黄金路径集成验证

## 当前主线

当前主线已经切换为：

1. `黄金路径集成验证`

架构整改 5 个 phase 已完成，当前目标重新回到黄金路径集成验证，不再继续扩散架构级拆分。

当前架构整改的 5 条主线是：

- `WorkflowEngine` 真契约化
- `context ↔ orchestrator` 通过 `domain` 解耦
- `executor` 按职责拆边界
- tool loop 并发执行器显式化
- `ContextCompactor` 四层上下文接入 generate 主链

补充现状：

- `Phase 0` 已完成：`ImplementationExecutor` 已从构造器工厂收回到 wiring 层，`SubtaskExecutionContext` 已补齐，tool loop 已显式注入执行器，continuation prompt 已回收到 prompt builder，`WorkflowEngine` 已升级为真 facade，`SupervisorAgent` 异常已可观测
- `Phase 1` 已完成：`StageType / RunRecord / RunStatus / RunConfig / GatePolicy / StageExecution / StageStatus` 已迁入 `devflow.agent.domain`，旧 `orchestrator` 模型文件已删除，`context -> orchestrator` 依赖已清空
- `Phase 2` 已完成：`executor.llm / executor.context / executor.generation / executor.shell / executor.tools / executor.subtask / executor.implementation / executor.runtime / executor.gate / executor.testing / executor.editing / executor.patch` 已全部落位
- `executor.subtask` 已收口：subtask execution / verification / recovery / review prompt / retry feedback / self-check review resolver 已整体下沉
- `executor.implementation.toolloop` 已收口：tool loop executor、prompt、session state、mutation contract、diagnostics、read-file ledger、result replacement state 已整体下沉
- `executor.runtime` 已收口：`RuntimeOwnershipMode / RuntimeScriptGraphInspector / HtmlRuntimeOwnershipContract / HtmlRuntimeContractResolver / HtmlEntryRuntimeOwnershipInspector / WebRuntimeWiringCheck / UiRuntimeContractResolver` 已归位到运行时支撑包
- `executor.gate` 已收口：`ArchitectIntegrationCheck / ImplementationCompleteness* / ImplementationStageGate / ImplementationGateEngine / TestEvidenceGate / GateReport` 已归位到统一 gate 包
- `Phase 3` 已完成：generate 主链统一切到 `LlmGenerateRequest`，`ContextCompactor` 与 `ContextBudgetPlanner` 已改为结构化四层上下文预算
- `Phase 4` 已完成：`StageProgressCoordinator` 已收回纯 orchestration，`ImplementationExecutor` 保持单构造器注入，subtask 执行主链改为 `SubtaskExecutionContext`
- 下一步不再是补架构骨架，而是恢复黄金路径集成验证

## 当前关键约束

当前仓库的硬约束是：

- 不允许兼容层、适配层、shim、fallback、临时 cap、heuristic patch
- 新骨架落地时，必须同步删除旧骨架
- “能运行”不算完成；只有同类问题整体收口才算完成
- 任何“为了降低风险而暂时保留”的逻辑，都视为未完成
- 任何“后续再清理”的说法，都视为本轮失败
- 每轮代码改动完成后，必须执行 `self-test + code review`

## 当前剩余问题面

当前仍需观察的，不再是基础设施骨架本身，而是黄金路径集成上的真实稳定性：

### 1. 外提 runtime 与宿主 HTML 接线在真实产物上的一致性

- 需要继续验证外提 runtime 产物不会再出现“文件存在但未接线”的孤儿状态
- 需要验证 wiring 约束已经真正后移到 verifier，而不是又从 planning 主链回流

### 2. 黄金路径中的 patch continuation 是否稳定回到原 owning subtask

- 需要继续验证真实失败 case 不会重新规划成新子任务
- 需要验证 retry 不会把同一问题重新放大成整阶段重做
- 需要继续验证 runtime wiring 失败后，coder 能基于显式 file contracts 稳定修复宿主接线，而不是在 inline / external companion 之间漂移
- 需要继续验证 stage-level contract gate 不会再产出“空 `overrideChanges` 的 PATCH 续跑”这一类历史 fatal
- 需要继续验证 implementation verification 产生的 repair-target / human-block 信号，会在真实集成里完整传到 stage artifact 与续跑入口

### 3. review / test 阶段是否持续消费结构化状态

- 需要继续验证后续阶段不会因为 artifact 结构变化又回退到 prose 猜测
- 需要继续验证集成日志、状态产物与真实执行路径一致

## 后续待办

以下不是当前黄金路径主线 blocker，但已经明确属于后续协议升级待办：

### 1. shared-file capability boundary 升级到 path 级 ownership

- 当前 shared-file boundary gate 仍按 `future subtask owner` 粒度收口，不是按 `path -> capability` 的精确协议裁决
- 这意味着：如果后续某个 subtask 同时拥有共享文件能力和非共享文件能力，当前 gate 会保守地要求当前 subtask 把该 future owner 的整组 `ownedCapabilities` 都列进 `deferredCapabilities`
- 这是当前刻意保守的 deterministic gate，不是 path 级真相；在缺少结构化 `path -> capability` ownership 之前，不能靠 heuristic 假装精确到 path 级
- 真正要收这条，必须作为单独协议升级任务处理，并联动修改：
  - planning / outline / detail 的结构化输出
  - capability partition gate 输入与 analyzer
  - prompt / parser / round-trip regression
- 触发条件：
  - 黄金路径集成或后续真实 case 证明当前保守 gate 误杀合法 plan
  - 或明确决定推进 `path -> capability` ownership 协议升级

## 当前判断

当前结论已经变成：

- implementation 编码内核的基础骨架已经完成代码层收口
- `domain` 共享模型层已经落地，`context ↔ orchestrator` 的旧模型耦合已切开
- 当前主问题已经不再是架构收口，而是黄金路径真实集成稳定性
- `Phase 0` 到 `Phase 4` 的 `self-test + code review` 已完成
- 仓库级单测 `mvn -q clean test` 已通过
- 下一步直接回到黄金路径集成测试，而不是继续做骨架整改

## 文档入口

- 当前执行清单：`docs/active-work-items.md`
- 架构整改执行清单：`docs/architecture-refactor-work-items.md`
- 工程约定：`docs/engineering-agreements.md`
- 高优先级硬约束：`AGENTS.md`
