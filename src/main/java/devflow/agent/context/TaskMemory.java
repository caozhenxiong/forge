package devflow.agent.context;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.List;

public record TaskMemory(
        String goal,
        String constraints,
        String currentStageSummary,
        String upstreamContractSummary,
        String authoritativeRequirementCatalog,
        String recentHistorySummary,
        String failureSummary,
        String repairSummary,
        String workingSetSummary,
        List<FailureDigest> recentFailures
) {

    public String toMarkdown() {
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(DocumentLanguage language) {
        StringBuilder builder = new StringBuilder("""
                # %s

                ## %s

                %s

                ## %s

                %s

                ## %s

                %s

                ## %s

                %s

                ## %s

                %s

                ## %s

                %s

                ## %s

                %s

                ## %s

                %s

                ## %s

                %s

                ## %s

                """.formatted(
                language.choose("任务记忆", "Task Memory"),
                language.choose("目标", "Goal"),
                blank(goal, language),
                language.choose("约束", "Constraints"),
                blank(constraints, language),
                language.choose("当前阶段摘要", "Current Stage Summary"),
                blank(currentStageSummary, language),
                language.choose("上游契约摘要", "Upstream Contract Summary"),
                blank(upstreamContractSummary, language),
                language.choose("权威需求目录", "Authoritative Requirement Catalog"),
                blank(authoritativeRequirementCatalog, language),
                language.choose("最近历史摘要", "Recent History Summary"),
                blank(recentHistorySummary, language),
                language.choose("失败摘要", "Failure Summary"),
                blank(failureSummary, language),
                language.choose("修复摘要", "Repair Summary"),
                blank(repairSummary, language),
                language.choose("工作集摘要", "Working Set Summary"),
                blank(workingSetSummary, language),
                language.choose("最近失败", "Recent Failures")
        ));
        if (recentFailures == null || recentFailures.isEmpty()) {
            builder.append(PlaceholderValues.bulletNone(language)).append('\n');
        } else {
            for (FailureDigest failure : recentFailures) {
                builder.append(failure.toMarkdown(language)).append("\n\n");
            }
        }
        return builder.toString().trim();
    }

    private String blank(String value, DocumentLanguage language) {
        return PlaceholderValues.orNone(value, language);
    }
}
