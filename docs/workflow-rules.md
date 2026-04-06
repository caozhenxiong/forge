# 工作流规则

## 目的

这份文档描述 `Forge` 当前第一版工作流的运行规则。

它不是架构蓝图，也不是使用手册，而是面向后续维护者说明：

- 每个阶段负责什么
- 每个阶段的输入输出是什么
- 哪些失败应该走 `PATCH`
- 哪些失败应该走 `REWORK`
- `self-check`、`verifier`、`CODE_REVIEW`、`TEST` 的职责边界是什么
- 连续失败时什么时候升级到 `diagnosis/repair`

后续如果代码行为发生变化，这份文档必须和实现一起更新。

补充工程原则：

- 复杂底层能力优先做 build-vs-buy 判断
- 结构化代码编辑默认选择 `tree-sitter`
- 不再把自研低层精确字符串替换作为长期主路径

## 总体流程

当前主流程基础顺序为：

1. `ANALYSIS`
2. `PRD`
3. `DESIGN`
4. `IMPLEMENTATION`
5. `CODE_REVIEW`
6. `TEST`

每个阶段都会生成 artifact，并在阶段结束后进入 review。

但当前已经不是“固定状态机直接决定一切”，而是：

- `WorkflowEngine`
  - 负责执行、约束、落盘
- `SupervisorAgent`
  - 负责决定下一步动作
- 各阶段 worker
  - 负责具体产物生成与验证

所以实际流程是“固定主阶段顺序 + supervisor 决策分流”。

review 的结果会驱动三种后续动作：

- `APPROVED`
- `REVISION_REQUIRED`
- `REJECTED`

其中 `REVISION_REQUIRED / REJECTED` 还必须带 `fixMode`：

- `PATCH`
- `REWORK`

## Supervisor 规则

### 1. Supervisor 只做流程决策

`SupervisorAgent` 不直接写代码、不直接改文件。

它只负责基于当前上下文判断：

- 是继续下一阶段
- 还是重试当前阶段
- 还是进入 repair
- 还是回退上游阶段
- 还是请求人工介入

### 2. Supervisor 的输入

当前决策至少会读取：

- 当前 `run` 摘要
- 当前 stage artifact 摘要
- 当前 review 结果
- 最近 review history 摘要
- `repair_brief`（若存在）

### 3. Supervisor 的输出动作

当前允许的动作：

- `ADVANCE_STAGE`
- `REQUEST_HUMAN_REVIEW`
- `RETRY_STAGE`
- `ROUTE_TO_REPAIR`
- `ROLLBACK_STAGE`
- `COMPLETE_RUN`
- `FAIL_RUN`

### 4. WorkflowEngine 的职责边界

即使 `SupervisorAgent` 给出决策，也仍由 `WorkflowEngine` 负责：

- 校验 action 是否合法
- 校验 target stage 是否合法
- 保证重试预算上限
- 记录 `supervisor_decision.md`
- 记录 `events.log`

也就是说：

- `SupervisorAgent` 是决策层
- `WorkflowEngine` 是执行和约束层

## 统一规则

### 1. artifact 优先

每个阶段都必须有明确产物。

当前 run 目录至少包含：

- `run.json`
- `<stage>.md`
- `<stage>_review.md`
- `<stage>_review_history.md`
- `events.log`

`TEST` 阶段额外包含：

- `test_cases.md`
- `test_execution.md`
- `test_report.md`

补充规则：

- 进入新阶段时，状态机会先把 `RUNNING + attempt` 落盘，再开始生成 artifact
- 这样即使模型生成较慢或阶段执行中途失败，`run.json` 也能反映真实当前阶段，而不是停留在上一个阶段

### 1.1 文档阶段必须遵循固定模板

`ANALYSIS / PRD / DESIGN` 不能只满足“有这些章节”。

要求：

- 主章节结构固定
- 章节顺序固定
- 标题命名固定
- 每个二级章节都必须有实质内容
- 不能留 `[TODO]`
- 不能省略“边界 / 不做什么 / 风险 / 待确认问题”

这样做的目的：

- 降低 reviewer 对文档结构的判断成本
- 让后续 `IMPLEMENTATION / TEST_CASE_DESIGN / TEST` 能稳定读取上游文档
- 让维护者能快速对照阶段输出是否合规

补充规则：

- 文档修订不应默认整篇重写
- 当 reviewer 明确指出缺失章节时，优先采用“只补缺失章节，再合并回旧稿”的方式修订
- 这样可以避免前半部分越写越长、后半部分章节持续丢失
- 即使 reviewer 指出的是逻辑问题而不是“缺章节”，文档修订也会优先按章节合并旧稿，避免一次修订把已有章节整体覆盖丢失
- 文档首次生成与补章/修订都应由同一类文档 agent 完成；差异应体现在 prompt 的行为模式，而不是切换成 code review agent
- 当前文档 agent 显式区分三种 mode：
  - `FULL_DRAFT`
  - `FILL_MISSING_SECTIONS`
  - `REVISE_WITH_EXISTING_DRAFT`
- reviewer 不只检查章节标题是否存在，还会检查章节正文是否有实质内容
- 如果某一章只有标题、没有正文或只有空的小节标题，也会被视为未完成章节并打回

### 2. review 历史不能覆盖

每次 reviewer 和 human 审批都必须追加到 review history，不能只保留最后一轮结果。

### 3. 自检失败不等于需求失败

技术自检只说明：

- 代码是否明显坏掉
- 工程链路是否可执行
- 页面/程序是否至少能启动

自检通过不代表需求已实现完成。

补充规则：

- required testcase 必须真实执行，未执行不能视为通过
- 当前如果技术栈尚未实现专用 testcase 执行器，required case 会失败，而不是默认通过
- 单个 `index.html` + 内联脚本的项目也会识别为 `web-static` 并进入浏览器级 testcase 执行路径
- fallback testcase 设计会优先基于 `tree-sitter` 提取真实 HTML 结构，而不是只靠正则猜测静态页面选择器

### 3.1 生成内容写盘前必须先过结构校验

`IMPLEMENTATION` 阶段不能只因为模型返回了非空文本，就直接覆盖现有文件。

当前规则：

- `.html`
  - 先做基本结构检查
  - 再走 `tree-sitter`
  - 内联脚本还要单独做脚本可解析性检查
- `.js/.mjs/.cjs`
  - 先走 `tree-sitter`
  - 再走 `node --check`
- `.java`
  - 先走 `tree-sitter`

约束：

- 结构不完整、语法不合法或明显截断的输出不能写盘
- 模型只返回“非空半截内容”不算成功
- 这层校验属于 implementation 的基础护栏，不由 reviewer 兜底

### 3.2 HTML 页面优先走区块级精确改写

对已有 HTML 页面，如果已经存在稳定锚点：

- `<main id="app-root">`
- `<style id="app-style">`
- `<script id="app-script">`

则 `IMPLEMENTATION` 在 `INCREMENTAL / PATCH` 模式下应优先：

- 只生成区块 JSON
- 只替换 `app-root / app-style / app-script` 的内部内容
- 不再整页重写

目的：

- 降低大文件截断风险
- 让页面结构、`<head>` 元信息和外围壳子保持稳定
- 让后续 patch 更容易收敛

当前边界：

- 只对 HTML 页面启用
- 只对已有稳定锚点的页面启用
- 锚点缺失时仍回退到完整文件生成

### 4. review 结论必须可执行

review 不能只说“有问题”。

每次 review 至少要给出：

- `decision`
- `fixMode`
- `summary`
- `changeRequest`
- `evidence`
- `actionItems`

其中：
- `changeRequest` 用来概括修复方向
- `evidence` 要说明支持结论的代码/测试/自检证据
- `actionItems` 要给出 coder 可直接执行的修改动作

补充规则：

- review 只能基于当前输入中的明确证据下结论
- 对“性能不达标”“超过 xx ms”这类结论，必须有自检或测试中的测量证据
- 如果没有测量数据，只能判定为“存在性能风险”或“缺少性能验证”，不能直接据此给出阻塞性性能失败结论
- 性能验证策略应由 `DESIGN` 阶段先定义，再由 `IMPLEMENTATION` 执行、`TEST` 验收
- 如果 `DESIGN` 没有定义性能测量要求，`IMPLEMENTATION` 不应因“缺少性能数据”被阻塞
- 如果 `DESIGN` 已定义性能测量要求，而实现未提供数据，则优先 `PATCH` 补基础测量

### 5. 连续失败不能无限重试同一路径

当同一类问题连续出现时，系统不应继续让原 `IMPLEMENTATION` 直接重复修复。

触发升级条件示例：

- 同一类 `changeRequest` 连续出现 `2-3` 次
- 同一类 `self-check/test` 错误连续出现 `2-3` 次
- 同一文件被多轮修改，但阻塞问题不收敛
- `PATCH` 多轮后没有实质进展

满足条件后，应进入：

- `diagnosis`
- `repair`

而不是继续无差别消耗 `IMPLEMENTATION` 预算。

## 各阶段职责

## `ANALYSIS`

职责：

- 把目标和约束整理成研发可理解的问题定义
- 识别关键风险、假设和待确认问题

输入：

- `goal`
- `constraints`

输出：

- `analysis.md`

通过标准：

- 问题定义清晰
- 目标和成功标准明确
- 风险与待确认问题可读

不负责：

- 写实现方案
- 写代码
- 细化算法实现细节
- 定义唯一解验证机制细节
- 给出量化性能测试方案
- 产出页面原型或交互原型细节

review 补充规则：

- `ANALYSIS` reviewer 只检查“是否足以进入 PRD”
- 如果文档已经覆盖问题定义、目标、约束、边界、风险和调研假设，就不应因为缺少设计/测试阶段细节而阻塞
- 下列内容属于后续阶段，不应作为 `ANALYSIS` 的阻塞项：
  - 算法实现与回溯细节
  - 唯一解验证机制
  - 量化性能指标与 benchmark 方案
  - UI 线框图、页面原型、交互原型

## `PRD`

职责：

- 把需求分析转成产品需求文档
- 明确功能范围、非功能要求和验收标准

输入：

- `analysis.md`

输出：

- `prd.md`

通过标准：

- 范围明确
- 场景明确
- 验收标准明确

不负责：

- 技术设计细节

review 补充规则：

- `PRD` reviewer 只检查产品层面的目标、场景、范围、边界和验收标准
- 下列内容属于后续阶段，不应作为 `PRD` 的阻塞项：
  - 算法实现细节
  - 唯一解验证机制实现
  - 模块划分、接口设计、类图
  - 性能 benchmark 或技术级测试方案

## `DESIGN`

职责：

- 把 PRD 转成工程可执行的技术设计
- 明确模块、数据流、风险与取舍

输入：

- `prd.md`

输出：

- `design.md`

通过标准：

- 设计足以指导 implementation
- 测试策略有基本说明

额外职责：

- 如果需求中存在性能/耗时类诉求，`DESIGN` 必须明确：
  - 是否需要测量
  - 测哪些指标
  - 如何测
  - 阈值或验收口径
- 后续 `IMPLEMENTATION` 和 `TEST` 均以 `DESIGN` 中的验证策略为准，不应自行发明新的性能门槛

不负责：

- 直接写实现代码

## `IMPLEMENTATION`

职责：

- 基于 `ANALYSIS / PRD / DESIGN` 落地代码实现
- 不是一次性大生成，而是拆成多个子任务逐步完成

输入：

- `analysis.md`
- `prd.md`
- `design.md`
- 上一轮 `changeRequest` 和 `fixMode`（若存在）

输出：

- `implementation.md`

内部流程：

1. 生成 implementation plan
2. 拆成 `3-6` 个可验证子任务
3. 每个子任务最多改 `2` 个文件
4. 逐个子任务写代码
5. 对复杂前端/网页/游戏任务，优先走“骨架 -> 渐进填充 -> polish”
6. `SupervisorAgent` 的 `focus / constraints` 会作为当前轮次约束传给下游实现
4. 每个子任务先做 `self-check`
5. `self-check` 通过后再做子任务 `verifier`
6. 所有子任务完成后生成 implementation artifact

通过标准：

- 子任务全部完成
- 自检通过
- verifier 没有阻塞性问题
- 若 `DESIGN` 明确要求基础性能测量，则实现已补齐对应测量结果

不负责：

- 最终架构审阅
- 最终测试放行

补充规则：

- `IMPLEMENTATION` 不负责自己定义性能指标
- 若 `DESIGN` 未要求性能测量，reviewer 不能因“缺少性能数据”阻塞实现
- 若 `DESIGN` 已要求性能测量，缺少数据时应优先 `PATCH`
- 没有测量证据时，reviewer 只能写“存在性能风险”或“缺少性能验证”，不能直接写“性能未达标”
- 代码生成阶段如果输出明显不完整、结构未闭合或脚本不可解析，不允许写盘覆盖原文件
- 默认不鼓励一轮写完整个产品；大任务要拆成小步，优先保证每一步都可自检、可运行
- 前端/静态网页任务优先把 HTML、CSS、JS 分职责拆文件，避免单个入口文件承载全部代码
- 子任务计划若一次改动超过 `2` 个文件，会被实现器拒绝并要求重新规划

### `IMPLEMENTATION` 的交付模式

当前实现阶段显式区分 4 种 `deliveryMode`：

- `SKELETON`
  - 建立最小可运行骨架
  - 目标是页面/程序先能打开、入口和模块先接上
- `INCREMENTAL`
  - 在已有骨架上逐步填充功能
  - 默认用于正常功能推进
- `PATCH`
  - 围绕明确 changeRequest 做最小修补
- `REWORK`
  - 解决结构性问题，允许较大范围调整

规则：

- 复杂前端/网页/游戏任务的第一子任务优先使用 `SKELETON`
- 后续子任务优先使用 `INCREMENTAL`
- 只有 review 明确要求或 fixMode 指定时，才使用 `PATCH / REWORK`

### `IMPLEMENTATION` 的两种模式

## `PATCH`

适用条件：

- 结构基本可接受
- 问题是局部 bug、遗漏、接线、边界条件

规则：

- 尽量保留现有结构
- 尽量少改文件
- 不随意重命名/重组模块
- 优先做最小补丁

## `REWORK`

适用条件：

- 结构混乱
- 模块边界错误
- 重复实现
- 文件组织明显失衡

规则：

- 可以重新拆分模块
- 可以调整结构
- 可以较大范围修改代码

### `IMPLEMENTATION` 当前限制

- 仍然是串行修改同一工作区
- 当前没有真正的子任务并行 merge 层
- 多子任务之间的“合并”本质上是顺序叠加，不是分支 merge
- 当前 file generation 仍以“完整文件输出”为主，但已增加不完整内容校验与重试；后续仍建议继续演进到 patch/section 级写入
- patch/section 级写入的长期默认方向是：
  - 优先使用现成专用编辑工具
  - 多语言结构化定位默认走 `tree-sitter`
  - HTML / DOM 修改优先成熟 DOM 工具
  - 纯文本场景才退回 diff / patch

### `IMPLEMENTATION` 连续失败后的升级规则

如果 implementation 内部已经多轮围绕同一问题修复但没有收敛：

1. 先触发 `DiagnosisAgent`
2. 由 `DiagnosisAgent` 产出 `repair brief`
3. 再交给 `RepairAgent`
4. `RepairAgent` 按 `PATCH` 或 `REWORK` 约束执行修复
5. 修复后仍然必须重新经过：
   - `self-check`
   - `verifier`

升级的目标不是多开 agent，而是避免继续沿着错误方向盲修。

### `repair brief` 规则

`repair brief` 是 `DiagnosisAgent` 和 `RepairAgent` 之间的标准交接物，至少包含：

- 当前阶段
- 连续失败的问题聚类
- 重复出现的错误
- 根因假设
- 受影响文件
- 关键证据
- 推荐修复模式
  - `PATCH`
  - `REWORK`
- `mustFixFirst`
  - 这一轮不先修就无法收敛的关键问题
- `forbiddenDirections`
  - 这几轮已经证明会把实现继续带偏的错误修复方向
- 明确不要改动的部分
- 修复完成后的验收目标
- `acceptanceChecks`
  - 下一轮 verifier/reviewer 必须优先检查的项目

规则：

- `RepairAgent` 不应该重新读取整轮噪音历史
- 它应优先依赖 `repair brief` 和相关文件内容
- `repair brief` 不是普通备注，而是强约束输入
- 进入 repair 路径后，`IMPLEMENTATION` 必须优先覆盖 `mustFixFirst`
- 若实现继续沿着 `forbiddenDirections` 偏离，应直接打回
- verifier 应优先围绕 `acceptanceChecks` 判定是否收敛

### review 打回后的重新自检规则

当 `CODE_REVIEW` 或 `TEST` 打回并重新路由回 `IMPLEMENTATION` 时：

1. 不会直接跳过验证进入下一轮 review
2. `IMPLEMENTATION` 会重新执行当前轮次
3. 每个子任务仍然会再次经过：
   - 代码生成/修复
   - `self-check`
   - `verifier`
4. 只有新的 implementation 结果通过内部检查后，才会重新进入后续 `CODE_REVIEW`

也就是说：

- review 打回后的修复不是“裸改后直接交 review”
- 而是必须重新经过 implementation 内部自检闭环

### `SupervisorAgent` 与 `repair_brief` 的关系

当连续失败被识别为需要 diagnosis/repair 时：

1. `DiagnosisAgent` 先产出 `repair_brief`
2. `SupervisorAgent` 在后续决策时会读取 `repair_brief`
3. 若问题已明确且适合定点修补，优先：
   - `ROUTE_TO_REPAIR`
4. 若问题根因明显属于上游文档或设计，才允许：
   - `ROLLBACK_STAGE`

这样可以避免固定状态机继续沿错误方向盲修。

## `CODE_REVIEW`

职责：

- 审阅实现结果是否满足设计和工程要求
- 判断应该补丁修复还是结构重做

输入：

- `implementation.md`
- 实际代码变更
- 自检结果

输出：

- `code_review.md`

输出必须包含：

- `decision`
- `fixMode`
- `summary`
- `changeRequest`

通过标准：

- 代码结构可接受
- 没有阻塞性工程风险
- 当前实现与 artifact、自检、实际代码一致

### `CODE_REVIEW` 的 fixMode 判定

优先给 `PATCH` 的情况：

- 结构整体可接受
- 某个按钮/事件/字段没接上
- 某个函数遗漏
- 局部 bug 或边界处理不完整

优先给 `REWORK` 的情况：

- 模块重复
- 职责混乱
- 文件结构不合理
- 入口和实现完全脱节
- 大量代码需要推倒重来

### 特别规则

如果 artifact 顶部写的是：

- `decision: APPROVED`

但 Findings 里仍列出明确阻塞问题，则不能按通过处理，必须降级成需要修改。

## `TEST`

职责：

- 验证最终交付是否满足“能运行 + 核心功能可验证”
- 不是单纯做语法检查

输入：

- `goal`
- `constraints`
- `prd.md`
- `design.md`
- `implementation.md`

输出：

- `test_cases.md`
- `test_execution.md`
- `test_report.md`

当前内部流程：

1. `self-check`
2. `test case design`
3. `test execution`
4. 汇总 test report

### `self-check` 的职责边界

只负责通用技术正确性：

- 构建是否通过
- 测试命令是否可跑
- 本地资源是否存在
- JS/脚本语法是否通过
- 页面/程序最基本是否能启动

不负责：

- 判断业务功能是否完整
- 判断交互体验是否符合 PRD

### `test case design`

职责：

- 基于目标、PRD、设计、实现生成结构化 testcase

每条 testcase 至少包含：

- `id`
- `title`
- `type`
- `required`
- `entry`
- `expected`
- `steps`

### `test execution`

职责：

- 按 testcase 真执行

当前第一版：

- 网页项目优先用 `Playwright`
- 若 `DESIGN` 明确提出性能验收要求，TEST 阶段可以执行：
  - `MEASURE_PAGE_LOAD_MAX_MS`
  - `ASSERT_WINDOW_METRIC_MAX_MS`
- `ASSERT_WINDOW_METRIC_MAX_MS` 依赖实现通过 `window.__devflowMetrics` 暴露运行时指标
- 非网页项目先保留 testcase 设计结果，后续再补专用执行器

### `TEST` 通过标准

必须同时满足：

- `self-check` 通过
- 所有必测 testcase 全部通过
- 若 `DESIGN` 定义了性能指标，需基于实测结果完成最终性能验收

只通过 `self-check` 不算测试通过。

## `PATCH` 和 `REWORK` 的分流规则

当前由 reviewer 明确给出 `fixMode`，状态机会按 `fixMode` 决定如何回到 `IMPLEMENTATION`。

原则：

- `PATCH`：最小修补
- `REWORK`：允许结构重做

不能默认把所有失败都当成重写整个功能。

## 重试预算原则

当前总自动修订次数仍然有限。

经验上应该逐步拆成：

- implementation 总预算
- self-check 重试预算
- patch 修复预算
- rework 预算

当前代码还没有完全拆开这些预算，所以维护时要注意：

- 不要把容易修复的技术问题和高层 review 问题混用同一套策略

## 维护原则

后续改流程时，优先遵守这些原则：

1. 不要把业务特例硬编码进 `self-check`
2. 不要让 `CODE_REVIEW` 只给模糊结论
3. 不要把所有失败都回退成“大重写”
4. 不要只保留最后一轮 review
5. 不要让 `TEST` 退化成纯语法检查

## 当前已知后续方向

1. 给非网页项目补 testcase 执行器
2. 拆分更细的重试预算
3. 强化子任务写集/patch 化，减少顺序覆盖
4. 必要时把 `PATCH` 抽成显式子阶段，而不是继续复用 `IMPLEMENTATION`
