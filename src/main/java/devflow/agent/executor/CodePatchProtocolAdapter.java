package devflow.agent.executor;

import devflow.agent.editing.CodePatchContentResolver;
import devflow.agent.editing.CodePreciseAction;
import devflow.agent.editing.CodePreciseOperation;
import devflow.agent.editing.CodePrecisePatch;
import java.util.List;

/**
 * 把当前代码精确改写协议映射到通用 patch 协议。
 *
 * <p>这层的作用不是替代 `CodePreciseEditor`，而是把外层编排逻辑从
 * `CodePreciseAction` 这类语言/协议细节里抽出来，便于后续接入其它语言。
 */
final class CodePatchProtocolAdapter {

    List<PatchOperation> toOperations(CodePrecisePatch patch) {
        if (patch == null || patch.operations() == null || patch.operations().isEmpty()) {
            return List.of();
        }
        return patch.operations().stream()
                .filter(operation -> operation != null && operation.action() != null)
                .map(this::toOperation)
                .toList();
    }

    private PatchOperation toOperation(CodePreciseOperation operation) {
        return new PatchOperation(
                mapType(operation.action()),
                mapTarget(operation),
                CodePatchContentResolver.resolve(operation).lines().toList()
        );
    }

    private PatchOperationType mapType(CodePreciseAction action) {
        if (action == CodePreciseAction.REPLACE_SYMBOL) {
            return PatchOperationType.REPLACE_BLOCK;
        }
        if (action == CodePreciseAction.REPLACE_SYMBOL_BODY) {
            return PatchOperationType.REPLACE_BODY;
        }
        if (action == CodePreciseAction.INSERT_INTO_SYMBOL) {
            return PatchOperationType.INSERT_INTO_BLOCK;
        }
        return PatchOperationType.APPEND_BLOCK;
    }

    private PatchTarget mapTarget(CodePreciseOperation operation) {
        if (operation.action() == CodePreciseAction.APPEND_FILE) {
            return new PatchTarget(PatchTargetKind.FILE_END, null, null);
        }
        return new PatchTarget(PatchTargetKind.SYMBOL, operation.targetSymbol(), operation.targetKind());
    }
}
