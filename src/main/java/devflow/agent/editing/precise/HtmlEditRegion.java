package devflow.agent.editing.precise;

/**
 * 表示 HTML 精确改写时可聚焦的稳定区块。
 *
 * <p>这些区块对应 Forge 约定的可编辑锚点，而不是业务场景特判。
 */
public enum HtmlEditRegion {
    MARKUP,
    STYLE,
    SCRIPT
}
