package devflow.agent.executor;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;

final class BashTool implements ImplementationTool {

    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    @Override
    public String name() {
        return "Bash";
    }

    @Override
    public String description() {
        return "Run a shell command inside the current project root.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("command"),
                "properties", Map.of(
                        "command", Map.of("type", "string"),
                        "timeout", Map.of("type", "integer")
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
            Input input = context.objectMapper().convertValue(toolCall.arguments(), Input.class);
            long timeoutMs = input.timeout() == null || input.timeout() <= 0 ? DEFAULT_TIMEOUT_MS : input.timeout();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            DefaultExecutor executor = new DefaultExecutor();
            executor.setWorkingDirectory(context.projectPath().toFile());
            executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));
            executor.setWatchdog(new ExecuteWatchdog(timeoutMs));
            int exitCode;
            try {
                CommandLine commandLine = new CommandLine("bash");
                commandLine.addArgument("-lc", false);
                commandLine.addArgument(input.command(), false);
                exitCode = executor.execute(commandLine);
            } catch (ExecuteException exception) {
                exitCode = exception.getExitValue();
            }
            return ToolInvocationResult.success(Map.of(
                    "exitCode", exitCode,
                    "stdout", stdout.toString(),
                    "stderr", stderr.toString(),
                    "timeoutMs", timeoutMs
            ));
        } catch (IllegalArgumentException exception) {
            return ToolInvocationResult.failure(Map.of("type", "error", "message", exception.getMessage()));
        } catch (Exception exception) {
            return ToolInvocationResult.failure(Map.of("type", "error", "message", exception.getMessage()));
        }
    }

    private record Input(
            String command,
            Long timeout
    ) {
    }
}
