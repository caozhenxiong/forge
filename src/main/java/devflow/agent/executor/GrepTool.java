package devflow.agent.executor;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class GrepTool implements ImplementationTool {

    private static final ImplementationToolSpecification SPECIFICATION = new ImplementationToolSpecification(
            "Grep",
            "Search file contents in the project using ripgrep.",
            Map.of(
                    "type", "object",
                    "required", List.of("pattern"),
                    "properties", Map.of(
                            "pattern", Map.of("type", "string"),
                            "path", Map.of("type", "string"),
                            "glob", Map.of("type", "string"),
                            "head_limit", Map.of("type", "integer")
                    )
            ),
            true,
            true,
            20_000,
            ImplementationToolPermissionScope.SEARCH_WORKSPACE
    );

    private final RipgrepCommandSupport ripgrep = new RipgrepCommandSupport();

    @Override
    public ImplementationToolSpecification specification() {
        return SPECIFICATION;
    }

    @Override
    public ToolInvocationResult invoke(LlmToolCall toolCall, ImplementationToolContext context) {
        try {
            Input input = context.objectMapper().convertValue(toolCall.arguments(), Input.class);
            ripgrep.ensureAvailable(context.projectPath());
            Path searchRoot = input.path() == null || input.path().isBlank()
                    ? context.projectPath()
                    : context.requireProjectAbsolutePath(input.path());
            java.util.ArrayList<String> command = new java.util.ArrayList<>(List.of(
                    "rg",
                    "--line-number",
                    "--with-filename",
                    "--color",
                    "never",
                    input.pattern(),
                    searchRoot.toString()
            ));
            if (input.glob() != null && !input.glob().isBlank()) {
                command.add("--glob");
                command.add(input.glob());
            }
            CommandResult result = ripgrep.run(context.projectPath(), command);
            if (result.exitCode() != 0 && result.exitCode() != 1) {
                return ToolInvocationResult.failure(Map.of("type", "error", "message", result.stderr()));
            }
            List<String> lines = Arrays.stream(result.stdout().split("\\R"))
                    .filter(line -> line != null && !line.isBlank())
                    .limit(input.headLimit() == null || input.headLimit() <= 0 ? Integer.MAX_VALUE : input.headLimit())
                    .toList();
            return ToolInvocationResult.success(Map.of(
                    "content", String.join("\n", lines),
                    "numLines", lines.size()
            ));
        } catch (IllegalArgumentException exception) {
            return ToolInvocationResult.failure(Map.of("type", "error", "message", exception.getMessage()));
        }
    }

    private record Input(
            String pattern,
            String path,
            String glob,
            @JsonProperty("head_limit") Integer headLimit
    ) {
    }
}
