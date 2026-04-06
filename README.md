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

## 当前结构

- `docs/architecture-plan.md`
  第一版架构方案归档
- `docs/editing-strategy.md`
  结构化编辑与“优先现成方案”原则
- `src/main/java/devflow/agent/orchestrator`
  工作流与状态机接口、核心枚举
- `src/main/java/devflow/agent/supervisor`
  主流程决策 agent、决策动作和结构化决策模型
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

当前工作流已经覆盖：

1. `ANALYSIS`
2. `PRD`
3. `DESIGN`
4. `IMPLEMENTATION`
5. `CODE_REVIEW`
6. `TEST`

主链路仍然按上面的阶段顺序组织，但当前已经不是纯固定状态机直推，而是：

- `DefaultWorkflowEngine`
  - 负责执行、持久化、事件记录、边界约束
- `SupervisorAgent`
  - 负责决定下一步动作
- 各阶段 worker
  - 负责生成产物、review、diagnosis、repair、test

默认 gate 策略：

- `ANALYSIS / PRD / DESIGN / CODE_REVIEW`：`AGENT_PLUS_HUMAN`
- `IMPLEMENTATION / TEST`：`AGENT_ONLY`

行为说明：

- 进入阶段后会自动生成产物
- 生成后会自动触发 reviewer
- `SupervisorAgent` 会基于当前 artifact、review 结果、history 和 `repair_brief` 决定下一步动作
- `AGENT_ONLY` 阶段通过后会自动进入下一阶段
- `AGENT_PLUS_HUMAN` 阶段通过后会停在 `AWAITING_HUMAN_REVIEW`
- `IMPLEMENTATION` 会先拆成多个子任务，再逐个子任务做代码生成、自检和验证
- `IMPLEMENTATION` 只有在子任务级验证通过后才会继续推进，最后还会再做阶段级自测
- `CODE_REVIEW` reviewer 必须明确给出 `fixMode = PATCH | REWORK`
- `PATCH` 表示走增量修补，`REWORK` 表示允许较大范围重构
- `self-check` 已切成“项目识别 -> 策略规划 -> 白名单能力执行”的通用技术规则
- 被拒绝后会根据阶段自动打回并重试，超过最大自动修订次数后失败
- 当前总自动修订次数上限为 `5`

`SupervisorAgent` 当前可输出的动作：

- `ADVANCE_STAGE`
- `REQUEST_HUMAN_REVIEW`
- `RETRY_STAGE`
- `ROUTE_TO_REPAIR`
- `ROLLBACK_STAGE`
- `COMPLETE_RUN`
- `FAIL_RUN`

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
- implementation 阶段在接受模型生成内容前，会先用 `tree-sitter` 校验 `HTML / JavaScript / Java / Python / Go` 的基本结构合法性
- fallback testcase 设计已优先基于 `tree-sitter` 提取静态 HTML 结构，而不是只靠正则猜测按钮和选择器
- `ANALYSIS / PRD / DESIGN` 已改成固定模板输出，降低文档漂移和阶段间理解偏差
- code review 阶段会输出 `fixMode`
- code review / test 打回后会把 `PATCH` 或 `REWORK` 明确传回 implementation
- 连续相同失败会触发 diagnosis，并生成 `repair_brief.md` 再交给 repair 路径
- `SupervisorAgent` 会把 review 结果、history、`repair_brief` 汇总成结构化流程决策，并额外落盘 `supervisor_decision.md`
- 测试层已拆出 `ProjectInspector / ValidationStrategyPlanner / ValidationExecutor`
- TEST 阶段现在会额外生成 `test_cases.md` 和 `test_execution.md`
- 网页项目会先做浏览器级 `Playwright` smoke，再按结构化 testcase 执行关键用例
- 若 `DESIGN` 定义了性能验收要求，网页项目的 TEST case 还支持：
  - `MEASURE_PAGE_LOAD_MAX_MS`
  - `ASSERT_WINDOW_METRIC_MAX_MS`
- `ASSERT_WINDOW_METRIC_MAX_MS` 依赖实现通过 `window.__devflowMetrics` 暴露运行时指标
- implementation 阶段的代码写入执行器
- code review 阶段的变更审阅
- test 阶段的命令执行器

当前 `tree-sitter` 已落地的作用范围：

- `HTML`
  - 写盘前结构校验
  - 静态 `id/button/canvas` 结构提取
  - 稳定锚点页面的区块级精确改写
- `JavaScript`
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
- 对已有 `Java / Python / Go` 文件，若 `tree-sitter` 能稳定提取符号：
  - `class / interface / enum / record / constructor / method`
  - `class / function`
  - `type / method / function`
- `IMPLEMENTATION` 在 `INCREMENTAL / PATCH` 模式下会优先输出符号级 JSON patch，而不是整文件重写

这版边界：

- HTML 精确改写只对带稳定锚点的页面启用
- Java/Python/Go 精确改写只对已有可解析符号的文件启用
- 不满足精确改写条件时仍会回退到完整文件生成
- Java/Python/Go 只对已有文件启用
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
