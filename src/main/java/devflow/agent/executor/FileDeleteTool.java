package devflow.agent.executor;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class FileDeleteTool implements ImplementationTool {

    @Override
    public String name() {
        return "Delete";
    }

    @Override
    public String description() {
        return "Delete a file that belongs to the current subtask.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("file_path"),
                "properties", Map.of(
                        "file_path", Map.of("type", "string")
                )
        );
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    @Override
    public int maxResultSizeChars() {
        return 8_000;
    }

    @Override
    public ToolInvocationResult invoke(LlmToolCall toolCall, ImplementationToolContext context) {
        try {
            DeleteInput input = context.objectMapper().convertValue(toolCall.arguments(), DeleteInput.class);
            Path absolutePath = context.requireProjectAbsolutePath(input.filePath());
            context.assertWritable(absolutePath);
            boolean existed = context.exists(absolutePath);
            String previous = existed ? context.readFile(absolutePath) : "";
            context.deleteFile(absolutePath);
            context.readFileStateLedger().invalidate(absolutePath);
            if (existed) {
                context.recordMutation(
                        ToolLoopMutationOperation.DELETE,
                        absolutePath,
                        previous,
                        "",
                        List.of()
                );
            }
            return ToolInvocationResult.success(Map.of(
                    "type", "delete",
                    "filePath", absolutePath.toString(),
                    "deleted", existed
            ));
        } catch (IllegalArgumentException exception) {
            return ToolInvocationResult.failure(Map.of(
                    "type", "error",
                    "message", exception.getMessage() == null ? "Delete failed." : exception.getMessage()
            ));
        }
    }

    private record DeleteInput(@JsonProperty("file_path") String filePath) {
    }
}
