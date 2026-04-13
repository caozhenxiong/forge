package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 历史测试名保留不变，但内部已经切到 exact replace 载荷。
 */
public final class StructuredDiffTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern TARGET_PATH_PATTERN =
            Pattern.compile("- targetPath: ([^\\r\\n]+)");
    private static final Pattern CONTENT_HASH_PATTERN =
            Pattern.compile("- contentHash: ([^\\r\\n]+)");
    private static final Pattern BASE_CONTENT_HASH_JSON_PATTERN =
            Pattern.compile("\"baseContentHash\"\\s*:\\s*\"([^\"]*)\"");
    private static final String CONTENT_MARKER = "（必须原样引用 oldText）：";
    private static final String CONTENT_BLOCK_PREFIX = "<<<CURRENT_CONTENT\n";
    private static final String CONTENT_BLOCK_SUFFIX = "\nCURRENT_CONTENT";

    private StructuredDiffTestSupport() {
    }

    public static String appendAtEnd(String prompt, String... afterLines) {
        ParsedPrompt parsed = parse(prompt);
        String oldText = parsed.content();
        String newText = appendContent(parsed.content(), List.of(afterLines));
        return exactEdit(parsed.targetPath(), parsed.contentHash(), oldText, newText, false);
    }

    public static String replaceCurrentContent(String prompt, String newContent) {
        ParsedPrompt parsed = parse(prompt);
        return exactEdit(parsed.targetPath(), parsed.contentHash(), parsed.content(), newContent, false);
    }

    public static String replaceLine(String prompt, String beforeLine, String... afterLines) {
        return replaceExactBlock(prompt, List.of(beforeLine), List.of(afterLines));
    }

    public static String replaceRangeStartingAt(String prompt, String firstLine, int beforeLineCount, String... afterLines) {
        ParsedPrompt parsed = parse(prompt);
        int startIndex = findLineIndex(parsed.lines(), firstLine);
        int endIndex = startIndex + beforeLineCount;
        if (endIndex > parsed.lines().size()) {
            throw new IllegalArgumentException("Range exceeds prompt content for line: " + firstLine);
        }
        String oldText = sliceLines(parsed.content(), startIndex, beforeLineCount);
        boolean preserveTrailingNewline = oldText.endsWith("\n");
        String newText = renderLines(List.of(afterLines), preserveTrailingNewline);
        return exactEdit(parsed.targetPath(), parsed.contentHash(), oldText, newText, false);
    }

    public static String replaceExactBlock(String prompt, List<String> beforeLines, List<String> afterLines) {
        ParsedPrompt parsed = parse(prompt);
        int startIndex = findBlockIndex(parsed.lines(), beforeLines);
        String oldText = sliceLines(parsed.content(), startIndex, beforeLines.size());
        boolean preserveTrailingNewline = oldText.endsWith("\n");
        String newText = renderLines(afterLines, preserveTrailingNewline);
        return exactEdit(parsed.targetPath(), parsed.contentHash(), oldText, newText, false);
    }

    public static String staleHash(String validPatchJson) {
        Matcher matcher = BASE_CONTENT_HASH_JSON_PATTERN.matcher(validPatchJson);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Patch JSON does not contain baseContentHash");
        }
        return matcher.replaceFirst("\"baseContentHash\": \"stale-source-hash\"");
    }

    public static String malformedJson(String validPatchJson) {
        Matcher matcher = BASE_CONTENT_HASH_JSON_PATTERN.matcher(validPatchJson);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Patch JSON does not contain baseContentHash");
        }
        String malformedHash = matcher.replaceFirst("\"baseContentHash\": \"" + matcher.group(1) + "\n\"");
        return """
                ```json
                %s
                ```
                """.formatted(malformedHash);
    }

    private static ParsedPrompt parse(String prompt) {
        return new ParsedPrompt(
                extractTargetPath(prompt),
                extractContentHash(prompt),
                extractCurrentContent(prompt)
        );
    }

    private static String extractTargetPath(String prompt) {
        Matcher matcher = TARGET_PATH_PATTERN.matcher(prompt);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Prompt does not contain targetPath");
        }
        return matcher.group(1).trim();
    }

    private static String extractContentHash(String prompt) {
        Matcher matcher = CONTENT_HASH_PATTERN.matcher(prompt);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Prompt does not contain contentHash");
        }
        return matcher.group(1).trim();
    }

    private static String extractCurrentContent(String prompt) {
        int markerIndex = prompt.lastIndexOf(CONTENT_MARKER);
        if (markerIndex < 0) {
            throw new IllegalArgumentException("Prompt does not contain current content marker");
        }
        String rawSection = prompt.substring(markerIndex + CONTENT_MARKER.length()).stripLeading();
        if (rawSection.startsWith("(new file)")) {
            return "";
        }
        if (!rawSection.startsWith(CONTENT_BLOCK_PREFIX)) {
            throw new IllegalArgumentException("Prompt does not contain exact content block");
        }
        int start = CONTENT_BLOCK_PREFIX.length();
        int end = rawSection.lastIndexOf(CONTENT_BLOCK_SUFFIX);
        if (end < start) {
            throw new IllegalArgumentException("Prompt content block is malformed");
        }
        return rawSection.substring(start, end);
    }

    private static int findLineIndex(List<String> lines, String targetLine) {
        for (int index = 0; index < lines.size(); index++) {
            if (targetLine.equals(lines.get(index))) {
                return index;
            }
        }
        String normalizedTarget = targetLine.stripLeading();
        for (int index = 0; index < lines.size(); index++) {
            if (normalizedTarget.equals(lines.get(index).stripLeading())) {
                return index;
            }
        }
        throw new IllegalArgumentException("Line not found in prompt content: " + targetLine);
    }

    private static int findBlockIndex(List<String> lines, List<String> block) {
        if (block == null || block.isEmpty()) {
            throw new IllegalArgumentException("Block cannot be empty");
        }
        for (int index = 0; index <= lines.size() - block.size(); index++) {
            if (matchesBlock(lines, block, index, false) || matchesBlock(lines, block, index, true)) {
                return index;
            }
        }
        throw new IllegalArgumentException("Block not found in prompt content: " + block);
    }

    private static boolean matchesBlock(List<String> lines, List<String> block, int startIndex, boolean trimLeading) {
        for (int offset = 0; offset < block.size(); offset++) {
            String expected = trimLeading ? block.get(offset).stripLeading() : block.get(offset);
            String actual = trimLeading ? lines.get(startIndex + offset).stripLeading() : lines.get(startIndex + offset);
            if (!expected.equals(actual)) {
                return false;
            }
        }
        return true;
    }

    private static String sliceLines(String content, int startIndex, int lineCount) {
        if (lineCount <= 0) {
            return "";
        }
        List<Integer> starts = logicalLineStarts(content);
        if (startIndex < 0 || startIndex >= starts.size()) {
            throw new IllegalArgumentException("Line start out of range: " + startIndex);
        }
        int start = starts.get(startIndex);
        int endLineIndex = startIndex + lineCount - 1;
        if (endLineIndex >= starts.size()) {
            throw new IllegalArgumentException("Line range exceeds content");
        }
        int end = endLineIndex + 1 < starts.size() ? starts.get(endLineIndex + 1) : content.length();
        return content.substring(start, end);
    }

    private static List<Integer> logicalLineStarts(String content) {
        if (content.isEmpty()) {
            return List.of();
        }
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int index = 0; index < content.length(); index++) {
            if (content.charAt(index) == '\n' && index + 1 < content.length()) {
                starts.add(index + 1);
            }
        }
        if (content.endsWith("\n") && !starts.isEmpty()) {
            int lastStart = starts.getLast();
            if (lastStart == content.length()) {
                starts.removeLast();
            }
        }
        return List.copyOf(starts);
    }

    private static String appendContent(String content, List<String> afterLines) {
        String addition = renderLines(afterLines, true);
        if (content.isEmpty()) {
            return addition;
        }
        if (content.endsWith("\n")) {
            return content + addition;
        }
        return content + "\n" + addition;
    }

    private static String renderLines(List<String> lines, boolean trailingNewline) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        String rendered = String.join("\n", lines);
        return trailingNewline ? rendered + "\n" : rendered;
    }

    private static String exactEdit(
            String targetPath,
            String baseContentHash,
            String oldText,
            String newText,
            boolean replaceAll
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetPath", targetPath);
        payload.put("baseContentHash", baseContentHash);
        payload.put("oldText", oldText);
        payload.put("newText", newText);
        payload.put("replaceAll", replaceAll);
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize exact replace test payload", exception);
        }
    }

    private record ParsedPrompt(String targetPath, String contentHash, String content) {
        List<String> lines() {
            if (content.isEmpty()) {
                return List.of();
            }
            String normalized = content.endsWith("\n") ? content.substring(0, content.length() - 1) : content;
            if (normalized.isEmpty()) {
                return List.of();
            }
            return List.of(normalized.split("\n", -1));
        }
    }
}
