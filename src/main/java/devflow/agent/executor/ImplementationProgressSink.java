package devflow.agent.executor;

/**
 * 负责把 implementation 运行中的中间产物增量落盘。
 * 这个接口只关心“如何发布当前快照”，不参与规划、执行或阶段 gate。
 */
@FunctionalInterface
public interface ImplementationProgressSink {

    void publish(ImplementationExecutionBundle bundle);

    static ImplementationProgressSink noop() {
        return bundle -> {
        };
    }
}
