package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.editing.precise.ExactReplaceEdit;
import java.nio.file.Path;

/**
 * 宿主嵌入 patch 行为接口。
 *
 * <p>把脚本/样式这类嵌入语言的差异从 `EmbeddedPatchKind` 枚举里抽出来，
 * 避免枚举继续承担整套 prompt、预算和 apply 逻辑。
 */
public interface EmbeddedPatchBehavior {

    public Path syntheticPath(Path relativePath);

    public String baseSystemPrompt();

    public String describeTargets(PatchContextBuilder patchContextBuilder, Path relativePath, String currentContent);

    public PatchApplyResult applyPatch(CodePatchKernel codePatchKernel, Path relativePath, String currentContent, ExactReplaceEdit edit);

    public String immutableHostRegionInstruction();

    public String appendBudgetInstruction(EditUnit unit);

    public String strictAppendOnlyInstruction();

    public String restrictedUnitInstruction();

    public String truncationAppendGuidance();

    public String truncationRestrictedGuidance();

    public double defaultOutputBudgetRatio();

    public int previewChars();
}
