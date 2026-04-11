package devflow.agent.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一处理固定 markdown 协议里的顶层章节与 metadata 行扫描。
 *
 * <p>这里故意不用正则去“猜”模型 prose，而是只扫描我们自己定义的稳定结构：
 * 二级标题、三级标题、以及 `- key: value` 这类 metadata 行。
 */
public final class MarkdownSectionScanner {

    private static final MarkdownHeadingParser HEADING_PARSER = new MarkdownHeadingParser();
    private static final MarkdownLineScanner LINE_SCANNER = new MarkdownLineScanner();

    private MarkdownSectionScanner() {
    }

    public static boolean isSecondLevelHeading(String line) {
        return HEADING_PARSER.isSecondLevelHeading(line);
    }

    public static Heading parseSecondLevelHeading(String line) {
        return HEADING_PARSER.parseSecondLevelHeading(line);
    }

    public static Heading parseThirdLevelHeading(String line) {
        return HEADING_PARSER.parseThirdLevelHeading(line);
    }

    public static boolean isHeadingLine(String line) {
        return HEADING_PARSER.isHeadingLine(line);
    }

    public static List<Section> scanSecondLevelSections(String markdown) {
        return scanSections(markdown, 2);
    }

    public static List<Section> scanThirdLevelSections(String markdown) {
        return scanSections(markdown, 3);
    }

    public static List<NumberedSection> scanThirdLevelSectionsWithNumberPath(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<NumberedHeadingOccurrence> headings = new ArrayList<>();
        LINE_SCANNER.forEachLine(markdown, (line, start, end) -> {
            NumberedHeading heading = HEADING_PARSER.parseNumberedHeading(line, 3);
            if (heading != null) {
                headings.add(new NumberedHeadingOccurrence(heading, start));
            }
        });
        if (headings.isEmpty()) {
            return List.of();
        }
        List<NumberedSection> sections = new ArrayList<>();
        for (int index = 0; index < headings.size(); index++) {
            NumberedHeadingOccurrence current = headings.get(index);
            int start = current.startOffset();
            int end = index + 1 < headings.size() ? headings.get(index + 1).startOffset() : markdown.length();
            // raw 需要保留原始切片，供基于 offset 的重写链直接回写；
            // 需要去头尾空白时应消费 body，而不是在这里 trim 掉章节边界。
            String raw = markdown.substring(start, end);
            String body = stripHeadingLine(raw);
            sections.add(new NumberedSection(current.heading().numberPath(), current.heading().title(), start, end, raw, body));
        }
        return List.copyOf(sections);
    }

    public static Map<String, String> parseMetadataLines(String content) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (content == null || content.isBlank()) {
            return metadata;
        }
        LINE_SCANNER.forEachLine(content, (line, start, end) -> {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            if (!(trimmed.startsWith("- ") || trimmed.startsWith("* "))) {
                return;
            }
            String body = trimmed.substring(2).trim();
            int colon = body.indexOf(':');
            if (colon <= 0 || colon >= body.length() - 1) {
                return;
            }
            String key = body.substring(0, colon).trim();
            String value = body.substring(colon + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                metadata.put(key, value);
            }
        });
        return metadata;
    }

    public static String normalizeHeadingTitle(String value) {
        return HEADING_PARSER.normalizeHeadingTitle(value);
    }

    private static List<Section> scanSections(String markdown, int level) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<HeadingOccurrence> headings = new ArrayList<>();
        LINE_SCANNER.forEachLine(markdown, (line, start, end) -> {
            Heading heading = parseHeading(line, level);
            if (heading != null) {
                headings.add(new HeadingOccurrence(heading, start));
            }
        });
        if (headings.isEmpty()) {
            return List.of();
        }
        List<Section> sections = new ArrayList<>();
        for (int index = 0; index < headings.size(); index++) {
            HeadingOccurrence current = headings.get(index);
            int start = current.startOffset();
            int end = index + 1 < headings.size() ? headings.get(index + 1).startOffset() : markdown.length();
            // raw 需要保留原始切片，供基于 offset 的重写链直接回写；
            // 需要去头尾空白时应消费 body，而不是在这里 trim 掉章节边界。
            String raw = markdown.substring(start, end);
            String body = stripHeadingLine(raw);
            sections.add(new Section(current.heading().number(), current.heading().title(), start, end, raw, body));
        }
        return List.copyOf(sections);
    }

    private static String stripHeadingLine(String sectionRaw) {
        if (sectionRaw == null || sectionRaw.isBlank()) {
            return "";
        }
        int newline = sectionRaw.indexOf('\n');
        return newline < 0 ? "" : sectionRaw.substring(newline + 1).trim();
    }

    private static Heading parseHeading(String line, int level) {
        return level == 2
                ? HEADING_PARSER.parseSecondLevelHeading(line)
                : HEADING_PARSER.parseThirdLevelHeading(line);
    }

    public record Heading(int number, String title) {
    }

    public record Section(int number, String title, int startOffset, int endOffset, String raw, String body) {
    }

    private record HeadingOccurrence(Heading heading, int startOffset) {
    }

    public record NumberedHeading(List<Integer> numberPath, String title) {
    }

    public record NumberedSection(List<Integer> numberPath, String title, int startOffset, int endOffset, String raw, String body) {
    }

    private record NumberedHeadingOccurrence(NumberedHeading heading, int startOffset) {
    }

    @FunctionalInterface
    interface LineConsumer {
        void accept(String line, int startOffset, int endOffset);
    }
}
