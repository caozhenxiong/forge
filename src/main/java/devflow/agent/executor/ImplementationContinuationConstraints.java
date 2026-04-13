package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * continuation/replanning 阶段必须继承的实现约束。
 *
 * <p>这层只表达上一轮 implementation 已经确定的文件事实：
 * 1. 哪些文件已经存在，不能再退回 SKELETON；
 * 2. 哪些 HTML 入口已经稳定存在，不能再退回整页重写；
 * 3. 哪些 HTML 入口已经确定了 runtime contract，后续必须沿用。
 */
record ImplementationContinuationConstraints(
        List<String> existingPaths,
        List<ProtectedHtmlEntryConstraint> protectedHtmlEntries
) {

    ImplementationContinuationConstraints {
        existingPaths = normalizePaths(existingPaths);
        protectedHtmlEntries = normalizeProtectedHtmlEntries(protectedHtmlEntries);
    }

    static ImplementationContinuationConstraints empty() {
        return new ImplementationContinuationConstraints(List.of(), List.of());
    }

    boolean active() {
        return !existingPaths.isEmpty() || !protectedHtmlEntries.isEmpty();
    }

    boolean marksExistingPath(String path) {
        String normalized = normalize(path);
        return !normalized.isBlank() && existingPaths.contains(normalized);
    }

    boolean protectsHtmlEntry(String path) {
        return protectedHtmlEntry(path) != null;
    }

    RuntimeOwnershipMode protectedRuntimeOwnership(String path) {
        HtmlRuntimeOwnershipContract contract = protectedRuntimeContract(path);
        return contract == null ? null : contract.runtimeOwnership();
    }

    HtmlRuntimeOwnershipContract protectedRuntimeContract(String path) {
        ProtectedHtmlEntryConstraint entry = protectedHtmlEntry(path);
        return entry == null ? null : entry.runtimeContract();
    }

    String toMarkdown(DocumentLanguage language) {
        if (!active()) {
            return PlaceholderValues.none(language);
        }
        StringBuilder builder = new StringBuilder();
        if (!existingPaths.isEmpty()) {
            builder.append(language.choose("已存在文件（不得退回 SKELETON）：", "Existing files (must not regress to SKELETON):"));
            for (String path : existingPaths) {
                builder.append('\n').append("- ").append(path);
            }
        }
        if (!protectedHtmlEntries.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(language.choose("受保护的 HTML 入口（不得退回整页重写 / REWORK）：", "Protected HTML entry files (must not regress to full rewrite / REWORK):"));
            for (ProtectedHtmlEntryConstraint entry : protectedHtmlEntries) {
                builder.append('\n').append("- ").append(entry.path());
                HtmlRuntimeOwnershipContract runtimeContract = entry.runtimeContract();
                if (runtimeContract != null && runtimeContract.runtimeOwnership() != null) {
                    builder.append(" [runtimeOwnership=").append(runtimeContract.runtimeOwnership()).append(']');
                }
                if (runtimeContract != null && !runtimeContract.runtimePaths().isEmpty()) {
                    builder.append(" [runtimePaths=").append(String.join(", ", runtimeContract.runtimePathStrings())).append(']');
                }
            }
        }
        return builder.toString();
    }

    private static List<String> normalizePaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String path : paths) {
            String value = normalize(path);
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private static List<ProtectedHtmlEntryConstraint> normalizeProtectedHtmlEntries(
            List<ProtectedHtmlEntryConstraint> entries
    ) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashMap<String, ProtectedHtmlEntryConstraint> normalized = new java.util.LinkedHashMap<>();
        for (ProtectedHtmlEntryConstraint entry : entries) {
            if (entry == null) {
                continue;
            }
            String path = normalize(entry.path());
            if (path.isBlank()) {
                continue;
            }
            HtmlRuntimeOwnershipContract runtimeContract = entry.runtimeContract();
            if (runtimeContract != null && runtimeContract.runtimeOwnership() != null) {
                runtimeContract = new HtmlRuntimeOwnershipContract(
                        Path.of(path),
                        runtimeContract.runtimeOwnership(),
                        runtimeContract.runtimePaths()
                );
            }
            normalized.put(path, new ProtectedHtmlEntryConstraint(path, runtimeContract));
        }
        return List.copyOf(normalized.values());
    }

    private ProtectedHtmlEntryConstraint protectedHtmlEntry(String path) {
        String normalized = normalize(path);
        if (normalized.isBlank()) {
            return null;
        }
        return protectedHtmlEntries.stream()
                .filter(entry -> normalized.equals(entry.path()))
                .findFirst()
                .orElse(null);
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return Path.of(path).normalize().toString().replace('\\', '/');
    }

    record ProtectedHtmlEntryConstraint(
            String path,
            HtmlRuntimeOwnershipContract runtimeContract
    ) {

        ProtectedHtmlEntryConstraint(String path) {
            this(path, (HtmlRuntimeOwnershipContract) null);
        }

        ProtectedHtmlEntryConstraint(String path, RuntimeOwnershipMode runtimeOwnership) {
            this(
                    path,
                    runtimeOwnership == null ? null : new HtmlRuntimeOwnershipContract(Path.of(path), runtimeOwnership, List.of())
            );
        }

        RuntimeOwnershipMode runtimeOwnership() {
            return runtimeContract == null ? null : runtimeContract.runtimeOwnership();
        }
    }
}
