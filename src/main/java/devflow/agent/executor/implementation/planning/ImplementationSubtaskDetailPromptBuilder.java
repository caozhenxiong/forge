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
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;

/**
 * 负责单个 implementation 子任务 detail 的 prompt。
 *
 * <p>detail 只围绕当前子任务自己的 targetPaths 输出精确变更声明，
 * 不再让模型一次性描述整份计划的所有文件修改。
 */
final class ImplementationSubtaskDetailPromptBuilder {

    private final int maxFilesPerSubtask;

    ImplementationSubtaskDetailPromptBuilder(int maxFilesPerSubtask) {
        this.maxFilesPerSubtask = maxFilesPerSubtask;
    }

    String systemPrompt(
            DeliveryPolicyEnvelope deliveryPolicy,
            FixMode fixMode,
            ImplementationPatchTarget implementationPatchTarget,
            ImplementationContinuationConstraints continuationConstraints
    ) {
        StringBuilder builder = new StringBuilder("""
                你是 implementation 子任务 detail 规划器。
                你需要把一次大的实现任务拆成可落地、可验证的子步骤。
                你必须只返回一个 JSON 对象，不要输出任何额外解释。
                JSON 格式：
                {
                  "subtaskId": "subtask-1",
                  "changes": [
                    {
                      "path": "相对路径",
                      "action": "WRITE|DELETE",
                      "reason": "为什么要改这个文件",
                      "runtimeScriptRole": "ROOT|LEAF|null"
                    }
                  ]
                }

                约束：
                1. 只能返回 JSON
                2. changes 只允许覆盖当前子任务自己的 targetPaths
                3. 不要新增不在 targetPaths 内的文件
                4. changes 至少 1 个，最多 %d 个
                5. 只返回最小文件变更声明，不要返回 editScope、runtimeOwnership、hostHtmlPatchRequired 等旧字段
                6. 不要改动当前子任务 targetPaths 之外的路径
                7. 不要试图修改 deliveryMode、targetPaths 或其他 outline 字段
                8. runtimeScriptRole 只允许用于当前 HTML 入口树下、且不在当前 reachable runtime graph 内的新增 runtime 脚本
                9. 若新增脚本本身会成为新的 runtime root，标记为 ROOT，并在同一子任务里携带宿主 HTML patch
                10. 若新增脚本只是挂在当前 reachable runtime anchor 下的 leaf/module，标记为 LEAF
                11. 非 runtime 文件、宿主 HTML、以及当前已 reachable 的 runtime 文件，不要填写 runtimeScriptRole
                """.formatted(maxFilesPerSubtask));
        if (deliveryPolicy != null) {
            builder.append("""

                    本轮交付策略：
                    1. 每个子任务最多改 %d 个文件
                    2. 每个子任务最多变更 %d 个符号
                    3. preferPreciseEditing=%s
                    """.formatted(
                    deliveryPolicy.maxFiles(),
                    deliveryPolicy.maxSymbols(),
                    deliveryPolicy.preferPreciseEditing()
            ));
        }
        if (fixMode == FixMode.PATCH) {
            builder.append("""

                    这是 PATCH continuation：
                    1. 只围绕当前子任务现有实现补局部缺口
                    2. 不要重新规划其他子任务
                    """);
        }
        if (implementationPatchTarget == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            builder.append("""

                    当前 PATCH 目标是运行时接线：
                    1. 只声明需要修复的入口文件、伴随文件或初始化文件
                    2. 只修复入口、引用路径、初始化或模块连通问题
                    3. 不要把运行时接线修复扩展成整页重写或重做主逻辑
                    """);
        }
        if (continuationConstraints != null && continuationConstraints.active()) {
            builder.append("""

                    这是 continuation 规划：
                    1. 已存在文件不得退回 SKELETON
                    2. 现有 HTML 入口不得退回整页重写或 REWORK
                    """);
        }
        return builder.toString();
    }

    String userPrompt(
            RunRecord runRecord,
            ContractView contractView,
            QualityPlan qualityPlan,
            DocumentLanguage language,
            String workspaceContext,
            PlanningRuntimeFacts runtimeFacts,
            ImplementationOutline outline,
            ImplementationOutlineSubtask subtask,
            String feedback
    ) {
        return """
                目标：
                %s

                当前子任务：
                - id: %s
                - 标题: %s
                - 目标: %s
                - 交付模式: %s
                - runnableMilestone: %s
                - targetPaths: %s
                - coverageRefs: %s
                - ownedCapabilities: %s
                - deferredCapabilities: %s
                - acceptanceCriteria: %s

                outline 摘要：
                %s

                结构化契约：
                %s

                当前 runtime 事实：
                - htmlEntryPath: %s
                - reachableRuntimePaths: %s
                - runtimeRootPaths: %s

                质量计划：
                %s

                当前工作区上下文：
                %s

                上一轮当前子任务反馈：
                %s
                """.formatted(
                runRecord.goal(),
                subtask.id(),
                subtask.title(),
                subtask.goal(),
                subtask.deliveryMode(),
                subtask.runnableMilestone(),
                safeList(subtask.targetPaths()),
                safeList(subtask.coverageRefs()),
                safeList(subtask.ownedCapabilities()),
                safeList(subtask.deferredCapabilities()),
                safeList(subtask.acceptanceCriteria()),
                outline == null ? "" : outline.summary(),
                contractView == null ? PlaceholderValues.none(language) : contractView.toMarkdown(language),
                runtimeFacts == null || !runtimeFacts.hasResolvedHtmlEntry() ? PlaceholderValues.none(language) : runtimeFacts.htmlEntryPath(),
                runtimeFacts == null ? "[]" : safePathList(runtimeFacts.reachableRuntimePaths()),
                runtimeFacts == null ? "[]" : safePathList(runtimeFacts.runtimeRootPaths()),
                qualityPlan == null ? PlaceholderValues.none(language) : qualityPlan.toMarkdown(language),
                workspaceContext,
                feedback == null || feedback.isBlank() ? PlaceholderValues.none(language) : feedback
        );
    }

    private String safeList(java.util.List<String> values) {
        return values == null || values.isEmpty() ? "[]" : values.toString();
    }

    private String safePathList(java.util.List<java.nio.file.Path> values) {
        return values == null || values.isEmpty()
                ? "[]"
                : values.stream().map(java.nio.file.Path::toString).toList().toString();
    }
}
