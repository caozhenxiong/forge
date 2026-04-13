package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;

/**
 * 表示 HTML 入口文件里的内联脚本工作集。
 * <p>
 * 这个工作集把 <script id="app-script"> 的内容提升成一个临时的“可精确编辑代码单元”，
 * 让后续生成链按代码符号而不是整段脚本去修改逻辑。
 */
record InlineScriptWorkingSet(
        Path syntheticPath,
        String scriptContent
) {
    boolean isUsable() {
        return syntheticPath != null
                && scriptContent != null
                && !scriptContent.isBlank();
    }
}
