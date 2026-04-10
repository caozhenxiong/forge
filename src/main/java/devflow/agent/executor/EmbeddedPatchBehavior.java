package devflow.agent.executor;

import devflow.agent.editing.CodePrecisePatch;
import java.nio.file.Path;

/**
 * 宿主嵌入 patch 行为接口。
 *
 * <p>把脚本/样式这类嵌入语言的差异从 `EmbeddedPatchKind` 枚举里抽出来，
 * 避免枚举继续承担整套 prompt、预算和 apply 逻辑。
 */
interface EmbeddedPatchBehavior {

    Path syntheticPath(Path relativePath);

    String baseSystemPrompt();

    String describeTargets(PatchContextBuilder patchContextBuilder, Path relativePath, String currentContent);

    PatchApplyResult applyPatch(CodePatchKernel codePatchKernel, Path relativePath, String currentContent, CodePrecisePatch patch);

    String immutableHostRegionInstruction();

    String appendBudgetInstruction(EditUnit unit);

    String strictAppendOnlyInstruction();

    String restrictedUnitInstruction();

    String truncationAppendGuidance();

    String truncationRestrictedGuidance();

    double defaultOutputBudgetRatio();

    int previewChars();
}
