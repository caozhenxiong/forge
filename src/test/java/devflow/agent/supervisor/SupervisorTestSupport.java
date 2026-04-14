package devflow.agent.supervisor;

import devflow.agent.orchestrator.StageFlowPolicy;

public final class SupervisorTestSupport {

    private SupervisorTestSupport() {
    }

    public static SupervisorFallbackPolicy newFallbackPolicy(StageFlowPolicy stageFlowPolicy) {
        return new SupervisorFallbackPolicy(
                new SupervisorStageFallbackSupport(stageFlowPolicy),
                new SupervisorGenerationRecoverySupport()
        );
    }
}
