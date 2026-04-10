package devflow.agent.supervisor;

import devflow.agent.context.ProjectedContext;
import devflow.agent.executor.GenerationFailureReport;
import devflow.agent.orchestrator.GatePolicy;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageFlowPolicy;
import devflow.agent.orchestrator.StageType;
import devflow.agent.review.ReviewResult;
import org.springframework.stereotype.Component;

/**
 * 统一维护 Supervisor 的默认保守决策。
 *
 * <p>当 LLM 不可用、响应不合法，或者需要一个稳定的保底路线时，
 * 由这层给出确定性决策。这样 SupervisorAgent 可以逐步退化成
 * “升级仲裁 + 结果净化”，而不是继续兼任流程规则中心。
 */
@Component
public class SupervisorFallbackPolicy {

    private final SupervisorStageFallbackSupport stageFallbackSupport;
    private final SupervisorGenerationRecoverySupport generationRecoverySupport;

    public SupervisorFallbackPolicy(StageFlowPolicy stageFlowPolicy) {
        this.stageFallbackSupport = new SupervisorStageFallbackSupport(stageFlowPolicy);
        this.generationRecoverySupport = new SupervisorGenerationRecoverySupport();
    }

    public SupervisorDecision decideStageFallback(
            RunRecord runRecord,
            StageType currentStage,
            GatePolicy gatePolicy,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            ProjectedContext projectedContext
    ) {
        return stageFallbackSupport.decide(
                runRecord,
                currentStage,
                gatePolicy,
                reviewResult,
                repeatedIssue,
                projectedContext
        );
    }

    public GenerationRecoveryDecision decideGenerationFallback(
            GenerationFailureReport failureReport,
            int subtaskAttempt,
            DeliveryPolicy currentPolicy
    ) {
        return generationRecoverySupport.decide(failureReport, subtaskAttempt, currentPolicy);
    }

    public DeliveryPolicy initialImplementationPolicy(ProjectedContext projectedContext) {
        return stageFallbackSupport.initialImplementationPolicy(projectedContext);
    }
}
