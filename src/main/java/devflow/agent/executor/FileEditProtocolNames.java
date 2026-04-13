package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 编辑内核唯一允许的协议名称。
 */
final class FileEditProtocolNames {

    static final String TARGETED_REWRITE = "targeted-rewrite";
    static final String FULL_REWRITE = "full-rewrite";

    private FileEditProtocolNames() {
    }
}
