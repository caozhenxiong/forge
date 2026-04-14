package devflow.agent.executor.editing;
import devflow.agent.editing.precise.HtmlEditRegion;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;
import java.util.Locale;

/**
 * 集中定义文件编辑主链里的策略标签和 operation 命名。
 *
 * <p>这些标签会同时进入 generation operation、observer 轨迹和 implementation 事件，
 * 如果继续散落在 coordinator 各处，后续改名、收敛日志或做配置化都会非常难维护。
 */
public final class FileEditStrategyNames {

    public static final String LOCAL_CODE_EDIT_REQUIRED = "local-code-edit-required";
    public static final String FULL_FILE_DISALLOWED = "full-file-disallowed";
    public static final String FULL_FILE = "full-file";
    public static final String INLINE_SCRIPT_WORKSET = "inline-script-workset";
    public static final String INLINE_STYLE_WORKSET = "inline-style-workset";
    public static final String PRECISE_CODE = "precise-code";
    public static final String STRUCTURED_HTML = "structured-html";
    public static final String PRECISE_HTML = "precise-html";
    public static final String FOCUSED_HTML_REGION = "focused-html-region";

    private FileEditStrategyNames() {
    }

    public static String operation(String strategy, Path relativePath) {
        return "implementation-" + strategy + ":" + relativePath;
    }

    public static String operation(String strategy, Path relativePath, String unitLabel) {
        return operation(strategy, relativePath) + ":" + unitLabel;
    }

    public static String observer(String strategy, String suffix) {
        return suffix == null || suffix.isBlank() ? strategy : strategy + "-" + suffix;
    }

    public static String focusedHtmlRegion(HtmlEditRegion region) {
        return FOCUSED_HTML_REGION + "-" + region.name().toLowerCase(Locale.ROOT);
    }
}
