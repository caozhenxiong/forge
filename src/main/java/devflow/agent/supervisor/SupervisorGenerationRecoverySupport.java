package devflow.agent.supervisor;

import devflow.agent.executor.GenerationFailureReport;
import devflow.agent.executor.GenerationFailureType;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一处理生成失败后的保守恢复决策。
 *
 * <p>这层只关心 subtask 级生成失败如何 `retry / repair / fail`，
 * 避免 supervisor fallback policy 同时维护阶段推进与生成恢复两套规则。
 */
final class SupervisorGenerationRecoverySupport {

    GenerationRecoveryDecision decide(
            GenerationFailureReport failureReport,
            int subtaskAttempt,
            DeliveryPolicy currentPolicy
    ) {
        DeliveryPolicy safeRetryPolicy = new DeliveryPolicy(
                currentPolicy == null ? DeliveryPolicyMode.PATCH : currentPolicy.mode(),
                1,
                1,
                true,
                false,
                true
        );
        if (failureReport == null) {
            return new GenerationRecoveryDecision(
                    GenerationRecoveryAction.FAIL_SUBTASK,
                    safeRetryPolicy,
                    "缺少生成失败证据，保守停止当前子任务。",
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
        if (!failureReport.retryable()) {
            return new GenerationRecoveryDecision(
                    GenerationRecoveryAction.FAIL_SUBTASK,
                    safeRetryPolicy,
                    "当前生成失败不可恢复，停止当前子任务。",
                    List.of(blank(failureReport.summary())),
                    List.of("不要继续重复相同生成请求"),
                    List.of(blank(failureReport.evidence()))
            );
        }
        if (subtaskAttempt >= 2) {
            return new GenerationRecoveryDecision(
                    GenerationRecoveryAction.ROUTE_TO_REPAIR,
                    DeliveryPolicy.recoverySafe(),
                    "同类生成失败已连续出现，切到更保守的 repair 式重试。",
                    mergeNonBlank(failureReport.summary(), failureReport.retryHint()),
                    List.of("限制改单范围", "保持局部精确改写", "先修当前文件的核心问题"),
                    List.of(blank(failureReport.evidence()))
            );
        }
        return new GenerationRecoveryDecision(
                GenerationRecoveryAction.RETRY_SUBTASK,
                safeRetryPolicy,
                "先按更保守的交付策略重试当前子任务。",
                mergeNonBlank(failureReport.summary(), failureReport.retryHint()),
                buildRetryConstraints(failureReport),
                List.of(blank(failureReport.evidence()))
        );
    }

    private List<String> buildRetryConstraints(GenerationFailureReport failureReport) {
        if (failureReport != null
                && (failureReport.usesPreciseEditingStrategy()
                || failureReport.preciseEditingFailure()
                || failureReport.failureType() == GenerationFailureType.OUTPUT_TRUNCATED)) {
            return List.of("不要整文件重写", "先缩小到单文件/单符号", "保持局部精确改写");
        }
        return List.of("不要整文件重写", "先缩小到单文件/单符号");
    }

    private List<String> mergeNonBlank(String first, String second) {
        List<String> items = new ArrayList<>();
        if (first != null && !first.isBlank()) {
            items.add(first.trim());
        }
        if (second != null && !second.isBlank()) {
            items.add(second.trim());
        }
        return items;
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
