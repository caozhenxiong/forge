package devflow.agent.executor.implementation.planning;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.domain.RunRecord;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;

/**
 * 负责 implementation outline 的 prompt。
 *
 * <p>outline 只解决“怎么拆、谁负责什么、每个子任务拥有哪组目标文件”，
 * 不再要求模型一次性返回完整 file change 细节。
 */
final class ImplementationOutlinePromptBuilder {

    private final int maxFilesPerSubtask;

    ImplementationOutlinePromptBuilder(int maxFilesPerSubtask) {
        this.maxFilesPerSubtask = maxFilesPerSubtask;
    }

    String systemPrompt(
            String note,
            DeliveryPolicyEnvelope deliveryPolicy,
            FixMode fixMode,
            ImplementationPatchTarget implementationPatchTarget,
            ImplementationContinuationConstraints continuationConstraints
    ) {
        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(note);
        StringBuilder builder = new StringBuilder("""
                你是 implementation outline 规划器。
                你需要把一次大的实现任务拆成可落地、可验证的子步骤。
                你必须只返回一个 JSON 对象，不要输出任何额外解释。
                JSON 格式：
                {
                  "summary": "本次实现总体摘要",
                  "subtasks": [
                    {
                      "id": "subtask-1",
                      "title": "子任务标题",
                      "goal": "该子任务要完成什么",
                      "deliveryMode": "SKELETON|INCREMENTAL|PATCH|REWORK",
                      "runnableMilestone": true,
                      "coverageRefs": ["CAP-1", "QCAP-TIMED_STATE_PROGRESSION"],
                      "ownedCapabilities": ["当前子任务必须完成的能力1"],
                      "deferredCapabilities": ["明确留给后续子任务的能力1"],
                      "acceptanceCriteria": ["验收标准1", "验收标准2"],
                      "targetPaths": ["index.html", "src/app.js"]
                    }
                  ]
                }

                约束：
                1. 只能返回 JSON
                2. 子任务数量控制在 3 到 6 个；PATCH 模式可收敛到 1 到 3 个
                3. 每个子任务都必须可单独验证
                4. 每个子任务都必须声明自己的 targetPaths
                5. 如果多个子任务顺序修改同一路径，必须保证步骤顺序自洽，不能让后续子任务越权改动无关文件
                6. targetPaths 只写相对路径，不写 action/reason/editScope
                7. 每个子任务 targetPaths 默认最多 %d 个
                8. 不要在 outline 阶段输出具体文件内容或 changes
                9. 必须覆盖执行契约要求的入口、运行表面和权威 coverageRefs 中 obligation=planning-required 的项
                10. runnableMilestone=true 的子任务必须负责把交付物推进到可启动、可验证的状态
                11. html-entry 场景下，runnableMilestone 子任务必须直接覆盖 HTML 入口文件
                12. continuation 时必须建立在现有文件事实之上，不得把已有文件退回骨架或重新开局
                13. obligation=optional 的 coverage ref 只有在你明确打算实现该增强时才写进 coverageRefs；待确认问题不得写进 implementation ownership
                """.formatted(maxFilesPerSubtask));
        builder.append(PlanningBoundaryContractPromptSupport.outlineSharedFileRuleBlock());
        if (deliveryPolicy != null) {
            builder.append("""

                    本轮交付策略：
                    1. 建议交付模式=%s
                    2. 默认每个子任务最多改 %d 个文件；若策略显式允许，可放宽到最多 %d 个文件
                    3. 当前策略下每个子任务最多改 %d 个文件
                    4. 每个子任务最多变更 %d 个符号
                    5. preferPreciseEditing=%s
                    6. forceBacklogSplit=%s
                    7. requireVerificationBeforeReview=%s
                    """.formatted(
                    deliveryPolicy.mode(),
                    maxFilesPerSubtask,
                    deliveryPolicy.maxFiles(),
                    deliveryPolicy.maxFiles(),
                    deliveryPolicy.maxSymbols(),
                    deliveryPolicy.preferPreciseEditing(),
                    deliveryPolicy.forceBacklogSplit(),
                    deliveryPolicy.requireVerificationBeforeReview()
            ));
        }
        if (fixMode == FixMode.PATCH) {
            builder.append("""

                    这是 PATCH continuation：
                    1. 只围绕当前缺口补局部能力
                    2. 不要重新开新的 backlog
                    3. 已存在文件不得退回 SKELETON
                    """);
        }
        if (implementationPatchTarget == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            builder.append("""

                    当前 PATCH 目标是运行时接线：
                    1. 必须沿用现有 runtime contract
                    2. 只修复入口、引用路径、初始化或模块连通问题
                    3. 不要回退到宿主内联主逻辑
                    """);
        }
        if (continuationConstraints != null && continuationConstraints.active()) {
            builder.append("""

                    这是 continuation 规划：
                    1. 已存在文件不得退回 SKELETON
                    2. 已存在 HTML 入口不得退回整页重写或 REWORK
                    3. 若上一轮已经确定 runtime contract，本轮不得切换所有权模式
                    """);
        }
        if (Boolean.TRUE.equals(directives.repairBriefPresent())
                || Boolean.TRUE.equals(directives.repairBriefEnforced())) {
            builder.append("""

                    这是 repair brief 驱动的修复：
                    1. 优先围绕 diagnosis 输出的根因和证据修复
                    2. 不要沿着 Forbidden Directions 重复失败路径
                    """);
        }
        return builder.toString();
    }

    String userPrompt(
            RunRecord runRecord,
            String workspaceContext,
            String plannerContextMarkdown,
            DeliveryPolicyEnvelope deliveryPolicy,
            ContractView contractView,
            QualityPlan qualityPlan,
            DocumentLanguage language,
            String requirementCatalog,
            ImplementationContinuationConstraints continuationConstraints,
            String feedback
    ) {
        return """
                目标：
                %s

                约束：
                %s

                结构化契约：
                %s

                质量计划：
                %s

                权威覆盖引用目录：
                %s

                continuation 约束：
                %s

                规划角色上下文切片：
                %s

                当前工作区上下文：
                %s

                shared-file boundary 提醒：
                %s

                必需证据：
                %s

                上一轮 outline 反馈：
                %s
                """.formatted(
                runRecord.goal(),
                runRecord.constraints(),
                contractView == null ? PlaceholderValues.none(language) : contractView.toMarkdown(language),
                qualityPlan == null ? PlaceholderValues.none(language) : qualityPlan.toMarkdown(language),
                requirementCatalog,
                continuationConstraints == null ? PlaceholderValues.none(language) : continuationConstraints.toMarkdown(language),
                plannerContextMarkdown,
                workspaceContext,
                language.choose(
                        "若多个子任务共享同一路径，前面的子任务必须把后续 owner 的 ownedCapabilities 全量写进 deferredCapabilities；不要留空，也不要只写一部分。",
                        "If multiple subtasks share the same path, earlier subtasks must defer the full ownedCapabilities set of each downstream owner; do not leave it empty or partial."
                ),
                renderBulletList(deliveryPolicy == null ? null : deliveryPolicy.requiredEvidence()),
                feedback == null || feedback.isBlank() ? PlaceholderValues.none(language) : feedback
        );
    }

    private String renderBulletList(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return PlaceholderValues.bulletMachineNone();
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("- ").append(value.trim());
        }
        return builder.length() == 0 ? PlaceholderValues.bulletMachineNone() : builder.toString();
    }
}
