package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.quality.CapabilityIds;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * TEST 阶段唯一允许消费的运行时 contract。
 *
 * <p>它只表达：
 * 1. 当前入口由哪些文件持有主运行时；
 * 2. 进入运行态的显式入口；
 * 3. 每个需要观测的能力项应该观测哪一个目标。
 */
public record UiRuntimeContract(
        String entryPath,
        List<String> ownerPaths,
        List<String> runStateEntryTargets,
        List<UiObservationTarget> observationTargets
) {

    public UiRuntimeContract {
        entryPath = entryPath == null ? "" : entryPath.trim().replace('\\', '/');
        ownerPaths = normalizeTextList(ownerPaths);
        runStateEntryTargets = normalizeTextList(runStateEntryTargets);
        observationTargets = observationTargets == null ? List.of() : List.copyOf(observationTargets);
    }

    public static UiRuntimeContract empty() {
        return new UiRuntimeContract("", List.of(), List.of(), List.of());
    }

    public UiRuntimeContract withRunStateEntryTargets(List<String> targets) {
        return new UiRuntimeContract(entryPath, ownerPaths, targets, observationTargets);
    }

    public UiObservationTarget targetFor(String capabilityId) {
        String normalized = CapabilityIds.normalize(capabilityId);
        if (normalized.isBlank()) {
            return null;
        }
        for (UiObservationTarget target : observationTargets) {
            if (target != null && normalized.equals(target.capabilityId())) {
                return target;
            }
        }
        return null;
    }

    public boolean hasRunStateEntryTargets() {
        return runStateEntryTargets != null && !runStateEntryTargets.isEmpty();
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                # %s

                - entryPath: %s

                ## ownerPaths

                %s

                ## runStateEntryTargets

                %s

                ## observationTargets

                %s
                """.formatted(
                language.choose("运行时观测契约", "UI Runtime Contract"),
                PlaceholderValues.orNone(entryPath, language),
                renderList(ownerPaths, language),
                renderList(runStateEntryTargets, language),
                renderTargets(language)
        ).trim();
    }

    private String renderTargets(DocumentLanguage language) {
        if (observationTargets == null || observationTargets.isEmpty()) {
            return PlaceholderValues.bulletNone(language);
        }
        StringBuilder builder = new StringBuilder();
        for (UiObservationTarget target : observationTargets) {
            if (target == null || target.capabilityId().isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ")
                    .append(target.capabilityId())
                    .append(" | selector=")
                    .append(target.selector())
                    .append(" | mode=")
                    .append(target.mode())
                    .append(" | required=")
                    .append(target.required())
                    .append(" | ownerPaths=")
                    .append(target.ownerPaths());
        }
        return builder.isEmpty() ? PlaceholderValues.bulletNone(language) : builder.toString();
    }

    private String renderList(List<String> values, DocumentLanguage language) {
        if (values == null || values.isEmpty()) {
            return PlaceholderValues.bulletNone(language);
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ").append(value.trim());
        }
        return builder.isEmpty() ? PlaceholderValues.bulletNone(language) : builder.toString();
    }

    private static List<String> normalizeTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim().replace('\\', '/'));
        }
        return List.copyOf(normalized);
    }
}
