package devflow.agent.loop;

import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class AgentLoop {

    public RunRecord runUntilStable(RunRecord initial, Function<LoopState, LoopStepResult> stepFunction) {
        LoopState state = LoopState.start(initial);
        while (true) {
            RunRecord current = state.runRecord();
            if (current.status() == RunStatus.COMPLETED
                    || current.status() == RunStatus.FAILED
                    || current.status() == RunStatus.CANCELLED) {
                return current;
            }
            LoopStepResult result = stepFunction.apply(state);
            if (result == null) {
                return current;
            }
            if (!result.continueLoop()) {
                return result.runRecord();
            }
            TransitionReason nextReason = result.transitionDecision() == null ? state.lastReason() : result.transitionDecision().reason();
            state = state.next(result.runRecord(), nextReason);
        }
    }
}
