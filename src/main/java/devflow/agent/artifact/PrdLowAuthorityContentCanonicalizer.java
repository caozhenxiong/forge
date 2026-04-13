package devflow.agent.artifact;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.SourceMetadataRoutingPolicy;
import devflow.agent.context.SourceMetadataRoutingPolicy.RoutedSourceMetadataItem;
import devflow.agent.context.SourceMetadataRoutingPolicy.SourceMetadataBucket;
import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.markdown.MarkdownSectionScanner;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 收口 PRD 正文里显式低权重条目的承载位置。
 *
 * <p>PRD 的 1-6 章承载的是产品承诺与边界，不应混入“推断 / 建议 / 设计选择 /
 * 待确认问题”这类低权重条目。这里会把显式低权重条目：
 * 1. 从 1-6 章正文移出；
 * 2. 汇总回 Source Metadata 对应 bucket；
 * 3. 保证 PRODUCT_CONTRACT 后续只看到净化后的正文。
 *
 * <p>这里不猜 prose 语义，只消费 {@link SourceMetadataRoutingPolicy} 已定义的稳定标签。
 */
final class PrdLowAuthorityContentCanonicalizer {

    private static final int SOURCE_METADATA_SECTION_NUMBER = 8;
    private static final Set<Integer> TARGET_TOP_LEVEL_SECTIONS = Set.of(1, 2, 3, 4, 5, 6);

    private final DocumentDraftAssembler draftAssembler;

    PrdLowAuthorityContentCanonicalizer(DocumentDraftAssembler draftAssembler) {
        this.draftAssembler = draftAssembler;
    }

    String canonicalize(String markdown, ConstraintSourceMetadata sourceMetadata, DocumentLanguage language) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        CapturedItems capturedItems = new CapturedItems();
        List<MarkdownSectionScanner.Section> sections = MarkdownSectionScanner.scanSecondLevelSections(markdown);
        if (sections.isEmpty()) {
            return markdown;
        }
        StringBuilder rebuilt = new StringBuilder();
        int cursor = 0;
        for (MarkdownSectionScanner.Section section : sections) {
            rebuilt.append(markdown, cursor, section.startOffset());
            if (TARGET_TOP_LEVEL_SECTIONS.contains(section.number())) {
                rebuilt.append(canonicalizeTopLevelSection(section.raw(), capturedItems));
            } else {
                rebuilt.append(markdown, section.startOffset(), section.endOffset());
            }
            cursor = section.endOffset();
        }
        rebuilt.append(markdown.substring(cursor));
        String canonicalMarkdown = rebuilt.toString().trim() + "\n";
        if (capturedItems.isEmpty()) {
            return canonicalMarkdown;
        }
        ConstraintSourceMetadata merged = merge(sourceMetadata, capturedItems);
        return draftAssembler.replaceTitledSection(
                canonicalMarkdown,
                ArtifactLabels.sourceMetadata(language),
                merged.toMarkdown(SOURCE_METADATA_SECTION_NUMBER, language)
        ).trim() + "\n";
    }

    private String canonicalizeTopLevelSection(String rawSection, CapturedItems capturedItems) {
        int lineBreak = rawSection.indexOf('\n');
        if (lineBreak < 0) {
            return rawSection;
        }
        String heading = rawSection.substring(0, lineBreak);
        String body = rawSection.substring(lineBreak + 1);
        StringBuilder rebuiltBody = new StringBuilder();
        boolean previousBlank = true;
        for (String line : body.split("\n", -1)) {
            RoutedSourceMetadataItem routed = classifyLine(line);
            if (routed != null) {
                capturedItems.add(routed);
                continue;
            }
            boolean blank = line.trim().isBlank();
            if (blank && previousBlank) {
                continue;
            }
            if (!rebuiltBody.isEmpty()) {
                rebuiltBody.append('\n');
            }
            rebuiltBody.append(line);
            previousBlank = blank;
        }
        String bodyContent = rebuiltBody.toString().stripTrailing();
        return bodyContent.isBlank()
                ? heading + "\n\n"
                : heading + "\n" + bodyContent + "\n\n";
    }

    private RoutedSourceMetadataItem classifyLine(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isBlank() || MarkdownSectionScanner.isHeadingLine(trimmed)) {
            return null;
        }
        return SourceMetadataRoutingPolicy.classify(stripListMarker(trimmed));
    }

    private ConstraintSourceMetadata merge(ConstraintSourceMetadata sourceMetadata, CapturedItems capturedItems) {
        ConstraintSourceMetadata base = sourceMetadata == null ? ConstraintSourceMetadata.empty() : sourceMetadata;
        return base.merge(new ConstraintSourceMetadata(
                List.of(),
                List.of(),
                capturedItems.items(SourceMetadataBucket.INFERENCE),
                capturedItems.items(SourceMetadataBucket.DESIGN_DECISION),
                capturedItems.items(SourceMetadataBucket.RECOMMENDATION),
                capturedItems.items(SourceMetadataBucket.OPEN_QUESTION)
        ));
    }

    private String stripListMarker(String trimmed) {
        if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ")) {
            return trimmed.substring(6).trim();
        }
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            return trimmed.substring(2).trim();
        }
        return trimmed;
    }

    private static final class CapturedItems {
        private final Set<String> inferences = new LinkedHashSet<>();
        private final Set<String> designDecisions = new LinkedHashSet<>();
        private final Set<String> recommendations = new LinkedHashSet<>();
        private final Set<String> openQuestions = new LinkedHashSet<>();

        void add(RoutedSourceMetadataItem item) {
            if (item == null || item.text().isBlank()) {
                return;
            }
            switch (item.bucket()) {
                case INFERENCE -> inferences.add(item.text());
                case DESIGN_DECISION -> designDecisions.add(item.text());
                case RECOMMENDATION -> recommendations.add(item.text());
                case OPEN_QUESTION -> openQuestions.add(item.text());
            }
        }

        boolean isEmpty() {
            return inferences.isEmpty()
                    && designDecisions.isEmpty()
                    && recommendations.isEmpty()
                    && openQuestions.isEmpty();
        }

        List<String> items(SourceMetadataBucket bucket) {
            return switch (bucket) {
                case INFERENCE -> List.copyOf(inferences);
                case DESIGN_DECISION -> List.copyOf(designDecisions);
                case RECOMMENDATION -> List.copyOf(recommendations);
                case OPEN_QUESTION -> List.copyOf(openQuestions);
            };
        }
    }
}
