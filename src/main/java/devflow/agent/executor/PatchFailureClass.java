package devflow.agent.executor;

/**
 * patch 失败的高层分类。
 *
 * <p>这里只区分两类：
 * 1. 机械错误：格式、语法、协议结构问题，可先尝试 repair；
 * 2. 非机械错误：边界、截断、目标缺失等问题，应走 split / reroute / regenerate。
 */
enum PatchFailureClass {
    MECHANICAL,
    NON_MECHANICAL
}
