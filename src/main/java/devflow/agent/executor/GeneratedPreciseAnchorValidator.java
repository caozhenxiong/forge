package devflow.agent.executor;

import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;

/**
 * 校验精确编辑主链产物是否仍保留稳定锚点。
 *
 * <p>`precise-code` 不只要求语法可解析，还要求后续单元仍能继续定位目标。
 * 否则当前单元即使局部可解析，也会把后续 patch 链直接打断。
 */
final class GeneratedPreciseAnchorValidator {

    private final TargetLocator targetLocator;

    GeneratedPreciseAnchorValidator(TargetLocator targetLocator) {
        this.targetLocator = targetLocator;
    }

    GeneratedContentValidationFailure validate(Path relativePath, String content) {
        if (relativePath == null || content == null || content.isBlank() || !ProjectPathSupport.isPreciseCode(relativePath)) {
            return null;
        }
        PatchTargetContext targetContext = targetLocator.locate(relativePath, content);
        if (targetContext.preciseEditingSupported() && !targetContext.targetNames().isEmpty()) {
            return null;
        }
        return new GeneratedContentValidationFailure(
                GeneratedContentValidationCode.PRECISE_EDIT_ANCHORS_MISSING,
                "当前代码文件未保留稳定的精确编辑锚点。"
        );
    }
}
