package devflow.agent.executor;

/**
 * 统一维护生成链里稳定的预算配置。
 *
 * <p>当前先收敛最容易反复散落的两类预算：
 * 1. 模型输出预算比例；
 * 2. 文件/上下文预览摘要的字符预算。
 *
 * <p>后续如果要继续配置化，可以优先从这里下沉到 properties，而不是回到各模块写死数字。
 */
public final class GenerationBudgetProfile {

    private static final String PREFIX = "devflow.generation-budget.";
    public static final int FILE_CONTEXT_PREVIEW_CHARS = 12000;
    public static final int INLINE_SCRIPT_PREVIEW_CHARS = 10000;
    public static final int INLINE_STYLE_PREVIEW_CHARS = INLINE_SCRIPT_PREVIEW_CHARS;
    public static final double STRUCTURED_REVIEW_OUTPUT_RATIO = 0.08d;
    public static final double DOCUMENT_REVIEW_OUTPUT_RATIO = 0.10d;
    public static final double IMPLEMENTATION_REVIEW_OUTPUT_RATIO = 0.12d;
    public static final double SUBTASK_REVIEW_OUTPUT_RATIO = 0.12d;
    public static final double SUPERVISOR_DECISION_OUTPUT_RATIO = 0.08d;
    public static final double GENERATION_RECOVERY_OUTPUT_RATIO = 0.08d;
    public static final double CODE_REVIEW_OUTPUT_RATIO = 0.40d;
    public static final double VALIDATION_STRATEGY_OUTPUT_RATIO = 0.18d;
    public static final double DIAGNOSIS_OUTPUT_RATIO = 0.25d;
    public static final double DIAGNOSIS_SIMILARITY_OUTPUT_RATIO = 0.05d;
    public static final double FULL_BUDGET_RATIO = 1.0d;
    public static final double PATCH_BUDGET_RATIO = 0.6d;
    public static final double SCAFFOLD_BUDGET_RATIO = 0.7d;

    private GenerationBudgetProfile() {
    }

    public static double structuredReviewOutputRatio() {
        return readPositiveDouble(PREFIX + "structured-review-output-ratio", STRUCTURED_REVIEW_OUTPUT_RATIO);
    }

    public static double documentFullDraftOutputRatio() {
        return readPositiveDouble(PREFIX + "document-full-draft-output-ratio", FULL_BUDGET_RATIO);
    }

    public static double documentPatchOutputRatio() {
        return readPositiveDouble(PREFIX + "document-patch-output-ratio", PATCH_BUDGET_RATIO);
    }

    public static double documentReviewOutputRatio() {
        return readPositiveDouble(PREFIX + "document-review-output-ratio", DOCUMENT_REVIEW_OUTPUT_RATIO);
    }

    public static double implementationReviewOutputRatio() {
        return readPositiveDouble(PREFIX + "implementation-review-output-ratio", IMPLEMENTATION_REVIEW_OUTPUT_RATIO);
    }

    public static double implementationPlanOutputRatio() {
        return readPositiveDouble(PREFIX + "implementation-plan-output-ratio", FULL_BUDGET_RATIO);
    }

    public static double implementationPlanRepairOutputRatio() {
        return readPositiveDouble(PREFIX + "implementation-plan-repair-output-ratio", FULL_BUDGET_RATIO);
    }

    public static double subtaskReviewOutputRatio() {
        return readPositiveDouble(PREFIX + "subtask-review-output-ratio", SUBTASK_REVIEW_OUTPUT_RATIO);
    }

    public static double supervisorDecisionOutputRatio() {
        return readPositiveDouble(PREFIX + "supervisor-decision-output-ratio", SUPERVISOR_DECISION_OUTPUT_RATIO);
    }

    public static double generationRecoveryOutputRatio() {
        return readPositiveDouble(PREFIX + "generation-recovery-output-ratio", GENERATION_RECOVERY_OUTPUT_RATIO);
    }

    public static double codeReviewOutputRatio() {
        return readPositiveDouble(PREFIX + "code-review-output-ratio", CODE_REVIEW_OUTPUT_RATIO);
    }

    public static double validationStrategyOutputRatio() {
        return readPositiveDouble(PREFIX + "validation-strategy-output-ratio", VALIDATION_STRATEGY_OUTPUT_RATIO);
    }

    public static double focusedScriptOutputRatio() {
        return readPositiveDouble(PREFIX + "focused-script-output-ratio", FULL_BUDGET_RATIO);
    }

    public static double focusedMarkupOutputRatio() {
        return readPositiveDouble(PREFIX + "focused-markup-output-ratio", FULL_BUDGET_RATIO);
    }

    public static double focusedStyleOutputRatio() {
        return readPositiveDouble(PREFIX + "focused-style-output-ratio", FULL_BUDGET_RATIO);
    }

    public static double inlineScriptUnitOutputRatio() {
        return readPositiveDouble(PREFIX + "inline-script-unit-output-ratio", FULL_BUDGET_RATIO);
    }

    public static double preciseCodeScaffoldOutputRatio() {
        return readPositiveDouble(PREFIX + "precise-code-scaffold-output-ratio", SCAFFOLD_BUDGET_RATIO);
    }

    public static double preciseCodeUnitOutputRatio() {
        return readPositiveDouble(PREFIX + "precise-code-unit-output-ratio", FULL_BUDGET_RATIO);
    }

    public static double preciseHtmlOutputRatio() {
        return readPositiveDouble(PREFIX + "precise-html-output-ratio", FULL_BUDGET_RATIO);
    }

    public static int fileContextPreviewChars() {
        return readPositiveInt(PREFIX + "file-context-preview-chars", FILE_CONTEXT_PREVIEW_CHARS);
    }

    public static int inlineScriptPreviewChars() {
        return readPositiveInt(PREFIX + "inline-script-preview-chars", INLINE_SCRIPT_PREVIEW_CHARS);
    }

    public static int inlineStylePreviewChars() {
        return readPositiveInt(PREFIX + "inline-style-preview-chars", INLINE_STYLE_PREVIEW_CHARS);
    }

    public static double diagnosisOutputRatio() {
        return readPositiveDouble(PREFIX + "diagnosis-output-ratio", DIAGNOSIS_OUTPUT_RATIO);
    }

    public static double diagnosisSimilarityOutputRatio() {
        return readPositiveDouble(PREFIX + "diagnosis-similarity-output-ratio", DIAGNOSIS_SIMILARITY_OUTPUT_RATIO);
    }

    public static double fullBudgetRatio() {
        return readPositiveDouble(PREFIX + "full-budget-ratio", FULL_BUDGET_RATIO);
    }

    public static double patchOutputRatio() {
        return readPositiveDouble(PREFIX + "patch-budget-ratio", PATCH_BUDGET_RATIO);
    }

    public static double wholeFileRewriteOutputRatio(DeliveryMode deliveryMode) {
        if (deliveryMode == DeliveryMode.PATCH) {
            return patchOutputRatio();
        }
        return fullBudgetRatio();
    }

    private static int readPositiveInt(String key, int fallback) {
        Integer configured = Integer.getInteger(key);
        return configured == null || configured <= 0 ? fallback : configured;
    }

    private static double readPositiveDouble(String key, double fallback) {
        String configured = System.getProperty(key);
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(configured.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
