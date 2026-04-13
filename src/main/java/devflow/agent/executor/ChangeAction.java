package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 描述子任务对单个文件的意图。
 * 这里只表达“写入”或“删除”这样的稳定动作，不承载更高层的业务语义。
 */
public enum ChangeAction {
    WRITE,
    DELETE
}
