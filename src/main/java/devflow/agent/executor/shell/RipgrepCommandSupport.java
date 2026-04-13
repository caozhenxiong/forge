package devflow.agent.executor.shell;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * rg 命令支持层。
 *
 * <p>Glob/Grep 直接依赖 ripgrep 二进制，不提供 Java fallback。
 */
public final class RipgrepCommandSupport {

    public void ensureAvailable(Path projectPath) {
        CommandResult result = run(projectPath, List.of("rg", "--version"));
        if (result.exitCode() != 0) {
            throw new IllegalStateException("ripgrep (rg) is required for implementation tool runtime.");
        }
    }

    public CommandResult run(Path projectPath, List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(projectPath.toFile())
                    .redirectErrorStream(false)
                    .start();
            int exitCode = process.waitFor();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(exitCode, stdout, stderr);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to run ripgrep command: " + command, exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to run ripgrep command: " + command, exception);
        }
    }
}
