package devflow.agent.context;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;

public record ProjectedContext(
        String currentStageSummary,
        String upstreamContractSummary,
        String authoritativeRequirementCatalog,
        String recentHistorySummary,
        String failureSummary,
        String repairSummary,
        String workingSetSummary,
        TaskMemory taskMemory,
        ContextViews contextViews
) {

    public ProjectedContext(
            String currentStageSummary,
            String upstreamContractSummary,
            String authoritativeRequirementCatalog,
            String recentHistorySummary,
            String failureSummary,
            String repairSummary,
            String workingSetSummary,
            TaskMemory taskMemory
    ) {
        this(
                currentStageSummary,
                upstreamContractSummary,
                authoritativeRequirementCatalog,
                recentHistorySummary,
                failureSummary,
                repairSummary,
                workingSetSummary,
                taskMemory,
                null
        );
    }

    public String toMarkdown() {
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
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
                """.formatted(
                language.choose("投影视图上下文", "Projected Context"),
                language.choose("当前阶段", "Current Stage"),
                blank(currentStageSummary, language),
                language.choose("上游契约", "Upstream Contract"),
                blank(upstreamContractSummary, language),
                language.choose("权威需求目录", "Authoritative Requirement Catalog"),
                blank(authoritativeRequirementCatalog, language),
                language.choose("最近历史", "Recent History"),
                blank(recentHistorySummary, language),
                language.choose("失败摘要", "Failure Summary"),
                blank(failureSummary, language),
                language.choose("修复摘要", "Repair Summary"),
                blank(repairSummary, language),
                language.choose("工作集", "Working Set"),
                blank(workingSetSummary, language)
        ).trim();
    }

    /**
     * 按角色输出上下文切片。
     *
     * <p>Phase 5 开始后，角色默认应优先消费切片视图，而不是无差别读取整份 projected context。
     */
    public String toMarkdown(ContextAccessProfile profile, DocumentLanguage language) {
        if (contextViews == null) {
            return toMarkdown(language);
        }
        return contextViews.forProfile(profile).toMarkdown(language);
    }

    private String blank(String value, DocumentLanguage language) {
        return PlaceholderValues.orNone(value, language);
    }
}
