package devflow.agent.loop;

import devflow.agent.orchestrator.RunRecord;

public record LoopStepResult(
        RunRecord runRecord,
        TransitionDecision transitionDecision,
        boolean continueLoop
) {
}
