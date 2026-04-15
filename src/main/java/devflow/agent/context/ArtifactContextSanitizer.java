package devflow.agent.context;

import devflow.agent.artifact.ArtifactSectionKind;
import devflow.agent.artifact.ArtifactSectionSupport;
import devflow.agent.context.ContractRuntimeOwnershipMode;
import devflow.agent.context.EntryPackagingMode;
import devflow.agent.context.ExecutionEntryKind;
import devflow.agent.text.TextCanonicalizer;
import devflow.agent.domain.StageType;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ArtifactContextSanitizer {

    private static final EnumSet<ArtifactSectionKind> FILTERED_SECTIONS = EnumSet.of(
            ArtifactSectionKind.SOURCE_METADATA,
            ArtifactSectionKind.CONTRACT_METADATA
    );
    private static final List<String> LOW_AUTHORITY_PREFIXES = List.of(
            "- recommendation:",
            "- 建议:",
            "- 设计选择:",
            "- 推断:",
            "- open question:",
            "- open questions:",
            "- 待确认问题:"
    );
    private static final Set<String> AUTHORITY_CONTROLLED_RUNTIME_TERMS = Set.of(
            ExecutionEntryKind.HTML_ENTRY.wireValue(),
            ExecutionEntryKind.MAIN_SCRIPT.wireValue(),
            ExecutionEntryKind.MAIN_CLASS.wireValue(),
            EntryPackagingMode.SELF_CONTAINED_ENTRY.wireValue(),
            EntryPackagingMode.ENTRY_WITH_LOCAL_DEPENDENCIES.wireValue(),
            ContractRuntimeOwnershipMode.ENTRY_OWNED.wireValue(),
            ContractRuntimeOwnershipMode.COMPANION_OWNED.wireValue(),
            "entry required",
            "launch required",
            "surface required"
    );

    private ArtifactContextSanitizer() {
    }

    public static String sanitizeForPrompt(String artifact, StageType sourceStage, String authorityCorpus) {
        return sanitize(artifact, sourceStage, authorityCorpus, true);
    }

    public static String sanitizeForProjection(String artifact, StageType sourceStage, String authorityCorpus) {
        return sanitize(artifact, sourceStage, authorityCorpus, false);
    }

    private static String sanitize(
            String artifact,
            StageType sourceStage,
            String authorityCorpus,
            boolean dropBlankLines
    ) {
        String filteredArtifact = ArtifactSectionSupport.removeSections(artifact, FILTERED_SECTIONS);
        if (filteredArtifact.isBlank()) {
            return "";
        }
        Set<String> authorityTerms = authorityTerms(authorityCorpus);
        StringBuilder builder = new StringBuilder();
        for (String rawLine : TextCanonicalizer.splitLines(filteredArtifact)) {
            String trimmed = rawLine.trim();
            if (trimmed.isBlank()) {
                if (!dropBlankLines) {
                    appendLine(builder, rawLine);
                }
                continue;
            }
            if (shouldDropLowAuthorityLine(trimmed)
                    || shouldDropAuthorityControlledRuntimeLine(trimmed, authorityTerms)) {
                continue;
            }
            appendLine(builder, rawLine);
        }
        return builder.toString().trim();
    }

    private static boolean shouldDropLowAuthorityLine(String line) {
        String normalized = normalizeLine(line);
        for (String prefix : LOW_AUTHORITY_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldDropAuthorityControlledRuntimeLine(String line, Set<String> authorityTerms) {
        if (authorityTerms.isEmpty()) {
            return false;
        }
        List<String> controlledTerms = controlledTermsInLine(line);
        if (controlledTerms.isEmpty()) {
            return false;
        }
        return controlledTerms.stream().anyMatch(term -> !authorityTerms.contains(term));
    }

    private static List<String> controlledTermsInLine(String line) {
        String normalized = normalizeLine(line);
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String term : AUTHORITY_CONTROLLED_RUNTIME_TERMS) {
            if (normalized.contains(term)) {
                terms.add(term);
            }
        }
        return List.copyOf(terms);
    }

    private static Set<String> authorityTerms(String authorityCorpus) {
        if (authorityCorpus == null || authorityCorpus.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String line : TextCanonicalizer.splitLines(authorityCorpus)) {
            String normalized = normalizeLine(line);
            if (!normalized.isBlank()) {
                terms.add(normalized);
            }
        }
        return Set.copyOf(terms);
    }

    private static String normalizeLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return TextCanonicalizer.collapseWhitespace(value)
                .replace('：', ':')
                .toLowerCase(Locale.ROOT);
    }

    private static void appendLine(StringBuilder builder, String line) {
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(line);
    }
}
