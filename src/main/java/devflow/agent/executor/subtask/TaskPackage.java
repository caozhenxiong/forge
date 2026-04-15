package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.SharedContextBundle;
import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.ArrayList;
import java.util.List;

import devflow.agent.executor.FileChange;
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

    public TaskPackage {
        ownedFiles = normalizePaths(ownedFiles);
        coverageRefs = normalizeValues(coverageRefs);
        ownedCapabilities = normalizeValues(ownedCapabilities);
        deferredCapabilities = normalizeValues(deferredCapabilities);
        acceptanceCriteria = normalizeValues(acceptanceCriteria);
        mustFixFirst = normalizeValues(mustFixFirst);
        forbiddenDirections = normalizeValues(forbiddenDirections);
    }

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

    public TaskPackage alignToSubtask(Subtask subtask) {
        if (subtask == null) {
            return this;
        }
        return new TaskPackage(
                blankValue(subtask.title(), title),
                blankValue(subtask.goal(), goal),
                subtask.deliveryMode() == null ? deliveryMode : subtask.deliveryMode().name(),
                subtask.runnableMilestone(),
                ownedFilesFromChanges(subtask.changes(), ownedFiles),
                emptyAware(subtask.coverageRefs(), coverageRefs),
                explicitOnly(subtask.ownedCapabilities()),
                explicitOnly(subtask.deferredCapabilities()),
                emptyAware(subtask.acceptanceCriteria(), acceptanceCriteria),
                mustFixFirst,
                forbiddenDirections,
                targetedContext,
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
                language.choose("边界契约提醒", "Boundary Contract Reminder"),
                boundaryContractReminder(language),
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

    private String blankValue(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private List<String> ownedFilesFromChanges(List<FileChange> changes, List<String> fallback) {
        if (changes == null || changes.isEmpty()) {
            return fallback == null ? List.of() : List.copyOf(fallback);
        }
        ArrayList<String> paths = new ArrayList<>();
        for (FileChange change : changes) {
            if (change == null || change.path() == null || change.path().isBlank()) {
                continue;
            }
            String normalized = java.nio.file.Path.of(change.path()).normalize().toString().replace('\\', '/');
            if (!paths.contains(normalized)) {
                paths.add(normalized);
            }
        }
        return paths.isEmpty() ? (fallback == null ? List.of() : List.copyOf(fallback)) : List.copyOf(paths);
    }

    private List<String> emptyAware(List<String> preferred, List<String> fallback) {
        if (preferred != null && !preferred.isEmpty()) {
            return List.copyOf(preferred);
        }
        return fallback == null ? List.of() : List.copyOf(fallback);
    }

    private List<String> explicitOnly(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return normalizeValues(values);
    }

    private String boundaryContractReminder(DocumentLanguage language) {
        return language.choose(
                """
                - 当前子任务只允许覆盖上面的负责文件、ownedCapabilities 与 acceptanceCriteria。
                - deferredCapabilities 只允许留给后续子任务；即使共享文件，也不能提前实现。
                - 若 reviewer 标记 capability boundary violation，offendingPaths 只能来自当前子任务的结构化变更范围。
                """.trim(),
                """
                - The current subtask may only touch the owned files, ownedCapabilities, and acceptanceCriteria listed above.
                - deferredCapabilities remain downstream-only scope, even when files are shared.
                - If the reviewer flags a capability boundary violation, offendingPaths must come from the current subtask's structured change-set.
                """.trim()
        );
    }

    private List<String> normalizeValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }

    private List<String> normalizePaths(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(java.nio.file.Path.of(value.trim()).normalize().toString().replace('\\', '/'));
        }
        return List.copyOf(normalized);
    }
}
