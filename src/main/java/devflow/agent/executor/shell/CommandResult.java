package devflow.agent.executor.shell;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 统一描述一次外部命令执行结果。
 *
 * <p>当前只给 ripgrep / shell 工具层复用，避免每个工具再各自定义一份
 * exitCode/stdout/stderr 载荷。
 */
public record CommandResult(
        int exitCode,
        String stdout,
        String stderr
) {

    public CommandResult {
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }
}
