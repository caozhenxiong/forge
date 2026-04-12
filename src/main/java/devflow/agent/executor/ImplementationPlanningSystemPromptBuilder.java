package devflow.agent.executor;

import devflow.agent.orchestrator.RunRecord;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;

/**
 * 负责 implementation planning 的 system prompt。
 *
 * <p>这里集中维护 planning 阶段的硬约束、交付策略、修复模式和 repair brief 规则，
 * 避免 `ImplementationPlanningPromptAssembler` 同时承担 system/user 两侧的长模板拼装。
 */
final class ImplementationPlanningSystemPromptBuilder {

    private final int maxFilesPerSubtask;
    private final int maxDeliveryPolicyFiles;

    ImplementationPlanningSystemPromptBuilder(int maxFilesPerSubtask, int maxDeliveryPolicyFiles) {
        this.maxFilesPerSubtask = maxFilesPerSubtask;
        this.maxDeliveryPolicyFiles = maxDeliveryPolicyFiles;
    }

    String build(
            String note,
            String performanceValidationGuidance,
            boolean preferSkeletonFlow,
            DeliveryPolicyEnvelope deliveryPolicy,
            FixMode fixMode,
            ImplementationPatchTarget implementationPatchTarget,
            ImplementationContinuationConstraints continuationConstraints
    ) {
        String system = baseSystemPrompt();
        system = appendDeliveryPolicy(system, deliveryPolicy);
        system = appendFixModeGuidance(system, fixMode);
        system = appendPatchTargetGuidance(system, implementationPatchTarget);
        system = appendRepairBriefGuidance(system, note);
        if (preferSkeletonFlow) {
            system = appendSkeletonFlowGuidance(system);
        }
        if (!performanceValidationGuidance.isBlank()) {
            system = appendPerformanceGuidance(system);
        }
        if (continuationConstraints != null && continuationConstraints.active()) {
            system = appendContinuationGuidance(system, implementationPatchTarget);
        }
        return system;
    }

    private String baseSystemPrompt() {
        return """
                你是一个资深软件工程师。你需要把一次大的实现任务拆成可落地、可验证的子步骤。
                你必须只返回一个 JSON 对象，不要输出任何额外解释。
                JSON 格式：
                {
                  "summary": "本次实现总体摘要",
                  "subtasks": [
                    {
                      "title": "子任务标题",
                      "goal": "该子任务要完成什么",
                      "deliveryMode": "SKELETON|INCREMENTAL|PATCH|REWORK",
                      "runnableMilestone": true,
                      "coverageRefs": ["CAP-1", "QCAP-TIMED_STATE_PROGRESSION"],
                      "ownedCapabilities": ["当前子任务必须完成的能力1"],
                      "deferredCapabilities": ["明确留给后续子任务的能力1"],
                      "acceptanceCriteria": ["验收标准1", "验收标准2"],
                      "changes": [
                        {
                          "path": "相对路径",
                          "action": "WRITE|DELETE",
                          "reason": "为什么要改这个文件",
                          "editScope": "AUTO|HOST_HTML_PATCH|INLINE_SCRIPT_PATCH|INLINE_STYLE_PATCH",
                          "runtimeOwnership": "INLINE_HOST|EXTERNAL_COMPANION|null",
                          "hostHtmlPatchRequired": false
                        }
                      ]
                    }
                  ]
                }

                约束：
                1. 子任务数量控制在 3 到 6 个
                2. 每个子任务都必须可单独验证
                3. 默认每个子任务最多改 %d 个文件；若本轮交付策略显式允许，可放宽到最多 %d 个文件
                4. 优先最小改动
                5. 只列出真正需要改动的文件
                6. 不要在此步骤输出文件内容
                7. 保持项目可编译、可测试
                8. deliveryMode 必须明确选择
                9. coverageRefs 必须只引用“权威覆盖引用目录”中出现的 ID，用来说明当前子任务直接承担了哪些产品能力或必需质量能力；final-acceptance 类型的引用可选，不要求每个子任务显式覆盖
                10. ownedCapabilities 必须只列当前子任务自己负责交付的能力
                11. deferredCapabilities 必须列明确留给后续子任务处理的能力，不能把它们混进当前子任务验收
                12. 若任务较大，优先拆成“最小可运行入口/表面 -> 核心功能填充 -> 接线与验证 -> polish”
                13. 若某个子任务使用 SKELETON，只能表示先建立可运行壳；后续必须至少安排一个非 SKELETON 子任务补齐行为、接线或集成，不能让计划停在空壳状态
                14. 只有 Execution Contract，以及 Source Metadata 中的 %s / %s 属于绑定约束；%s / %s / %s / %s 只能作为参考，不能直接当成必须满足的硬要求
                15. 如果 Execution Contract 要求 launch 或 surface，至少必须有一个子任务把 runnableMilestone 设为 true，用来负责入口接线、运行时初始化、模块集成或最小可运行验证
                16. runnableMilestone=true 的子任务必须负责把当前交付物推进到“可启动、可验证”的状态，不能只是静态骨架或占位页面
                17. 对 html-entry 场景，runnableMilestone 只要求形成可启动、可验证的入口与运行表面；后续子任务可以继续在同一入口文件内做更细的稳定编辑，也可以拆成本地相对路径模块，但不要把某一种文件组织方式当成唯一合法方案
                18. editScope 只用于声明编辑内核的首选 patch 作用域：
                    - 非 html-entry 文件默认使用 AUTO
                    - 需要同时修改宿主 HTML 的 markup/style/script 多个区块时，用 HOST_HTML_PATCH
                    - 只需要改 <script id="app-script"> 时，用 INLINE_SCRIPT_PATCH
                    - 只需要改 <style id="app-style"> 时，用 INLINE_STYLE_PATCH
                19. 只要 changes 里包含 html-entry 入口文件，该条变更就必须显式声明 editScope / runtimeOwnership / hostHtmlPatchRequired，不能省略：
                    - html-entry 不允许使用 AUTO
                    - HOST_HTML_PATCH 对应 hostHtmlPatchRequired=true
                    - INLINE_SCRIPT_PATCH / INLINE_STYLE_PATCH 对应 hostHtmlPatchRequired=false
                20. 只要 changes 里包含 html-entry 入口文件，该条变更就必须显式声明 runtimeOwnership：
                    - INLINE_HOST: 主运行时继续由宿主 HTML 自己持有
                    - EXTERNAL_COMPANION: 宿主 HTML 只保留接线，主运行时外提到派生 companion 脚本
                21. 同一个 html-entry 在同一轮计划里只能使用一种 runtimeOwnership，禁止一边保留完整内联主逻辑，一边再补 external runtime
                22. 若 html-entry 使用 EXTERNAL_COMPANION：
                    - 若上一轮 continuation 已给出 runtime contract，必须沿用同一组 runtime 根脚本，不得自行改名或换入口
                    - 若当前是首次外提主运行时，同一子任务必须同时声明宿主 HTML 与至少一个 runtime root 脚本变更
                """.formatted(
                maxFilesPerSubtask,
                maxDeliveryPolicyFiles,
                devflow.agent.context.SourceMetadataKeys.HARD_USER_REQUIREMENTS,
                devflow.agent.context.SourceMetadataKeys.HARD_UPSTREAM_FACTS,
                devflow.agent.context.SourceMetadataKeys.SOFT_INFERENCES,
                devflow.agent.context.SourceMetadataKeys.SOFT_DESIGN_DECISIONS,
                devflow.agent.context.SourceMetadataKeys.SOFT_RECOMMENDATIONS,
                devflow.agent.context.SourceMetadataKeys.OPEN_QUESTIONS
        );
    }

    private String appendDeliveryPolicy(String system, DeliveryPolicyEnvelope deliveryPolicy) {
        return system + """

                本轮交付策略：
                1. deliveryMode 参考建议：%s
                2. 每个子任务最多改 %d 个文件
                3. 每个子任务最多变更 %d 个符号
                4. preferPreciseEditing=%s
                5. forceBacklogSplit=%s
                6. requireVerificationBeforeReview=%s
                """.formatted(
                deliveryPolicy.mode(),
                deliveryPolicy.maxFiles(),
                deliveryPolicy.maxSymbols(),
                deliveryPolicy.preferPreciseEditing(),
                deliveryPolicy.forceBacklogSplit(),
                deliveryPolicy.requireVerificationBeforeReview()
        );
    }

    private String appendFixModeGuidance(String system, FixMode fixMode) {
        if (fixMode == FixMode.PATCH) {
            return system + """

                    这是修复模式：
                    1. 只围绕当前反馈做最小补丁修改
                    2. 优先复用现有文件和现有模块，不要新增重复模块
                    3. 不要推翻已经完成的功能
                    4. 子任务数量尽量控制在 1 到 3 个
                    """;
        }
        if (fixMode == FixMode.REWORK) {
            return system + """

                    这是重构模式：
                    1. 可以调整文件结构和模块划分来解决结构性问题
                    2. 优先消除重复实现、未接线文件和错误分层
                    3. 仍然要围绕当前代码和需求收敛，不要无意义推翻重来
                    4. 子任务数量控制在 3 到 6 个
                    """;
        }
        return system;
    }

    private String appendRepairBriefGuidance(String system, String note) {
        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(note);
        if (!Boolean.TRUE.equals(directives.repairBriefPresent()) && !Boolean.TRUE.equals(directives.repairBriefEnforced())) {
            return system;
        }
        return system + """

                这是 repair brief 驱动的修复：
                1. 优先围绕 diagnosis 输出的根因和证据修复
                2. 子任务要直接对准 Must Fix First、Acceptance Target 和 Acceptance Checks
                3. 不要重新发散成新的大范围实现目标
                4. 不要沿着 Forbidden Directions 继续重复失败路径
                """;
    }

    private String appendPatchTargetGuidance(String system, ImplementationPatchTarget implementationPatchTarget) {
        if (implementationPatchTarget == null || implementationPatchTarget == ImplementationPatchTarget.NONE) {
            return system;
        }
        if (implementationPatchTarget == ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION) {
            return system + """

                    当前是 completed-plan PATCH continuation：
                    1. 只能基于现有实现补局部缺口，不要重新开新的 backlog
                    2. 不要把已存在文件退回 SKELETON，也不要重新发散成大范围 REWORK
                    3. 子任务要直接围绕当前 review/changeRequest 指出的缺口组织
                    """;
        }
        if (implementationPatchTarget == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            return system + """

                    当前 PATCH 目标是修复运行时接线：
                    1. 必须沿用现有 runtime contract，不要改名、换入口或发明新的 companion 文件名
                    2. 只修复 HTML 入口、引用路径、初始化或模块连通问题
                    3. 不要重新规划完整实现，也不要回退到宿主内联主逻辑
                    """;
        }
        return system;
    }

    private String appendSkeletonFlowGuidance(String system) {
        return system + """

                当前任务更适合渐进交付：
                1. 第一子任务优先建立最小可运行入口或运行表面，deliveryMode 使用 SKELETON
                2. 优先按入口、接线、核心逻辑、验证与 polish 逐步拆分，不要把所有能力塞进单个文件或单个子任务
                3. 后续子任务使用 INCREMENTAL，逐步补齐核心能力与交互
                4. 不要试图在一个子任务里完成整个产品
                5. 每个子任务完成后，项目应保持“至少可启动、可自检”
                """;
    }

    private String appendPerformanceGuidance(String system) {
        return system + """

                DESIGN 已定义基础性能/验证要求：
                1. 若技术方案要求记录耗时或 benchmark，请把相应测量入口纳入实现子任务
                2. 若需要在 TEST 阶段自动验收，优先提供可读取的页面测量信号，例如 %s
                3. 优先补充轻量、可执行的基础测量，不要无意义扩展复杂压测框架
                4. 若设计没有要求性能测量，不要自行发散额外 benchmark
                """.formatted(WebRuntimeMetricKeys.PUBLIC_METRICS_OBJECT);
    }

    private String appendContinuationGuidance(String system, ImplementationPatchTarget implementationPatchTarget) {
        String runtimeContractRule = "3. 若上一轮 HTML 入口已经确定 runtime contract，本轮不得切换所有权模式，也不得更换已确认的 runtime 根脚本";
        return system + """

                这是 continuation 规划：
                1. 已存在文件不得退回 SKELETON
                2. 已存在 HTML 入口不得退回整页重写或 REWORK
                %s
                4. 当前规划必须建立在上一轮已稳定文件事实之上，不得把 continuation 当作重新开局
                """.formatted(runtimeContractRule);
    }
}
