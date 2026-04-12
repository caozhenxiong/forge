package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;

public record GenerationFailureReport(
        String targetFile,
        String deliveryMode,
        String strategy,
        GenerationFailureType failureType,
        int generationAttempts,
        boolean retryable,
        String summary,
        String evidence,
        String retryHint
) {

    private static final String PRECISE_STRATEGY_PREFIX = "precise-";

    public ReviewResult toReviewResult() {
        return new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                summary == null ? "" : summary,
                retryHint == null ? "" : retryHint,
                evidence == null ? "" : evidence,
                "1. 先按失败原因收缩改单范围。 2. 重新调用模型前保留现有可用文件。 3. 优先使用更保守的 delivery policy。",
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
        );
    }

    public String toMarkdown() {
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %d
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                """.formatted(
                language.choose("目标文件", "targetFile"),
                blank(targetFile, language),
                language.choose("交付模式", "deliveryMode"),
                blank(deliveryMode, language),
                language.choose("生成策略", "strategy"),
                blank(strategy, language),
                language.choose("失败类型", "failureType"),
                failureType == null ? "UNKNOWN" : failureType,
                language.choose("生成尝试次数", "generationAttempts"),
                generationAttempts,
                language.choose("可重试", "retryable"),
                retryable,
                language.choose("摘要", "summary"),
                blank(summary, language),
                language.choose("证据", "evidence"),
                blank(evidence, language),
                language.choose("重试提示", "retryHint"),
                blank(retryHint, language)
        ).trim();
    }

    public boolean preciseEditingFailure() {
        if (usesPreciseEditingStrategy()) {
            return true;
        }
        return failureType == GenerationFailureType.MODEL_OUTPUT_INVALID
                || failureType == GenerationFailureType.SNAPSHOT_STALE
                || failureType == GenerationFailureType.TARGET_SCOPE_VIOLATION
                || failureType == GenerationFailureType.TARGET_NOT_FOUND
                || failureType == GenerationFailureType.TARGET_NOT_UNIQUE
                || failureType == GenerationFailureType.NO_MATERIAL_CHANGE
                || failureType == GenerationFailureType.SYNTAX_INVALID;
    }

    public boolean usesPreciseEditingStrategy() {
        return strategy != null && strategy.startsWith(PRECISE_STRATEGY_PREFIX);
    }

    private String blank(String value, DocumentLanguage language) {
        return PlaceholderValues.orNone(value, language);
    }
}
