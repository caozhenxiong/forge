package devflow.agent.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StructuredDiffTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern SOURCE_HASH_PATTERN =
            Pattern.compile("- sourceHash: ([^\\r\\n]+)");
    private static final Pattern NUMBERED_LINE_PATTERN =
            Pattern.compile("^\\s*(\\d+) \\| ?(.*)$");
    private static final Pattern EXPECTED_SOURCE_HASH_JSON_PATTERN =
            Pattern.compile("\"expectedSourceHash\"\\s*:\\s*\"([^\"]*)\"");
    private static final String NUMBERED_CONTENT_MARKER = "内容（带行号）：";

    private StructuredDiffTestSupport() {
    }

    public static String appendAtEnd(String prompt, String... afterLines) {
        ParsedPrompt parsed = parse(prompt);
        return patch(
                parsed.sourceHash(),
                List.of(new HunkSpec(parsed.lines().size() + 1, List.of(), List.of(afterLines)))
        );
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
        return patch(
                parsed.sourceHash(),
                List.of(new HunkSpec(
                        startIndex + 1,
                        List.copyOf(parsed.lines().subList(startIndex, endIndex)),
                        List.of(afterLines)
                ))
        );
    }

    public static String replaceExactBlock(String prompt, List<String> beforeLines, List<String> afterLines) {
        ParsedPrompt parsed = parse(prompt);
        int startIndex = findBlockIndex(parsed.lines(), beforeLines);
        List<String> actualBeforeLines = List.copyOf(parsed.lines().subList(startIndex, startIndex + beforeLines.size()));
        return patch(
                parsed.sourceHash(),
                List.of(new HunkSpec(startIndex + 1, actualBeforeLines, afterLines))
        );
    }

    public static String staleHash(String validPatchJson) {
        Matcher matcher = EXPECTED_SOURCE_HASH_JSON_PATTERN.matcher(validPatchJson);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Patch JSON does not contain expectedSourceHash");
        }
        return matcher.replaceFirst("\"expectedSourceHash\": \"stale-source-hash\"");
    }

    public static String malformedJson(String validPatchJson) {
        Matcher matcher = EXPECTED_SOURCE_HASH_JSON_PATTERN.matcher(validPatchJson);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Patch JSON does not contain expectedSourceHash");
        }
        String malformedHash = matcher.replaceFirst("\"expectedSourceHash\": \"" + matcher.group(1) + "\n\"");
        return """
                ```json
                %s
                ```
                """.formatted(malformedHash.replaceFirst("\\n\\s*\\]\\s*\\n\\s*}", ",\n  ]\n}"));
    }

    private static ParsedPrompt parse(String prompt) {
        String sourceHash = extractSourceHash(prompt);
        List<String> numberedLines = extractNumberedLines(prompt);
        return new ParsedPrompt(sourceHash, numberedLines);
    }

    private static String extractSourceHash(String prompt) {
        Matcher matcher = SOURCE_HASH_PATTERN.matcher(prompt);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Prompt does not contain sourceHash");
        }
        return matcher.group(1).trim();
    }

    private static List<String> extractNumberedLines(String prompt) {
        int markerIndex = prompt.lastIndexOf(NUMBERED_CONTENT_MARKER);
        if (markerIndex < 0) {
            throw new IllegalArgumentException("Prompt does not contain numbered content");
        }
        String contentSection = prompt.substring(markerIndex + NUMBERED_CONTENT_MARKER.length()).strip();
        if (contentSection.startsWith("(empty file)")) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : contentSection.split("\\R", -1)) {
            Matcher matcher = NUMBERED_LINE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            lines.add(matcher.group(2));
        }
        return List.copyOf(lines);
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

    private static String patch(String expectedSourceHash, List<HunkSpec> hunks) {
        List<Map<String, Object>> hunkPayloads = new ArrayList<>(hunks.size());
        for (HunkSpec hunk : hunks) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sourceStartLine", hunk.sourceStartLine());
            payload.put("beforeLines", hunk.beforeLines());
            payload.put("afterLines", hunk.afterLines());
            hunkPayloads.add(payload);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expectedSourceHash", expectedSourceHash);
        payload.put("hunks", hunkPayloads);
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize structured diff test payload", exception);
        }
    }

    private record ParsedPrompt(String sourceHash, List<String> lines) {
    }

    private record HunkSpec(int sourceStartLine, List<String> beforeLines, List<String> afterLines) {
    }
}
