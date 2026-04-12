package devflow.agent.context;

import devflow.agent.markdown.MarkdownSectionScanner;
import devflow.agent.text.TextCanonicalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Contract 文档 section 定位器。
 *
 * <p>它只负责：
 * 1. 解析二级/三级编号 section；
 * 2. 按标题或编号定位稳定 section；
 * 3. 处理 PRD 功能范围里的主 subsection 拼接。
 */
final class ContractSectionResolver {

    String sectionByNumber(String markdown, int sectionNumber) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return parseSections(markdown).stream()
                .filter(block -> block.number() == sectionNumber)
                .map(SectionBlock::body)
                .findFirst()
                .orElse("");
    }

    String sectionByTitle(String markdown, String headingTitle) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return parseSections(markdown).stream()
                .filter(block -> normalize(block.title()).equals(normalize(headingTitle)))
                .map(SectionBlock::body)
                .findFirst()
                .orElse("");
    }

    String productRequiredCapabilitiesSection(String prd) {
        String scopeSection = sectionByNumber(prd, 3);
        if (scopeSection.isBlank()) {
            return "";
        }
        List<SubsectionBlock> subsections = parseSubsections(scopeSection, 3);
        if (subsections.isEmpty()) {
            return scopeSection;
        }
        String primaryBodies = subsections.stream()
                .filter(block -> block.subnumber() == 1)
                .map(SubsectionBlock::body)
                .filter(body -> body != null && !body.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
        return primaryBodies.isBlank() ? scopeSection : primaryBodies;
    }

    String productOptionalCapabilitiesSection(String prd) {
        String scopeSection = sectionByNumber(prd, 3);
        if (scopeSection.isBlank()) {
            return "";
        }
        List<SubsectionBlock> subsections = parseSubsections(scopeSection, 3);
        if (subsections.isEmpty()) {
            return "";
        }
        return subsections.stream()
                .filter(block -> block.subnumber() == 2)
                .map(SubsectionBlock::body)
                .filter(body -> body != null && !body.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private List<SectionBlock> parseSections(String markdown) {
        return MarkdownSectionScanner.scanSecondLevelSections(markdown).stream()
                .map(section -> new SectionBlock(section.number(), section.title(), section.body()))
                .toList();
    }

    private List<SubsectionBlock> parseSubsections(String sectionBody, int parentSectionNumber) {
        List<SubsectionBlock> blocks = new ArrayList<>();
        for (MarkdownSectionScanner.NumberedSection section
                : MarkdownSectionScanner.scanThirdLevelSectionsWithNumberPath(sectionBody)) {
            List<Integer> numberPath = section.numberPath();
            if (numberPath.size() < 2 || numberPath.getFirst() != parentSectionNumber) {
                continue;
            }
            blocks.add(new SubsectionBlock(numberPath.get(0), numberPath.get(1), section.title(), section.body()));
        }
        return blocks;
    }

    private String normalize(String value) {
        return value == null ? "" : TextCanonicalizer.removeWhitespace(value).toLowerCase();
    }

    private record SectionBlock(int number, String title, String body) {
    }

    private record SubsectionBlock(int number, int subnumber, String title, String body) {
    }
}
