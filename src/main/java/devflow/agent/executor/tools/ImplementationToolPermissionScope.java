package devflow.agent.executor.tools;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

public enum ImplementationToolPermissionScope {
    READ_WORKSPACE,
    SEARCH_WORKSPACE,
    WRITE_OWNED_PATHS,
    DELETE_OWNED_PATHS,
    EXECUTE_SHELL
}
