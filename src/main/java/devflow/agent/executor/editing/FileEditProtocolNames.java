package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 编辑内核唯一允许的协议名称。
 */
public final class FileEditProtocolNames {

    public static final String TARGETED_REWRITE = "targeted-rewrite";
    public static final String FULL_REWRITE = "full-rewrite";

    private FileEditProtocolNames() {
    }
}
