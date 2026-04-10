package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.List;

public record RuntimeSnapshot(
        String entry,
        String pageTitle,
        Integer pageLoadMs,
        int canvasCount,
        List<String> selectors,
        List<String> exposedMetricKeys,
        List<String> consoleErrors,
        List<String> pageErrors
) {

    public boolean usable() {
        return selectors != null && !selectors.isEmpty();
    }

    public String toMarkdown() {
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                # %s

                - entry: %s
                - %s: %s
                - pageLoadMs: %s
                - canvasCount: %s
                - exposedMetricKeys: %s
                - consoleErrors: %s
                - pageErrors: %s

                ## %s

                %s
                """.formatted(
                language.choose("运行时快照", "Runtime Snapshot"),
                blank(entry, language),
                language.choose("页面标题", "pageTitle"),
                blank(pageTitle, language),
                pageLoadMs == null ? "n/a" : pageLoadMs,
                canvasCount,
                exposedMetricKeys == null ? 0 : exposedMetricKeys.size(),
                consoleErrors == null ? 0 : consoleErrors.size(),
                pageErrors == null ? 0 : pageErrors.size(),
                language.choose("选择器", "Selectors"),
                renderList(selectors, language)
        ).trim();
    }

    public boolean exposesMetric(String metricKey) {
        if (metricKey == null || metricKey.isBlank() || exposedMetricKeys == null || exposedMetricKeys.isEmpty()) {
            return false;
        }
        return exposedMetricKeys.stream().anyMatch(metricKey::equals);
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

    private String blank(String value, DocumentLanguage language) {
        return PlaceholderValues.orNone(value, language);
    }
}
