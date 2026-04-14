package devflow.agent.orchestrator;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;

/**
 * 统一封装重复问题判断，避免 coordinator 直接感知 diagnosis 细节。
 */
public class RepeatIssueDetector {

    private final DiagnosisAgent diagnosisAgent;

    public RepeatIssueDetector(DiagnosisAgent diagnosisAgent) {
        this.diagnosisAgent = diagnosisAgent;
    }

    public boolean shouldDiagnose(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            ReviewResult reviewResult
    ) {
        if (reviewResult == null || reviewResult.decision() == ReviewDecision.APPROVED || stageType == StageType.ANALYSIS) {
            return false;
        }
        return diagnosisAgent.shouldDiagnose(
                projectPath,
                runRecord,
                stageType,
                reviewResult.fixMode(),
                reviewResult.summary(),
                reviewResult.changeRequest()
        );
    }
}
