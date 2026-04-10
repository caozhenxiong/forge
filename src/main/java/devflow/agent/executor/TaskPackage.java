package devflow.agent.executor;

import devflow.agent.context.SharedContextBundle;
import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.List;

public record TaskPackage(
        String title,
        String goal,
        String deliveryMode,
        boolean runnableMilestone,
        List<String> ownedFiles,
        List<String> coverageRefs,
        List<String> ownedCapabilities,
        List<String> deferredCapabilities,
        List<String> acceptanceCriteria,
        List<String> mustFixFirst,
        List<String> forbiddenDirections,
        String targetedContext,
        SharedContextBundle sharedContextBundle
) {

    public TaskPackage scopeToFile(String ownedFile, String fileTargetedContext) {
        return new TaskPackage(
                title,
                goal,
                deliveryMode,
                runnableMilestone,
                ownedFiles,
                coverageRefs,
                ownedCapabilities,
                deferredCapabilities,
                acceptanceCriteria,
                mustFixFirst,
                forbiddenDirections,
                fileTargetedContext == null || fileTargetedContext.isBlank() ? targetedContext : fileTargetedContext,
                sharedContextBundle
        );
    }

    public String toMarkdown() {
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                ## %s: %s

                - %s: %s
                - %s: %s
                - %s: %s

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

                ### %s

                ```text
                %s
                ```

                ### %s

                %s
                """.formatted(
                language.choose("任务包", "Task Package"),
                blank(title, language),
                language.choose("目标", "goal"),
                blank(goal, language),
                language.choose("交付模式", "deliveryMode"),
                blank(deliveryMode, language),
                language.choose("可运行里程碑", "Runnable Milestone"),
                runnableMilestone,
                language.choose("负责文件", "Owned Files"),
                bullets(ownedFiles, language),
                language.choose("覆盖引用", "Coverage Refs"),
                bullets(coverageRefs, language),
                language.choose("当前负责能力", "Owned Capabilities"),
                bullets(ownedCapabilities, language),
                language.choose("后续负责能力", "Deferred Capabilities"),
                bullets(deferredCapabilities, language),
                language.choose("验收标准", "Acceptance Criteria"),
                bullets(acceptanceCriteria, language),
                ArtifactLabels.mustFixFirst(language),
                bullets(mustFixFirst, language),
                ArtifactLabels.forbiddenDirections(language),
                bullets(forbiddenDirections, language),
                language.choose("目标上下文", "Targeted Context"),
                blank(targetedContext, language),
                language.choose("共享上下文引用", "Shared Context Reference"),
                sharedContextBundle == null ? PlaceholderValues.none(language) : sharedContextBundle.toMarkdown(language)
        ).trim();
    }

    private String bullets(List<String> values, DocumentLanguage language) {
        if (values == null || values.isEmpty()) {
            return PlaceholderValues.bulletNone(language);
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .map(value -> "- " + value)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(PlaceholderValues.bulletNone(language));
    }

    private String blank(String value, DocumentLanguage language) {
        return PlaceholderValues.orNone(value, language);
    }
}
