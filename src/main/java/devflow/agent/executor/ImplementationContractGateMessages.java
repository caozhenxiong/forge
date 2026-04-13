package devflow.agent.executor;

import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewReasonCode;
import java.util.List;

/**
 * implementation contract gate 的共享文案与 reason 归一入口。
 *
 * <p>Stage reviewer 与 implementation continuation 都必须消费同一套 gate 解释，
 * 避免同一个 deterministic failure 在不同阶段被写成两套语义。
 */
public final class ImplementationContractGateMessages {

    private ImplementationContractGateMessages() {
    }

    public static ReviewReasonCode reasonCode(ArchitectIntegrationCheckResult architectCheckResult) {
        if (architectCheckResult == null || architectCheckResult.failureReason() == null) {
            return ReviewReasonCode.NONE;
        }
        if (architectCheckResult.failureReason() == ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID
                && architectCheckResult.implementationPatchTarget() == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            return ReviewReasonCode.RUNTIME_WIRING_GAP;
        }
        return ReviewReasonCode.IMPLEMENTATION_GAP;
    }

    public static String summary(ArchitectIntegrationCheckResult architectCheckResult) {
        if (architectCheckResult == null || architectCheckResult.failureReason() == null) {
            return "当前实现未满足 approved contract。";
        }
        return switch (architectCheckResult.failureReason()) {
            case ENTRY_MISSING -> "当前实现缺少 approved contract 要求的可启动入口。";
            case RUNTIME_WIRING_INVALID -> "当前实现的入口接线或运行时所有权未满足 approved contract。";
            case SURFACE_MISSING -> "当前实现缺少 approved contract 要求的可见运行表面。";
            case IMPLEMENTATION_INCOMPLETE -> "当前实现仍未补齐 approved contract 要求的能力。";
        };
    }

    public static String changeRequest(ArchitectIntegrationCheckResult architectCheckResult) {
        if (architectCheckResult == null || architectCheckResult.failureReason() == null) {
            return "请在当前 IMPLEMENTATION 阶段继续补齐实现，并保持 approved contract 不变。";
        }
        return switch (architectCheckResult.failureReason()) {
            case ENTRY_MISSING ->
                    "请在当前 IMPLEMENTATION 阶段补齐可启动入口，不要回退或重写已批准的设计契约。";
            case RUNTIME_WIRING_INVALID ->
                    "请在当前 IMPLEMENTATION 阶段修复入口接线与运行时所有权，使交付结果满足 approved contract。";
            case SURFACE_MISSING ->
                    "请在当前 IMPLEMENTATION 阶段补齐可见运行表面，并保持 approved contract 不变。";
            case IMPLEMENTATION_INCOMPLETE ->
                    "请在当前 IMPLEMENTATION 阶段补齐缺失实现，不要把当前缺口回退成设计问题。";
        };
    }

    public static String evidence(ArchitectIntegrationCheckResult architectCheckResult, List<String> incompleteSubtasks) {
        if (architectCheckResult == null) {
            return "";
        }
        String scope = blank(architectCheckResult.scope() == null ? null : architectCheckResult.scope().name()).isBlank()
                ? ""
                : " scope=" + architectCheckResult.scope().name() + "。";
        String reason = blank(architectCheckResult.failureReason() == null ? null : architectCheckResult.failureReason().name()).isBlank()
                ? ""
                : " failureReason=" + architectCheckResult.failureReason().name() + "。";
        String details = blank(architectCheckResult.details()).isBlank() ? "" : " details=" + architectCheckResult.details().trim();
        String incomplete = incompleteSubtasks == null || incompleteSubtasks.isEmpty()
                ? ""
                : " 未完成子任务=" + String.join("；", incompleteSubtasks) + "。";
        return "当前实现未通过 contract gate。" + scope + reason + details + incomplete;
    }

    public static String actionItems(ArchitectIntegrationCheckResult architectCheckResult) {
        if (architectCheckResult == null || architectCheckResult.failureReason() == null) {
            return "1. 在当前 IMPLEMENTATION 阶段继续补齐缺失实现。 2. 保持 approved contract 不变。 3. 完成后重新进入 implementation review。";
        }
        return switch (architectCheckResult.failureReason()) {
            case RUNTIME_WIRING_INVALID ->
                    "1. 优先修复入口接线、资源引用和初始化流程。 2. 保持既有 runtime 所有权不变，不要重开新骨架。 3. 满足 approved contract 后再重新审阅。";
            case ENTRY_MISSING ->
                    "1. 补齐 approved contract 要求的可启动入口。 2. 不要回退或重做已批准的设计契约。 3. 完成后重新进入 implementation review。";
            case SURFACE_MISSING, IMPLEMENTATION_INCOMPLETE ->
                    "1. 在当前 IMPLEMENTATION 阶段补齐缺失能力。 2. 保持 approved contract 不变。 3. 完成后重新进入 implementation review。";
        };
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }
}
