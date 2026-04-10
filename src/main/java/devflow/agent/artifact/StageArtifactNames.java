package devflow.agent.artifact;

import devflow.agent.orchestrator.StageType;

/**
 * 集中维护阶段主产物、review 产物与 review history 的文件名映射。
 *
 * <p>阶段文件名属于稳定协议的一部分，不应继续散落在 FileArtifactStore 里的 if/else 链中。
 */
public final class StageArtifactNames {

    private StageArtifactNames() {
    }

    public static String artifact(StageType stageType) {
        if (stageType == StageType.ANALYSIS) {
            return "analysis.md";
        }
        if (stageType == StageType.PRD) {
            return "prd.md";
        }
        if (stageType == StageType.DESIGN) {
            return "design.md";
        }
        if (stageType == StageType.IMPLEMENTATION) {
            return "implementation.md";
        }
        if (stageType == StageType.CODE_REVIEW) {
            return "code_review.md";
        }
        return "test_report.md";
    }

    public static String review(StageType stageType) {
        if (stageType == StageType.ANALYSIS) {
            return "analysis_review.md";
        }
        if (stageType == StageType.PRD) {
            return "prd_review.md";
        }
        if (stageType == StageType.DESIGN) {
            return "design_review.md";
        }
        if (stageType == StageType.IMPLEMENTATION) {
            return "implementation_review.md";
        }
        if (stageType == StageType.CODE_REVIEW) {
            return "code_review_review.md";
        }
        return "test_review.md";
    }

    public static String reviewHistory(StageType stageType) {
        if (stageType == StageType.ANALYSIS) {
            return "analysis_review_history.md";
        }
        if (stageType == StageType.PRD) {
            return "prd_review_history.md";
        }
        if (stageType == StageType.DESIGN) {
            return "design_review_history.md";
        }
        if (stageType == StageType.IMPLEMENTATION) {
            return "implementation_review_history.md";
        }
        if (stageType == StageType.CODE_REVIEW) {
            return "code_review_review_history.md";
        }
        return "test_review_history.md";
    }
}
