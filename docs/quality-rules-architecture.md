# 质量规则架构

## 目的

这份文档定义 `Forge` 下一轮质量收敛主线：

- coder 的结构优雅性如何被系统约束
- test 的覆盖完整性如何被系统约束
- 用户习惯与交互质量如何被系统约束

目标不是增加更多 prompt 细节，而是把这些要求前移成：

- `Rules`
- `Profile`
- `Plan`
- `Gate`
- `Evidence`

## 设计原则

### 1. 执行内核继续对齐 Codex

底层执行主链继续保持：

- `patch-first`
- `tool-result-first`
- `budget-first`
- `repair-before-regenerate`

也就是说，质量约束层不替代当前执行内核，而是建立在现有内核之上。

### 2. 质量约束层借鉴 Claude Code

质量要求不再主要放在临时 prompt 里，而是前移到：

- 仓库级规则
- 分层 gate
- 自动验证钩子

它们的作用类似 Claude Code 的：

- `CLAUDE.md`
- `.claude/rules/`
- hooks

但在 `Forge` 中仍以当前文档、contract 和 gate 模型表达。

### 3. 不做产品特判

这套设计不应该写成：

- “前端页面必须拆成三个文件”
- “小游戏必须测某几个键”

而应该写成：

- 从项目特征推导结构风险
- 从能力表面推导覆盖矩阵
- 从交互语义推导体验约束

### 4. 通用层不承载领域语义

质量核心不应直接猜：

- `pause / resume`
- `reset / restart`
- `score / preview`

这类语义属于任务或产品契约，不属于通用 profiler。

通用层只允许保留：

- 输入通道是否存在
- 时间驱动状态变化是否存在
- 宿主内嵌主逻辑是否存在
- 是否存在可观察运行时 surface

领域能力必须来自：

- 上游 contract
- planner 产出的 `QualityIntent`
- 显式 repo rules

## 核心抽象

### 1. QualityRules

仓库级质量规则源。

它的来源应分成三层，顺序从低到高：

- 资源级默认规则
- 项目级 repo rules
- 项目路径下的显式 repo rule 覆写

也就是说，`Forge` 不应长期把默认质量规则写死在 Java 常量里，而应该更接近 `Codex / Claude Code` 的做法：

- 默认规则来自版本化资源
- 项目可以通过 repo rule 文件覆盖
- 规则加载失败应尽早报错，不做运行时临时覆写

职责：

- 定义什么叫完成
- 定义什么叫结构合理
- 定义什么叫测试覆盖足够
- 定义什么叫交互质量达标

建议拆成三部分：

- `StructureRules`
- `VerificationRules`
- `ExperienceRules`

### 2. FeatureProfile

项目特征画像。

它只描述事实，不直接做结论。

示例字段：

- `hasTimedProgression`
- `hasDiscreteUserInput`
- `hasEmbeddedLogic`
- `hasIndependentEntryFile`
- `hasCanvasSurface`
- `hasBackgroundLoop`
- `hasVisibleRuntimeSurface`

这些字段可以来自：

- `ExecutionContract`
- `RuntimeSnapshot`
- 代码结构扫描结果

### 3. QualityIntent

质量意图承载领域语义，但它不在通用 profiler 里推断，而由任务与契约显式提供。

建议拆成：

- `InteractionIntent`
- `StructureIntent`
- `CoverageIntent`

它的来源是：

- 上游 `goal / constraints`
- `PRD / DESIGN` 的结构化 contract
- planner 的显式产物

### 4. CapabilitySurface

用户可感知能力表面。

它不是实现细节，也不是 testcase。

示例：

- `page-load`
- `start-state-transition`
- `timed-state-progression`
- `primary-directional-input`
- `visible-progress-signal`
- `preview-state`
- `terminal-state`

这些能力表面应由：

- `FeatureProfile`
- `QualityIntent`
- `ExecutionContract`

共同推导，而不是从按钮文案或 selector 关键词直接猜。

### 5. StructureRiskReport

结构风险报告。

它描述当前实现结构是否存在高风险，而不是直接规定实现形式。

示例：

- `hostDocumentComplexity = HIGH`
- `embeddedLogicDominance = HIGH`
- `moduleBoundaryClarity = LOW`
- `justificationRequired = true`

### 6. QualityPlan

质量计划是运行时主入口。

它由规则和特征共同推导，供 coder / reviewer / tester / gate 共享使用。

建议包含：

- `QualityIntent`
- `StructurePolicy`
- `CoveragePolicy`
- `ExperiencePolicy`
- `CapabilityMatrix`

## 三类策略

### 1. StructurePolicy

解决“实现结构是否合理”。

它不应硬编码某种文件布局，而应表达：

- 当前结构风险
- 推荐边界
- 哪些场景需要 justification
- 哪些风险达到 gate 阻断级别

例如：

- `preferLogicExternalization`
- `blockOnUnjustifiedEmbeddedDominance`
- `maxHostDocumentRisk`

### 2. CoveragePolicy

解决“测试是否测全”。

它不直接定义步骤，而是定义：

- 哪些能力必须覆盖
- 哪些能力只需 smoke
- 哪些能力必须验证状态变化
- 哪些能力必须验证失败/恢复语义

### 3. ExperiencePolicy

解决“是否符合用户习惯和低惊讶度”。

这层不再依赖 reviewer 临场发挥，而是形成结构化 expectation。

例如：

- 时间推进应处于可操作区间
- pause 必须冻结可观察状态
- reset 必须恢复初始可交互状态
- primary controls 必须具有一致语义

## 推导链

建议新增统一推导器：

- `QualityPolicyResolver`

输入：

- `QualityRules`
- `FeatureProfile`
- `QualityIntent`
- `ExecutionContract`
- `ContractView`
- `RuntimeSnapshot`

输出：

- `QualityPlan`

这意味着系统不再从“页面/游戏/前端”直接跳到固定策略，而是：

- 先识别特征
- 再推导质量计划

## 运行时接入点

### 验证步骤语义

测试阶段不能继续通过 selector / 文本文案去猜：

- 这是 start control
- 这是 pause toggle
- 这是 progress signal

这类语义必须结构化进入测试计划，而不是在 `repair` 或 `coverage inferencer` 里临时推断。

建议引入：

- `TestStepSemantic`

例如：

- `RUN_STATE_ENTRY`
- `RUN_STATE_TOGGLE`
- `STATE_RESET`
- `PRIMARY_CONTROL`
- `PRIMARY_SURFACE`
- `PROGRESS_SIGNAL`

这样：

- testcase planner 负责显式输出 step semantic
- testcase sanitizer / repair 只消费结构化 semantic
- capability inferencer 只消费 step semantic + case capability

而不是再去读 selector token 猜 `start / pause / score`

### 新增约束：质量计划要前移，但 `TEST` 执行本身不前移

这里前移的是：

- `QualityPlan`
- `CapabilityMatrix`
- required capability surface
- 结构风险约束

它们必须在这些阶段开始生效：

- `ImplementationPlanner`
- `ImplementationPlanGate`
- `SubtaskVerificationSupport`
- `ImplementationCompletenessGate`

不前移的是：

- testcase 执行
- Playwright 执行
- runtime snapshot 采集
- 最终 `TEST` 报告

也就是说：

- `TEST` 仍然是最终执行验证阶段
- 但 `TEST` 暴露出的缺失能力覆盖信号，必须通过结构化 quality ledger / execution directive 回流到 `IMPLEMENTATION`
- 后续 implementation planning 必须显式为这些 required capability surface 分配责任，不能继续等到 `TEST` 末端补救

### 新增约束：repair-before-regenerate 必须先修机械错误

对 `patch-first` 主链，以下错误不应直接进入 regenerate：

- JSON 载荷格式错误
- strict single-symbol `REPLACE_SYMBOL_BODY` 中重复包装完整函数/类声明
- 局部语法壳体缺失闭合符

正确链路应是：

- deterministic repair
- validate
- minimal model repair
- validate
- 只有 repair 全部失败后才允许 regenerate

对 strict single-symbol code unit，`body-only repair` 必须在 contract validator 之前尝试。
这样可以避免“完整声明包装误塞进 body payload”这类纯机械错误直接退化成整轮重生成。

### 1. Plan

在 planning 阶段：

- 生成或刷新 `FeatureProfile`
- 生成或刷新 `QualityIntent`
- 推导 `QualityPlan`
- 把 `CapabilityMatrix` 写入当前轮上下文

### 2. Coder

coder 不再只读实现任务，还要读：

- `QualityIntent`
- `StructurePolicy`
- `CapabilityMatrix` 中与当前增量相关的 required capabilities

### 3. Reviewer

review 不再只看“能跑”或“是否实现需求”，还要显式评估：

- `StructurePolicy`
- `ExperiencePolicy`

### 4. Tester

tester 不再从零自由生成 case，而是：

1. 先读取 `CapabilityMatrix`
2. 再把能力项展开成具体 testcase
3. 再把执行结果回填到 coverage ledger

这里还需要两个约束：

- testcase planner 的输出预算不能继续固定写成单一小值，而应由动态 output budget 驱动；
- planner prompt 不应继续写“网页/小游戏至少...”这类产品特判，而应围绕 `FeatureProfile + CapabilityMatrix` 描述 required coverage。

### 5. Gate

建议新增或增强三类 gate：

- `StructureGate`
- `CoverageGate`
- `ExperienceGate`

它们共同决定：

- `PASS`
- `REVISION_REQUIRED`
- `BLOCKED`

## 当前收口重点

最近的黄金路径已经证明，质量规则主线还有 5 个关键缺口需要补齐：

1. `FeatureProfiler / CapabilitySurfaceBuilder` 仍残留领域词表和特定场景推导。
2. `QualityPlan` 只进入了 `review/test`，还没有前移到 `ImplementationPlanner / SubtaskVerificationSupport / ImplementationCompletenessGate`。
3. `repair-before-regenerate` 现在能修 JSON 和语法，但 `repair validate` 仍缺宿主产物结构校验。
4. `QualityRules` 虽已支持规则覆盖，但默认规则源还没有切到“资源默认规则 + 项目级 repo rule + runtime 覆写”三层加载。
5. testcase planner 仍保留固定 `1200` 预算和“2~5 条 required case / 网页小游戏至少...”这类 prompt 经验值，导致能力覆盖要求会被保守预算与产品特判共同压缩。

所以当前实现顺序应当是：

1. 去掉通用层里的领域硬编码；
2. 把 `QualityIntent` 和 `QualityPlan` 前移到 implementation 主链；
3. 为 repair 链补 `host-artifact structural validation`；
4. 把 `QualityRules` 默认值外提为 repo-style 规则源；
5. 把 testcase planner 改成 capability matrix 驱动 + 动态 output budget；
6. 再重跑黄金路径。

## 与 Codex 继续对齐的收口项

在完成上面的基础收口后，最近的黄金路径又暴露出 3 个“执行一致性”问题，它们更接近 `Codex` 的 file-oriented patch 要求：

### 1. 计划、artifact 与执行链必须共享同一份变更真相

当 `QualityPlan` 已把宿主 HTML 归一化成：

- `HOST_HTML_PATCH`
- companion runtime script file

后续的：

- implementation artifact
- task package
- worker result
- 实际文件执行路由

都不应再退回成旧的 `INLINE_SCRIPT_PATCH` 视图。

这对应 `Codex` 的一个核心习惯：文件级 patch 和工具结果是唯一真相，不能“计划是一套、落盘又是一套”。

### 2. 外提脚本后的宿主 HTML 必须保持稳定入口形态

一旦 companion script 接管主逻辑，宿主 HTML 后续应只承担：

- markup
- style
- script reference / bootstrap hook

而不应继续保留旧的主逻辑 inline script。

这不是前端特判，而是宿主文档结构稳定性的要求。

### 3. repair-before-regenerate 必须以完整文件结构为成功条件

当前 repair 链已经能修：

- JSON payload
- 局部语法结构

但 `v107` 暴露出：即使局部语法修复成功，代码文件仍可能出现：

- 同名函数包装重复嵌套
- companion script scaffold 结构异常

因此 repair 成功判定必须升级成：

- `json-valid`
- `syntax-valid`
- `scope-valid`
- `file-structure-valid`

全部通过才算 repair 成功。

## 证据模型

建议新增两份结构化账本：

### 1. CoverageLedger

记录：

- 计划要求覆盖哪些 capability
- 实际哪些 testcase 覆盖到了
- 哪些 capability 仍未覆盖

### 2. QualityLedger

记录：

- 结构风险
- justification
- 体验 expectation
- review findings
- gate outcome

## 为什么这版更贴近 Codex

这套设计和 Codex 对齐的点在于：

- 规则前置，而不是临时补 prompt
- patch 和工具结果继续作为执行核心
- “done means what” 被显式写进规则与 gate
- 测试和验证由结构化计划驱动，不靠临场发挥

也和 Claude Code 对齐，因为：

- 规则是第一层
- gate / hook 是第二层
- 模型只负责生成补丁、步骤和语义判断

## 非目标

当前不做这些事情：

- 不引入按产品名硬编码的模板库
- 不把“前端默认三文件”写成全局硬规则
- 不增加新的常驻大 agent
- 不让 reviewer prose 决定全部 gate

## 实施阶段

### Phase 1

引入：

- `QualityRules`
- `FeatureProfile`
- `CapabilitySurface`
- `StructureRiskReport`

### Phase 2

引入：

- `QualityPlan`
- `CapabilityMatrix`
- `CapabilityMatrixBuilder`

并让 `TestCasePlanner` 先吃矩阵再生成步骤。

### Phase 3

引入：

- `StructureGate`
- `CoverageGate`
- `ExperienceGate`

并接到 reviewer / test 的主链上。

### Phase 4

引入：

- `CoverageLedger`
- `QualityLedger`
- `VerificationHookPipeline`

把质量检查进一步自动化。

## 验收标准

达到下面这些条件，才算这条主线完成：

- 结构质量不再主要靠 reviewer prose 临场判断
- testcase 不再只挑 2~5 条自由 case，而是受 `CapabilityMatrix` 约束
- 明显违背用户习惯的实现能被 `ExperienceGate` 或测试证据拦下
- 规则、计划、gate、证据形成闭环
