package devflow.agent.executor.generation;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.DeliveryMode;

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

    public static final int DEFAULT_FILE_CONTEXT_PREVIEW_CHARS = 12_000;
    public static final int DEFAULT_INLINE_SCRIPT_PREVIEW_CHARS = 10_000;
    public static final int DEFAULT_INLINE_STYLE_PREVIEW_CHARS = DEFAULT_INLINE_SCRIPT_PREVIEW_CHARS;
    public static final double DEFAULT_SUPERVISOR_DECISION_OUTPUT_RATIO = 0.08d;
    public static final double DEFAULT_GENERATION_RECOVERY_OUTPUT_RATIO = 0.08d;
    public static final double DEFAULT_CODE_REVIEW_OUTPUT_RATIO = 0.40d;
    public static final double DEFAULT_VALIDATION_STRATEGY_OUTPUT_RATIO = 0.18d;
    public static final double DEFAULT_DIAGNOSIS_OUTPUT_RATIO = 0.25d;
    public static final double DEFAULT_DIAGNOSIS_SIMILARITY_OUTPUT_RATIO = 0.05d;
    public static final double DEFAULT_FULL_BUDGET_RATIO = 1.0d;
    public static final double DEFAULT_PATCH_BUDGET_RATIO = 0.6d;
    public static final double DEFAULT_SCAFFOLD_BUDGET_RATIO = 0.7d;

    private GenerationBudgetProfile() {
    }

    public static double documentFullDraftOutputRatio() {
        return DEFAULT_FULL_BUDGET_RATIO;
    }

    public static double documentPatchOutputRatio() {
        return DEFAULT_PATCH_BUDGET_RATIO;
    }

    public static double implementationPlanOutputRatio() {
        return DEFAULT_FULL_BUDGET_RATIO;
    }

    public static double implementationPlanRepairOutputRatio() {
        return DEFAULT_FULL_BUDGET_RATIO;
    }

    public static double supervisorDecisionOutputRatio() {
        return DEFAULT_SUPERVISOR_DECISION_OUTPUT_RATIO;
    }

    public static double generationRecoveryOutputRatio() {
        return DEFAULT_GENERATION_RECOVERY_OUTPUT_RATIO;
    }

    public static double codeReviewOutputRatio() {
        return DEFAULT_CODE_REVIEW_OUTPUT_RATIO;
    }

    public static double validationStrategyOutputRatio() {
        return DEFAULT_VALIDATION_STRATEGY_OUTPUT_RATIO;
    }

    public static double focusedScriptOutputRatio() {
        return DEFAULT_FULL_BUDGET_RATIO;
    }

    public static double focusedMarkupOutputRatio() {
        return DEFAULT_FULL_BUDGET_RATIO;
    }

    public static double focusedStyleOutputRatio() {
        return DEFAULT_FULL_BUDGET_RATIO;
    }

    public static double inlineScriptUnitOutputRatio() {
        return DEFAULT_FULL_BUDGET_RATIO;
    }

    public static double preciseCodeScaffoldOutputRatio() {
        return DEFAULT_SCAFFOLD_BUDGET_RATIO;
    }

    public static double preciseCodeUnitOutputRatio() {
        return DEFAULT_FULL_BUDGET_RATIO;
    }

    public static double preciseHtmlOutputRatio() {
        return DEFAULT_FULL_BUDGET_RATIO;
    }

    public static int fileContextPreviewChars() {
        return DEFAULT_FILE_CONTEXT_PREVIEW_CHARS;
    }

    public static int inlineScriptPreviewChars() {
        return DEFAULT_INLINE_SCRIPT_PREVIEW_CHARS;
    }

    public static int inlineStylePreviewChars() {
        return DEFAULT_INLINE_STYLE_PREVIEW_CHARS;
    }

    public static double diagnosisOutputRatio() {
        return DEFAULT_DIAGNOSIS_OUTPUT_RATIO;
    }

    public static double diagnosisSimilarityOutputRatio() {
        return DEFAULT_DIAGNOSIS_SIMILARITY_OUTPUT_RATIO;
    }

    public static double fullBudgetRatio() {
        return DEFAULT_FULL_BUDGET_RATIO;
    }

    public static double patchOutputRatio() {
        return DEFAULT_PATCH_BUDGET_RATIO;
    }

    public static double wholeFileRewriteOutputRatio(DeliveryMode deliveryMode) {
        if (deliveryMode == DeliveryMode.PATCH) {
            return DEFAULT_PATCH_BUDGET_RATIO;
        }
        return DEFAULT_FULL_BUDGET_RATIO;
    }
}
