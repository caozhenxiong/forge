package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;

/**
 * 表示 HTML 入口文件里的内联样式工作集。
 *
 * <p>这个工作集把 `<style id="app-style">` 的内容提升成一个临时的“可精确编辑样式单元”，
 * 让后续编辑链可以像普通 `style.css` 一样按规则块做 patch。
 */
record InlineStyleWorkingSet(
        Path syntheticPath,
        String styleContent,
        int stableRuleCount,
        int totalRuleCount
) {
    boolean isUsable() {
        return syntheticPath != null
                && styleContent != null
                && !styleContent.isBlank();
    }

    boolean supportsRuleLevelPatch() {
        return isUsable() && stableRuleCount > 0 && totalRuleCount > 0;
    }
}
