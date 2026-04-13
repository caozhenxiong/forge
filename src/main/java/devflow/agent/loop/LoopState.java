package devflow.agent.loop;

import devflow.agent.domain.RunRecord;

public record LoopState(
        RunRecord runRecord,
        int iteration,
        TransitionReason lastReason
) {

    public static LoopState start(RunRecord runRecord) {
        return new LoopState(runRecord, 0, null);
    }

    public LoopState next(RunRecord nextRecord, TransitionReason nextReason) {
        return new LoopState(nextRecord, iteration + 1, nextReason);
    }
}
