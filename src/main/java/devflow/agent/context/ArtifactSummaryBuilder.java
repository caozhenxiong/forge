package devflow.agent.context;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ArtifactSummaryBuilder {

    public String summarizeMarkdown(String content, int limit) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() > limit ? normalized.substring(0, limit) + " ...<truncated>" : normalized;
    }

    public String renderBulletList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "- (none)";
        }
        String rendered = items.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .map(item -> "- " + item)
                .collect(Collectors.joining("\n"));
        return rendered.isBlank() ? "- (none)" : rendered;
    }
}
