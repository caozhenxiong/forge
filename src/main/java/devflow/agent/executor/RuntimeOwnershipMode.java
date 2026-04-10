package devflow.agent.executor;

/**
 * HTML 入口的运行时所有权模式。
 *
 * <p>这层只回答一个问题：入口页的主运行时到底归谁负责。
 * 目前只允许两种最终态：
 * 1. INLINE_HOST: 宿主 HTML 自己保留主运行时；
 * 2. EXTERNAL_COMPANION: 宿主 HTML 只负责接线，主运行时外提到派生 companion 脚本。
 */
public enum RuntimeOwnershipMode {
    INLINE_HOST,
    EXTERNAL_COMPANION
}
