package devflow.agent.executor;

import java.nio.file.Path;
import java.util.List;

/**
 * HTML 入口 runtime ownership 检查结果。
 *
 * <p>这层输出稳定的 issues / evidence，避免上游继续从 prose 文本里反推
 * “到底是 orphan companion、还是 dual-track、还是 wiring 缺失”。
 */
record HtmlEntryRuntimeOwnershipInspection(
        RuntimeOwnershipMode expectedMode,
        Path htmlEntryPath,
        Path companionRuntimePath,
        List<String> issues,
        List<String> evidence
) {

    boolean passed() {
        return issues == null || issues.isEmpty();
    }

    String summary() {
        return passed() ? "" : String.join(" ", issues);
    }

    String evidenceMarkdown() {
        if (evidence == null || evidence.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String item : evidence) {
            if (item == null || item.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("- ").append(item);
        }
        return builder.toString();
    }

    static HtmlEntryRuntimeOwnershipInspection success(
            RuntimeOwnershipMode expectedMode,
            Path htmlEntryPath,
            Path companionRuntimePath
    ) {
        return new HtmlEntryRuntimeOwnershipInspection(expectedMode, htmlEntryPath, companionRuntimePath, List.of(), List.of());
    }

    static HtmlEntryRuntimeOwnershipInspection failure(
            RuntimeOwnershipMode expectedMode,
            Path htmlEntryPath,
            Path companionRuntimePath,
            List<String> issues,
            List<String> evidence
    ) {
        return new HtmlEntryRuntimeOwnershipInspection(expectedMode, htmlEntryPath, companionRuntimePath, List.copyOf(issues), List.copyOf(evidence));
    }
}
