package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 声明单文件变更在编辑内核里的首选作用域。
 *
 * <p>这个字段的目的不是描述业务能力，而是告诉 patch 路由：
 * 当前文件更适合先走宿主级 patch，还是直接进入宿主内部的嵌入 patch。
 * 这样路由决策就不再依赖 prose 猜测或隐式结构启发。
 */
public enum FileEditScope {
    AUTO,
    HOST_HTML_PATCH,
    INLINE_SCRIPT_PATCH,
    INLINE_STYLE_PATCH
}
