package devflow.agent.review;

import devflow.agent.markdown.MarkdownSectionScanner;
import java.util.Map;

/**
 * reviewer 层共享的固定 markdown 协议辅助方法。
 *
 * <p>这里只解析我们自己定义的稳定章节和 metadata 行，
 * 不从 reviewer prose 里猜语义，也不靠正则回读自然语言。
 */
final class ReviewMarkdownSupport {

    private ReviewMarkdownSupport() {
    }

    static String extractSectionByTitle(String markdown, String headingTitle) {
        if (markdown == null || markdown.isBlank() || headingTitle == null || headingTitle.isBlank()) {
            return "";
        }
        return MarkdownSectionScanner.scanSecondLevelSections(markdown).stream()
                .filter(section -> MarkdownSectionScanner.normalizeHeadingTitle(section.title())
                        .equals(MarkdownSectionScanner.normalizeHeadingTitle(headingTitle)))
                .map(MarkdownSectionScanner.Section::body)
                .findFirst()
                .orElse("");
    }

    static Map<String, String> parseMetadataSection(String sectionBody) {
        return MarkdownSectionScanner.parseMetadataLines(sectionBody);
    }
}
