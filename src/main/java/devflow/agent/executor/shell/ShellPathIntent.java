package devflow.agent.executor.shell;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;

public record ShellPathIntent(
        Path path,
        ShellPathIntentKind kind
) {
}
