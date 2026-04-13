package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;

import devflow.agent.editing.ExactReplaceEdit;
import devflow.agent.editing.FileStateSnapshot;
import java.nio.file.Path;

/**
 * exact-replace 语义修复的确定性层。
 *
 * <p>这里只修“宿主已知真相字段”：
 * 1. targetPath；
 * 2. baseContentHash。
 *
 * <p>它不猜 oldText/newText 的业务意图；那类问题只能继续留在当前 unit，
 * 由后续最小化 repair turn 生成新的 exact-replace payload。
 */
final class DeterministicExactReplaceRepairer {

    ExactReplaceEdit repair(
            Path relativePath,
            String currentContent,
            ExactReplaceEdit edit,
            PatchFailure failure
    ) {
        if (relativePath == null || edit == null || failure == null || failure.toolFailureCode() == null) {
            return null;
        }
        String normalizedTargetPath = relativePath.normalize().toString().replace('\\', '/');
        FileStateSnapshot snapshot = ExactReplacePromptSupport.capture(normalizedTargetPath, currentContent);
        ToolFailureCode failureCode = failure.toolFailureCode();
        boolean targetPathMismatch = !normalizedTargetPath.equals(edit.targetPath());
        boolean baseHashMismatch = !snapshot.contentHash().equals(edit.baseContentHash());
        if (failureCode == ToolFailureCode.SNAPSHOT_STALE && !baseHashMismatch && !targetPathMismatch) {
            return null;
        }
        if (failureCode != ToolFailureCode.SNAPSHOT_STALE
                && failureCode != ToolFailureCode.MODEL_OUTPUT_INVALID) {
            return null;
        }
        if (!targetPathMismatch && !baseHashMismatch) {
            return null;
        }
        return new ExactReplaceEdit(
                normalizedTargetPath,
                snapshot.contentHash(),
                edit.oldText(),
                edit.newText(),
                edit.replaceAll()
        );
    }
}
