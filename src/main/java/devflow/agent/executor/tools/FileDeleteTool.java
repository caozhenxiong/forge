package devflow.agent.executor.tools;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.LlmToolCall;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class FileDeleteTool implements ImplementationTool {

    private static final ImplementationToolSpecification SPECIFICATION = new ImplementationToolSpecification(
            "Delete",
            "Delete a file that belongs to the current subtask.",
            Map.of(
                    "type", "object",
                    "required", List.of("file_path"),
                    "properties", Map.of(
                            "file_path", Map.of("type", "string")
                    )
            ),
            false,
            false,
            8_000,
            ImplementationToolPermissionScope.DELETE_OWNED_PATHS
    );

    @Override
    public ImplementationToolSpecification specification() {
        return SPECIFICATION;
    }

    @Override
    public ToolInvocationResult invoke(LlmToolCall toolCall, ToolExecutionContext context) {
        try {
            DeleteInput input = context.objectMapper().convertValue(toolCall.arguments(), DeleteInput.class);
            Path absolutePath = context.requireProjectAbsolutePath(input.filePath());
            context.assertWritable(absolutePath);
            boolean existed = context.exists(absolutePath);
            String previous = existed ? context.readFile(absolutePath) : "";
            context.deleteFile(absolutePath);
            context.clearReadState(absolutePath);
            if (existed) {
                context.recordDeleteMutation(absolutePath, previous);
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
