# 2026-04-06 迭代经验与问题清单

## 目的

这份文档记录 `Forge` 第一版在真实项目跑通过程中暴露出来的流程问题、实现 bug 和有效优化点。

它不描述理想架构，而是记录已经踩过的坑和当前形成的工程经验，后续迭代时优先对照本清单检查。

## 今日结论

- 工作流主链路已经能跑通，但质量瓶颈不在“状态机”，而在 `IMPLEMENTATION -> REVIEW -> 修复` 这一段。
- 复杂功能不能靠“一次性完整实现”稳定完成，必须拆成子任务分步实现和分步验证。
- reviewer 不能只给“通过/不通过”，必须明确给出修复类型。
- `self-check` 不应该写业务特判，应该优先依赖项目自身构建链路和通用技术规则。
- `TEST` 不能只靠 self-check 通过，必须补 testcase 设计和 testcase 执行层。
- review 历史必须保留每一轮记录，不能只保留最后一条。
- 当相同问题连续多轮不收敛时，需要升级到 `diagnosis -> repair`，不能只靠原实现器反复盲修。
- 对复杂底层能力不要默认手搓；要先判断是否有成熟现成方案，结构化代码编辑默认优先 `tree-sitter`。

## 新的工程原则

### 0. 先判断有没有现成方案

问题：

- 很容易在“精确修改代码”“通用 patch”“结构化编辑”这类能力上直接开始手搓底层
- 这会很快把系统带进维护成本高、边界脆弱、跨语言不可复用的方向

经验：

- 复杂底层能力优先做 build-vs-buy 判断
- 默认优先选择成熟现成方案
- 结构化代码编辑默认优先 `tree-sitter`
- HTML / DOM 修改优先 DOM 工具
- 低层字符串锚点替换不应作为长期主路径

当前决策：

- `Forge` 已把“结构化编辑默认选择 `tree-sitter`、复杂底层能力优先现成方案”写入长期策略文档
- 未接线的自研低层精确文本编辑类已移除，避免后续沿错误方向继续扩展
- `tree-sitter` 已进入主链路，先用于生成内容验收和静态结构提取，而不是继续扩展字符串锚点替换

## 已确认的流程问题

### 1. `IMPLEMENTATION` 不能走一次性大生成

问题：

- 直接让模型一次性完成整个功能，复杂任务容易出现：
  - 只实现了一半
  - 文件结构漂移
  - 一轮修复引入另一轮回归
  - reviewer 每次都在抓低级未完成错误

经验：

- `IMPLEMENTATION` 必须先生成 `implementation plan`
- 再拆成 `3-6` 个可验证子任务
- 每个子任务独立实现、独立自检、独立 verifier
- 复杂功能优先靠“逐步收敛”，不是靠大 prompt 一次到位

当前已落地：

- `ImplementationExecutor` 已改成 `plan -> subtask -> self-check -> verifier`

### 2. `CODE_REVIEW` 不能只给统一打回

问题：

- 之前 `CODE_REVIEW` 或 `TEST` 一旦失败，就统一回到完整 `IMPLEMENTATION`
- 这会导致本来只需要修一个 bug，却被当成整轮重写
- 结果是：
  - 越修越散
  - 模块重复
  - 结构退化
  - 自动修订次数被快速耗尽

经验：

- reviewer 必须明确返回 `fixMode`
- `fixMode` 至少分成：
  - `PATCH`
  - `REWORK`

规则：

- `PATCH`
  - 结构基本可接受
  - 只做最小补丁修复
- `REWORK`
  - 结构明显有问题
  - 允许较大范围调整

当前已落地：

- `ReviewResult` 已包含 `fixMode`
- `OllamaLlmProvider`、`StageReviewer`、`DefaultWorkflowEngine`、`ImplementationExecutor` 已串起来

### 3. `IMPLEMENTATION` 失败后需要“修复模式”而不是默认重写

问题：

- 即使已经回到 `IMPLEMENTATION`，如果 prompt 不区分修补和重构，模型还是容易整份文件重写

经验：

- `PATCH` 模式必须明确要求：
  - 只修当前反馈
  - 尽量保留现有结构
  - 不要重写整份文件
- `REWORK` 模式才允许调整模块边界和结构

当前已落地：

- 通过 `[FIX_MODE=PATCH] / [FIX_MODE=REWORK]` 回传给 `ImplementationExecutor`

### 3.1 单一实现器在死循环中会持续跑偏

问题：

- 某些 run 会连续多轮得到相同或高度相似的 `changeRequest`
- 原实现器会继续沿着原路径修补，反复消耗预算
- 即使 reviewer 已经指出方向，模型也可能因为上下文噪音或错误惯性继续修错

经验：

- 当相同问题连续多轮不收敛时，应该停止原实现器的盲修
- 需要先做一次失败归纳，再做定点修复

建议机制：

- 新增 `DiagnosisAgent`
  - 汇总最近几轮失败原因
  - 归纳根因、证据、受影响文件和修复边界
- 新增 `RepairAgent`
  - 基于问题摘要做最小必要修复
  - 优先走 `PATCH`
  - 只有明确结构性问题时才走 `REWORK`

关键产物：

- `repair_brief.md`
- 或 `repair_brief.json`

其中应明确：

- 当前失败聚类
- 重复错误
- 根因假设
- 建议修复模式
- `mustFixFirst`
- `forbiddenDirections`
- 不要改动的范围
- 修复完成后的验收目标
- `acceptanceChecks`

这类“失败摘要 -> 修复交接”机制，比单纯加大重试次数更有效。

补充经验：

- `repair_brief` 不能只是落一个文件，否则 implementer 很容易继续沿旧方向盲修
- 它必须成为强约束输入：
  - implementer 优先围绕 `mustFixFirst` 修
  - verifier 优先检查 `acceptanceChecks`
  - 如果实现继续走 `forbiddenDirections`，应直接拒绝

## 已确认的实现 bug

### 4. `TEST` 阶段曾错误把通过结果判成失败

问题：

- `test_report.md` 明明是：
  - `decision: APPROVED`
- 但 `StageReviewer` 以前只认 `exitCode`
- 如果没有 `exitCode` 行，就默认失败

影响：

- 实际已经通过测试的 run 被错误打回
- 随后又重新进入 `IMPLEMENTATION`
- 白白消耗修订次数

当前已修复：

- `reviewTestArtifact()` 现在优先读取 `decision`
- 只有没有 `decision` 时才回退到 `exitCode`

### 5. `Ollama` 空响应必须重试后失败退出

问题：

- 某些模型调用会返回空的 `response`
- 之前 provider 遇到空内容直接抛错退出

经验：

- LLM provider 不能假设每次都返回稳定文本
- 空响应应由 provider 层统一重试
- 如果重试后仍为空，流程应直接失败，不能再降级放行

当前已修复：

- `OllamaLlmProvider.generate()` 会对空响应自动重试 `3` 次
- 最终失败时会附带更具体诊断信息
- reviewer 不再对空响应做启发式降级放行
- `done_reason=length` 的响应不再视为可用输出，会继续重试，避免把截断内容当成功

### 5.1 截断输出不能只靠 reviewer 发现

问题：

- 模型可能返回非空但半截的文件
- 如果只把“非空”当成功，半成品会覆盖原文件
- 之后即使 `TEST` 发现问题，也已经把可用文件污染掉了

经验：

- 截断问题必须在 implementation 写盘前拦住
- “非空”不是成功标准
- 必须加结构校验和语法校验

当前已落地：

- `.html/.js/.java` 生成内容在写盘前会先过 `tree-sitter`
- `.js` 还会追加 `node --check`
- `.html` 还会追加内联脚本解析校验
- 校验失败会触发重新生成，而不是直接覆盖文件

### 6. implementation plan JSON 不稳定

问题：

- 模型返回的 `implementation plan` 有时不是合法 JSON
- 例如：
  - 数组括号没闭合
  - 字段丢失
  - 多输出了一些解释

经验：

- 计划型输出必须经过解析修复层
- 不能把模型 JSON 直接当绝对可信输入

当前已修复：

- `ImplementationExecutor` 增加了 `parsePlanWithRepair`
- 解析失败时会调用模型做 JSON 修复，最多重试 `3` 次

### 7. 性能类 review 不能凭感觉下结论

问题：

- reviewer 容易直接写“无法满足 500ms 验收标准”“性能不达标”
- 但输入里并没有真实性能测量结果

经验：

- 性能类结论必须有证据来源
- 没有测量数据时，只能说“存在性能风险”或“缺少性能验证”
- 不能把“可能慢”直接写成阻塞性失败
- 性能验证不应由 `IMPLEMENTATION` 临场决定
- 正确链路应为：
  - `DESIGN` 定义性能验证策略
  - `IMPLEMENTATION` 按设计补基础测量
  - `TEST` 做最终性能验收

当前已修复：

- implementation verifier 和 implementation review prompt 都要求：
  - 性能结论必须引用自检/测试中的测量证据
  - 否则只能给风险提示，不能直接判定超标
- `StageReviewer` 现在会读取 `DESIGN` 产物：
  - 若 `DESIGN` 未定义性能测量要求，性能风险不会阻塞 `IMPLEMENTATION`
  - 若 `DESIGN` 已定义性能测量要求，缺少数据时会要求补基础测量
- `ImplementationExecutor` 也会读取 `DESIGN` 中的验证策略，把基础测量要求带入 plan/subtask verifier prompt

### 7. verifier 上下文截断会造成误判

问题：

- verifier 之前只看长文件前半段
- 某些关键逻辑在文件尾部，例如：
  - 键盘事件绑定
  - 页面初始化
  - `window.onload`
- verifier 会误以为“没有实现”

经验：

- 长文件验证不能只截头
- 更合理的做法是：
  - 保留头部和尾部
  - 或改成更细粒度的目标上下文抽取

当前已修复：

- 长文件验证改成头尾双端保留

## 已确认的自检经验

### 8. `self-check` 不应该写死业务逻辑

问题：

- 一开始 `self-check` 混入了太多前端页面行为假设
- 例如按钮绑定、DOM 关系等业务化判断
- 容易出现两个问题：
  - 规则过窄，误杀正常实现
  - 为某个项目修规则，污染所有项目

经验：

- `self-check` 只应该做通用技术规则：
  - 构建是否通过
  - 测试是否通过
  - 资源文件是否存在
  - JS 语法是否通过
  - 页面最基本能否启动
- 业务正确性应该交给：
  - `subtask acceptance criteria`
  - `verifier`
  - `CODE_REVIEW`

当前已调整：

- 先探测项目自身工具链：
  - `mvn test`
  - `gradle test`
  - `npm/pnpm/yarn build`
  - `npm/pnpm/yarn test`
- 对纯静态网页项目：
  - 检查本地资源引用是否存在
  - 对 `.js/.mjs/.cjs` 跑 `node --check`
  - 对 HTML 内联脚本也跑 `node --check`
- 原来的业务化按钮检查已降级，不再作为主规则

### 9. 没有工具链时，通用语法检查比 DOM 特判更稳

经验：

- 对静态网页项目，优先顺序应当是：
  1. 资源是否存在
  2. 脚本语法是否正确
  3. 是否能做最小 smoke test
- 业务按钮、状态变量、事件名不应该作为通用自检的核心条件

### 10. 长期应该补浏览器级 smoke test

当前现状：

- 现在已经有 `node --check`
- 纯网页项目已经补上 `Playwright` headless smoke

建议：

- 把浏览器 smoke 保持在“通用技术检查”层
- 业务功能验证不要继续硬编码在 `self-check`

### 10.1 `TEST` 需要 testcase 设计与执行分层

问题：

- 只做 `self-check`，最多证明“代码没明显坏”
- 不能证明“功能真的可用”
- 真实网页项目里，语法通过和能运行是两回事

经验：

- `TEST` 至少要拆成：
  - `self-check`
  - `test case design`
  - `test execution`
- 只有必测 testcase 全通过，`TEST` 才能判定通过

当前已落地：

- `TEST` 阶段会生成 `test_cases.md`
- `TEST` 阶段会生成 `test_execution.md`
- 网页项目优先使用 `Playwright` 执行结构化 testcase

## 已确认的可观测性问题

### 11. 不能只记录最后一条 review

问题：

- 之前 `.md` review 文件每次都会被覆盖
- 最终只能看到最后一轮 review
- 无法判断：
  - reviewer 是否一直在重复同一个问题
  - 修订方向是否在收敛
  - human 是在哪一轮批准/拒绝的

经验：

- review 必须有“最新态”和“历史态”两份产物

当前已落地：

- 最新 review：
  - `analysis_review.md`
  - `implementation_review.md`
  - 等
- review 历史：
  - `analysis_review_history.md`
  - `implementation_review_history.md`
  - `code_review_review_history.md`
  - 等
- CLI 已支持：
  - `run show <runId> <stage> --review --history`

## 工具与入口经验

### 12. Maven 入口不能依赖当前工作目录

问题：

- 从错误目录直接执行 `mvn spring-boot:run`
- 会拿错 `pom.xml`
- 表现成：
  - `No plugin found for prefix 'spring-boot'`

经验：

- CLI 项目应该提供稳定入口脚本
- 不应该要求用户永远记得切目录

当前已落地：

- 新增 `forge.sh`
- 会先切到仓库根目录再调用 Maven

## 当前仍然存在的局限

### 13. `PATCH` 现在仍然复用 `IMPLEMENTATION`

现状：

- `fixMode=PATCH` 已经接通
- 但实现上仍然是“回到 implementation，再以 patch prompt 运行”

问题：

- 这已经比整轮重写好
- 但还不够彻底

下一步建议：

- 增加显式的 `FIX_IMPLEMENTATION` 或等价内部模式
- 把“首次实现”和“补丁修复”分开统计和观察

### 14. 修订预算还比较粗

现状：

- `maxAutoRevisions = 5`
- 目前更多是整阶段级别控制

问题：

- 首次实现、code review 修补、test 修补 共享同一套预算仍然偏粗

下一步建议：

- 分成：
  - implementation 初次预算
  - patch 预算
  - rework 预算

### 15. verifier 仍有主观性

现状：

- verifier 现在已经比最初稳
- 但仍然依赖模型主观判断

下一步建议：

- 增加更强的结构化 verifier 输入
- 尽量把“可客观判断”的部分交给工具
- 把模型判断留给：
  - 业务完整性
  - 结构合理性
  - 风险识别

### 16. 非空输出不等于可用输出

现状：

- 只要模型返回非空文本，过去就可能直接落盘

问题：

- 半截文件、未闭合 HTML、不可解析脚本都会被当成“成功生成”

当前修复：

- 代码生成会在写盘前做完整性校验
- 对 HTML 检查基本结构闭合
- 对 JS 和内联脚本做语法校验
- 不完整内容会重试，最终仍失败则直接报错

### 17. required testcase 未执行不能算通过

现状：

- 过去如果没有专用 testcase 执行器，系统会把 testcase 记成通过

问题：

- 会产生 `test_report=APPROVED` 但产品实际不可用的假阳性

当前修复：

- required case 未执行时不再标记为 passed
- 对单个 `index.html + inline script` 的网页项目，也会识别成 `web-static`

### 18. 阶段状态必须先落盘再执行

现状：

- 过去是先生成 artifact，再更新 `run.json`

问题：

- 外部看起来像卡在上一个阶段

当前修复：

- 进入阶段时先写入 `RUNNING + attempt`
- 再执行 artifact 生成
- 即使中途失败，当前阶段状态也可见

## 建议的后续迭代顺序

1. 将完整文件输出继续演进到 patch/section 级写入
2. 将 `PATCH` 模式进一步做成独立内部阶段
3. 拆分修订预算
4. 增强 verifier 的结构化输入
5. 再考虑更复杂的代码合并和并行子任务

## 结论

今天最重要的经验不是“换更大的模型”，而是：

- 复杂任务要分步做
- reviewer 结果要分类
- `self-check` 要收敛到通用技术规则
- 历史信息必须保留
- 所有失败都打回完整 `IMPLEMENTATION` 是错误流程

这些点比单纯堆模型能力更影响系统质量。
