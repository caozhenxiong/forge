package devflow.agent.context;

import devflow.agent.artifact.ArtifactSectionKind;
import devflow.agent.artifact.ArtifactSectionSupport;
import devflow.agent.text.TextCanonicalizer;
import devflow.agent.orchestrator.StageType;
import java.util.EnumSet;

public final class ArtifactContextSanitizer {

    private static final EnumSet<ArtifactSectionKind> FILTERED_SECTIONS = EnumSet.of(
            ArtifactSectionKind.SOURCE_METADATA,
            ArtifactSectionKind.CONTRACT_METADATA,
            ArtifactSectionKind.CURRENT_NOTES
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
        StringBuilder builder = new StringBuilder();
        for (String rawLine : TextCanonicalizer.splitLines(filteredArtifact)) {
            String trimmed = rawLine.trim();
            if (trimmed.isBlank()) {
                if (!dropBlankLines) {
                    appendLine(builder, rawLine);
                }
                continue;
            }
            appendLine(builder, rawLine);
        }
        return builder.toString().trim();
    }

    private static void appendLine(StringBuilder builder, String line) {
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(line);
    }
}
