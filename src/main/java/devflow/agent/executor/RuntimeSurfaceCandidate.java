package devflow.agent.executor;

/**
 * 运行时可观测表面候选。
 *
 * <p>这里只表达浏览器执行后已经看到的稳定事实，
 * 不负责推断业务语义或选择最终 target。
 */
public record RuntimeSurfaceCandidate(
        String selector,
        UiObservationMode mode,
        long area
) {

    public RuntimeSurfaceCandidate {
        selector = selector == null ? "" : selector.trim();
        mode = mode == null ? UiObservationMode.DOM_SIGNATURE : mode;
        area = Math.max(area, 0L);
    }

    public boolean usable() {
        return !selector.isBlank();
    }
}
