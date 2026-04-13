# Forge 当前状态

## 目的

这份文档只维护**当前代码与业务现状**，不再混历史路线讨论。

如果要判断：

- 当前主流程是什么
- 当前编码内核做到什么程度
- 当前主线还剩什么
- 当前下一步该先做什么

先看这里。

## 项目定位

`Forge` 是一个面向研发流程的编程 agent 内核。它的目标不是单轮代码生成，而是把一条完整研发链做成可追踪、可审阅、可打回、可恢复的工作流：

- `ANALYSIS`
- `PRD`
- `DESIGN`
- `IMPLEMENTATION`
- `CODE_REVIEW`
- `TEST`

## 当前业务主流程

当前对外仍然是六阶段工作流：

1. `ANALYSIS`
2. `PRD`
3. `DESIGN`
4. `IMPLEMENTATION`
5. `CODE_REVIEW`
6. `TEST`

阶段名没有变化，但 `IMPLEMENTATION / REVIEW / TEST` 的内部约束已经明显强化：

- implementation 已切到 Claude 风格 `tool loop`
- review 已切到结构化 contract-first
- test 已切到结构化证据主路径

## 当前编码内核现状

截至 `2026-04-13`，已经完成的事实是：

- implementation 子任务执行主链已经切到 `assistant -> tool_use -> tool_result -> assistant`
- `Read / Edit / Write / Delete / Glob / Grep / Bash` 已进入主链
- Bash 写入子集已收紧到单段、可建模的简单命令；`sed/tee` 和泛化“只读命令 + 重定向落盘”已移出主链，文件内容变更统一回到 `Read/Edit/Write/Delete`
- tool loop 的终态成功判定已收紧：如果当前 subtask 声明的文件交付契约未被工具真实落盘满足，纯 assistant prose 不再被当作成功，而是直接以 `NO_MATERIAL_CHANGE` 在执行阶段失败
- implementation planning 的 subtask detail 已收敛成 Claude 风格最小协议，只允许 `path / action / reason`
- `transcript / readFileState / tool-result replacement / mutation / diagnostics` 已收进单一 session state
- implementation 的 live control flow 现在只认 `implementation_state.json`；`stageReady / continuationMode / continuation 文本 / overrideChanges` 都由上游 gate 一次产出，读取侧与 coordinator 不再二次推导
- accepted change-set 已进入 implementation coder 的确定性边界：新本地依赖只能引用当前 owned files 或项目里已存在资产
- HTML runtime ownership 仍由执行链与 verifier 校验，但已经不再由 planning detail 预判或填写
- runtime wiring 续跑 scope 已改成显式携带 `host html + companion runtime roots`，不再只给一个宿主 HTML 让 coder 自己猜 companion 路径
- coder prompt 已显式展示 `Current File Contracts`，并且当前 attempt 使用的 task package 会和 `executionState.effectiveChanges()` 对齐，不再出现“writable files 已缩窄，但 Current Subtask 仍展示旧 owned files”的双轨提示
- PRD 的低权重条目已从正文承诺区收束到 `Source Metadata`；`推断 / 建议 / 设计选择 / 待确认问题` 不再进入 `PRODUCT_CONTRACT` 的 capability / acceptance 投影
- snapshot / restore 只恢复确定性会话状态，不再跨 attempt 恢复 transcript
- implementation 阶段只保留一份 `contract gate` 真相源，progress / report / state snapshot / resume 共用同一结果
- completed-plan 的 `PATCH` 续跑会直接回到 owning subtask，以 patch-only 方式继续，不再追加 synthetic continuation subtask
- `PATCH_EXISTING_IMPLEMENTATION` 的 stage artifact / parser / continuation note / resume 主链已经统一成结构化 `overrideChanges` 协议，不再允许空 scope 静默续跑
- `implementation_stage_status.md` / `worker_results.md` / `implementation_diagnostics.md` 已降为派生展示物，不再参与 continuation、review intake 或 stage progress 判定
- 子任务级 patch review 若未显式给出 scope，会先归一到当前 subtask 的 effective change-set；如果仍没有安全 scope，会直接阻断到人工，不再伪造 auto-patch
- implementation verification 遇到 `TEST_PLAN_DEFECT`、`RUNTIME_PROBE_INVALID` 这类非实现问题时，不会再被静默放过；当前会直接阻断到人工，避免带着无效测试证据继续推进实现
- implementation stage roll-up 已能保留 `ROUTE_TO_REPAIR_TARGET` 的结构化 repair scope，不再把它降级成泛化“未完成子任务”
- 编码主链只认 `chat/tool` provider，不再走 `generate -> chat` 桥接
- 仓库级单测 `mvn -q test` 已通过

这说明：

- “旧逐文件生成主入口”这条主线已经退出 implementation 核心
- 当前问题不再是“要不要切 tool loop”
- `Claude` 风格编码内核四段基础设施已经在代码层完成收口
- 下一步不再是继续补基础骨架，而是做黄金路径集成验证

## 当前主线

当前主线已经从“四段基础设施收口”切换为：

1. `黄金路径集成验证`

当前集成验证的重点不是再补新骨架，而是验证现有内核在真实 case 上是否满足：

- implementation planning 不会再因为 detail 的 runtime metadata 猜测而提前失败
- runtime ownership / wiring contract 在真实产物上是否稳定落地
- implementation execute 阶段是否不再出现“零工具终止 -> observe/self-check 读不到声明文件”的旧崩溃路径
- split delivery / patch continuation 在真实 case 上是否严格沿用同一 contract gate
- review / test 是否继续消费同一份结构化状态，而不是回退到 prose 猜测
- PRD reviewer 是否不再因为正文功能范围混入低权重条目而提前打回

补充现状：

- 最新一次黄金路径集成重新验证时，尚未进入 `IMPLEMENTATION`，先在 `PRD` reviewer 因“推断/建议”措辞未清理而连续打回
- 当前代码已经把这类低权重条目从正文承诺区收束到 `Source Metadata`，仍待下一轮黄金路径集成确认 reviewer 不再因此打回
- 这个阻塞发生在 stage 文档评审层，不是本轮 implementation 内核改动带出的回归

## 当前关键约束

当前仓库的硬约束是：

- 不允许兼容层、适配层、shim、fallback、临时 cap、heuristic patch
- 新骨架落地时，必须同步删除旧骨架
- “能运行”不算完成；只有同类问题整体收口才算完成
- 任何“为了降低风险而暂时保留”的逻辑，都视为未完成
- 任何“后续再清理”的说法，都视为本轮失败
- 每轮代码改动完成后，必须执行 `self-test + code review`

## 当前剩余问题面

当前仍需观察的，不再是基础设施骨架本身，而是黄金路径集成上的真实稳定性：

### 1. 外提 runtime 与宿主 HTML 接线在真实产物上的一致性

- 需要继续验证外提 runtime 产物不会再出现“文件存在但未接线”的孤儿状态
- 需要验证 wiring 约束已经真正后移到 verifier，而不是又从 planning 主链回流

### 2. 黄金路径中的 patch continuation 是否稳定回到原 owning subtask

- 需要继续验证真实失败 case 不会重新规划成新子任务
- 需要验证 retry 不会把同一问题重新放大成整阶段重做
- 需要继续验证 runtime wiring 失败后，coder 能基于显式 file contracts 稳定修复宿主接线，而不是在 inline / external companion 之间漂移
- 需要继续验证 stage-level contract gate 不会再产出“空 `overrideChanges` 的 PATCH 续跑”这一类历史 fatal
- 需要继续验证 implementation verification 产生的 repair-target / human-block 信号，会在真实集成里完整传到 stage artifact 与续跑入口

### 3. review / test 阶段是否持续消费结构化状态

- 需要继续验证后续阶段不会因为 artifact 结构变化又回退到 prose 猜测
- 需要继续验证集成日志、状态产物与真实执行路径一致

## 当前判断

当前结论已经变成：

- 编码主链和基础设施四段都已经完成代码层收口
- 本轮 `self-test + code review` 已完成
- 下一步应直接看黄金路径集成结果，而不是回头再补新的基础设施骨架

## 文档入口

- 当前执行清单：`docs/active-work-items.md`
- 工程约定：`docs/engineering-agreements.md`
- 高优先级硬约束：`AGENTS.md`
