package devflow.agent.executor.runtime;

/**
 * 运行时显式控制入口候选。
 *
 * <p>这层只承载浏览器快照中真实存在的控制器信息，
 * 不把它直接升级成 run-state-entry 语义。
 */
public record RuntimeControlCandidate(
        String selector,
        String text
) {

    public RuntimeControlCandidate {
        selector = selector == null ? "" : selector.trim();
        text = text == null ? "" : text.trim();
    }

    public boolean usable() {
        return !selector.isBlank();
    }
}
