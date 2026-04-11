package devflow.agent.artifact;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.markdown.MarkdownSectionScanner;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 收口 PRD 中可绑定的量化约束。
 *
 * <p>只处理 PRD 的稳定章节：
 * 1. `## 4.1 性能`
 * 2. `## 5.2 质量验收`
 *
 * <p>策略是：
 * 1. hard.* 中已明确给出的量化项，允许原样保留；
 * 2. validation.* 中显式声明的加载/交互阈值，允许保留并投影成规范句式；
 * 3. 其他无来源量化阈值全部删除。
 */
final class PrdQuantitativeConstraintCanonicalizer {

    private static final List<Integer> PERFORMANCE_SECTION = List.of(4, 1);
    private static final List<Integer> QUALITY_ACCEPTANCE_SECTION = List.of(5, 2);

    private final QuantitativeConstraintSpec quantitativeConstraintSpec = new QuantitativeConstraintSpec();

    String canonicalize(
            String markdown,
            ConstraintSourceMetadata authoritativeSourceMetadata,
            ValidationMetadata validationMetadata,
            DocumentLanguage language
    ) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        List<MarkdownSectionScanner.Section> sections = MarkdownSectionScanner.scanSecondLevelSections(markdown);
        if (sections.isEmpty()) {
            return markdown;
        }
        Set<String> hardAuthority = hardQuantitativeAuthority(authoritativeSourceMetadata);
        StringBuilder rebuilt = new StringBuilder();
        int cursor = 0;
        for (MarkdownSectionScanner.Section section : sections) {
            rebuilt.append(markdown, cursor, section.startOffset());
            if (section.number() == 4 || section.number() == 5) {
                rebuilt.append(canonicalizeTopLevelSection(section.raw(), hardAuthority, validationMetadata, language));
            } else {
                rebuilt.append(markdown, section.startOffset(), section.endOffset());
            }
            cursor = section.endOffset();
        }
        rebuilt.append(markdown.substring(cursor));
        return rebuilt.toString().trim() + "\n";
    }

    private String canonicalizeTopLevelSection(
            String rawSection,
            Set<String> hardAuthority,
            ValidationMetadata validationMetadata,
            DocumentLanguage language
    ) {
        List<MarkdownSectionScanner.NumberedSection> subsections =
                MarkdownSectionScanner.scanThirdLevelSectionsWithNumberPath(rawSection);
        if (subsections.isEmpty()) {
            return rawSection;
        }
        StringBuilder rebuilt = new StringBuilder();
        int cursor = 0;
        for (MarkdownSectionScanner.NumberedSection subsection : subsections) {
            rebuilt.append(rawSection, cursor, subsection.startOffset());
            if (isTargetSection(subsection.numberPath())) {
                rebuilt.append(canonicalizeSection(subsection.raw(), hardAuthority, validationMetadata, language));
            } else {
                rebuilt.append(rawSection, subsection.startOffset(), subsection.endOffset());
            }
            cursor = subsection.endOffset();
        }
        rebuilt.append(rawSection.substring(cursor));
        return rebuilt.toString();
    }

    private String canonicalizeSection(
            String rawSection,
            Set<String> hardAuthority,
            ValidationMetadata validationMetadata,
            DocumentLanguage language
    ) {
        int lineBreak = rawSection.indexOf('\n');
        if (lineBreak < 0) {
            return rawSection;
        }
        String heading = rawSection.substring(0, lineBreak);
        String body = rawSection.substring(lineBreak + 1);
        StringBuilder rebuiltBody = new StringBuilder();
        Set<String> emittedProjectedLines = new LinkedHashSet<>();
        for (String line : body.split("\n", -1)) {
            String rewritten = rewriteLine(line, hardAuthority, validationMetadata, language);
            if (rewritten == null) {
                continue;
            }
            String dedupeKey = rewritten.trim();
            if (!dedupeKey.isBlank() && !emittedProjectedLines.add(dedupeKey)) {
                continue;
            }
            if (!rebuiltBody.isEmpty()) {
                rebuiltBody.append('\n');
            }
            rebuiltBody.append(rewritten);
        }
        return heading + "\n" + rebuiltBody;
    }

    private String rewriteLine(
            String line,
            Set<String> hardAuthority,
            ValidationMetadata validationMetadata,
            DocumentLanguage language
    ) {
        String trimmed = line.trim();
        if (trimmed.isBlank() || trimmed.startsWith("### ") || trimmed.startsWith("#### ")) {
            return line;
        }
        String content = stripListMarker(trimmed);
        QuantitativeConstraintSpec.QuantitativeConstraintMatch match = quantitativeConstraintSpec.analyze(content);
        if (!match.quantitative()) {
            return line;
        }
        if (hardAuthority.contains(match.normalizedAuthorityItem())) {
            return line;
        }
        if (!quantitativeConstraintSpec.matchesValidationThreshold(match, validationMetadata)) {
            return null;
        }
        String projected = quantitativeConstraintSpec.projectValidationLine(match.metric(), validationMetadata, language);
        if (projected == null || projected.isBlank()) {
            return null;
        }
        return listPrefix(line) + projected;
    }

    private Set<String> hardQuantitativeAuthority(ConstraintSourceMetadata authoritativeSourceMetadata) {
        Set<String> authority = new LinkedHashSet<>();
        if (authoritativeSourceMetadata == null) {
            return authority;
        }
        addQuantitativeItems(authority, authoritativeSourceMetadata.hardUserRequirements());
        addQuantitativeItems(authority, authoritativeSourceMetadata.hardUpstreamFacts());
        return authority;
    }

    private void addQuantitativeItems(Set<String> authority, List<String> items) {
        if (items == null) {
            return;
        }
        for (String item : items) {
            if (!quantitativeConstraintSpec.isQuantitativeAuthorityItem(item)) {
                continue;
            }
            authority.add(quantitativeConstraintSpec.normalizeAuthorityItem(item));
        }
    }

    private boolean isTargetSection(List<Integer> numberPath) {
        return PERFORMANCE_SECTION.equals(numberPath) || QUALITY_ACCEPTANCE_SECTION.equals(numberPath);
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

    private String listPrefix(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        if (line.startsWith("- [ ] ", index) || line.startsWith("* [ ] ", index)) {
            return line.substring(0, index) + "- [ ] ";
        }
        if (line.startsWith("- ", index) || line.startsWith("* ", index)) {
            return line.substring(0, index) + "- ";
        }
        return line.substring(0, index);
    }
}
