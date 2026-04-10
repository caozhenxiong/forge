package devflow.agent.context;

import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.text.TextCanonicalizer;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ArtifactSummaryBuilder {

    public String summarizeMarkdown(String content, int limit) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = TextCanonicalizer.collapseWhitespace(content);
        return PlaceholderValues.truncateInline(normalized, limit);
    }

    public String renderBulletList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return PlaceholderValues.bulletMachineNone();
        }
        String rendered = items.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .map(item -> "- " + item)
                .collect(Collectors.joining("\n"));
        return rendered.isBlank() ? PlaceholderValues.bulletMachineNone() : rendered;
    }
}
