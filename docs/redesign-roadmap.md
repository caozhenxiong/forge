# Forge 后续演进路线图

> 说明：这份文档只讨论后续怎么演进，不再复述已经完成的重构过程。当前代码与业务状态请优先看 `docs/current-state.md`。

## 目的

这份文档只回答两个问题：

1. 当前版本在正式集成验收前后，还需要补哪些收尾项
2. 当前版本稳定后，下一阶段应该往哪里演进

它不再沿用“中改 / 大改”这类历史分期表述，也不再兼任实施日志。

## 当前基线

当前代码已经完成一轮大规模重构，主线已经切到：

- `patch-first`
- `tool-result-first`
- `budget-first`
- 结构化 gate / 结构化 review 语义
- 单一现状文档与收敛后的流程门面

当前版本是否完成，不再看“历史分期目标”，而看：

- 正式黄金路径集成能否稳定通过
- 当前尾项是否收口
- 当前规则与文档是否一致

## 当前版本尾项

这些项和当前版本直接相关，优先级最高。

### 1. 规则继续收紧为 contract / evidence 驱动

当前虽然已经明显减少隐式默认值，但还要继续减少：

- 默认放行
- 默认兜底
- 默认弱约束

目标是让流程更多由：

- `contract`
- `tool result`
- `test evidence`
- `repair artifact`

直接驱动。

### 2. HTML 整体重写与更细粒度 resume cursor 继续增强

当前 HTML patch 主链已经稳定很多，但还有两个增强方向：

- HTML 文档整体重写的安全边界
- 更细粒度的 `resume cursor`

这部分仍然属于当前版本尾项，不应直接跳到下一套体系里。

## 当前版本已补齐的关键尾项

下面这些尾项已经在当前版本内收口，不再作为未完成项保留：

- `repair -> implementation / reviewer`
  - repair brief / repair alignment 已进入 implementation review 上下文
  - reviewer 会把 repair 产物作为当前轮修复契约的一部分看待
- `runtime snapshot -> testcase -> diagnosis / repair`
  - diagnosis 现在直接读取最新的 `test_runtime_snapshot.md`、`test_execution.md`、`test_report.md`
  - diagnosis history 也会保留 review evidence / actionItems，而不再只看 summary / changeRequest

## 集成验收后的近端演进

下面这些项不再属于“当前版本是否完成”的阻塞条件，但会是当前版本稳定后的第一批增强项。

### 0. 质量规则架构

这条是下一轮主线，目标是把：

- coder 的结构优雅性
- test 的覆盖完整性
- 用户习惯与交互质量

从 prompt 软要求前移为：

- `QualityRules`
- `FeatureProfile`
- `QualityPlan`
- `CapabilityMatrix`
- `Structure / Coverage / Experience Gates`

具体设计见：

- [quality-rules-architecture.md](/home/linus/workspace/forge/docs/quality-rules-architecture.md)

当前这条主线的下一步收口点已经明确：

- 去掉质量核心中的领域语义硬编码
- 将 `QualityPlan` 前移到 implementation 规划和子任务验证
- 为 `repair-before-regenerate` 增加宿主产物结构校验

### 1. 更完整的 turn loop

当前 `Plan / Coder / Reviewer` 已接入基础 `AgentTurnLoop`，后续要继续增强：

- 更清晰的内部状态迁移
- 更统一的 turn 级 budget / retry / handoff 语义
- 更少的角色内分支特判

### 2. 更完整的四层上下文体系

当前已经有：

- `Durable Context`
- `Working Context`
- `Evidence Context`
- `Trace Context`

后续要继续增强：

- 更明确的访问边界
- 更清晰的裁剪策略
- 更一致的角色视图

### 3. 完整的 `FileReadState / EditSession`

当前 patch 主链已经建立，但还没有把：

- 文件读取状态
- 当前编辑会话
- 编辑后状态演进

完整收成统一会话模型。

### 4. 更完整的 `TestContract / RepairContract`

当前主链主要围绕 `ExecutionContract` 展开。后续要进一步补齐：

- `TestContract`
- `RepairContract`

让测试和修复都拥有更明确的结构化契约。

### 5. 文档阶段分段生成 / continuation

当前 `ANALYSIS / PRD / DESIGN` 已经有预算与 telemetry，但首稿仍可能走单次 `FULL_DRAFT`。
后续要把文档阶段进一步改成：

- 分段生成
- continuation / resume cursor
- 更细粒度的 section patch

这项作为集成验收后的近端增强保留，不作为本次预算上调的范围。

## 长期演进方向

下面这些属于下一阶段体系升级，不应与当前版本尾项混在一起。

### 1. 完整上下文压缩体系

后续再考虑完整的：

- `snip`
- `microcompact`
- `collapse`
- `autocompact`

### 2. 分层长期记忆系统

把当前运行时上下文进一步升级成：

- session 级
- task 级
- project 级
- 长期记忆级

### 3. sidechain 多 agent transcript

如果后面需要更强的多 agent 协作，再做：

- side transcript
- 结构化回注
- 主 transcript 污染控制

### 4. 独立工具编排层

把工具系统进一步推进成独立层：

- tool registry
- tool orchestration
- permission / execution policy

### 5. CLI / Server 与核心彻底解耦

这一步属于更后期的工程边界收敛，不应提前和当前主线混做。

### 6. 更灵活的语言实现方式

继续扩展多语言支持，但原则仍然是：

- 通用内核
- 轻量适配
- 不把语言特例堆回核心

## 建议顺序

后续建议按这个顺序推进：

1. 先做当前版本尾项
2. 再跑正式黄金路径集成验收
3. 集成稳定后，推进近端演进项
4. 最后再考虑长期体系升级

## 当前实施入口

下一条主线的正式执行清单见：

- [active-work-items.md](/home/linus/workspace/forge/docs/active-work-items.md)

## 当前决策

当前决策很明确：

- 不再沿用“中改 / 大改”这套旧分期语言
- 当前版本优先收尾并通过正式集成验收
- 后续路线以“当前尾项 / 近端演进 / 长期方向”三层维护
