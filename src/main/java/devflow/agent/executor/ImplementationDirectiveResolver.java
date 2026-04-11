package devflow.agent.executor;

import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.FileChangePayload;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.util.EnumParsers;
import java.util.List;

/**
 * 统一解析 implementation 阶段的执行指令。
 *
 * <p>这里专门负责把 merged directive payload 映射成
 * delivery policy / fix mode 这类稳定执行参数，避免上下文门面继续内联默认值与边界裁剪。
 */
final class ImplementationDirectiveResolver {

    private final int maxFilesPerSubtask;
    private final int maxDeliveryPolicyFiles;

    ImplementationDirectiveResolver(int maxFilesPerSubtask, int maxDeliveryPolicyFiles) {
        this.maxFilesPerSubtask = maxFilesPerSubtask;
        this.maxDeliveryPolicyFiles = maxDeliveryPolicyFiles;
    }

    DeliveryPolicyEnvelope resolveDeliveryPolicy(ExecutionDirectivePayload directives) {
        DeliveryMode mode = EnumParsers.parseIgnoreCase(DeliveryMode.class, directives.deliveryMode(), null);
        Integer maxFiles = directives.deliveryMaxFiles();
        Integer maxSymbols = directives.deliveryMaxSymbols();
        Boolean preferPrecise = directives.deliveryPreferPreciseEditing();
        Boolean forceBacklogSplit = directives.deliveryForceBacklogSplit();
        Boolean requireVerification = directives.deliveryRequireVerificationBeforeReview();
        return new DeliveryPolicyEnvelope(
                mode == null ? DeliveryMode.INCREMENTAL : mode,
                maxFiles == null ? maxFilesPerSubtask : Math.max(1, Math.min(maxFiles, maxDeliveryPolicyFiles)),
                maxSymbols == null ? 4 : Math.max(1, maxSymbols),
                preferPrecise == null || preferPrecise,
                forceBacklogSplit != null && forceBacklogSplit,
                requireVerification == null || requireVerification,
                directives.requiredEvidence()
        );
    }

    FixMode resolveFixMode(ExecutionDirectivePayload directives) {
        return EnumParsers.parseIgnoreCase(FixMode.class, directives.fixMode(), FixMode.NONE);
    }

    ImplementationPatchTarget resolveImplementationPatchTarget(ExecutionDirectivePayload directives) {
        return EnumParsers.parseIgnoreCase(
                ImplementationPatchTarget.class,
                directives.implementationPatchTarget(),
                ImplementationPatchTarget.NONE
        );
    }

    List<FileChange> resolveOverrideChanges(ExecutionDirectivePayload directives) {
        if (directives == null || directives.overrideChanges() == null || directives.overrideChanges().isEmpty()) {
            return List.of();
        }
        return directives.overrideChanges().stream()
                .filter(payload -> payload != null && payload.path() != null && !payload.path().isBlank())
                .map(this::toFileChange)
                .toList();
    }

    private FileChange toFileChange(FileChangePayload payload) {
        return new FileChange(
                payload.path(),
                EnumParsers.parseIgnoreCase(ChangeAction.class, payload.action(), ChangeAction.WRITE),
                payload.reason() == null ? "" : payload.reason(),
                EnumParsers.parseIgnoreCase(FileEditScope.class, payload.editScope(), FileEditScope.AUTO),
                EnumParsers.parseIgnoreCase(RuntimeOwnershipMode.class, payload.runtimeOwnership(), null),
                Boolean.TRUE.equals(payload.hostHtmlPatchRequired())
        );
    }
}
