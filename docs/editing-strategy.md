# 结构化编辑策略

## 目的

这份文档定义 `Forge` 后续在“代码修改基础设施”上的长期方向，避免在复杂底层能力上重复手搓轮子。

核心原则有两条：

- 结构化代码编辑默认选择 `tree-sitter`
- 遇到复杂底层能力时，先判断是否有成熟现成方案，再决定是否自研

## 总原则

### 1. 先 Build-vs-Buy，再编码

凡是下面这类能力，默认先做现成方案调研：

- 结构化代码解析
- AST 级修改
- DOM 修改
- diff / patch
- 浏览器自动化
- 单元测试框架
- benchmark / profiling

只有在以下条件同时满足时，才考虑自研：

- 现成方案明显不能满足当前需求
- 接入成本高于维护成本
- 自研范围可以收敛到一个很小、很稳定的边界

### 2. 不把“精确字符串替换”作为主路径

以下方式不能作为长期主方案：

- 依赖唯一文本锚点的 `replaceExact`
- 大段文本插前插后
- 先整文件重写，再希望 reviewer 抓住问题

这些方式只适合作为临时兜底，不适合作为核心编辑基础设施。

## 默认技术路线

### 1. 代码结构化编辑：`tree-sitter`

`tree-sitter` 作为默认的结构化代码解析骨架，负责：

- 多语言语法树解析
- 节点定位
- 范围提取
- 结构校验
- 为后续 patch / section 级写入提供稳定锚点

选择它的原因：

- 多语言能力强
- 适合长期走“Java 内核 + 多语言项目”路线
- 比纯字符串锚点稳定
- 比一开始就自己做通用 parser 更现实

注意：

- `tree-sitter` 是结构化解析基础设施，不等于完整编辑框架
- 它负责“看懂结构和定位范围”，不负责包办所有语言的高层改写体验

### 2. HTML 修改：优先 `jsoup`

对 HTML 页面、DOM 结构、属性修改这类问题，优先使用现成 DOM 工具，而不是强行统一走 `tree-sitter`。

原则：

- DOM 修改优先 `jsoup`
- 只有需要和多语言统一策略打通时，才考虑 `tree-sitter-html`

### 3. 通用文本 patch：优先成熟 diff/patch 库

对无法直接走 AST/DOM 的文本文件：

- 优先使用成熟 diff / patch 库
- 不优先自研锚点替换器

这类能力适合作为：

- fallback
- 低风险文本文件修改
- 补丁可视化与审计

### 4. JS/TS 高层改写：优先现成 codemod 工具

当目标是：

- 改 import/export
- 调整函数签名
- 移动对象属性
- 改 JSX/TS 节点

优先考虑现成 JS/TS codemod 工具，而不是在 Java 里硬做所有细粒度变换。

适合方向：

- `jscodeshift`
- `ts-morph`

Java 内核可以把它们当 sidecar 能力来调用，而不是在主进程里复制一套完整 AST 编辑体验。

## 编辑能力分层

长期建议分成 4 层：

### 第一层：能力选择

先判断文件和任务类型：

- HTML / DOM
- JS / TS
- Java
- 配置文件
- 纯文本

### 第二层：优先现成编辑器

按类型选择最合适的现成能力：

- HTML -> `jsoup`
- JS/TS -> codemod 工具或 `tree-sitter`
- 通用代码结构定位 -> `tree-sitter`
- 纯文本 -> diff/patch 库

### 第三层：生成编辑计划

模型不再直接吐整个文件，而是先产出编辑计划，例如：

- 目标文件
- 目标节点 / 范围
- 操作类型
- 预期结果

### 第四层：验证后提交

所有编辑都必须在提交前经过：

- 结构合法性验证
- 语法验证
- 必要时运行构建/测试

## 对 `Forge` 的具体约束

### 1. 默认方向

后续 `IMPLEMENTATION` 的文件修改基础设施按这个优先级演进：

1. 现成专用工具
2. `tree-sitter` 结构化定位
3. patch / section 级写入
4. 整文件重写仅作为最后兜底

### 2. 不再扩展自研低层精确替换类

像下面这类能力，不再作为主线继续演进：

- `REPLACE_EXACT`
- `INSERT_AFTER_EXACT`
- `INSERT_BEFORE_EXACT`

原因：

- 锚点脆弱
- 歧义高
- 难跨语言复用
- 一旦文件被模型轻微改写，定位就会失效

### 3. 未来的实现边界

短期：

- 继续保留完整文件输出，但加严格验证
- 逐步引入 section 级写入

中期：

- 接入 `tree-sitter`
- 用结构化节点范围代替文本锚点

长期：

- 形成“模型生成编辑计划 -> 结构化执行器应用 -> 校验后提交”的闭环

## 维护规则

以后遇到想手搓复杂底层轮子的场景，先回答这几个问题：

1. 有没有成熟现成方案
2. 接进来是否明显比自研更省
3. 这个能力是不是核心竞争力
4. 我们是不是只需要一个受控的薄封装，而不是重做整套系统

如果前两条答案偏正向，就优先接现成方案。

## 当前已落地状态

第一阶段已经不是只停留在原则层，当前实际已落地：

- `tree-sitter` 已接入 `Forge` 的解析层
- 当前支持：
  - HTML
  - JavaScript
  - TypeScript
  - Java
  - Python
  - Go
- 当前主要用途：
  - 生成内容写盘前的结构合法性校验
  - 静态 HTML 结构快照提取
  - 为 fallback testcase 设计提供真实 DOM 线索
  - 为 HTML / JavaScript / TypeScript / Java / Python / Go 精确改写提供稳定结构边界

当前代码位置：

- `src/main/java/devflow/agent/parsing/TreeSitterSupport.java`
- `src/main/java/devflow/agent/parsing/TreeSitterParseSummary.java`
- `src/main/java/devflow/agent/parsing/HtmlStructureSnapshot.java`

当前接入点：

- `ImplementationExecutor`
  - 对 `.html/.js/.ts/.java/.py/.go` 的模型输出先做 `tree-sitter` 校验，再决定是否接受
  - 对带稳定锚点的 HTML 页面，`INCREMENTAL / PATCH` 优先走区块级精确改写
  - 对已有 `JavaScript / TypeScript / Java / Python / Go` 文件，`INCREMENTAL / PATCH` 优先走符号级精确改写
  - 写入流程已改成事务式：stage 候选文件，重新校验，通过后 commit，失败则保留调试 artifact
- `TestCasePlanner`
  - 对静态网页从真实 HTML 中提取按钮、`id`、`canvas` 等结构，再生成 testcase

当前 HTML 精确改写约束：

- 第一版只支持稳定锚点页面：
  - `<main id="app-root">`
  - `<style id="app-style">`
  - `<script id="app-script">`
- 模型输出不再是完整 HTML 文档，而是区块 JSON：
  - `markupHtml`
  - `styleCss`
  - `scriptJs`
- 执行器再基于 `tree-sitter` 定位这些区块并应用替换

当前代码精确改写约束：

- 当前覆盖语言：
  - JavaScript
  - TypeScript
  - Java
  - Python
  - Go
- 精确改写只对已有可解析符号的文件启用
- 当前支持动作：
  - `REPLACE_SYMBOL`
  - `INSERT_INTO_SYMBOL`
  - `APPEND_FILE`
- 模型输出不再是完整源码文件，而是符号级 JSON patch
- 执行器会先基于 `tree-sitter` 提取符号清单，再应用 patch 并重新校验结构合法性
- 失败的候选写入会保留在 `.devflow/write-transactions/failed/`

当前仍未做：

- 通用 AST refactor
- 更丰富的节点级语义改写
- 跨文件统一编辑计划执行器

也就是说：

- 现在的 `tree-sitter` 已经进入主链路
- 已经从“结构理解 + 写盘前验证”前进到“受控的区块/符号级 patch”
- 还不是“完整精确编辑器”
