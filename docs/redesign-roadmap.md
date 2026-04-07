# Forge 中改与大改路线图

## 目的

这份文档定义 `Forge` 下一阶段的演进路线，分成两层：

- 中改
  在保留当前 `run / artifact / workflow / tree-sitter editing` 主体资产的前提下，把系统从“固定阶段流水线”升级成“主循环 + 决策驱动”的 agent 内核。
- 大改
  在中改稳定后，把 `Forge` 升级成“多 agent、上下文管理、记忆系统、工具编排、CLI/Server 可切壳”的通用内核。

这份文档不替代当前架构文档，而是回答两个问题：

- 下一轮应该具体改什么
- 中改完成后，如何继续往更完整的 agent 内核演进

## 当前判断

`Forge` 当前已经具备这些重要基础：

- 结构化阶段产物与 review artifact
- `SupervisorAgent`
- `diagnosis / repair`
- `tree-sitter` 结构验收与精确改写
- 事务式写入
- testcase 设计与执行基础

但当前主流程仍然有四个明显瓶颈：

- 流程决策仍有较强的固定状态机味道
- 上下文在长任务中不断堆积，缺少稳定的投影视图和压缩层
- `IMPLEMENTATION` 虽然已经小步化，但 delivery policy 还不够显式
- `repair / test evidence / supervisor` 还没有形成真正的强约束闭环

因此，下一阶段最合理的方向不是继续增加更多固定阶段，而是做一次中等规模的核心流程升级。

## 当前进度

截至当前版本，下面这些中改基础已经落地：

- `AgentLoop`
- `TransitionDecision / TransitionReason`
- `ContextProjector / TaskMemory / FailureDigest`
- `SupervisorAgent.deliveryPolicy`
- `implementation_backlog.md`
- `repair_alignment.md`
- `test_runtime_snapshot.md`
- required testcase 三态：`PASSED / FAILED / BLOCKED`

这份路线图后续重点转成：

- 把这些基础能力继续收紧
- 再逐步完成更完整的中改闭环

## 中改目标

中改的目标不是重写系统，而是在保留现有资产的前提下完成这些升级：

1. 引入真正的 `AgentLoop`
2. 让 `SupervisorAgent` 从“阶段路由器”升级成“transition controller”
3. 引入轻量的上下文投影和任务记忆层
4. 把 implementation 从“分步执行”进一步升级成“delivery policy 驱动”
5. 让 `repair_brief`、`test evidence`、`verification result` 真正控制后续流程

中改完成后，`Forge` 应该具备：

- 统一的主循环
- 明确的 transition reason
- 轻量的上下文压缩与摘要
- 风险驱动的 delivery policy
- repair 和 test 证据驱动的收敛机制

## 中改架构概览

中改后的职责边界应为：

- `AgentLoop`
  负责每轮循环的生命周期
- `SupervisorAgent`
  负责做下一步动作决策
- `WorkflowEngine`
  负责执行决策、写入状态、运行 worker
- `Worker Agents`
  负责生成 artifact、review、diagnosis、repair、test
- `ContextProjector`
  负责给当前轮构造“投影视图”
- `TaskMemory`
  负责当前 run 的紧凑记忆，而不是全量历史

整体思路是：

- 原始 artifact 和历史全部保留
- 模型每一轮只看经过投影和压缩后的必要上下文
- 决策由 `SupervisorAgent` 输出
- 执行由 `WorkflowEngine` 负责

## 中改详细方案

### 迭代 A：引入 AgentLoop

#### 目标

把当前“阶段推进 + reroute”的散落逻辑，收敛成统一主循环。

#### 主要改动

- 新增 `AgentLoop`
- 新增 `LoopState`
- 新增 `TransitionDecision`
- 新增 `TransitionReason`
- 新增 `LoopBudget`

`AgentLoop` 的基本责任：

1. 读取当前 `run` 状态
2. 构造投影视图
3. 请求 `SupervisorAgent` 决策
4. 执行决策
5. 记录 transition artifact 和事件
6. 判断下一轮是否继续

#### Supervisor 输出结构

建议固定字段：

- `action`
- `targetStage`
- `reason`
- `deliveryPolicy`
- `focus`
- `constraints`
- `requiredEvidence`
- `humanRequired`

#### 第一版支持的 action

- `ENTER_STAGE`
- `RETRY_STAGE`
- `ROUTE_TO_DIAGNOSIS`
- `ROUTE_TO_REPAIR`
- `ROLLBACK_STAGE`
- `REQUEST_HUMAN`
- `FAIL_RUN`
- `COMPLETE_RUN`

#### 验收标准

- 阶段推进不再依赖分散的 if/else
- 每次转移都有明确 `TransitionReason`
- `run` 目录里有单独的 transition artifact，可解释“为什么进入这一步”

### 迭代 B：上下文投影与任务记忆

#### 目标

避免每一轮把完整历史、完整文档和完整失败轨迹原样喂给模型。

#### 主要改动

- 新增 `ContextProjector`
- 新增 `ProjectedContext`
- 新增 `TaskMemory`
- 新增 `FailureDigest`
- 新增 `ArtifactSummaryBuilder`

#### 设计原则

- 原始 artifact 保留，不做破坏性压缩
- 给模型的是“投影视图”，不是原始全量历史
- 最近两轮保留较完整内容
- 更早历史转成摘要
- `repair_brief`、最近失败证据、当前 design 要求优先级最高

#### 第一版上下文分层

- `CurrentStageContext`
  当前阶段核心输入
- `RecentHistory`
  最近两轮产物与 review
- `FailureContext`
  最近失败原因、repair brief、test evidence
- `UpstreamContract`
  目标、约束、PRD、DESIGN 的高优先级摘要
- `WorkingSet`
  当前需要看的真实文件片段

#### 第一版不做的事

- 不立即实现完整四层压缩
- 不立即实现跨 session 的长期记忆检索
- 不立即对所有 artifact 做深度语义摘要

#### 验收标准

- 单轮 prompt 输入长度明显下降
- 同一任务多轮修复时，模型能稳定看到真正重要的上下文
- 复杂 run 不再因为历史过长而持续发散

### 迭代 C：Delivery Policy 与 Implementation Backlog

#### 目标

把 `IMPLEMENTATION` 从“已有小步执行”进一步升级成“风险特征驱动的交付策略”。

#### 重要原则

不要写死：

- 前端项目先骨架
- 后端项目先补测试

应该改成：

- `Design` 产出 `delivery policy`
- `ProjectFingerprint` 产出客观风险特征
- `SupervisorAgent` 最终决定本轮 delivery mode 和约束

#### 新增产物

- `implementation_backlog.md`

内容建议包括：

- 子任务列表
- 每步目标
- 每步验收标准
- 每步建议 delivery mode
- 每步预计涉及文件
- 每步建议的验证方式

#### Delivery policy 字段

- `mode`
  - `SKELETON`
  - `INCREMENTAL`
  - `PATCH`
  - `REWORK`
- `maxFiles`
- `maxSymbols`
- `preferPreciseEditing`
- `forceBacklogSplit`
- `requireVerificationBeforeReview`

#### 风险特征来源

- 文件大小
- 修改文件数量
- 是否有稳定结构锚点
- 是否支持精确 patch
- 是否已有测试入口
- 是否涉及高耦合运行时交互
- 最近修复是否频繁发散

#### 验收标准

- 实现阶段每轮改动范围可解释、可追踪
- delivery mode 由策略和证据驱动，而不是技术栈标签驱动
- “大块生成”成为例外，不是默认

### 迭代 D：Repair 强约束化

#### 目标

让 `repair_brief` 从“诊断摘要文件”升级成真正的流程约束物。

#### 主要改动

- 强化 `RepairBrief`
- 新增 `repair_alignment.md`
- verifier 和 supervisor 都必须读取 repair brief

#### RepairBrief 结构建议

- `issueCluster`
- `rootCause`
- `mustFixFirst`
- `priorityOrder`
- `forbiddenDirections`
- `filesToTouch`
- `filesToAvoid`
- `acceptanceChecks`
- `verificationSteps`

#### repair_alignment.md 结构建议

- 本轮覆盖了哪些 `mustFixFirst`
- 哪些 `acceptanceChecks` 已满足
- 哪些仍未满足
- 本轮是否违反 `forbiddenDirections`
- 本轮实际修改了哪些文件

#### 行为规则

- 若 implementation 没覆盖 `mustFixFirst`，则 verifier 直接拒绝
- 若 implementation 修改了 `filesToAvoid`，必须给明确理由，否则拒绝
- 若本轮继续沿 `forbiddenDirections` 修补，直接拒绝

#### 验收标准

- 连续失败后不会继续沿错误方向空转
- repair 真正改变 implementer 的关注重点
- diagnosis 和 repair 结果能稳定收敛下一轮实现

### 迭代 E：测试证据驱动化

#### 目标

让 `TEST` 真正变成可信 gate，而不是“报告说过了就算过了”。

#### 主要改动

- 在 smoke 后采集 runtime snapshot
- testcase 设计基于 runtime snapshot，而不是静态猜测
- required case 使用三态：
  - `PASSED`
  - `FAILED`
  - `BLOCKED`
- 每条 case 都必须带结构化 evidence

#### 新增产物

- `test_runtime_snapshot.json`
- `test_runtime_snapshot.md`

#### 每条 testcase 结果建议字段

- `status`
- `expected`
- `observed`
- `evidence`
- `failureReason`
- `selectorsChecked`
- `artifacts`

#### 规则

- required case 若 `FAILED` 或 `BLOCKED`，`TEST` 不得通过
- testcase 设计必须优先基于运行时页面，而不是静态文件脑补
- 性能测试只在 `DESIGN` 明确要求后才变成强制 gate

#### 验收标准

- testcase 不再和真实页面结构严重错位
- test failure 能直接喂给 diagnosis / repair
- `TEST` 通过与否建立在真实执行证据上

## 中改阶段的模块清单

建议新增或扩展这些模块：

- `agent/loop`
  - `AgentLoop`
  - `LoopState`
  - `TransitionDecision`
  - `TransitionReason`
- `agent/context`
  - `ContextProjector`
  - `ProjectedContext`
  - `TaskMemory`
  - `FailureDigest`
- `agent/supervisor`
  - 强化 `SupervisorAgent`
  - 新增 delivery policy 输出
- `agent/repair`
  - 强化 `RepairBrief`
  - 新增 `repair_alignment`
- `agent/testing`
  - runtime snapshot
  - structured test evidence

## 中改后的验收标准

中改完成后，至少要满足：

1. 任何一轮流程转移都能回答“为什么”
2. 模型输入是稳定的投影视图，而不是原始混乱历史
3. 实现阶段默认按受控 delivery policy 推进
4. repair 不再只是建议，而是强约束
5. test 失败能提供直接可用于 repair 的证据

## 从中改到大改的路径

中改做完后，不建议立刻继续堆功能，而应该按下面顺序升级。

### 大改阶段 1：完整上下文管理

在中改的 `ContextProjector` 基础上，继续升级为真正的上下文管理系统。

目标：

- `Snip`
- `Microcompact`
- `Collapse`
- `Autocompact`

原则：

- 原始历史不丢
- 模型只看投影视图
- 压缩逻辑是系统能力，不是 prompt 技巧

### 大改阶段 2：记忆系统分层

把当前零散的 repair/history/artifact 摘要，升级成分层记忆：

- `SessionMemory`
- `TaskMemory`
- `LongTermMemory`
- `ProjectMemory`

这样 diagnosis、repair、supervisor 就不需要每次都从 artifact 目录反向拼历史。

### 大改阶段 3：真正多 agent sidechain

当前 diagnosis/repair/supervisor 虽然角色已分，但还不是真正的 sidechain agent。

大改后应变成：

- 主 agent 有主 transcript
- diagnosis / repair / testcase planner 有独立 side transcript
- 只把结构化产物回注主流程

目的：

- 降低上下文污染
- 让不同子 agent 独立收敛
- 避免所有角色挤在一个上下文里相互干扰

### 大改阶段 4：工具编排层独立

把工具执行继续从 workflow 中拆出来：

- `ToolRegistry`
- `ToolOrchestrator`
- `StreamingToolExecutor`
- `PermissionAdapter`
- `ExecutionPolicy`

这样 Forge 后面接更多工具时，不需要继续在 workflow 代码里堆规则。

### 大改阶段 5：壳子与引擎分离

最后再做壳子分离：

- `forge-core`
- `forge-tool`
- `forge-agent`
- `forge-cli`
- `forge-server`

遵循原则：

- 先稳定核心引擎
- 再切 CLI / Server 壳
- 不要一边重构核心，一边同时做 server 化

## 建议实施顺序

推荐顺序：

1. 迭代 A：AgentLoop
2. 迭代 B：上下文投影与任务记忆
3. 迭代 C：Delivery Policy 与 Implementation Backlog
4. 迭代 D：Repair 强约束化
5. 迭代 E：测试证据驱动化

中改稳定后，再按：

1. 上下文管理完整化
2. 记忆系统分层
3. 多 agent sidechain
4. 工具编排独立
5. CLI / Server 壳分离

## 当前决策

当前 `Forge` 不走“直接大改”的路线。

当前决策是：

- 先做中改
- 保留现有 artifact/run 体系
- 保留现有 `tree-sitter` 编辑主线
- 优先把主循环、上下文投影、delivery policy、repair、test evidence 收紧

只有中改稳定后，才进入大改阶段。
