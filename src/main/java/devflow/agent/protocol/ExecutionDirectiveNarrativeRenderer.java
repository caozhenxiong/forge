package devflow.agent.protocol;

import devflow.agent.context.ContractMetadataKeys;
import devflow.agent.i18n.PlaceholderValues;

/**
 * 把 execution directive 渲染成人类可读的补充说明。
 *
 * <p>流程实际依赖的机器真相在 {@link ExecutionDirectivePayload} 中；
 * 这里的 markdown 只服务于模型阅读与调试展示。
 */
public final class ExecutionDirectiveNarrativeRenderer {

    private ExecutionDirectiveNarrativeRenderer() {
    }

    public static String renderRevisionNote(
            ExecutionDirectivePayload payload,
            String summary,
            String changeRequest,
            String evidence,
            String actionItems
    ) {
        return """
                %s

                ## Revision Summary
                %s

                ## Change Request
                %s

                ## Evidence
                %s

                ## Action Items
                %s
                """.formatted(
                ExecutionDirectiveProtocol.renderBlock(payload),
                blank(summary),
                blankWithoutStructuredBlocks(changeRequest),
                blankWithoutStructuredBlocks(evidence),
                blankWithoutStructuredBlocks(actionItems)
        ).trim();
    }

    public static String renderRetryFeedback(
            ExecutionDirectivePayload payload,
            String selfCheckSummary,
            String selfCheckDetails,
            String verificationSummary,
            String verificationChangeRequest,
            String completenessSummary,
            String completenessEvidence
    ) {
        return """
                %s

                ## Retry Feedback

                ### Self Check
                - summary: %s
                - details: %s

                ### Verification
                - summary: %s
                - changeRequest: %s

                ### Completeness
                - summary: %s
                - evidence:
                %s
                """.formatted(
                ExecutionDirectiveProtocol.renderBlock(payload),
                blank(selfCheckSummary),
                blankWithoutStructuredBlocks(selfCheckDetails),
                blank(verificationSummary),
                blankWithoutStructuredBlocks(verificationChangeRequest),
                blank(completenessSummary),
                blankWithoutStructuredBlocks(completenessEvidence, PlaceholderValues.bulletMachineNone())
        ).trim();
    }

    public static String renderRepairBriefVerification(String feedback) {
        return """
                ## Repair Brief Verification
                当前子任务处于 repair brief 强约束下，请优先验证：
                1. Must Fix First 是否已有直接证据表明被覆盖
                2. Acceptance Checks 是否至少在当前子任务范围内得到响应
                3. 是否仍沿着 Forbidden Directions 继续偏离

                修复上下文：
                %s
                """.formatted(blankWithoutStructuredBlocks(feedback)).trim();
    }

    public static String renderPerformanceValidationGuidance(Integer pageLoadMaxMs, Integer interactionMaxMs) {
        return """
                ## Design Performance Validation
                Contract Metadata 已显式声明需要性能测量，请补充对应的基础测量结果，再决定是否需要优化：
                - %s: true
                - %s: %s
                - %s: %s
                """.formatted(
                ContractMetadataKeys.VALIDATION_PERFORMANCE_MEASUREMENT_REQUIRED,
                ContractMetadataKeys.VALIDATION_PAGE_LOAD_MAX_MS,
                pageLoadMaxMs == null ? PlaceholderValues.machineNone() : pageLoadMaxMs,
                ContractMetadataKeys.VALIDATION_INTERACTION_MAX_MS,
                interactionMaxMs == null ? PlaceholderValues.machineNone() : interactionMaxMs
        ).trim();
    }

    private static String blank(String value) {
        return blank(value, "");
    }

    private static String blank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String blankWithoutStructuredBlocks(String value) {
        return blankWithoutStructuredBlocks(value, "");
    }

    private static String blankWithoutStructuredBlocks(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String stripped = StructuredArtifactBlocks.stripAllKnownBlocks(value).trim();
        return stripped.isBlank() ? defaultValue : stripped;
    }
}
