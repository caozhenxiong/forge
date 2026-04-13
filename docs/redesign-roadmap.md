# Forge 后续演进路线图

> 说明：这份文档只讨论在当前主线完成后，Forge 下一步往哪里演进。当前代码与业务状态请优先看 `docs/current-state.md`，当前执行清单请优先看 `docs/active-work-items.md`。

## 目的

这份文档只回答两个问题：

1. 当前主线完成后，下一阶段还值得做什么
2. 这些方向的先后顺序是什么

它不再兼任实施日志，也不再保留历史分期话术。

## 当前前提

进入这里讨论的前提是：

- 当前 implementation 内核已经完成主链收口
- 当前版本是否稳定，先看黄金路径集成结果
- 未通过集成前，不在这里继续堆新的“当前收尾项”

也就是说：

- 当前版本是否完成，看 `docs/active-work-items.md`
- 完成之后做什么，看这里

## 集成稳定后的近端演进

### 1. 质量规则继续前移为 contract / evidence 驱动

当前已经明显减少默认放行、默认兜底和 prose 猜测。下一步更值得做的是把质量要求进一步结构化，而不是继续补场景规则。

方向包括：

- 继续把 coder 的结构质量约束前移到 planning / implementation verification
- 继续把 test 的覆盖完整性收成结构化 coverage contract
- 继续把体验要求收成结构化 expectation，而不是 reviewer 临场发挥
- 让更多判定直接由 `contract / tool result / test evidence / repair artifact` 驱动

如果这条进入下一轮正式主线，核心抽象大致会围绕：

- `QualityRules`
- `FeatureProfile`
- `QualityIntent`
- `QualityPlan`
- `Structure / Coverage / Experience Gates`

### 2. 文档阶段分段生成与 continuation

当前 `ANALYSIS / PRD / DESIGN` 已有预算与 telemetry，但首稿仍可能依赖单次大生成。

下一步可演进为：

- 分段生成
- section 级 continuation / resume cursor
- 更细粒度的 section patch
- 在摘要/压缩时保留结构化 contract 精度

这条是产品化体验增强，不属于当前主线阻塞项。

### 3. 更完整的上下文压缩与恢复

当前已经有 durable state、working state 和结构化 state snapshot，但后续还可以继续增强：

- 更明确的上下文裁剪层级
- 更稳定的 contract-preserving compaction
- 更清晰的 resume 恢复边界

重点不是“压得更狠”，而是“压缩后仍保住关键约束和修复上下文”。

### 4. 更完整的测试/修复契约

当前主链主要围绕 `ExecutionContract`、review 语义和 test evidence 展开。

后续可继续增强：

- 更显式的 `TestContract`
- 更显式的 `RepairContract`
- test -> diagnosis -> repair 的更稳定结构化回注

### 5. 更独立的工具编排与审批

当前工具主链已经进入 implementation coder，但更后一步仍值得做：

- 更完整的 tool registry
- 更明确的 permission / approval contract
- 更稳定的 tool execution accounting
- shell 审批链的显式拒绝/恢复语义

这条应该建立在当前主链稳定之上，不应与当前集成收尾混做。

## 长期方向

这些方向不是当前版本的完成条件，只是中长期演进候选：

- 完整上下文压缩体系
- 分层长期记忆系统
- 更强的多 agent sidechain transcript
- CLI / Server 与核心进一步解耦
- 更灵活但仍受控的多语言编辑能力接入

## 建议顺序

建议按这个顺序推进：

1. 先完成并跑通当前黄金路径集成
2. 再把近端演进项按一条主线拉出来
3. 每次只推进一条主线，不并行堆多个大主题
4. 长期方向只在近端主线稳定后再进入实施
