package devflow.agent.executor.shell;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

public enum ShellPathIntentKind {
    READ_FILE,
    WRITE_FILE,
    DELETE_FILE,
    PREPARE_DIRECTORY
}
