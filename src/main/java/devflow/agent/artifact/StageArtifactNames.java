package devflow.agent.artifact;

import devflow.agent.domain.StageType;

/**
 * 集中维护阶段主产物、review 产物与 review history 的文件名映射。
 *
 * <p>阶段文件名属于稳定协议的一部分，不应继续散落在 FileArtifactStore 里的 if/else 链中。
 */
public final class StageArtifactNames {

    private StageArtifactNames() {
    }

    public static String artifact(StageType stageType) {
        return switch (stageType) {
            case ANALYSIS -> "analysis.md";
            case PRD -> "prd.md";
            case DESIGN -> "design.md";
            case IMPLEMENTATION -> "implementation.md";
            case CODE_REVIEW -> "code_review.md";
            case TEST -> "test_report.md";
        };
    }

    public static String review(StageType stageType) {
        return switch (stageType) {
            case ANALYSIS -> "analysis_review.md";
            case PRD -> "prd_review.md";
            case DESIGN -> "design_review.md";
            case IMPLEMENTATION -> "implementation_review.md";
            case CODE_REVIEW -> "code_review_feedback.md";
            case TEST -> "test_review.md";
        };
    }

    public static String reviewHistory(StageType stageType) {
        return switch (stageType) {
            case ANALYSIS -> "analysis_review_history.md";
            case PRD -> "prd_review_history.md";
            case DESIGN -> "design_review_history.md";
            case IMPLEMENTATION -> "implementation_review_history.md";
            case CODE_REVIEW -> "code_review_feedback_history.md";
            case TEST -> "test_review_history.md";
        };
    }
}
