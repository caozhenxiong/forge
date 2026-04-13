package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 表示 HTML 精确改写时可聚焦的稳定区块。
 * 这些区块对应 Forge 约定的可编辑锚点，而不是业务场景特判。
 */
public enum HtmlEditRegion {
    MARKUP,
    STYLE,
    SCRIPT
}
