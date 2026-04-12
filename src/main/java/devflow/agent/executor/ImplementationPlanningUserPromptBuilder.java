package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.quality.QualityPlan;
import java.util.List;

/**
 * 负责 implementation planning 的 user prompt。
 *
 * <p>这层只负责把运行目标、上游文档、contract、planner context 与上一轮反馈
 * 收成稳定文本，不再和 system 侧的流程约束混在一个类里。
 */
final class ImplementationPlanningUserPromptBuilder {

    String build(
            RunRecord runRecord,
            String analysis,
            String prd,
            String design,
            String note,
            String workspaceContext,
            String plannerContextMarkdown,
            String performanceValidationGuidance,
            DeliveryPolicyEnvelope deliveryPolicy,
            ContractView contractView,
            QualityPlan qualityPlan,
            DocumentLanguage language,
            String requirementCatalog,
            ImplementationContinuationConstraints continuationConstraints,
            String planningFeedback
    ) {
        return """
                目标：
                %s

                约束：
                %s

                需求分析：
                %s

                产品需求文档：
                %s

                技术方案设计：
                %s

                结构化契约：
                %s

                质量计划：
                %s

                质量清单：
                %s

                权威覆盖引用目录：
                %s

                continuation 约束：
                %s

                当前备注：
                %s

                规划角色上下文切片：
                %s

                当前工作区上下文：
                %s

                %s：
                %s

                %s

                上一轮计划反馈：
                %s
                """.formatted(
                runRecord.goal(),
                runRecord.constraints(),
                analysis,
                prd,
                design,
                contractView == null ? PlaceholderValues.none(language) : contractView.toMarkdown(language),
                qualityPlan == null ? PlaceholderValues.none(language) : qualityPlan.toMarkdown(language),
                qualityPlan == null ? PlaceholderValues.none(language) : qualityPlan.qualityChecklist().toMarkdown(language),
                requirementCatalog,
                continuationConstraints == null ? PlaceholderValues.none(language) : continuationConstraints.toMarkdown(language),
                note,
                plannerContextMarkdown,
                workspaceContext,
                ArtifactLabels.requiredEvidence(language),
                renderBulletList(deliveryPolicy.requiredEvidence()),
                performanceValidationGuidance,
                planningFeedback.isBlank() ? PlaceholderValues.none(language) : planningFeedback
        );
    }

    private String renderBulletList(List<String> values) {
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
