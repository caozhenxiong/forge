package devflow.agent.executor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * tool result 的线程级 transcript budget。
 *
 * <p>它不再按“当前 batch 先截一刀”工作，而是：
 * 1. 维护 toolUseId -> replacement 的稳定映射；
 * 2. 在完整 transcript 上重放 replacement；
 * 3. 只允许替换当前新产生且尚未进入模型上下文的 tool result。
 */
final class ImplementationToolResultBudgetManager {

    private static final int DEFAULT_MAX_RESULT_SIZE_CHARS = 50_000;
    private static final int MAX_TOOL_RESULTS_PER_TRANSCRIPT_CHARS = 200_000;
    private static final int PREVIEW_CHARS = 2_000;
    private static final String PERSISTED_OUTPUT_TAG = "<persisted-output>";
    private static final String PERSISTED_OUTPUT_CLOSING_TAG = "</persisted-output>";

    List<LlmChatMessage> appendToolResults(
            List<LlmChatMessage> transcript,
            Path toolResultsDirectory,
            List<ImplementationToolResultMessage> results,
            ToolLoopResultReplacementState replacementState
    ) {
        List<LlmChatMessage> baseTranscript = applyKnownReplacements(transcript, replacementState);
        if (results == null || results.isEmpty()) {
            return baseTranscript;
        }
        List<PendingToolResult> pendingResults = new ArrayList<>();
        for (ImplementationToolResultMessage result : results) {
            if (result == null) {
                continue;
            }
            String existingReplacement = replacementState == null ? null : replacementState.replacement(result.toolUseId());
            if (existingReplacement != null) {
                pendingResults.add(PendingToolResult.replaced(result, existingReplacement));
                continue;
            }
            int threshold = Math.min(
                    result.maxResultSizeChars() <= 0 ? DEFAULT_MAX_RESULT_SIZE_CHARS : result.maxResultSizeChars(),
                    DEFAULT_MAX_RESULT_SIZE_CHARS
            );
            String content = result.content() == null ? "" : result.content();
            if (content.length() > threshold) {
                String replacement = persist(toolResultsDirectory, result.toolUseId(), content);
                pendingResults.add(PendingToolResult.replaced(result, replacement));
            } else {
                pendingResults.add(PendingToolResult.raw(result));
            }
        }
        int currentRawSize = toolChars(baseTranscript);
        int totalRawSize = currentRawSize + rawChars(pendingResults);
        while (totalRawSize > MAX_TOOL_RESULTS_PER_TRANSCRIPT_CHARS) {
            PendingToolResult candidate = pendingResults.stream()
                    .filter(PendingToolResult::raw)
                    .max(Comparator.comparingInt(PendingToolResult::contentLength))
                    .orElse(null);
            if (candidate == null) {
                break;
            }
            String replacement = persist(toolResultsDirectory, candidate.message().toolUseId(), candidate.message().content());
            candidate.replace(replacement);
            totalRawSize = currentRawSize + rawChars(pendingResults);
        }

        List<LlmChatMessage> merged = new ArrayList<>(baseTranscript);
        for (PendingToolResult pending : pendingResults) {
            if (replacementState != null) {
                replacementState.markSeen(pending.message().toolUseId());
                if (!pending.raw()) {
                    replacementState.recordReplacement(pending.message().toolUseId(), pending.content());
                }
            }
            merged.add(LlmChatMessage.toolResult(
                    pending.message().toolName(),
                    pending.message().toolUseId(),
                    pending.content()
            ));
        }
        return List.copyOf(merged);
    }

    private List<LlmChatMessage> applyKnownReplacements(
            List<LlmChatMessage> transcript,
            ToolLoopResultReplacementState replacementState
    ) {
        if (transcript == null || transcript.isEmpty()) {
            return List.of();
        }
        List<LlmChatMessage> normalized = new ArrayList<>();
        for (LlmChatMessage message : transcript) {
            if (message == null || message.role() != LlmChatRole.TOOL || replacementState == null) {
                normalized.add(message);
                continue;
            }
            String replacement = replacementState.replacement(message.toolCallId());
            if (replacement == null) {
                normalized.add(message);
                continue;
            }
            normalized.add(LlmChatMessage.toolResult(message.toolName(), message.toolCallId(), replacement));
        }
        return List.copyOf(normalized);
    }

    private int toolChars(List<LlmChatMessage> transcript) {
        int total = 0;
        if (transcript == null) {
            return 0;
        }
        for (LlmChatMessage message : transcript) {
            if (message != null && message.role() == LlmChatRole.TOOL && message.content() != null) {
                total += message.content().length();
            }
        }
        return total;
    }

    private int rawChars(List<PendingToolResult> results) {
        int total = 0;
        for (PendingToolResult result : results) {
            if (result.raw()) {
                total += result.contentLength();
            }
        }
        return total;
    }

    private String persist(Path directory, String toolUseId, String content) {
        try {
            Files.createDirectories(directory);
            Path path = directory.resolve(toolUseId + extension(content));
            if (!Files.exists(path)) {
                Files.writeString(path, content == null ? "" : content);
            }
            String preview = preview(content);
            return """
                    %s
                    Output too large. Full output saved to: %s

                    Preview:
                    %s
                    %s
                    """.formatted(
                    PERSISTED_OUTPUT_TAG,
                    path,
                    preview,
                    PERSISTED_OUTPUT_CLOSING_TAG
            ).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist tool result", exception);
        }
    }

    private String extension(String content) {
        if (content == null) {
            return ".txt";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return ".json";
        }
        return ".txt";
    }

    private String preview(String content) {
        if (content == null || content.length() <= PREVIEW_CHARS) {
            return content == null ? "" : content;
        }
        return content.substring(0, PREVIEW_CHARS) + "\n...";
    }

    private static final class PendingToolResult {

        private final ImplementationToolResultMessage message;
        private String content;
        private boolean raw;

        private PendingToolResult(ImplementationToolResultMessage message, String content, boolean raw) {
            this.message = message;
            this.content = content == null ? "" : content;
            this.raw = raw;
        }

        static PendingToolResult raw(ImplementationToolResultMessage message) {
            return new PendingToolResult(message, message.content(), true);
        }

        static PendingToolResult replaced(ImplementationToolResultMessage message, String replacement) {
            return new PendingToolResult(message, replacement, false);
        }

        ImplementationToolResultMessage message() {
            return message;
        }

        String content() {
            return content;
        }

        int contentLength() {
            return content.length();
        }

        boolean raw() {
            return raw;
        }

        void replace(String replacement) {
            this.content = replacement == null ? "" : replacement;
            this.raw = false;
        }
    }
}
