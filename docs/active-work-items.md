# 当前执行清单

## 用途

这份文档只记录**当前主线**。

补充：

- 当前仓库的主线执行清单已切换到 `docs/architecture-refactor-work-items.md`
- 本文档保留为黄金路径与业务验证入口说明，不再承担当前架构整改的逐项追踪

规则：

- 只保留当前还在执行的主线，不再把历史大清单长期堆在这里
- 每完成一条，直接打勾
- 如果主线改变，先更新这份文档，再改代码
- 在当前四段全部完成前，不进入黄金路径集成测试

## 当前主线

当前业务验证主线仍是：

1. `黄金路径集成验证`

这条主线的目标不是继续补基础骨架，而是验证当前编码内核在真实 case 上是否已经收口：

- accepted change-set 是否真正成为 implementation coder 的唯一新增依赖边界
- planning detail 是否已经压成最小协议，不再在 plan 阶段猜 runtime metadata
- runtime ownership / wiring 是否只由实现与 verifier 基于产物事实裁决
- runtime wiring 续跑 scope 与 coder prompt 是否共享同一份文件契约
- patch continuation / review / test 是否继续沿用同一份结构化状态
- PRD 低权重条目是否只留在 `Source Metadata`，不再污染 `PRODUCT_CONTRACT`

## Final State

完成态必须同时满足：

- implementation plan / detail 不再要求模型输出 `editScope / runtimeOwnership / hostHtmlPatchRequired`
- coder 新引入的本地依赖路径只能落到 accepted change-set 或项目既有资产
- runtime ownership / wiring 只由实现与 verifier 链消费，不再由 planning detail 预判
- runtime wiring patch continuation 必须显式携带宿主 HTML 与 companion runtime 根脚本，coder prompt 中的当前 task package 必须和 effective change-set 对齐
- `PATCH_EXISTING_IMPLEMENTATION` 续跑必须显式携带结构化 `overrideChanges`；没有安全 scope 时只能阻断到人工，不能静默 auto-patch
- implementation verification 中，非实现类 TEST blocker 必须直接阻断到人工，不能静默放过或继续冒充 implementation retry
- PRD 的 `1-6` 正文承诺区不再保留显式 `推断 / 建议 / 设计选择 / 待确认问题`；这类条目只存在于 `Source Metadata`
- 黄金路径集成测试至少跑通一条真实 case

## Removal Plan

本轮必须同步删除：

- implementation planning detail 中旧的 runtime metadata 协议
- coder 通过私发新本地资产名绕开 accepted change-set 的旧路径
- coder 越界失败后回填单文件 HTML 继续推进的旧路径
- retry/continuation 中“实际 writable files 已缩窄，但当前子任务描述仍停留在旧 scope”的旧提示路径
- stage / subtask review 中“`PATCH_EXISTING_IMPLEMENTATION` 但 `overrideChanges=[]`”的旧续跑路径
- implementation verification 中“`patchTarget=NONE && overrideChanges=[]` 就直接放过”的旧分支

## Joint-Change Scope

以下内容必须一起改：

- 文档：`docs/current-state.md`、本文档
- 计划链：detail 最小协议、final gate 去掉 runtime metadata 预判
- coder tool loop：本地依赖闭包、HTML runtime ownership 变更时校验
- 测试：单测、代码 review、黄金路径集成验证

## 执行清单

### Phase 1. 计划与实现边界收口

- [x] detail 收敛成最小协议，不再输出 runtime metadata
- [x] implementation final gate 不再在 planning 阶段裁决 runtime ownership / wiring
- [x] 实现阶段新增本地依赖只能落到 accepted change-set 或项目既有资产
- [x] HTML runtime ownership 在 tool mutation 时校验，不再允许单文件回填绕开
- [x] runtime wiring 续跑 scope 显式带上宿主 HTML 与 companion runtime roots
- [x] coder prompt 的当前 task package 与 `effectiveChanges()` 对齐，并显式展示当前文件契约
- [x] `PATCH_EXISTING_IMPLEMENTATION` continuation / report / parser / resume 共用结构化 `overrideChanges`，不再允许空 scope 自动续跑
- [x] 子任务级空 scope patch review 会归一到当前 effective change-set；仍无安全 scope 时直接阻断人工
- [x] implementation verification 中，TEST blocker 不再静默放过；会显式转成人工阻断或当前阶段结构化失败
- [x] implementation stage roll-up 保留 `ROUTE_TO_REPAIR_TARGET`，不再把精确 repair scope 降级成泛化未完成态
- [x] PRD 低权重条目从正文承诺区收束到 `Source Metadata`，并从 `PRODUCT_CONTRACT` 投影中移除
- [x] implementation execute 成功态已收紧到“声明的文件交付契约已被工具真实落盘满足”；纯 assistant prose 不再冒充成功并泄漏到 observe/self-check

### Phase 2. 验证与集成

- [x] 跑本轮 `self-test`
- [x] 做一次 `code review`
- [ ] 跑黄金路径集成测试
- [ ] 根据集成结果更新 `docs/current-state.md`

## 当前状态

- 当前阶段：`黄金路径集成验证暂停，等待架构整改主线完成`
- 当前约束：`不允许场景特判、不允许文件名硬编码、不允许把 runtime metadata 塞回 planning detail`
- 当前阻塞：`当前优先级已切到架构整改；黄金路径验证后移到 architecture refactor 完成之后`
- 当前补充：`空 scope PATCH continuation 这一类历史 fatal 已在主链封死，待集成验证确认真实 case 不再复现`
- 当前判定标准：`不允许兼容层 / fallback / 双轨并存 / “后续再清理”`
