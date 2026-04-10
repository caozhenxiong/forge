package devflow.agent.context;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.List;

public record DesignContract(
        List<String> technicalGoals,
        List<String> architecturalBoundaries,
        List<String> dataModelCommitments,
        List<String> keyFlows,
        List<String> runtimeSurfaceRequirements,
        List<String> verificationRequirements,
        List<String> riskAndTradeoffs
) {

    public String toMarkdown() {
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                ## %s

                > %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s
                """.formatted(
                language.choose("设计契约（参考）", "Design Contract (Reference Only)"),
                language.choose("以下内容是对设计方案的参考性摘要；除非它同时出现在 Execution Contract 或 Source Metadata 的 hard.* 中，否则不自动升级为绑定硬约束。", "The following content is a reference summary of design choices; it must not be promoted to binding hard constraints unless it is also present in the Execution Contract or Source Metadata hard.* entries."),
                language.choose("技术目标", "Technical Goals"),
                bullets(technicalGoals, language),
                language.choose("架构边界", "Architectural Boundaries"),
                bullets(architecturalBoundaries, language),
                language.choose("数据模型约束", "Data Model Commitments"),
                bullets(dataModelCommitments, language),
                language.choose("关键流程", "Key Flows"),
                bullets(keyFlows, language),
                language.choose("运行表面要求", "Runtime Surface Requirements"),
                bullets(runtimeSurfaceRequirements, language),
                language.choose("验证要求", "Verification Requirements"),
                bullets(verificationRequirements, language),
                language.choose("风险与取舍", "Risk And Tradeoffs"),
                bullets(riskAndTradeoffs, language)
        ).trim();
    }

    private String bullets(List<String> items, DocumentLanguage language) {
        if (items == null || items.isEmpty()) {
            return PlaceholderValues.bulletNone(language);
        }
        return items.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .map(item -> "- " + item)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(PlaceholderValues.bulletNone(language));
    }
}
