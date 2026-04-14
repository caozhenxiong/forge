package devflow.agent.supervisor;

import devflow.agent.executor.generation.GenerationFailureReport;

import devflow.agent.context.ProjectedContext;
import devflow.agent.domain.GatePolicy;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageType;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.ReviewResult;

/**
 * 统一拼装 supervisor 相关 prompt。
 * 这样 SupervisorAgent 只负责调模型、解析结果和兜底，而不再继续内嵌大段模板字符串。
 */
public class SupervisorPromptAssembler {

    private final SupervisorDecisionPromptBuilder decisionPromptBuilder;
    private final SupervisorGenerationRecoveryPromptBuilder generationRecoveryPromptBuilder;

    public SupervisorPromptAssembler(SupervisorArtifactRenderer artifactRenderer) {
        this.decisionPromptBuilder = new SupervisorDecisionPromptBuilder(artifactRenderer);
        this.generationRecoveryPromptBuilder = new SupervisorGenerationRecoveryPromptBuilder(artifactRenderer);
    }

    public String decisionSystemPrompt() {
        return decisionPromptBuilder.systemPrompt();
    }

    public String decisionUserPrompt(
            RunRecord runRecord,
            DocumentLanguage language,
            StageType currentStage,
            StageType nextStage,
            GatePolicy gatePolicy,
            StageExecution currentExecution,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            ProjectedContext projectedContext,
            SupervisorDecision fallback
    ) {
        return decisionPromptBuilder.userPrompt(
                runRecord,
                language,
                currentStage,
                nextStage,
                gatePolicy,
                currentExecution,
                reviewResult,
                repeatedIssue,
                projectedContext,
                fallback
        );
    }

    public String generationRecoverySystemPrompt() {
        return generationRecoveryPromptBuilder.systemPrompt();
    }

    public String generationRecoveryUserPrompt(
            RunRecord runRecord,
            GenerationFailureReport failureReport,
            int subtaskAttempt,
            String subtaskTitle,
            String subtaskGoal,
            String feedback,
            ProjectedContext projectedContext,
            DocumentLanguage language,
            GenerationRecoveryDecision fallback
    ) {
        return generationRecoveryPromptBuilder.userPrompt(
                runRecord,
                failureReport,
                subtaskAttempt,
                subtaskTitle,
                subtaskGoal,
                feedback,
                projectedContext,
                language,
                fallback
        );
    }
}
