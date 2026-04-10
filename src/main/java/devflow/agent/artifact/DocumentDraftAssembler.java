package devflow.agent.artifact;

import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.markdown.MarkdownSectionScanner;
import devflow.agent.orchestrator.StageType;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档草稿的 section 级装配器。
 *
 * <p>这里统一承担章节提纲提取、缺失章节 merge、顶层 section replace 等确定性拼装逻辑。
 */
final class DocumentDraftAssembler {

    String mergeDocumentDraft(StageType stageType, String previousDraft, String generated, List<Integer> targetSections) {
        if (previousDraft == null || previousDraft.isBlank()) {
            return generated;
        }
        List<Integer> orderedSections = topLevelSectionNumbers(stageType);
        String merged = previousDraft;
        List<Integer> effectiveTargetSections = targetSections == null || targetSections.isEmpty()
                ? orderedSections
                : targetSections.stream().filter(number -> number != null && number > 0).toList();
        for (Integer headingNumber : effectiveTargetSections) {
            String block = extractSectionBlockByNumber(generated, headingNumber);
            if (block == null || block.isBlank()) {
                continue;
            }
            merged = upsertSectionBlockByNumber(merged, headingNumber, block.trim(), orderedSections);
        }
        return merged;
    }

    String renderRequestedSections(List<Integer> targetSections) {
        if (targetSections == null || targetSections.isEmpty()) {
            return PlaceholderValues.machineNone();
        }
        return targetSections.stream()
                .filter(number -> number != null && number > 0)
                .map(number -> "## " + number + ".")
                .reduce((left, right) -> left + "、" + right)
                .orElse(PlaceholderValues.machineNone());
    }

    List<Integer> topLevelSectionNumbers(StageType stageType) {
        return switch (stageType) {
            case ANALYSIS -> List.of(1, 2, 3, 4, 5, 6, 7);
            case PRD -> List.of(1, 2, 3, 4, 5, 6, 7, 8);
            case DESIGN -> List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
            default -> List.of();
        };
    }

    String documentOutline(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String line : markdown.lines().toList()) {
            String trimmed = line.trim();
            if (isMarkdownHeading(trimmed)) {
                builder.append(trimmed).append('\n');
            }
        }
        return builder.toString().trim();
    }

    String replaceTitledSection(String markdown, String title, String replacement) {
        if (markdown == null || markdown.isBlank()) {
            return replacement;
        }
        List<DocumentSectionBlock> sections = parseTopLevelSections(markdown);
        DocumentSectionBlock matching = sections.stream()
                .filter(section -> normalizeHeading(section.title()).equals(normalizeHeading(title)))
                .findFirst()
                .orElse(null);
        if (matching == null) {
            return markdown.stripTrailing() + "\n\n" + replacement + "\n";
        }
        return markdown.substring(0, matching.start()).stripTrailing()
                + "\n\n" + replacement + "\n\n"
                + markdown.substring(matching.end()).stripLeading();
    }

    List<DocumentSectionBlock> parseTopLevelSections(String content) {
        return MarkdownSectionScanner.scanSecondLevelSections(content).stream()
                .filter(section -> section.number() > 0)
                .map(section -> new DocumentSectionBlock(
                        section.number(),
                        section.title(),
                        section.startOffset(),
                        section.endOffset(),
                        content.substring(section.startOffset(), section.endOffset())
                ))
                .toList();
    }

    private String extractSectionBlockByNumber(String content, int headingNumber) {
        List<DocumentSectionBlock> sections = parseTopLevelSections(content);
        DocumentSectionBlock current = sections.stream().filter(section -> section.number() == headingNumber).findFirst().orElse(null);
        return current == null ? null : current.raw();
    }

    private String upsertSectionBlockByNumber(String content, int headingNumber, String newBlock, List<Integer> orderedSections) {
        List<DocumentSectionBlock> sections = parseTopLevelSections(content);
        DocumentSectionBlock existing = sections.stream().filter(section -> section.number() == headingNumber).findFirst().orElse(null);
        if (existing != null) {
            return content.substring(0, existing.start()).stripTrailing()
                    + "\n\n" + newBlock + "\n\n"
                    + content.substring(existing.end()).stripLeading();
        }
        int currentIndex = orderedSections.indexOf(headingNumber);
        for (int i = currentIndex + 1; i < orderedSections.size(); i++) {
            Integer nextNumber = orderedSections.get(i);
            DocumentSectionBlock anchor = sections.stream().filter(section -> section.number() == nextNumber).findFirst().orElse(null);
            if (anchor != null) {
                return content.substring(0, anchor.start()).stripTrailing()
                        + "\n\n" + newBlock + "\n\n"
                        + content.substring(anchor.start()).stripLeading();
            }
        }
        return content.stripTrailing() + "\n\n" + newBlock + "\n";
    }

    private String normalizeHeading(String value) {
        return MarkdownSectionScanner.normalizeHeadingTitle(value);
    }

    private boolean isMarkdownHeading(String trimmed) {
        if (trimmed == null || trimmed.isBlank() || trimmed.charAt(0) != '#') {
            return false;
        }
        int level = 0;
        while (level < trimmed.length() && trimmed.charAt(level) == '#') {
            level++;
        }
        return level > 0 && level < trimmed.length() && Character.isWhitespace(trimmed.charAt(level));
    }
}
