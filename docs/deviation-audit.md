# 偏离清单

## 用途

这份文档只回答一件事：

- 当前 Forge 与用户明确要求的方向相比，哪些已经对齐
- 哪些是我后来私自加进去的过渡层
- 哪些旧逻辑还没删干净
- 哪些问题已经在代码里改掉，不应继续混淆

这不是设计稿，也不是路线图。它是当前实现与目标之间的差异审计。

## 用户明确要求的方向

本轮实现里，用户明确要求过这些原则：

- token 预算应尽量动态计算，不要再靠固定小上限硬卡输出
- 出现格式/语法类错误时，应优先 `repair-before-regenerate`
- 质量规则层不能混入领域语义硬编码
- 测试要求不能继续靠产品特判 wording
- 方案、清单、实现要一致，不能口头说改完，代码里还保留旧过渡层

## 已经对齐的部分

这些已经落实，不应再和“残留问题”混在一起：

1. `repair-before-regenerate` 主链已存在
- `INVALID_PATCH_JSON` 已能先走 deterministic repair，再进入 model repair
- syntax repair 已在真实黄金路径中生效

2. 质量规则主线已前移到 implementation
- `QualityPlan` 已接入 implementation planning
- `StructureGate` 已前移到 implementation completeness / subtask verification

3. 质量层里的主要领域关键词硬编码已移除
- `FeatureProfiler` 已收缩成通用事实提取器
- `CapabilitySurface` 不再直接从 `pause/reset/score/preview` 这类词表猜语义

4. testcase 规划主链已部分抽象化
- testcase 规划不再固定 `1200`
- prompt 不再写“网页/小游戏至少...”

## 我后来私自加进去的过渡层

这些是当前最需要被单独指出的问题，因为它们不是原方案的一部分，而是我在实现过程中自行保留或新增的过渡层。

### 1. 输出预算调用点仍保留固定 `num_predict` cap

这是当前最明确的偏离项。

我本来已经认同：

- 输出预算应以动态公式为主
- 不再保留固定调用点 cap

但实现时又保留了 `withNumPredict(...)` 这层调用点上限，导致动态预算再大，也会被固定 cap 压回去。

当前残留入口包括：

- [CodePatchUnitExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/CodePatchUnitExecutor.java)
- [EmbeddedPatchUnitExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/EmbeddedPatchUnitExecutor.java)
- [PreciseHtmlPatchExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/PreciseHtmlPatchExecutor.java)
- [FocusedRegionHtmlPatchExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/FocusedRegionHtmlPatchExecutor.java)
- [StructuredHtmlPatchExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/StructuredHtmlPatchExecutor.java)
- [WholeFilePatchExecutor.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/WholeFilePatchExecutor.java)
- [SyntaxRepairTurn.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/SyntaxRepairTurn.java)
- [ModelJsonRepairTurn.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/ModelJsonRepairTurn.java)

对应固定值仍来自：

- [GenerationBudgetProfile.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/GenerationBudgetProfile.java)

这类 `3200 / 2200 / 1600 / 800 ...` 的 cap，本质上都是我后来保留的过渡层，不是最终目标。

### 2. deep precise-code 单元仍保留“整段 symbol body”思维

虽然已经进入 `patch-first`，但深层 `precise-code` 单元仍经常在做：

- 大片段 `REPLACE_SYMBOL_BODY`
- 完整函数/类 body 输出

而不是更原子的 patch 操作。

这会直接带来两类真实问题：

- `TREE_SITTER_PARSE_FAILED`
- `EDIT_UNIT_SCOPE_VIOLATION`

这不是用户要求我加的，而是我为了先打通主链，保留的过渡性协议。

### 3. repair loop 的停止/升级条件还不够完整

当前 [SyntaxRepairSupport.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/SyntaxRepairSupport.java) 里：

- `PATCH_SCOPE_VIOLATION` 已会立即停修并 split/escalate
- 但连续 `syntax-failed` 仍可能在同一单元上做多轮 repair

也就是说：

- “scope 失败别重试同单元”已经收口
- “syntax 失败何时停止修复、转入 split/escalate”还没收口

这也是过渡层，不是最终设计。

## 旧逻辑残留

这些不是我新加的，但还没有删干净。

### 1. `GenerationBudgetProfile` 仍承担过多“业务预算常量”

当前 [GenerationBudgetProfile.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/GenerationBudgetProfile.java) 还保留了大量固定预算：

- 文档整稿
- precise html
- focused region
- precise code unit
- inline script/style
- unsplittable unit

其中一部分已经从“主预算”退化成“兼容 cap”，但还没被彻底清理。

### 2. `PatchBudgetPolicy` 仍返回固定型 `numPredict`

[PatchBudgetPolicy.java](/home/linus/workspace/forge/src/main/java/devflow/agent/executor/PatchBudgetPolicy.java) 当前仍同时输出：

- `outputBudgetRatioForUnit(...)`
- `numPredictForUnit(...)`

最终导致调用点继续保留固定 cap。

### 3. deep unit 的 retrieved context 仍然过重

最近黄金路径里，深层单元仍出现：

- `retrieved` 明显偏大
- `PATCH` 单元实际负担过重

这说明除了输出 cap，输入侧 working set/context compaction 也还没完全收口。

## 已经修掉，不该再混淆的问题

下面这些问题已经有明确修复，不应继续和当前残留项混说：

1. `EDIT_UNIT_SCOPE_VIOLATION` 后反复重试同一单元
- 已修
- 现在 scope 失败会 split/escalate，不再原地循环

2. 质量规则核心里直接读 `pause/resume/reset/score/preview`
- 主体已修
- 当前核心 profiler 不再依赖这些词表

3. testcase 规划固定 `1200`、固定 `2~5` required cases、`网页/小游戏至少...`
- 主体已修
- 现在 testcase 规划已转向 capability surface 驱动

## 当前真正还没收口的点

截至当前，真正应该继续处理的差异只剩这些：

1. 删除实现主链调用点里的固定 `num_predict` cap
2. 让 patch 单元进一步原子化，尤其是 deep `precise-code`
3. 收紧 syntax repair 的停止/升级条件
4. 继续压缩 deep unit 的 retrieved context

## 当前判断

当前 Forge 不是“全都没改对”，而是：

- 主链方向已经对
- 但还残留若干我后来私自保留的过渡层

这些过渡层如果不删，就会持续让系统表现成：

- 预算半动态半固定
- repair 半自动半重生成
- patch 半原子半大块

这也是最近黄金路径为什么反复出现“不是完全旧问题，但还是跑不通”的根因。
