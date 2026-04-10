# Forge

`Forge` 是一个面向研发流程的编程 agent 内核，目标是把下面这条链路做成可追溯、可审阅、可打回重做的工作流：

- 需求分析与调研
- 产品需求文档
- 技术方案设计
- 代码实现
- Code Review
- 测试

当前仓库已经进入第一版可运行阶段，重点是把长期演进需要的 Java 内核边界立起来：

- 模块化单体
- 显式工作流状态机
- `SupervisorAgent` 主流程决策层
- 节点级 review gate
- 文档产物落盘
- 可插拔模型与执行器
- 文档阶段固定模板规范

当前工程原则补充：

- 结构化代码编辑默认选择 `tree-sitter`
- 复杂底层能力先做 build-vs-buy 判断，再决定是否自研
- HTML / DOM 修改优先使用成熟现成工具，而不是强行统一手搓
- 人读文档默认跟随用户需求语言：
  - `goal / constraints / note` 以中文为主时，输出中文文档
  - 以英文为主时，输出英文文档
  - `Contract Metadata` 标题与键名保持稳定英文，便于机器读取
- 文档 prompt 与 reviewer 统一按“信息来源 / 约束升级”管理，不再针对具体场景补关键词规则
- 当前代码与业务状态总览以 `docs/current-state.md` 为准
- 文档索引以 `docs/README.md` 为准
- 根约定与工程约定分别维护在：
  - `AGENTS.md`
  - `docs/engineering-agreements.md`

## 文档入口

- `docs/README.md`
  当前文档索引
- `docs/current-state.md`
  当前代码与业务状态总览
- `docs/workflow-rules.md`
  当前工作流与 gate 规则
- `docs/redesign-roadmap.md`
  后续演进路线图
- `docs/engineering-agreements.md`
  工程约定

## 当前结构
- `src/main/java/devflow/agent/orchestrator`
  工作流与状态机接口、核心枚举
- `src/main/java/devflow/agent/supervisor`
  主流程决策 agent、决策动作和结构化决策模型
- `src/main/java/devflow/agent/loop`
  `AgentLoop`、transition decision 与 transition reason
- `src/main/java/devflow/agent/context`
  上下文投影、任务记忆、失败摘要与结构化 contract 抽取
- `src/main/java/devflow/agent/review`
  review 决策模型
- `src/main/java/devflow/agent/artifact`
  产物存储抽象
- `src/main/java/devflow/agent/executor`
  模型与执行器抽象
- `src/main/java/devflow/agent/parsing`
  `tree-sitter` 解析、结构快照与写盘前结构校验
- `src/main/java/devflow/agent/editing`
  HTML 锚点区块改写与 Java/Python/Go 符号级精确改写
- `src/main/java/devflow/agent/project`
  仓库工作区抽象
- `src/main/java/devflow/agent/interfaceadapter`
  CLI / API 入口预留

## 当前工作流

当前工作流、gate 规则、阶段行为和 artifact 约束已经统一整理到：

- `docs/current-state.md`
- `docs/workflow-rules.md`

这里不再重复维护一份“现状说明”，避免 README 和规则文档继续双写。

## 构建

目标运行时：

- Java 21
- Maven
- Spring Boot

本机已验证通过：

```bash
mvn test
```

最省事的启动方式：

```bash
cd /home/linus/workspace/forge
./forge.sh run autopilot --project /path/to/repo --goal '你的目标' --constraints '你的约束'
```

`forge.sh` 会自动切到仓库根目录再执行 Maven，避免在别的目录里触发 `No plugin found for prefix 'spring-boot'`。

可选模型配置：

```bash
mvn -q spring-boot:run \
  -Dspring-boot.run.arguments='run start <runId> --project /path/to/repo --devflow.ollama.model=qwen2.5-coder:14b'
```

阶段级模型配置也已经支持，推荐这样配：

```bash
/home/linus/workspace/forge/forge.sh run autopilot \
  --project /path/to/repo \
  --goal '你的目标' \
  --constraints '你的约束' \
  --reviewer autopilot \
  --devflow.ollama.model=qwen2.5-coder:14b \
  --devflow.ollama.models.analysis=qwen3.5:27b \
  --devflow.ollama.models.prd=qwen3.5:27b \
  --devflow.ollama.models.design=qwen3.5:27b \
  --devflow.ollama.models.implementation=qwen3-coder:30b \
  --devflow.ollama.models.code-review=qwen3-coder:30b \
  --devflow.ollama.models.diagnosis=qwen3.5:27b \
  --devflow.ollama.models.repair=qwen3-coder:30b \
  --devflow.ollama.models.supervisor=qwen3.5:27b
```

说明：

- `devflow.ollama.model`
  - 全局默认模型
- `devflow.ollama.models.*`
  - 阶段或能力级覆盖
  - 目前支持：
    - `analysis`
    - `prd`
    - `design`
    - `implementation`
    - `code-review`
    - `test`
    - `test-case-design`
    - `validation-strategy`
    - `diagnosis`
    - `repair`
    - `supervisor`

预算配置也支持通过参数覆盖：

```bash
/home/linus/workspace/forge/forge.sh run autopilot \
  --project /path/to/repo \
  --goal '你的目标' \
  --constraints '你的约束' \
  --reviewer autopilot \
  --devflow.ollama.models.implementation=qwen3-coder:30b \
  --devflow.ollama.budget.models.qwen3-coder.context-window-tokens=36864 \
  --devflow.ollama.budget.models.qwen3-coder.safe-output-ratio=0.75 \
  --devflow.ollama.budget.models.qwen3-coder.reserve-ratio=0.05 \
  --devflow.ollama.budget.models.qwen3-coder.minimum-reserve-tokens=1200 \
  --devflow.ollama.budget.models.qwen3-coder.maximum-reserve-tokens=4096
```

说明：

- `context-window-tokens`
  - Forge 用来裁剪 prompt、预算和 `num_ctx`
- `safe-output-ratio`
  - Forge 会按 `safeOutputCeiling = clamp(availableOutput * safeOutputRatio, minimumOutputTokens, availableOutput)` 计算动态安全上限
- `reserve-ratio / minimum-reserve-tokens / maximum-reserve-tokens`
  - Forge 会按 `clamp(num_ctx * reserveRatio, minReserve, maxReserve)` 计算动态预留
  - 不再对 `32k/64k/128k` 一律使用同一个固定预留值
- 这些配置是 Forge 的调用预算，不等于模型理论最大值
- 大生成任务默认走动态预算请求：
  - `availableOutput = num_ctx - promptTokens - reserveTokens`
  - `safeOutputCeiling = clamp(availableOutput * safeOutputRatio, minimumOutputTokens, availableOutput)`
  - `effectiveOutput = min(requestedOutput, safeOutputCeiling)`
  - 文档整稿、implementation planning、precise-html、precise-code、inline patch 不再先写死一个固定小 `num_predict`
- 如需微调任务侧预算，优先覆写输出比例，例如：
  - `-Ddevflow.generation-budget.precise-html-output-ratio=1.0`
  - `-Ddevflow.generation-budget.document-patch-output-ratio=0.6`

## 当前 CLI

初始化工作区：

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments='init --project /path/to/repo'
```

创建 run：

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments='run create --project /path/to/repo --goal 建立 最小 可运行 的 Java 内核 --constraints 第一版 先 跑通 工作流'
```

一条命令创建并启动到下一个 gate：

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments='run bootstrap --project /path/to/repo --goal 建立 最小 可运行 的 Java 内核 --constraints 第一版 先 跑通 工作流'
```

一条命令自动跑到底：

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments='run autopilot --project /path/to/repo --goal 建立 最小 可运行 的 Java 内核 --constraints 第一版 先 跑通 工作流 --reviewer autopilot --devflow.ollama.model=qwen2.5-coder:14b'
```

启动 run：

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments='run start <runId> --project /path/to/repo'
```

查看状态：

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments='run status <runId> --project /path/to/repo'
```

批准或打回某阶段：

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments='run approve <runId> ANALYSIS --reviewer linus --project /path/to/repo'
mvn -q spring-boot:run -Dspring-boot.run.arguments='run reject <runId> PRD --reviewer linus --reason 需求 还不完整 --project /path/to/repo'
```

查看阶段产物：

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments='run show <runId> ANALYSIS --project /path/to/repo'
mvn -q spring-boot:run -Dspring-boot.run.arguments='run show <runId> ANALYSIS --review --project /path/to/repo'
mvn -q spring-boot:run -Dspring-boot.run.arguments='run show <runId> ANALYSIS --review --history --project /path/to/repo'
```

查看事件日志：

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments='run logs <runId> --project /path/to/repo'
```

## 当前已实现能力

- `.devflow/runs/<runId>/run.json` 文件持久化
- `.devflow/runs/<runId>/baseline/` 工作区基线快照
- 阶段产物与 review 产物落盘
- 每个阶段的 review 历史落盘，保留每一轮 reviewer / human 审批结果
- `events.log` 事件日志
- `init / run create / run start / run status / run approve / run reject / run show / run resume / run logs`
- `run bootstrap` 一条命令完成 `create + start`
- `run autopilot` 一条命令完成 `create + start + 自动批准所有人工 gate`
- 真实 `Ollama` provider
- 支持按阶段/能力选择不同模型
- 自动 reviewer 与人工签核
- implementation 阶段内置子任务 planner，先把大功能拆成 3-6 个可验证的子步骤
- implementation 阶段按子任务逐步写代码，每个子任务会做自检和 verifier 校验
- implementation 阶段内置自测，优先使用项目自身构建/测试工具链，没有工具链时退回通用静态 Web 检查
- implementation 阶段在接受模型生成内容前，会先用 `tree-sitter` 校验 `HTML / JavaScript / TypeScript / Java / Python / Go` 的基本结构合法性
- `WRITE` 已改成事务式候选写入：先 stage 候选文件，再校验，最后 commit；失败会保留调试 artifact
- fallback testcase 设计已优先基于 `tree-sitter` 提取静态 HTML 结构，而不是只靠正则猜测按钮和选择器
- `ANALYSIS / PRD / DESIGN` 已改成固定模板输出，降低文档漂移和阶段间理解偏差
- code review 阶段会输出 `fixMode`
- code review / test 打回后会把 `PATCH` 或 `REWORK` 明确传回 implementation
- 连续相同失败会触发 diagnosis，并生成 `repair_brief.md` 再交给 repair 路径
- `SupervisorAgent` 会把 review 结果、history、`repair_brief` 汇总成结构化流程决策，并额外落盘 `supervisor_decision.md`
- `REQUEST_HUMAN_REVIEW` 只有当前阶段 gate 为 `AGENT_PLUS_HUMAN` 时才允许生效，不会再把 `AGENT_ONLY` 阶段错误阻塞到人工
- 测试层已拆出 `ProjectInspector / ValidationStrategyPlanner / ValidationExecutor`
- TEST 阶段现在会额外生成 `test_cases.md` 和 `test_execution.md`
- 网页项目会先做浏览器级 `Playwright` smoke，再按结构化 testcase 执行关键用例
- 运行时快照、Playwright smoke 和 testcase 默认入口都会复用 `ProjectInspector` 探测到的真实 HTML entry，而不是写死 `index.html`
- 若 `DESIGN` 定义了性能验收要求，网页项目的 TEST case 还支持：
  - `MEASURE_PAGE_LOAD_MAX_MS`
  - `ASSERT_WINDOW_METRIC_MAX_MS`
- `ASSERT_WINDOW_METRIC_MAX_MS` 依赖实现通过 `window.__devflowMetrics` 暴露运行时指标
- implementation 阶段的代码写入执行器
- code review 阶段的变更审阅
- test 阶段的命令执行器
- implementation 阶段已引入架构师/worker 风格的交接 artifact：
  - `implementation_shared_context.md`
  - `task_packages.md`
  - `worker_results.md`

当前 `tree-sitter` 已落地的作用范围：

- `HTML`
  - 写盘前结构校验
  - 静态 `id/button/canvas` 结构提取
  - 稳定锚点页面的区块级精确改写
- `JavaScript`
  - 写盘前语法树合法性校验
- `TypeScript`
  - 写盘前语法树合法性校验
- `Java`
  - 写盘前基础结构校验
- `Python`
  - 写盘前基础结构校验
- `Go`
  - 写盘前基础结构校验
- `Java / Python / Go`
  - 符号级精确改写支持
  - 当前支持动作：
    - `REPLACE_SYMBOL`
    - `REPLACE_SYMBOL_BODY`
    - `INSERT_INTO_SYMBOL`
    - `APPEND_FILE`
- `JavaScript / TypeScript`
  - 符号级精确改写支持
  - 当前支持动作：
    - `REPLACE_SYMBOL`
    - `REPLACE_SYMBOL_BODY`
    - `INSERT_INTO_SYMBOL`
    - `APPEND_FILE`

当前边界：

- 还没有进入通用 AST refactor 级别改写
- 当前仍然是“受控 patch/section/symbol 写入”，不是任意语义重构
- 更复杂的跨文件重构仍应继续建立在这层解析能力之上

当前这版新增的精确改写能力：

- 对已有 HTML 页面，若已存在稳定锚点：
  - `<main id="app-root">`
  - `<style id="app-style">`
  - `<script id="app-script">`
- `IMPLEMENTATION` 在 `INCREMENTAL / PATCH` 模式下会优先做区块级精确改写，而不是整页重写
- 入口文件如果需要补充外部资源或额外结构片段，也可以通过 `headAppendHtml / bodyAppendHtml` 追加 `<script src>`、`<link rel=\"stylesheet\">` 或挂载节点，而不必退回整页重写
- 现有 HTML 入口在 `INCREMENTAL / PATCH` 下不再允许静默退回 `whole-file`
- `OUTPUT_TRUNCATED` 不再默认关闭 `preferPreciseEditing`；恢复链会优先保持局部编辑并继续收缩 `EditUnit`
- 单入口内联脚本不再被当成一个 `inline-unit-all` 大块处理，而是先 `append-only` 追加辅助符号，再让入口符号负责编排
- 对已有 `JavaScript / TypeScript / Java / Python / Go` 文件，若 `tree-sitter` 能稳定提取符号：
  - `class / interface / enum / record / constructor / method`
  - `class / function`
  - `type / method / function`
  - `class / method / function / variable`
- `IMPLEMENTATION` 在 `INCREMENTAL / PATCH` 模式下会优先输出符号级 JSON patch，而不是整文件重写
- 如果只是补齐现有函数、方法或类体内部逻辑，优先使用 `REPLACE_SYMBOL_BODY`
  - 这样可以避免为了补一个函数体而重写整段导出声明
  - 也能降低长符号整段重写时的截断风险
- 对新建空代码文件，不再一次尝试吐完整模块
  - 会先通过 `APPEND_FILE` 建立最小可解析骨架
  - 骨架成功后，再基于新符号重新规划后续 `EditUnit`
  - 后续实现继续按符号局部补齐

这版边界：

- HTML 精确改写只对带稳定锚点的页面启用
- JavaScript/TypeScript/Java/Python/Go 精确改写对已有可解析符号的文件默认启用；空文件会先建立骨架再进入符号级改写
- 不满足精确改写条件时仍会回退到完整文件生成
- 事务写入失败时，会在 `.devflow/write-transactions/failed/` 保留候选内容、旧文件快照和失败原因
- 精确 patch 连续失败时，不会直接抛 fatal 中断 run；Forge 会先把失败原因结构化，再交给 supervisor 决定是否继续保持局部编辑、缩小改单范围或停止当前子任务
- 只在 `PATCH / INCREMENTAL` 模式启用
- 仍不支持任意 AST 级重构或自动移动跨文件依赖

## 当前自动模式说明

当前已经支持“自动跑到人工 gate 为止”的模式：

- `run start`
- `run resume`
- `run bootstrap`

这几个命令都会自动执行后续阶段，直到：

- 碰到 `AGENT_PLUS_HUMAN` 阶段并进入 `AWAITING_HUMAN_REVIEW`
- 或阶段被打回重试
- 或 run 完成/失败

如果你需要彻底无人值守，使用下面任意一种：

- `run autopilot`
- `run bootstrap --auto-approve`
- `run resume <runId> --auto-approve`

当前仍未实现：

- REST API / Web UI
- 多 provider 路由
- 多 worker / 分布式执行
- 更强的仓库上下文筛选与 patch 策略

## 编辑基础设施方向

当前实现阶段仍以“完整文件输出 + 严格验证”为主，但后续不再继续扩展自研的低层文本锚点替换能力。

默认演进优先级：

1. 优先接现成专用工具
2. 结构化代码编辑默认走 `tree-sitter`
3. HTML / DOM 修改优先走成熟 DOM 工具
4. 通用文本文件才退回 diff / patch 或小范围文本修改

详细说明见：

- `docs/editing-strategy.md`

## IMPLEMENTATION 阶段当前策略

当前实现阶段不是“一次性把全部代码吐完”，而是下面这个内部闭环：

1. 先根据 `ANALYSIS / PRD / DESIGN` 输出 implementation plan
2. 把大任务拆成 3-6 个可验证子任务
3. 每个子任务最多改 `2` 个文件
4. 对复杂前端/网页/游戏任务，优先走 `SKELETON -> INCREMENTAL -> PATCH/REWORK`
5. 逐个子任务生成代码
6. 每个子任务执行后先跑自检
7. 自检通过后再做子任务 verifier
8. 子任务失败时优先在 implementation 内部重试
9. `CODE_REVIEW` 如果给出 `PATCH`，后续实现会按最小补丁策略修复
10. `CODE_REVIEW` 如果给出 `REWORK`，后续实现允许较大范围结构调整
11. 所有子任务跑完后，才进入后续 `CODE_REVIEW / TEST`

这样做的目标是减少“整体实现一次失败后整段打回”的浪费，让复杂任务更接近工程上的逐步交付。

## TEST / 自检 当前策略

当前测试层采用混合模式：

1. `ProjectInspector`
   - 自动识别技术栈、包管理器、项目类型
2. `ValidationStrategyPlanner`
   - 基于项目指纹和受控 capability 列表规划自检步骤
   - 模型只能在系统支持的能力中做选择
3. `ValidationExecutor`
   - 执行真正的白名单工具能力

当前已支持的通用 capability 包括：

- `mvn/gradle` 测试
- `npm/pnpm/yarn` 的 `build/test`
- 静态网页本地资源引用检查
- JavaScript 与 HTML 内联脚本的 `node --check`
- 静态网页的 `Playwright` headless smoke

这样做的目标是：

- 不把某个业务页面的特殊规则硬编码进 `self-check`
- 同时又不把工具选择完全交给模型自由发挥

### TEST 阶段当前闭环

当前 `TEST` 阶段已经不是单一的“自检报告”，而是：

1. `self-check`
   - 先确认构建、语法、资源和页面基础可启动性
2. `test case design`
   - 根据 `goal / PRD / DESIGN / IMPLEMENTATION` 生成结构化 testcase
   - testcase 会落到 `test_cases.md`
3. `test execution`
   - 网页项目优先使用 `Playwright` 执行 testcase
   - 执行记录落到 `test_execution.md`
4. `test report`
   - 只有 `self-check` 通过且必测 testcase 全通过，`TEST` 才会判 `APPROVED`
