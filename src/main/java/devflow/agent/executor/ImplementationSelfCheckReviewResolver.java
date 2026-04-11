package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewRevisionRoute;
import java.util.List;

/**
 * 在进入 LLM reviewer 之前，先消费 implementation 自检的结构化工具结果。
 *
 * <p>职责边界：
 * 1. 只处理确定性 tool failure；
 * 2. 不根据 prose 猜业务意图；
 * 3. 只在本地结构化证据不足时把决定交还给 reviewer。
 */
final class ImplementationSelfCheckReviewResolver {

    ReviewResult resolve(
            SelfCheckResult selfCheck,
            List<ToolResult> toolResults,
            DocumentLanguage language
    ) {
        if (selfCheck == null || selfCheck.passed()) {
            return null;
        }
        ToolResult failedTool = firstFailedTool(toolResults);
        if (failedTool == null) {
            return patchReview(
                    language.choose("当前实现未通过本地自检。", "The current implementation did not pass local self-checks."),
                    language.choose("请先修复当前实现的本地校验失败，再重新验证当前子任务。", "Repair the local validation failure before rerunning the current subtask verification."),
                    selfCheck.details(),
                    language.choose("优先修复当前自检失败，再继续进入 reviewer。", "Repair the self-check failure before continuing to the reviewer."),
                    ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                    ReviewReasonCode.IMPLEMENTATION_GAP
            );
        }
        return switch (failedTool.failureCode()) {
            case PLAYWRIGHT_PROBE_PAYLOAD_INVALID, PLAYWRIGHT_PROBE_EXECUTION_FAILED -> new ReviewResult(
                    ReviewDecision.REVISION_REQUIRED,
                    FixMode.PATCH,
                    language.choose(
                            "实现阶段的浏览器 probe 结果无效，当前子任务不能继续自动修复。",
                            "The implementation-stage browser probe is invalid, so the current subtask cannot continue automatically."
                    ),
                    language.choose(
                            "请先修复 implementation self-check 的 probe/collector 协议或执行环境，再重新执行当前子任务验证。",
                            "Repair the implementation self-check probe/collector contract or execution environment before rerunning the current subtask verification."
                    ),
                    failureEvidence(failedTool, selfCheck),
                    language.choose(
                            "这类失败属于工具链阻塞，不应继续靠 reviewer prose 猜测业务修复方向。",
                            "This failure is a tooling blocker and should not be turned into a reviewer guess about product fixes."
                    ),
                    ImplementationPatchTarget.NONE,
                    List.of(),
                    ReviewRevisionRoute.REQUEST_HUMAN,
                    ReviewReasonCode.RUNTIME_PROBE_INVALID
            );
            case RUNTIME_WIRING_INVALID -> patchReview(
                    language.choose("当前实现未通过运行时接线校验。", "The current implementation failed runtime wiring validation."),
                    language.choose("请先修复入口接线或运行时装配，再重新验证当前子任务。", "Repair the entry wiring or runtime assembly before rerunning the current subtask verification."),
                    failureEvidence(failedTool, selfCheck),
                    language.choose("优先修复宿主入口、模块装配和运行时接线。", "Repair the host entry, module assembly, and runtime wiring first."),
                    ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                    ReviewReasonCode.RUNTIME_WIRING_GAP
            );
            case RESOURCE_MISSING,
                    JAVASCRIPT_SYNTAX_INVALID,
                    INLINE_SCRIPT_INVALID,
                    JAVASCRIPT_STRUCTURE_INVALID,
                    HTML_STRUCTURE_INVALID,
                    GENERATED_CONTENT_INVALID,
                    CONTENT_VALIDATION_EXCEPTION,
                    COMMAND_FAILED,
                    TREE_SITTER_PARSE_FAILED -> patchReview(
                            language.choose("当前实现未通过本地结构或语法校验。", "The current implementation failed local structural or syntax validation."),
                            language.choose("请先修复当前实现中的确定性校验失败，再重新验证当前子任务。", "Repair the deterministic validation failure in the current implementation before rerunning the current subtask verification."),
                            failureEvidence(failedTool, selfCheck),
                            language.choose("优先根据自检工具给出的失败证据修复实现。", "Repair the implementation according to the self-check tool evidence first."),
                            ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                            ReviewReasonCode.IMPLEMENTATION_GAP
                    );
            default -> patchReview(
                    language.choose("当前实现未通过本地自检。", "The current implementation did not pass local self-checks."),
                    language.choose("请先修复当前实现的自检失败，再重新验证当前子任务。", "Repair the self-check failure before rerunning the current subtask verification."),
                    failureEvidence(failedTool, selfCheck),
                    language.choose("优先修复当前自检失败，再继续进入 reviewer。", "Repair the self-check failure before continuing to the reviewer."),
                    ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                    ReviewReasonCode.IMPLEMENTATION_GAP
            );
        };
    }

    private ReviewResult patchReview(
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            ImplementationPatchTarget implementationPatchTarget,
            ReviewReasonCode reasonCode
    ) {
        return new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                summary,
                changeRequest,
                evidence,
                actionItems,
                implementationPatchTarget,
                List.of(),
                ReviewRevisionRoute.PATCH_CURRENT_STAGE,
                reasonCode
        );
    }

    private ToolResult firstFailedTool(List<ToolResult> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return null;
        }
        return toolResults.stream()
                .filter(toolResult -> toolResult != null && toolResult.status() == ToolStatus.FAILED)
                .findFirst()
                .orElse(null);
    }

    private String failureEvidence(ToolResult toolResult, SelfCheckResult selfCheck) {
        if (toolResult != null && toolResult.evidence() != null && !toolResult.evidence().isBlank()) {
            return toolResult.evidence();
        }
        return selfCheck == null ? "" : selfCheck.details();
    }
}
