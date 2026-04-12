# 当前执行清单

## 用途

这份文档只记录**当前正在做**、并且必须按顺序完成的事项。

规则：

- 每次开始一轮新重构前，先更新这份文档
- 每做完一条，就直接在这里打勾
- 如果某条被放弃、拆分或改方向，也先更新这里，再改代码
- 没有出现在这里的事项，不视为当前执行中的主线

## 当前主线

当前只做一条：

1. `AGENTS 合规收口与黄金路径稳定化`

---

## A. 设计与实施准备

目标：先把质量规则架构写成正式设计和可追踪清单，再进入代码落地。

- [x] 新增正式设计文档 [quality-rules-architecture.md](/home/linus/workspace/forge/docs/quality-rules-architecture.md)
- [x] 在设计文档中定义 `QualityRules`
  - `StructureRules`
  - `VerificationRules`
  - `ExperienceRules`
- [x] 在设计文档中定义 `FeatureProfile`
- [x] 在设计文档中定义 `CapabilitySurface`
- [x] 在设计文档中定义 `StructureRiskReport`
- [x] 在设计文档中定义 `QualityPlan`
- [x] 在设计文档中定义 `CapabilityMatrix`
- [x] 在设计文档中设计 `QualityPolicyResolver`
- [x] 在设计文档中设计 `CapabilityMatrixBuilder`
- [x] 在设计文档中设计 `StructureGate / CoverageGate / ExperienceGate`
- [x] 在设计文档中设计 `CoverageLedger / QualityLedger`
- [x] 在设计文档中明确 `Plan / Coder / Reviewer / Tester / Gate` 的接入点
- [x] 在设计文档中明确与当前 `patch-first / tool-result-first / budget-first / repair-before-regenerate` 的关系
- [x] 将方案同步到 [redesign-roadmap.md](/home/linus/workspace/forge/docs/redesign-roadmap.md)
- [x] 将方案同步到 [current-state.md](/home/linus/workspace/forge/docs/current-state.md)

## B. 当前问题

目标：先明确这轮为什么还没对齐，再进入代码收口。

- [x] 确认 `FeatureProfiler` 仍包含领域语义硬编码
- [x] 确认 `CapabilitySurfaceBuilder` 仍直接从硬编码 feature 推导能力面
- [x] 确认 `StructureGate` 只接在 `StageReviewer`，没有前移到 implementation 主链
- [x] 确认 `repair-before-regenerate` 的 validate 仍缺宿主产物结构校验
- [x] 确认最近黄金路径 `v103` 已复现这些问题

## C. 代码收口

目标：把质量规则主线从“review/test 附加层”收成真正前置约束，并删掉通用层里的剩余硬编码与保守 testcase 预算。

- [x] 将 `FeatureProfiler` 收缩为纯通用事实提取器，删除 `pause/resume/reset/score/preview` 等领域关键词
- [x] 重写 `CapabilitySurfaceBuilder`，改为由 `QualityIntent + FeatureFacts + Contract` 推导能力面
- [x] 引入 `QualityIntent / InteractionIntent / StructureIntent / CoverageIntent`
- [x] 将 `QualityPlan` 接入 `ImplementationPlanner`
- [x] 将 `StructureGate` 前移到 `SubtaskVerificationSupport`
- [x] 扩展 `ImplementationCompletenessGate`，支持结构质量阻断
- [x] 将质量清单显式前移到 implementation planning / subtask verification 提示链，避免只在最终 test/review 才看到结构、覆盖和体验约束
- [x] 将 required capability surface 显式升级为 implementation plan gate，避免缺失质量覆盖只在 `TEST` 阶段补救
- [x] 新增宿主产物结构校验，补到 `repair-before-regenerate` 的 validate 链
- [x] 将 `TEST` 阶段缺失能力覆盖信号前移到 `IMPLEMENTATION` 主链，确保 `timed-state-progression / primary-interaction / primary-visual-surface` 这类 required surface 在回流后会直接进入 implementation 计划与修复说明
- [x] 将 `QualityPlan` 的 required capability surface 显式接入 implementation 计划约束，确保 planner 需要为 required surface 分配子任务责任，而不是只在 TEST 阶段补救
- [x] 将 coverage contract 收成权威主链：PRD 固定章节本地投影 `PRODUCT_CONTRACT`、下游 block-only 抽取、planning/test/gate 统一消费 `AuthoritativeCoverageCatalog`
- [x] 删除 implementation / repair / html patch 主链调用点中的固定 `num_predict` cap，统一改为动态 output ratio 驱动
- [x] 修正 `Implementation` 产物落盘与执行链中的 `editScope` 一致性，确保质量计划外提脚本后不再把后续子任务渲染成 `INLINE_SCRIPT_PATCH`
- [x] 修正外提脚本后的宿主 HTML 正规化，确保 `index.html` 在 companion 脚本接管后不再保留旧的主逻辑 inline script
- [x] 将质量计划中的宿主 HTML 接线要求前移到 implementation plan 归一化，确保后续运行时脚本子任务会显式补入 `HOST_HTML_PATCH`
- [x] 补强 `ArchitectIntegrationCheck` 的 runtime wiring 检查，确保 HTML 入口未接入同目录/子目录运行脚本时不能再通过 implementation stage gate
- [x] 收紧严格单 symbol `precise-code` 单元的协议与修复链，避免 `index.app.js` 再出现同名函数/类包装重复嵌套
- [x] 将严格单 symbol `precise-code` 的机械错误修复前移到 validator 之前，先尝试 body-only repair，再决定是否 regenerate
- [x] 收紧 `style.css` 的 `precise-code` 单元切分，避免多 selector 单元反复触发 `EDIT_UNIT_SCOPE_VIOLATION`
- [x] 将 `repair-before-regenerate` 的成功判定升级为“语法 + scope + 宿主/代码文件结构”三层全部通过
- [x] 删除 `TestCaseBehaviorRepairSupport / TestCaseCapabilityInferencer` 中基于 selector token 的 `start/pause/score` 语义硬编码，改成结构化 step semantic / case capability 驱动
- [x] 将 testcase prompt / payload / sanitizer 扩展为显式 `step semantic`，避免测试修复与能力推断继续读 selector 文本猜语义
- [x] 将 `StructureGateEvaluator` 改成只消费 `StructureRiskReport / StructurePolicy`，去掉 capability 推断式阻断
- [x] 将 `QualityRulesLoader` 改成“资源默认规则 + 项目级 repo rule 覆盖”的严格加载，不再把默认质量规则长期写死在 Java 常量里，也不再接受运行时临时覆写
- [x] 将 `QualityPlanFactory` 改成按项目路径加载 repo quality rules，并同步更新 planner / reviewer / tester 调用链
- [x] 将 `TestCasePromptAssembler` 改成 capability matrix / feature profile 驱动，去掉“网页/小游戏至少...”这类产品特判式 wording
- [x] 将 `TestCasePlanner` 的输出预算改成动态 output ratio 驱动，去掉 testcase 规划固定 `1200` 上限
- [x] 将 testcase required case 约束改成“覆盖 required capability surfaces”，不再在 prompt 里固定写 `2~5` 条
- [x] 将 inline script repair validate 升级为“tree-sitter + JavaScript 结构 + scope”三层校验，并补宿主/脚本结构回归
- [x] 收紧 `inline-script-workset` 的资格与续跑兼容性，删除单入口 `append/orchestrator` 骨架；失配旧 progress 直接丢弃并改走 `focused script region`
- [ ] 收紧 `inline-style-workset` 的单元选择与收窄路径，避免宿主样式 patch 反复触发 `EDIT_UNIT_SCOPE_VIOLATION`
- [x] 收紧 `precise-code` 深层单元的预算/拆分与收窄路径，避免 `index.app.js` 深层单元持续 `OUTPUT_TRUNCATED`
- [x] 补单测与定向回归
- [x] 删除主链兼容层与旧入口转发，测试改为直连真实 owner
- [x] 将 review artifact 协议收口为 `REVIEW_RESULT` block-only，删除 key-value retrofit 与回写
- [x] 将 runtime wiring retry 改为 `SubtaskRevisionDirective` 结构化 override，不再靠 prose change request 续跑
- [x] 将 html-entry 计划 contract 显式化，要求 `editScope / runtimeOwnership / hostHtmlPatchRequired` 成组声明
- [x] 将 `PATCH_EXISTING_IMPLEMENTATION` continuation / review / repair note 统一成结构化 `overrideChanges` 协议，不再允许空 scope 下的静默 replanning
- [x] 将 runtime ownership / wiring 检查改成只认宿主显式接线与 inline module import，删除 `index.app.js` 默认根、basename 猜测与 orphan root ownership 推断
- [x] 将静态 HTML 结构信号并入 `QualityPlan` runtime source，避免 implementation review 在缺少浏览器快照时漏判高风险内联交互页
- [x] 删除 `StageReviewer` 兼容构造器，测试与装配改为直连真实依赖
- [x] 删除 TSX heuristic symbol fallback，invalid parse 不再产出不稳定符号
- [x] 将 validation / testcase planning 收成 deterministic primary path，不再保留 fallback 语义主路径
- [x] 将 quality rules 加载改成严格资源默认 + 项目规则覆盖，配置错误直接失败
- [x] 将 implementation review 改成 contract-first gate，不再因为 `single html / 外提脚本 / embedded dominance` 直接回退 `DESIGN`
- [x] 将阶段 directive 从 canonical artifact 中移出，单独持久化到 `*_directive.md`
- [x] 将结构风险 gate 降为 advisory，不再把“是否外提主逻辑”作为 implementation/completeness 的阻断条件
- [ ] 重跑黄金路径集成测试
- [x] 将 `IMPLEMENTATION` 未完成的 continuation 改成原生阶段流转，不再伪造 `ReviewResult / SupervisorDecision`
- [ ] 根据集成结果更新 [current-state.md](/home/linus/workspace/forge/docs/current-state.md)

---

## 当前状态

- `AGENTS 合规收口与黄金路径稳定化`：`unit-tested, awaiting integration`

## 当前说明

- `repair-before-regenerate`
- `token budget` 分层预算重构
- `v102` 黄金路径集成验收

这三条已经完成，但最近黄金路径继续暴露出以下执行一致性问题：

- `QualityPlan` 已能把高复杂度宿主 HTML 子任务前移成“外提 companion runtime script”，`v124` 已验证后续子任务会显式补入 `HOST_HTML_PATCH + index.app.js`
- 宿主 HTML 在外提脚本后，当前执行链已经会做 companion script 接线与旧 inline script 正规化，但还要继续补 host/script 结构回归
- `strict single-symbol precise-code` 现在会在执行前把受限多符号父单元直接拆成 leaf unit，不再先执行 parent unit 再靠 scope failure 补救
- implementation / repair / html patch / review / diagnosis / supervisor / testcase planning 主链中的固定 `num_predict` 调用点 cap 已删除，当前由 `outputBudgetRatio + OutputBudgetCalculator` 统一决定请求输出
- `TEST` 已明确给出 `timed-state-progression / primary-interaction` 缺失通过证据；testcase 规划链现已切到动态 output ratio + required capability surface 驱动，不再受固定 `1200` 上限和固定 `2~5` 条约束影响
- 当前 quality rules 已切成“资源默认规则 + 项目级 repo rule 覆盖”的严格加载，配置缺失或损坏会直接失败
- `TestCasePromptAssembler` 已切到 capability matrix / feature profile 驱动，当前剩余主线不再是产品特判 wording，而是严格单 symbol `precise-code` 与 `inline-style-workset` 的执行一致性
- `precise-html` 宿主宽协议现在只打一枪；若 JSON repair 后仍失败，会直接收窄到 `focused-html-region`，不再同构重试宽 `precise-html`
- 测试主链已经切到 `step semantic / capability` 驱动，但还需要继续减少质量 gate 与规则层里的 capability 推断式阻断、Java 默认规则写死和 testcase 规划固定条数约束
- 最近黄金路径已证明：HTML 入口未接入 `js/*.js` 运行脚本的问题已被 `runtime wiring gate` 拦住，但 repair validate 仍需继续收紧到“语法 + scope + 结构”全部通过
- continuation/replanning 已切到原生 state 约束；当前最新代码尚未重跑黄金路径，下一步只剩集成验证
- implementation state snapshot 已补齐 `architectImplementationPatchTarget / reviewImplementationPatchTarget`，恢复链不再从 `architectFailureReason` 反推 PATCH 语义
- `IMPLEMENTATION` 未完成状态现在会直接走原生 continuation，不再伪造 review/supervisor 语义回流；对应单元测试已通过，集成尚未重跑
- `PATCH_EXISTING_IMPLEMENTATION` 现在必须携带结构化 `overrideChanges`；review / revision note / repair note / continuation 已统一消费同一份文件级 patch scope
- runtime ownership / wiring 现在只认宿主显式 `<script src>` 接线和 inline module import，不再从 orphan runtime 文件、basename 或默认 companion 路径反推 ownership
- implementation review 已改成 contract-first：运行时所有权/入口接线不一致时修当前阶段，不再把实现形态问题粗暴上卷到 `DESIGN`
- canonical stage artifact 不再承载修订 prose；当前 directive 已单独落到 `*_directive.md`
- structure risk 已降为提示信息，不再作为 implementation/completeness 的独立阻断 gate

因此当前主线调整为：

- 质量规则收口
