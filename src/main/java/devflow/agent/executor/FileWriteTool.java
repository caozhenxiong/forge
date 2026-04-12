package devflow.agent.executor;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class FileWriteTool implements ImplementationTool {

    private final StructuredPatchSupport structuredPatchSupport = new StructuredPatchSupport();

    @Override
    public String name() {
        return "Write";
    }

    @Override
    public String description() {
        return "Create or overwrite a file. Read an existing file first before overwriting it.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("file_path", "content"),
                "properties", Map.of(
                        "file_path", Map.of("type", "string"),
                        "content", Map.of("type", "string")
                )
        );
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    @Override
    public int maxResultSizeChars() {
        return 100_000;
    }

    @Override
    public ToolInvocationResult invoke(LlmToolCall toolCall, ImplementationToolContext context) {
        try {
            WriteInput input = context.objectMapper().convertValue(toolCall.arguments(), WriteInput.class);
            Path absolutePath = context.requireProjectAbsolutePath(input.filePath());
            context.assertWritable(absolutePath);
            boolean exists = context.exists(absolutePath);
            String previous = exists ? context.readFile(absolutePath) : "";
            if (exists) {
                CoderReadFileState readState = context.readFileStateLedger().get(absolutePath);
                if (readState == null || readState.partialView()) {
                    return ToolInvocationResult.failure(Map.of(
                            "type", "error",
                            "message", "File has not been read yet. Read it first before writing to it."
                    ));
                }
                if (context.modificationTime(absolutePath) > readState.timestamp() && !(readState.fullView() && previous.equals(readState.content()))) {
                    return ToolInvocationResult.failure(Map.of(
                            "type", "error",
                            "message", "File has been modified since read. Read it again before writing."
                    ));
                }
            }
            String content = input.content() == null ? "" : input.content();
            context.writeFile(absolutePath, content);
            context.readFileStateLedger().put(absolutePath, new CoderReadFileState(content, context.modificationTime(absolutePath), null, null, false));
            List<StructuredPatchHunk> structuredPatch = structuredPatchSupport.build(previous, content);
            context.recordMutation(
                    exists ? ToolLoopMutationOperation.UPDATE : ToolLoopMutationOperation.CREATE,
                    absolutePath,
                    previous,
                    content,
                    structuredPatch
            );
            return ToolInvocationResult.success(new WriteOutput(
                    exists ? "update" : "create",
                    absolutePath.toString(),
                    content,
                    exists ? previous : null,
                    structuredPatch
            ));
        } catch (IllegalArgumentException exception) {
            return ToolInvocationResult.failure(Map.of("type", "error", "message", exception.getMessage()));
        }
    }

    private record WriteInput(
            @JsonProperty("file_path") String filePath,
            String content
    ) {
    }

    private record WriteOutput(
            String type,
            String filePath,
            String content,
            String originalFile,
            List<StructuredPatchHunk> structuredPatch
    ) {
    }
}
