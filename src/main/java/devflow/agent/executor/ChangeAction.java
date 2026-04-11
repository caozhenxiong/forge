package devflow.agent.executor;

/**
 * 描述子任务对单个文件的意图。
 * 这里只表达“写入”或“删除”这样的稳定动作，不承载更高层的业务语义。
 */
public enum ChangeAction {
    WRITE,
    DELETE
}
