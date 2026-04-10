package devflow.agent.review;

import devflow.agent.markdown.MarkdownSectionScanner;
import devflow.agent.orchestrator.StageType;
import java.util.List;

/**
 * 负责文档级结构完整性检查。
 *
 * <p>这里不判断文档语义质量，只判断：
 * 1. 二级章节标题是否合法；
 * 2. 文档是否为空。
 */
final class DocumentIntegrityGuardSupport {

    String detect(StageType stageType, String candidateContent, List<Integer> orderedSections) {
        List<String> malformedHeadings = candidateContent.lines()
                .map(String::trim)
                .filter(this::isSecondLevelHeadingLine)
                .filter(line -> {
                    MarkdownSectionScanner.Heading heading = MarkdownSectionScanner.parseSecondLevelHeading(line);
                    return heading == null || heading.number() <= 0 || heading.title().isBlank();
                })
                .toList();
        if (!malformedHeadings.isEmpty()) {
            return "存在非法或污染的章节标题：" + malformedHeadings.getFirst();
        }

        String trimmed = candidateContent.trim();
        if (trimmed.isBlank()) {
            return "文档为空。";
        }
        return null;
    }

    private boolean isSecondLevelHeadingLine(String line) {
        return MarkdownSectionScanner.isSecondLevelHeading(line);
    }
}
