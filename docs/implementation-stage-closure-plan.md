# IMPLEMENTATION 增量收口方案

## Purpose

本方案服务于当前主线 `黄金路径集成验证`，不是重开已经完成的基础整改。

约束来源：

- 根目录 `AGENTS.md`
- `docs/engineering-agreements.md`
- `docs/current-state.md`
- `docs/active-work-items.md`

本方案只覆盖**当前还未被代码和状态文档证明解决的 implementation 主线残余问题**。已经收口的项必须显式标注“不要重做”，避免把工作重心从真实未完成项拉回已关闭路径。

## Summary

当前主线不是继续扩散架构整改，而是把已完成的编码内核放回真实 case 做黄金路径验证。

因此，这份方案只保留三类增量收口工作：

1. 明确 runtime contract 的**承载边界与生效时机**：
   - planning detail 不承载 runtime metadata
   - accepted / continuation scope 可以承载 runtime contract
2. 补齐 unsupported shell write 等**工具失败证据的落盘与消费闭环**
3. 以真实黄金路径集成验证作为唯一验收，而不是重做已完成整改

## 已完成项（不要重做）

以下事项已经由代码与状态文档收口，本轮不得重新定义为“当前根因”：

1. implementation live control flow 只认 `implementation_state.json`
2. `StageProgressCoordinator` 已按 `READY / CONTINUE / BLOCKED` 分流 implementation
3. 纯 assistant prose 在未满足文件交付契约时，不再被当作成功
4. planning detail 已收敛为最小协议，不再承载 `runtimeOwnership` 等 runtime metadata
5. `PATCH` continuation 没有 concrete patch target / safe scope 时，已不能静默续跑
6. runtime wiring continuation scope 已能显式携带 `host html + companion runtime roots`

本轮实施中，不允许再把这些项写成“待收口问题”，也不允许为了本轮调整把它们退回旧设计。

## 当前未闭合项

### 1. accepted / continuation scope 里的 runtime contract 何时必须具备

当前边界已经明确：

- planning detail 不承载 runtime metadata
- `FileChange` / accepted scope 可以承载 `runtimeOwnership`

但还需要进一步收口的问题是：

- 当某个 HTML 文件已经被确定为当前实现闭环里的 host entry 时，accepted / continuation scope 在什么时点必须具备完整 runtime contract
- 如果当前 scope 已经需要 runtime contract，但结构化来源无法提供该 contract，系统应该在哪里阻断，而不是把空值继续带到 coder / verifier / review

这项问题的关键不是“把 metadata 塞回 planning”，而是**把 accepted scope 的结构化边界定义清楚**。

### 2. unsupported shell write 的证据是否已完整闭环

当前主链已经收紧 Bash 写入子集，但还需要确认并收口：

- unsupported shell write / 非法重定向 / 越权写入在工具层是否总能产出结构化失败证据
- 这些证据是否会稳定进入 event log、implementation diagnostics、attempt failure 聚合结果
- repair / review / 集成排障是否读取同一份结构化证据，而不是回退成看 prose 或手工猜测

这项问题的关键不是新增一套 reason code，而是**复用现有 owner，把证据链打通**。

### 3. 黄金路径集成验证仍是当前唯一业务验收

本轮最终仍必须回到真实 case 验证：

- accepted scope 是否真正成为 coder 的唯一新增依赖边界
- runtime contract 在 accepted / continuation scope 的边界定义是否足够支撑真实网页产出
- unsupported shell write 等失败证据是否能帮助 repair / review / 排障，而不是只在日志里留下模糊痕迹

## Protocol Boundaries

本轮协议边界必须明确写死，禁止实现时再次漂移：

### 1. planning detail 不承载 runtime metadata

- `ImplementationSubtaskDetailChange` 继续只保留最小协议
- 不允许把 `runtimeOwnership`、`hostHtmlPatchRequired`、companion 路径等高层 runtime 语义塞回 planning detail

### 2. accepted / continuation scope 可以承载 runtime contract

- `FileChange`、`effectiveChanges()`、结构化 `overrideChanges` 可以携带 runtime contract
- 这些 contract 只能来自**结构化来源**：
  - accepted change-set
  - continuation scope
  - 当前运行态已有的 runtime wiring 事实
- 不允许通过模型 prose、文件名猜测、场景黑名单回灌这些语义

### 3. REWORK 继续合法

- `REWORK + ImplementationPatchTarget.NONE` 作为 stage-level rework / rollback 仍然合法
- 本轮禁止的不是 `REWORK + NONE`
- 本轮真正要继续守住的是：
  - `FixMode.PATCH` 必须有 concrete patch target
  - `PATCH_EXISTING_IMPLEMENTATION` 必须有结构化 `overrideChanges`

### 4. reason code 不新开体系

失败分类继续复用现有 owner：

- 工具/本地验证失败：`ToolFailureCode`
- attempt / generation 聚合失败：`GenerationFailureType`
- review 语义结论：`ReviewReasonCode`

本轮不新增第四套 reason-code 体系。

## Final State

完成态必须同时满足：

1. `docs/current-state.md` 中已完成项保持成立，没有被本轮改动回退。
2. planning detail 仍然不承载 runtime metadata。
3. accepted / continuation scope 对 host entry HTML 的 runtime contract 要求被明确且单点定义。
4. 当 host entry HTML 已进入 accepted / continuation scope 且当前闭环需要 runtime contract 时：
   - contract 缺失会在进入 coder 前被阻断
   - 不会再把 `runtimeOwnership=null` 静默带到后置链路
5. unsupported shell write 的失败证据可以被稳定落盘并被 repair / review / 排障消费。
6. 本轮不新增 reason-code 枚举，只复用既有 owner。
7. 黄金路径集成验证至少跑通一条真实 case，或明确给出基于结构化证据的剩余 blocker。

## Removal Plan

本轮必须删除旧文档里的错误表述，不能保留为“说法不同但都能理解”：

1. 删除“implementation machine truth 不单一”作为当前根因的表述。
2. 删除“prose-only 完成判定仍是当前问题”的表述。
3. 删除“要禁掉所有 `REWORK + NONE`”的表述。
4. 删除“planning detail 或 plan 阶段要补 runtime metadata”的暗示。
5. 删除“新增一套 attempt failure reason code”这类再造一层枚举体系的表述。

## Joint-Change Scope

如果进入实现，本轮只允许改这几个联动面：

### 1. runtime contract 边界

- accepted scope / continuation scope 物化链路
- runtime wiring scope builder
- `ImplementationMutationContractGuard`
- `SubtaskVerificationSupport`

### 2. 失败证据闭环

- Bash / tool failure 事件写入
- implementation diagnostics / event log 聚合
- repair / review 读取结构化失败证据的入口

### 3. 集成验证与文档

- 黄金路径集成测试
- `docs/current-state.md`
- `docs/active-work-items.md`
- 本方案文档与对应 tracker

不在本轮范围内：

- 重开 implementation machine truth 改造
- 重开 prose-only success 判定改造
- 重开 PATCH concrete target 规范化改造

## Implementation

### Phase 1. 锁定基线，防止重做已完成项

目标：先把“不要重做”的边界钉死。

实施：

1. 在相关设计和实现说明中显式引用 `docs/current-state.md` 与 `docs/active-work-items.md` 的已完成项。
2. 所有实现方案、review 和测试结论都必须先判断“是不是在重做已关闭路径”。
3. 任何试图把 runtime metadata 塞回 planning detail、把 `REWORK + NONE` 视为非法、或把 prose-only success 写回当前根因的修改，直接视为方案违规。

完成标志：

- 本轮方案与后续实现不再重开已完成整改。

### Phase 2. 收口 accepted / continuation scope 的 runtime contract 边界

目标：不回灌 planning detail 的前提下，明确 runtime contract 在 accepted scope 的生效点。

实施：

1. 定义 host entry HTML 的 runtime contract 只在 accepted / continuation scope 物化时进入结构化变更集。
2. 该 contract 只能来自结构化来源：
   - 当前 accepted change-set
   - 当前 continuation scope
   - 当前执行状态中已知的 runtime wiring 事实
3. 如果某个 host entry HTML 已经进入 accepted / continuation scope，且当前闭环要求 companion wiring，但结构化来源无法提供 runtime contract，则在进入 coder 前直接阻断。
4. mutation guard、verification、review 继续消费同一份 accepted scope contract，不新增第二套 runtime metadata 来源。

完成标志：

- planning detail 仍最小化。
- accepted / continuation scope 的 runtime contract 要求单点定义。
- `runtimeOwnership=null` 不再作为 host entry HTML 的可接受 accepted scope 状态。

### Phase 3. 收口 unsupported shell write 的证据闭环

目标：让工具失败成为统一的结构化排障证据，而不是日志噪声。

实施：

1. unsupported shell write、非法重定向、越权写入等失败，由工具层继续使用既有 `ToolFailureCode` 体系表达，不新增新枚举。
2. attempt 聚合层继续复用既有 `GenerationFailureType`，只负责汇总失败类别，不复制工具层原因码。
3. review 如需给出高层语义结论，继续复用既有 `ReviewReasonCode`，不在执行链新增 review 外 reason-code。
4. event log、implementation diagnostics、attempt failure 摘要统一引用这条结构化证据链，避免 repair / review 再退回 prose 推断。

完成标志：

- unsupported shell write 的失败证据可追踪、可聚合、可被 repair / review 消费。
- 没有新增第四套 reason-code 体系。

### Phase 4. 黄金路径集成验证

目标：以真实 case 验证本轮增量收口是否有效。

实施：

1. 跑至少一条真实黄金路径 case。
2. 重点核对：
   - accepted scope 是否成为 coder 的唯一新增依赖边界
   - runtime contract 是否只在 accepted / continuation scope 生效
   - unsupported shell write 等失败是否留下结构化证据
3. 集成若失败，必须先基于结构化证据归因，再决定是否进入下一轮收口；不得回退成“凭现象猜根因”。

完成标志：

- 至少一条真实 case 通过，或失败原因已被结构化证据明确锁定。

## Test Plan

本轮测试只覆盖仍未闭合的问题，不重测已证明成立的基线结论。

### 1. runtime contract 边界

- planning detail 仍不出现 runtime metadata
- host entry HTML 进入 accepted / continuation scope 且需要 companion wiring 时，若 contract 缺失则在 coder 前阻断
- accepted scope contract 被 mutation guard 与 verifier 共同消费

### 2. failure evidence 闭环

- unsupported shell write 会产出结构化 `ToolFailureCode`
- attempt 聚合结果会保留对应的失败类别与证据引用
- repair / review 可读取同一份结构化失败证据

### 3. 黄金路径集成

- 跑一条真实 case
- 核对 accepted scope、runtime contract、failure evidence 三条主线

## Assumptions

1. 当前仓库中 `implementation_state.json` 单一真相源、prose-only success 收紧、PATCH concrete target 约束均已成立，本轮不重做。
2. runtime contract 的新增边界只允许落在 accepted / continuation scope，不回灌 planning detail。
3. `REWORK` 在 stage-level rollback / rework 中继续合法，本轮不会改变这一协议。
4. 若黄金路径集成再次失败，必须先读结构化证据，再决定下一轮改动面。

## Closure Decision

本轮可以直接进入实现，但前提是严格限定在“增量收口”范围内。

如果实现过程中发现需要重开以下任一已关闭路径：

- implementation machine truth
- prose-only success 判定
- PATCH concrete target 规范化
- planning detail runtime metadata

则说明本轮方案越界，必须先停下来回到文档层修正，而不是继续把旧问题重开。

## Completion Gate Result

只有同时满足以下条件，本轮才算成功：

1. 已完成项没有被重开或回退。
2. planning detail 仍保持最小协议。
3. accepted / continuation scope 的 runtime contract 边界已单点定义清楚。
4. host entry HTML 的 runtime contract 缺失不会再静默漏到 coder / verifier / review。
5. unsupported shell write 的结构化失败证据链已闭环。
6. 没有新增 reason-code 体系。
7. 已完成 `self-test + code review + 黄金路径集成验证 + 文档更新`。

任一条件不满足，都视为未完成。
