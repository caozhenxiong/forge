package devflow.agent.executor.implementation.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.loop.AgentTurnLoop;

public final class ImplementationPlanningWiring {

    private ImplementationPlanningWiring() {
    }

    public static ImplementationPlanner createPlanner(
            LlmProvider llmProvider,
            ObjectMapper objectMapper,
            ImplementationPlanCoverageAnalyzer coverageAnalyzer,
            AgentTurnLoop planningTurnLoop,
            int payloadRepairAttempts,
            int maxPlanningUnitAttempts,
            int maxFilesPerSubtask,
            int maxDeliveryPolicyFiles
    ) {
        return new ImplementationPlanner(
                objectMapper,
                new ImplementationPlanGate(coverageAnalyzer),
                new ImplementationOutlineGate(coverageAnalyzer),
                new ImplementationSubtaskDetailGate(),
                new ImplementationPlanAssembler(),
                new ImplementationPlanningFeedbackRouter(),
                new ImplementationPlanGateInputBuilder(),
                maxPlanningUnitAttempts,
                new ImplementationPlanningPromptAssembler(maxFilesPerSubtask, maxDeliveryPolicyFiles),
                new ImplementationPlanningTurnRunner(llmProvider, planningTurnLoop),
                new ImplementationPlanningPayloadParser(llmProvider, objectMapper, payloadRepairAttempts)
        );
    }
}
