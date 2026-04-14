package devflow.agent.orchestrator;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;

/**
 * 统一完成工具结果读取与 review guarding。
 */
public class StageToolResultGate {

    private final StageToolResultLoader toolResultLoader;
    private final StageToolResultGuard toolResultGuard;

    public StageToolResultGate(StageToolResultLoader toolResultLoader, StageToolResultGuard toolResultGuard) {
        this.toolResultLoader = toolResultLoader;
        this.toolResultGuard = toolResultGuard;
    }

    public StageToolResultGateResult apply(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            ReviewResult reviewed
    ) {
        StageToolResultSummary toolSummary = toolResultLoader.load(projectPath, runRecord, stageType);
        ReviewResult reviewResult = toolResultGuard.guard(stageType, reviewed, toolSummary);
        return new StageToolResultGateResult(reviewResult, toolSummary);
    }
}
