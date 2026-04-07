package devflow.agent.executor;

import java.util.List;

public record RuntimeSnapshot(
        String entry,
        String pageTitle,
        Integer pageLoadMs,
        int canvasCount,
        List<String> selectors,
        List<String> consoleErrors,
        List<String> pageErrors
) {

    public boolean usable() {
        return selectors != null && !selectors.isEmpty();
    }

    public String toMarkdown() {
        return """
                # Runtime Snapshot

                - entry: %s
                - pageTitle: %s
                - pageLoadMs: %s
                - canvasCount: %s
                - consoleErrors: %s
                - pageErrors: %s

                ## Selectors

                %s
                """.formatted(
                blank(entry),
                blank(pageTitle),
                pageLoadMs == null ? "n/a" : pageLoadMs,
                canvasCount,
                consoleErrors == null ? 0 : consoleErrors.size(),
                pageErrors == null ? 0 : pageErrors.size(),
                renderList(selectors)
        ).trim();
    }

    private String renderList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "- (none)";
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
        return builder.isEmpty() ? "- (none)" : builder.toString();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
