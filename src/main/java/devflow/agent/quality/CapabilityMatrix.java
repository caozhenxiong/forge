package devflow.agent.quality;

import com.fasterxml.jackson.annotation.JsonIgnore;
import devflow.agent.i18n.DocumentLanguage;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 当前轮必须覆盖的能力矩阵。
 */
public record CapabilityMatrix(List<CapabilityMatrixEntry> entries) {

    public CapabilityMatrix {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static CapabilityMatrix empty() {
        return new CapabilityMatrix(List.of());
    }

    @JsonIgnore
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Set<CapabilitySurface> requiredSurfaces() {
        Set<CapabilitySurface> required = new LinkedHashSet<>();
        for (CapabilityMatrixEntry entry : entries) {
            if (entry != null && entry.required() && entry.surface() != null) {
                required.add(entry.surface());
            }
        }
        return Set.copyOf(required);
    }

    public boolean requires(CapabilitySurface surface) {
        if (surface == null) {
            return false;
        }
        return entries.stream().anyMatch(entry -> entry != null && entry.required() && surface == entry.surface());
    }

    public String toMarkdown(DocumentLanguage language) {
        if (entries.isEmpty()) {
            return language.choose("- 无能力矩阵要求", "- No capability matrix requirements");
        }
        StringBuilder builder = new StringBuilder();
        for (CapabilityMatrixEntry entry : entries) {
            if (entry == null || entry.surface() == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ")
                    .append(entry.surface().wireValue())
                    .append(" [")
                    .append(entry.expectation().name())
                    .append(entry.requiresObservableStateChange()
                            ? language.choose(", needs-observable-state-change", ", needs-observable-state-change")
                            : "")
                    .append("]");
            if (!entry.rationale().isBlank()) {
                builder.append(": ").append(entry.rationale());
            }
        }
        return builder.toString();
    }
}
