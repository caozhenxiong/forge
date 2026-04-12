package devflow.agent.executor;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FileReadTool implements ImplementationTool {

    @Override
    public String name() {
        return "Read";
    }

    @Override
    public String description() {
        return "Read a file from the current project. Use before editing or writing an existing file.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("file_path"),
                "properties", Map.of(
                        "file_path", Map.of("type", "string", "description", "Absolute path to the file to read."),
                        "offset", Map.of("type", "integer", "description", "Optional starting line number, zero-based."),
                        "limit", Map.of("type", "integer", "description", "Optional line count to read.")
                )
        );
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public int maxResultSizeChars() {
        return 100_000;
    }

    @Override
    public ToolInvocationResult invoke(LlmToolCall toolCall, ImplementationToolContext context) {
        try {
            ReadInput input = context.objectMapper().convertValue(toolCall.arguments(), ReadInput.class);
            Path absolutePath = context.requireProjectAbsolutePath(input.filePath());
            if (!context.exists(absolutePath)) {
                return ToolInvocationResult.failure(Map.of(
                        "type", "error",
                        "message", "File does not exist.",
                        "filePath", absolutePath.toString()
                ));
            }
            long currentTimestamp = context.modificationTime(absolutePath);
            CoderReadFileState existingState = context.readFileStateLedger().get(absolutePath);
            if ((input.offset() == null || input.offset() == 0) && input.limit() == null
                    && existingState != null
                    && existingState.fullView()
                    && existingState.timestamp() == currentTimestamp) {
                return ToolInvocationResult.success(Map.of(
                        "type", "file_unchanged",
                        "filePath", absolutePath.toString()
                ));
            }
            String content = context.readFile(absolutePath);
            String selected = slice(content, input.offset(), input.limit());
            boolean partial = input.offset() != null || input.limit() != null;
            context.readFileStateLedger().put(absolutePath, new CoderReadFileState(
                    partial ? content : selected,
                    currentTimestamp,
                    input.offset(),
                    input.limit(),
                    partial
            ));
            return ToolInvocationResult.success(new ReadOutput(
                    "text",
                    absolutePath.toString(),
                    selected,
                    input.offset(),
                    input.limit(),
                    partial,
                    lineCount(content)
            ));
        } catch (IllegalArgumentException exception) {
            return ToolInvocationResult.failure(Map.of("type", "error", "message", exception.getMessage()));
        }
    }

    private String slice(String content, Integer offset, Integer limit) {
        if ((offset == null || offset == 0) && limit == null) {
            return content;
        }
        String[] lines = content.split("\\R", -1);
        int start = offset == null ? 0 : Math.max(0, offset);
        int end = limit == null ? lines.length : Math.min(lines.length, start + Math.max(0, limit));
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < end; index++) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(lines[index]);
        }
        return builder.toString();
    }

    private int lineCount(String content) {
        return content == null || content.isEmpty() ? 0 : content.split("\\R", -1).length;
    }

    private record ReadInput(
            @JsonProperty("file_path") String filePath,
            Integer offset,
            Integer limit
    ) {
    }

    private record ReadOutput(
            String type,
            String filePath,
            String content,
            Integer offset,
            Integer limit,
            boolean partialView,
            int totalLines
    ) {
    }
}
