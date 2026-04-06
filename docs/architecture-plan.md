# Java 内核版编程 Agent 方案

## 总结

第一版按长期系统思路实现为一个 Java 模块化单体内核，而不是一次性脚本工具。它负责完整编排这条研发流程：

`需求分析/调研 -> PRD -> 技术方案 -> 代码实现 -> Code Review -> 测试`

系统以任务运行单元 `Run` 为核心，采用“显式状态机 + 主流程决策 agent”推进。每个阶段都有：

- 输入上下文
- 产物文档或结果
- reviewer 审核
- 可配置 gate
- 驳回后修订重试

其中 `IMPLEMENTATION` 阶段内部还会继续拆成：

- implementation plan
- subtask execution
- subtask self-check
- subtask verifier
- implementation stage self-check

第一版先做：

- Java 服务内核
- 本地 CLI 入口
- 文档目录落盘
- Ollama 模型后端
- 节点级 gate 配置

## 核心架构

### 总体形态

采用模块化单体，但边界按未来服务化拆分设计。

核心模块：

- `orchestrator`
  工作流状态机、阶段推进、回退重试、决策执行
- `supervisor`
  主流程控制 agent，负责决定下一步动作
- `review`
  reviewer agent、审核记录、gate 判定
- `artifact`
  文档模板、产物写入、版本记录
- `executor`
  模型调用、命令执行、后续 worker 抽象
- `validation`
  项目识别、验证策略规划、白名单自检能力执行
- `interface`
  CLI、后续 REST API 入口
- `project`
  仓库上下文、文件扫描、代码改动范围管理

### 代码修改基础设施

后续 `project / executor` 模块的演进遵循两条原则：

- 结构化代码编辑默认选择 `tree-sitter`
- 复杂底层能力优先采用现成成熟方案，而不是默认手搓

长期方向：

1. 优先接现成专用编辑能力
   - HTML / DOM 修改优先成熟 DOM 工具
   - JS / TS 高层改写优先成熟 codemod 工具
2. 多语言结构化代码范围定位默认走 `tree-sitter`
3. 通用文本修改才退回 diff / patch
4. 整文件重写只作为最后兜底

不再作为主线继续演进的方向：

- 依赖唯一文本锚点的精确替换器
- 大量字符串前插/后插
- 先整文件重写，再依赖 reviewer 补救

当前第一阶段已落地：

- `parsing` 层已基于 `tree-sitter` 接入
- 当前支持：
  - HTML
  - JavaScript
  - Java
  - Python
  - Go
- 当前用途：
  - 生成内容写盘前结构验收
  - 静态 HTML 结构快照提取
  - 为 testcase fallback 提供真实 DOM 线索
  - 为 HTML 区块级改写和 Java/Python/Go 符号级 patch 提供结构范围

当前仍未进入：

- 通用 AST refactor
- 跨文件语义级自动改写

### 角色与执行模型

采用多角色逻辑加单编排内核。

角色：

- `SupervisorAgent`
- `Analyst`
- `ProductWriter`
- `Architect`
- `Implementer`
- `DiagnosisAgent`
- `RepairAgent`
- `Reviewer`
- `Tester`

实现方式：

- Java 内核负责执行与约束
- `SupervisorAgent` 负责流程调度决策
- 每个角色有独立 prompt/template
- 所有角色先共用同一个 LLM provider 抽象
- 第一版默认 provider 为 `Ollama`

## 工作流与状态机

### Supervisor 决策层

当前主阶段顺序保持不变，但阶段之间的推进、重试、repair 或回退，不再完全由硬编码规则直接决定，而是由 `SupervisorAgent` 输出结构化决策，再由 `WorkflowEngine` 执行。

职责分工：

- `WorkflowEngine`
  - 执行阶段
  - 落盘 artifact / review / history / events
  - 保证 action、stage、重试预算等边界合法
- `SupervisorAgent`
  - 读取当前 run 摘要、artifact、review history、`repair_brief`
  - 决定下一步动作
- `Worker Agents`
  - 负责具体阶段产出，不负责全局流程决策

`SupervisorAgent` 当前输出动作：

- `ADVANCE_STAGE`
- `REQUEST_HUMAN_REVIEW`
- `RETRY_STAGE`
- `ROUTE_TO_REPAIR`
- `ROLLBACK_STAGE`
- `COMPLETE_RUN`
- `FAIL_RUN`

推荐输出字段：

- `action`
- `targetStage`
- `mode`
- `reason`
- `focus`
- `constraints`
- `humanRequired`

### 阶段定义

- `ANALYSIS`
- `PRD`
- `DESIGN`
- `IMPLEMENTATION`
- `CODE_REVIEW`
- `TEST`

### 阶段状态

每阶段状态：

- `PENDING`
- `RUNNING`
- `NEEDS_REVISION`
- `AWAITING_HUMAN_REVIEW`
- `APPROVED`
- `FAILED`
- `SKIPPED`

`Run` 总体状态：

- `CREATED`
- `IN_PROGRESS`
- `BLOCKED`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

### Gate 策略

每个阶段独立配置 gate：

- `AGENT_ONLY`
- `AGENT_PLUS_HUMAN`
- `SKIP`

默认建议：

- `ANALYSIS/PRD/DESIGN/CODE_REVIEW` 使用 `AGENT_PLUS_HUMAN`
- `IMPLEMENTATION/TEST` 使用 `AGENT_ONLY`

### 打回与修订

驳回来源：

- reviewer agent
- human reviewer
- test failure

驳回规则：

- `ANALYSIS/PRD/DESIGN` 被拒绝：回到当前阶段重写
- `IMPLEMENTATION` 被拒绝：回到 `IMPLEMENTATION`
- `CODE_REVIEW/TEST` 被拒绝：进入 `IMPLEMENTATION`，但 reviewer 必须给出 `fixMode`
- 每次打回都记录驳回人、驳回原因、修订要求和修订轮次

默认最大自动修订次数：`5`

说明：

- 固定状态机仍提供安全边界
- `SupervisorAgent` 不允许自由跳过所有阶段或执行任意工具
- 第一版只负责流程调度决策，不直接写代码

### Review 结论分级

为了避免所有打回都退化成“大改重写”，review 结果除了 `decision` 之外，还必须包含：

- `fixMode = NONE | PATCH | REWORK`

约定：

- `NONE`
  仅用于 `APPROVED`
- `PATCH`
  表示结构基本可接受，只做局部修补
- `REWORK`
  表示结构存在明显问题，允许较大范围重构

应用规则：

- `CODE_REVIEW + PATCH`
  进入 implementation 的增量修补模式
- `CODE_REVIEW + REWORK`
  进入 implementation 的重构模式
- `TEST + PATCH`
  默认进入 implementation 的增量修补模式
- `TEST + REWORK`
  仅在测试暴露出结构性问题时使用

### IMPLEMENTATION 内部执行模型

`IMPLEMENTATION` 阶段不是一次性整体生成代码，而是使用“规划 + 分步执行 + 分步验证”的内部闭环。

建议顺序：

1. 根据 `ANALYSIS / PRD / DESIGN` 先产出 implementation plan
2. 将实现目标拆为 3 到 6 个可验证子任务
3. 对每个子任务逐个生成代码
4. 每个子任务执行后先做 self-check
5. self-check 通过后，再做子任务 verifier
6. 子任务失败时优先在 implementation 内部重试
7. 如果外部 review 返回 `PATCH`，implementation 优先做最小补丁修复
8. 如果外部 review 返回 `REWORK`，implementation 允许重新规划并调整结构
9. 所有子任务完成后，再进入 `CODE_REVIEW`

这样可以减少 reviewer 反复拒绝“低级未完成实现”的次数，让复杂功能更接近逐步交付。

### 连续失败后的诊断与修复升级

当 `IMPLEMENTATION`、`CODE_REVIEW` 或 `TEST` 连续多轮围绕同一问题反复失败时，不应继续让原实现器盲目重试，而应升级到“诊断 + 修复”模式。

触发条件建议：

- 同一类 `changeRequest` 连续出现 `2-3` 次
- 同一类 `self-check/test` 错误连续出现 `2-3` 次
- 同一文件被重复修改但阻塞问题没有收敛
- `PATCH` 多轮后没有实质进展

升级后新增两个逻辑角色：

- `DiagnosisAgent`
  - 汇总最近几轮失败轨迹
  - 判断当前失败是 `PATCH` 型还是 `REWORK` 型
  - 提炼根因、证据、受影响文件和修复边界
- `RepairAgent`
  - 基于诊断结果做定点修复
  - 目标是最小必要修改，而不是重做整个功能

### repair brief 交接物

为了让 `RepairAgent` 不再重新消费大量噪音上下文，诊断阶段必须产出结构化交接物，例如：

- `.devflow/runs/<runId>/repair_brief.md`
- 或 `.devflow/runs/<runId>/repair_brief.json`

建议字段：

- `stage`
- `failureCluster`
- `symptoms`
- `repeatedErrors`
- `rootCauseHypothesis`
- `affectedFiles`
- `evidence`
- `recommendedMode`
  - `PATCH`
  - `REWORK`
- `doNotChange`
- `acceptanceTarget`

交接原则：

- `DiagnosisAgent` 输出的是“问题摘要 + 修复边界”
- `RepairAgent` 只消费高价值摘要、相关文件和必要的 PRD/DESIGN 摘要
- 不再把完整历史全文无差别喂给修复模型

### 状态机扩展建议

现有主阶段保持不变，但在内部编排上增加升级路径：

1. `IMPLEMENTATION`
2. `CODE_REVIEW / TEST`
3. 若连续失败达到阈值：
   - `DIAGNOSIS`
   - `REPAIR_IMPLEMENTATION`
4. 再回到：
   - `self-check`
   - `verifier`
   - `CODE_REVIEW / TEST`

状态机原则：

- `PATCH` 优先进入 `REPAIR_IMPLEMENTATION`
- `REWORK` 优先回完整 `IMPLEMENTATION`
- 诊断阶段的职责是避免在错误方向上继续烧掉预算

## 关键接口与数据模型

### 核心对象

- `Run`
  - `runId`
  - `projectPath`
  - `goal`
  - `constraints`
  - `currentStage`
  - `runStatus`
  - `stageStates`
- `StageExecution`
  - `stageType`
  - `status`
  - `attempt`
  - `artifactRefs`
  - `reviewDecision`
  - `reviewNotes`
- `ImplementationPlan`
  - `summary`
  - `subtasks`
- `ImplementationSubtask`
  - `title`
  - `goal`
  - `acceptanceCriteria`
  - `changes`
- `ReviewDecision`
  - `APPROVED`
  - `REVISION_REQUIRED`
  - `REJECTED`
- `RepairBrief`
  - `stage`
  - `failureCluster`
  - `repeatedErrors`
  - `rootCauseHypothesis`
  - `affectedFiles`
  - `evidence`
  - `recommendedMode`
  - `doNotChange`
  - `acceptanceTarget`
- `GatePolicy`
  - `AGENT_ONLY`
  - `AGENT_PLUS_HUMAN`
  - `SKIP`

### 执行层抽象

- `LlmProvider`
- `ArtifactStore`
- `ProjectWorkspace`
- `WorkflowEngine`

实现层还需要承担：

- `SupervisorAgent`
- `SupervisorDecision`
- `ImplementationPlanner`
- `SubtaskExecutor`
- `SubtaskVerifier`
- `SelfCheckRunner`
- `ProjectInspector`
- `ValidationStrategyPlanner`
- `ValidationExecutor`
- `TestCasePlanner`
- `TestCaseExecutor`
- `DiagnosisAgent`
- `RepairAgent`

## 第一版技术栈建议

- Java 21
- Maven
- Spring Boot
- Jackson
- Slf4j + Logback

第一版状态和产物先走文件系统，不先引入数据库。

## TEST / 自检 策略

`self-check` 不直接写业务特判，而采用：

1. `ProjectInspector`
   - 识别技术栈、构建工具、项目类型
2. `ValidationStrategyPlanner`
   - 基于项目指纹规划自检策略
   - 模型只能从受控 capability 列表中选择
3. `ValidationExecutor`
   - 按白名单能力执行实际检查

第一版能力优先级：

- 先使用项目自身测试/构建工具链
- 没有工具链时再退回通用静态检查

第一版通用 capability：

- `mvn/gradle test`
- `npm/pnpm/yarn build/test`
- 网页本地资源引用检查
- JavaScript 与 HTML 内联脚本的语法检查
- 网页 `Playwright` smoke

编辑基础设施补充：

- `tree-sitter` 负责多语言结构化解析、节点定位和范围校验
- 专用现成工具优先于通用自研编辑器
- 后续如果引入 patch / section 级写入，应优先建立在 `tree-sitter` 或专用编辑器之上，而不是继续扩展低层文本锚点替换

模型分工建议：

- 文档阶段：
  - `analysis/prd/design`
- 编码阶段：
  - `implementation/code-review/repair`
- 决策阶段：
  - `diagnosis/supervisor`

并通过 `devflow.ollama.models.*` 做阶段级覆盖，包括：

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

## TEST 阶段当前分层

当前 `TEST` 阶段内部已经拆成三层：

1. `self-check`
   - 面向通用技术正确性
   - 构建、语法、资源、页面基础可启动性
2. `test case design`
   - 基于 `goal / PRD / DESIGN / IMPLEMENTATION` 生成结构化 testcase
   - testcase 作为独立产物落盘
3. `test execution`
   - 按 testcase 执行
   - 网页项目优先接 `Playwright`

判定规则：

- 仅 `self-check` 通过不等于 `TEST` 通过
- 必测 testcase 全通过后，`TEST` 才能进入 `APPROVED`

## 对外入口

### CLI

第一版 CLI 命令建议：

- `init`
- `run create`
- `run start <runId>`
- `run resume <runId>`
- `run status <runId>`
- `run approve <runId> <stage>`
- `run reject <runId> <stage> --comment ...`
- `run show <runId> <stage>`
- `run logs <runId>`

### REST API 预留

后续预留：

- `POST /runs`
- `POST /runs/{id}/start`
- `POST /runs/{id}/approve`
- `POST /runs/{id}/reject`
- `GET /runs/{id}`
- `GET /runs/{id}/artifacts/{stage}`

## 测试与验收

核心测试：

- `Run` 创建后目录和状态文件生成正确
- 工作流按阶段顺序推进
- `AGENT_ONLY` 正常自动过 gate
- `AGENT_PLUS_HUMAN` 正常进入待人工审批
- reject 后正确回退到指定阶段
- 最大修订次数超限后 run 失败
- 进程中断后 `resume` 能恢复
- 产物缺失或损坏时系统能报告明确错误
- implementation plan 能拆出子任务
- 子任务失败时会优先在 implementation 内部重试
- 子任务级自检失败能给出明确反馈

验收标准：

- 工作流闭环可运行
- 每阶段都有可读产物
- review 决策可追踪
- 人工审批可插入
- 失败后可恢复

## 实施顺序

1. 建立 Java 工程骨架和模块边界
2. 定义 `Run`、阶段状态机和文件持久化
3. 实现 `OllamaLlmProvider`
4. 实现前三个文档阶段：analysis / prd / design
5. 实现 reviewer 与 gate 机制
6. 实现 implementation / code review / test
7. 实现 CLI
8. 补集成测试与失败恢复
