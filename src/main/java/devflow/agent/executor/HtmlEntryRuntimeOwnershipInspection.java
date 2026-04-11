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
        HtmlRuntimeOwnershipContract runtimeContract,
        List<Path> referencedRuntimePaths,
        List<Path> availableRuntimePaths,
        boolean keepsInlineAnchor,
        boolean keepsNonEmptyInlineScript,
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

    RuntimeOwnershipMode expectedMode() {
        return runtimeContract == null ? null : runtimeContract.runtimeOwnership();
    }


    static HtmlEntryRuntimeOwnershipInspection success(
            HtmlRuntimeOwnershipContract runtimeContract,
            List<Path> referencedRuntimePaths,
            List<Path> availableRuntimePaths,
            boolean keepsInlineAnchor,
            boolean keepsNonEmptyInlineScript
    ) {
        return new HtmlEntryRuntimeOwnershipInspection(
                runtimeContract,
                referencedRuntimePaths == null ? List.of() : List.copyOf(referencedRuntimePaths),
                availableRuntimePaths == null ? List.of() : List.copyOf(availableRuntimePaths),
                keepsInlineAnchor,
                keepsNonEmptyInlineScript,
                List.of(),
                List.of()
        );
    }

    static HtmlEntryRuntimeOwnershipInspection failure(
            HtmlRuntimeOwnershipContract runtimeContract,
            List<Path> referencedRuntimePaths,
            List<Path> availableRuntimePaths,
            boolean keepsInlineAnchor,
            boolean keepsNonEmptyInlineScript,
            List<String> issues,
            List<String> evidence
    ) {
        return new HtmlEntryRuntimeOwnershipInspection(
                runtimeContract,
                referencedRuntimePaths == null ? List.of() : List.copyOf(referencedRuntimePaths),
                availableRuntimePaths == null ? List.of() : List.copyOf(availableRuntimePaths),
                keepsInlineAnchor,
                keepsNonEmptyInlineScript,
                List.copyOf(issues),
                List.copyOf(evidence)
        );
    }
}
