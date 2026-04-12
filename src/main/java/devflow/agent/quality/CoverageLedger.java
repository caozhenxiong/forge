package devflow.agent.quality;

import devflow.agent.i18n.DocumentLanguage;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 测试覆盖账本。
 */
public record CoverageLedger(List<CoverageLedgerEntry> entries) {

    public CoverageLedger {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static CoverageLedger empty() {
        return new CoverageLedger(List.of());
    }

    public boolean hasMissingRequiredCoverage() {
        return entries.stream().anyMatch(CoverageLedgerEntry::missingRequired);
    }

    public long missingRequiredCount() {
        return entries.stream().filter(CoverageLedgerEntry::missingRequired).count();
    }

    public Set<String> missingRequiredCapabilityIds() {
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        for (CoverageLedgerEntry entry : entries) {
            if (entry != null && entry.missingRequired() && !entry.capabilityId().isBlank()) {
                missing.add(entry.capabilityId());
            }
        }
        return Set.copyOf(missing);
    }

    public String toMarkdown(DocumentLanguage language) {
        if (entries.isEmpty()) {
            return language.choose("- 无覆盖账本", "- No coverage ledger");
        }
        StringBuilder builder = new StringBuilder();
        for (CoverageLedgerEntry entry : entries) {
            if (entry == null || entry.capabilityId().isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ")
                    .append(entry.capabilityId())
                    .append(" | required=")
                    .append(entry.required())
                    .append(" | status=")
                    .append(entry.status());
            if (!entry.caseIds().isEmpty()) {
                builder.append(" | cases=").append(String.join(", ", entry.caseIds()));
            }
            if (!entry.note().isBlank()) {
                builder.append(" | note=").append(entry.note());
            }
        }
        return builder.toString();
    }
}
