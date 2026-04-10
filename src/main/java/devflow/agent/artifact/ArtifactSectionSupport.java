package devflow.agent.artifact;

import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.markdown.MarkdownSectionScanner;
import devflow.agent.text.TextCanonicalizer;
import java.util.EnumSet;

/**
 * 统一处理 artifact 顶层章节的确定性分类与过滤。
 *
 * <p>这层只识别我们自己定义的稳定章节协议，例如 Source Metadata、
 * Contract Metadata、Current Notes；不承担自然语言语义推断。
 */
public final class ArtifactSectionSupport {

    private static final String SOURCE_METADATA_TITLE =
            MarkdownSectionScanner.normalizeHeadingTitle(ArtifactLabels.sourceMetadata(DocumentLanguage.EN));
    private static final String CONTRACT_METADATA_TITLE =
            MarkdownSectionScanner.normalizeHeadingTitle(ArtifactLabels.contractMetadata(DocumentLanguage.EN));
    private static final String CURRENT_NOTES_TITLE = MarkdownSectionScanner.normalizeHeadingTitle(ArtifactLabels.currentNotes(DocumentLanguage.EN));

    private ArtifactSectionSupport() {
    }

    public static ArtifactSectionKind classifySecondLevelHeading(String headingLine) {
        if (headingLine == null || headingLine.isBlank()) {
            return ArtifactSectionKind.OTHER;
        }
        MarkdownSectionScanner.Heading heading = MarkdownSectionScanner.parseSecondLevelHeading(headingLine);
        if (heading == null) {
            return ArtifactSectionKind.OTHER;
        }
        String title = MarkdownSectionScanner.normalizeHeadingTitle(heading.title());
        if (SOURCE_METADATA_TITLE.equals(title)) {
            return ArtifactSectionKind.SOURCE_METADATA;
        }
        if (CONTRACT_METADATA_TITLE.equals(title)) {
            return ArtifactSectionKind.CONTRACT_METADATA;
        }
        if (CURRENT_NOTES_TITLE.equals(title)) {
            return ArtifactSectionKind.CURRENT_NOTES;
        }
        return ArtifactSectionKind.OTHER;
    }

    public static boolean isSecondLevelHeading(String line) {
        return MarkdownSectionScanner.isSecondLevelHeading(line);
    }

    public static String removeSections(String markdown, EnumSet<ArtifactSectionKind> filteredKinds) {
        if (markdown == null || markdown.isBlank() || filteredKinds == null || filteredKinds.isEmpty()) {
            return markdown == null ? "" : markdown;
        }
        StringBuilder builder = new StringBuilder();
        boolean skipping = false;
        for (String rawLine : TextCanonicalizer.splitLines(markdown)) {
            ArtifactSectionKind sectionKind = classifySecondLevelHeading(rawLine);
            if (sectionKind != ArtifactSectionKind.OTHER) {
                skipping = filteredKinds.contains(sectionKind);
            } else if (isSecondLevelHeading(rawLine)) {
                skipping = false;
            }
            if (skipping) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(rawLine);
        }
        return builder.toString().strip();
    }

}
