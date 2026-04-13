package devflow.agent.executor.shell;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

public enum ShellCommandDisposition {
    ALLOW_READ_ONLY,
    ALLOW_WRITE,
    DENY
}
