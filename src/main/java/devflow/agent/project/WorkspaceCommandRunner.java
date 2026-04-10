package devflow.agent.project;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 工作区命令执行支撑。
 */
final class WorkspaceCommandRunner {

    CommandResult runCommand(Path projectPath, List<String> command, Duration timeout) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(projectPath.toFile())
                    .redirectErrorStream(false)
                    .start();

            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new CommandResult(124, "", "Command timed out after " + timeout);
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(process.exitValue(), stdout, stderr);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to run command in " + projectPath + ": " + command, exception);
        }
    }
}
