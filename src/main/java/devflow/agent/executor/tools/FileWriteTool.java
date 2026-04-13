package devflow.agent.executor.tools;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.LlmToolCall;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class FileWriteTool implements ImplementationTool {

    private static final ImplementationToolSpecification SPECIFICATION = new ImplementationToolSpecification(
            "Write",
            "Create or overwrite a file. Read an existing file first before overwriting it.",
            Map.of(
                    "type", "object",
                    "required", List.of("file_path", "content"),
                    "properties", Map.of(
                            "file_path", Map.of("type", "string"),
                            "content", Map.of("type", "string")
                    )
            ),
            false,
            false,
            100_000,
            ImplementationToolPermissionScope.WRITE_OWNED_PATHS
    );

    private final StructuredPatchSupport structuredPatchSupport = new StructuredPatchSupport();

    @Override
    public ImplementationToolSpecification specification() {
        return SPECIFICATION;
    }

    @Override
    public ToolInvocationResult invoke(LlmToolCall toolCall, ToolExecutionContext context) {
        try {
            WriteInput input = context.objectMapper().convertValue(toolCall.arguments(), WriteInput.class);
            Path absolutePath = context.requireProjectAbsolutePath(input.filePath());
            context.assertWritable(absolutePath);
            boolean exists = context.exists(absolutePath);
            if (exists) {
                context.assertExistingFileWholeRewriteAllowed(absolutePath, "Write");
                context.assertFreshReadBeforeOverwrite(absolutePath);
            }
            String previous = exists ? context.readFile(absolutePath) : "";
            String content = input.content() == null ? "" : input.content();
            if (exists && previous.equals(content)) {
                return ToolInvocationResult.failure(Map.of(
                        "type", "error",
                        "code", "NO_MATERIAL_CHANGE",
                        "message", "Write did not change the file."
                ));
            }
            context.assertMutationContract(absolutePath, content);
            context.writeFile(absolutePath, content);
            context.rememberReadState(
                    absolutePath,
                    new ToolExecutionContext.ToolReadState(content, context.modificationTime(absolutePath), null, null, false)
            );
            List<StructuredPatchHunk> structuredPatch = structuredPatchSupport.build(previous, content);
            if (exists) {
                context.recordUpdateMutation(absolutePath, previous, content, structuredPatch);
            } else {
                context.recordCreateMutation(absolutePath, content, structuredPatch);
            }
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
