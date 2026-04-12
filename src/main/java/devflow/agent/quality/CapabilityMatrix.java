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

    public Set<String> requiredCapabilityIds() {
        Set<String> required = new LinkedHashSet<>();
        for (CapabilityMatrixEntry entry : entries) {
            if (entry != null && entry.required() && !entry.capabilityId().isBlank()) {
                required.add(entry.capabilityId());
            }
        }
        return Set.copyOf(required);
    }

    public boolean requires(String capabilityId) {
        String normalized = CapabilityIds.normalize(capabilityId);
        if (normalized.isBlank()) {
            return false;
        }
        return entries.stream().anyMatch(entry -> entry != null && entry.required() && normalized.equals(entry.capabilityId()));
    }

    public Set<String> requiredObservationTargetIds() {
        Set<String> targets = new LinkedHashSet<>();
        for (CapabilityMatrixEntry entry : entries) {
            if (entry != null && entry.required() && entry.targetsBuiltinObservationSurface()) {
                targets.add(entry.observationTargetId());
            }
        }
        return Set.copyOf(targets);
    }

    public String toMarkdown(DocumentLanguage language) {
        if (entries.isEmpty()) {
            return language.choose("- 无能力矩阵要求", "- No capability matrix requirements");
        }
        StringBuilder builder = new StringBuilder();
        for (CapabilityMatrixEntry entry : entries) {
            if (entry == null || entry.capabilityId().isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ")
                    .append(entry.capabilityId())
                    .append(" [")
                    .append(entry.expectation().name())
                    .append(entry.requiresObservationTarget()
                            ? ", target=" + entry.observationTargetId()
                            : "")
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
