package devflow.agent.loop;

import devflow.agent.domain.RunRecord;

public record LoopStepResult(
        RunRecord runRecord,
        TransitionDecision transitionDecision,
        boolean continueLoop
) {
}
