package devflow.agent.executor.tools;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.LlmToolCall;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class FileEditTool implements ImplementationTool {

    private static final ImplementationToolSpecification SPECIFICATION = new ImplementationToolSpecification(
            "Edit",
            "Edit an existing file in place. Read the file first and use a unique old_string.",
            Map.of(
                    "type", "object",
                    "required", List.of("file_path", "old_string", "new_string"),
                    "properties", Map.of(
                            "file_path", Map.of("type", "string"),
                            "old_string", Map.of("type", "string"),
                            "new_string", Map.of("type", "string"),
                            "replace_all", Map.of("type", "boolean")
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
            EditInput input = context.objectMapper().convertValue(toolCall.arguments(), EditInput.class);
            Path absolutePath = context.requireProjectAbsolutePath(input.filePath());
            context.assertWritable(absolutePath);
            boolean exists = context.exists(absolutePath);
            if (!exists) {
                if (input.oldString() == null || !input.oldString().isEmpty()) {
                    return error("File does not exist. Use Write for new files or Read to verify the path.");
                }
                String newContent = input.newString() == null ? "" : input.newString();
                context.assertMutationContract(absolutePath, newContent);
                context.writeFile(absolutePath, newContent);
                context.rememberReadState(
                        absolutePath,
                        new ToolExecutionContext.ToolReadState(newContent, context.modificationTime(absolutePath), null, null, false)
                );
                List<StructuredPatchHunk> structuredPatch = structuredPatchSupport.build("", newContent);
                context.recordCreateMutation(absolutePath, newContent, structuredPatch);
                return ToolInvocationResult.success(new EditOutput(
                        absolutePath.toString(),
                        input.oldString(),
                        newContent,
                        "",
                        structuredPatch,
                        false,
                        false
                ));
            }
            ToolExecutionContext.ToolReadState readState = context.readState(absolutePath);
            if (readState == null || readState.partialView()) {
                return error("File must be read first with a full read before Edit.");
            }
            String currentContent = context.readFile(absolutePath);
            if (stale(readState, context.modificationTime(absolutePath), currentContent)) {
                return error("File has been unexpectedly modified. Read it again before attempting to edit it.");
            }
            String oldString = input.oldString() == null ? "" : input.oldString();
            String newString = input.newString() == null ? "" : input.newString();
            if (oldString.equals(newString)) {
                return error("old_string and new_string must differ.");
            }
            String revised = applyExactReplace(currentContent, oldString, newString, Boolean.TRUE.equals(input.replaceAll()));
            context.assertMutationContract(absolutePath, revised);
            context.writeFile(absolutePath, revised);
            context.rememberReadState(
                    absolutePath,
                    new ToolExecutionContext.ToolReadState(revised, context.modificationTime(absolutePath), null, null, false)
            );
            List<StructuredPatchHunk> structuredPatch = structuredPatchSupport.build(currentContent, revised);
            context.recordUpdateMutation(absolutePath, currentContent, revised, structuredPatch);
            return ToolInvocationResult.success(new EditOutput(
                    absolutePath.toString(),
                    oldString,
                    newString,
                    currentContent,
                    structuredPatch,
                    false,
                    Boolean.TRUE.equals(input.replaceAll())
            ));
        } catch (IllegalArgumentException exception) {
            return error(exception.getMessage());
        }
    }

    private String applyExactReplace(String source, String oldString, String newString, boolean replaceAll) {
        if (oldString.isEmpty()) {
            if (!source.isEmpty()) {
                throw new IllegalArgumentException("Empty old_string is only valid when the target file is empty.");
            }
            return newString;
        }
        int occurrences = countOccurrences(source, oldString);
        if (occurrences == 0) {
            throw new IllegalArgumentException("String to replace not found in file.");
        }
        if (!replaceAll && occurrences > 1) {
            throw new IllegalArgumentException("old_string is not unique. Provide more context or set replace_all=true.");
        }
        String revised = replaceAll
                ? source.replace(oldString, newString)
                : replaceFirst(source, oldString, newString);
        if (revised.equals(source)) {
            throw new IllegalArgumentException("Edit did not change the file.");
        }
        return revised;
    }

    private boolean stale(ToolExecutionContext.ToolReadState readState, long currentTimestamp, String currentContent) {
        if (readState == null) {
            return true;
        }
        if (currentTimestamp <= readState.timestamp()) {
            return false;
        }
        return !(readState.fullView() && readState.content().equals(currentContent));
    }

    private int countOccurrences(String source, String target) {
        int count = 0;
        int start = 0;
        while (true) {
            int index = source.indexOf(target, start);
            if (index < 0) {
                return count;
            }
            count++;
            start = index + Math.max(1, target.length());
        }
    }

    private String replaceFirst(String source, String target, String replacement) {
        int index = source.indexOf(target);
        if (index < 0) {
            return source;
        }
        return source.substring(0, index) + replacement + source.substring(index + target.length());
    }

    private ToolInvocationResult error(String message) {
        return ToolInvocationResult.failure(Map.of("type", "error", "message", message == null ? "Edit failed." : message));
    }

    private record EditInput(
            @JsonProperty("file_path") String filePath,
            @JsonProperty("old_string") String oldString,
            @JsonProperty("new_string") String newString,
            @JsonProperty("replace_all") Boolean replaceAll
    ) {
    }

    private record EditOutput(
            String filePath,
            String oldString,
            String newString,
            String originalFile,
            List<StructuredPatchHunk> structuredPatch,
            boolean userModified,
            boolean replaceAll
    ) {
    }
}
