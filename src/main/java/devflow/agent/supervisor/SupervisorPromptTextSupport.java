package devflow.agent.supervisor;

import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.text.TextCanonicalizer;

/**
 * 统一处理 supervisor prompt 里的小型文本归一逻辑。
 */
final class SupervisorPromptTextSupport {

    String shrink(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = TextCanonicalizer.collapseWhitespace(value);
        return PlaceholderValues.truncateInline(normalized, 2200);
    }

    String blank(String value) {
        return value == null ? "" : value;
    }
}
