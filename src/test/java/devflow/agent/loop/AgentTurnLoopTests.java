package devflow.agent.loop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentTurnLoopTests {

    private final AgentTurnLoop loop = new AgentTurnLoop();

    @Test
    void stopsWhenTurnReachesCompleteState() {
        AgentTurnSnapshot result = loop.runUntilSettled(
                AgentTurnSnapshot.start(),
                snapshot -> switch (snapshot.state()) {
                    case IDLE -> AgentTurnStepResult.advance(snapshot.next(AgentTurnState.PREPARE_CONTEXT, null, "prepare"));
                    case PREPARE_CONTEXT -> AgentTurnStepResult.advance(snapshot.next(AgentTurnState.SELECT_NEXT_UNIT, "unit-1", "select"));
                    case SELECT_NEXT_UNIT -> AgentTurnStepResult.advance(snapshot.next(AgentTurnState.EXECUTE_STEP, "unit-1", "execute"));
                    case EXECUTE_STEP -> AgentTurnStepResult.advance(snapshot.next(AgentTurnState.OBSERVE_RESULT, "unit-1", "observe"));
                    case OBSERVE_RESULT -> AgentTurnStepResult.advance(snapshot.next(AgentTurnState.EVALUATE_RESULT, "unit-1", "evaluate"));
                    case EVALUATE_RESULT -> AgentTurnStepResult.stop(snapshot.next(AgentTurnState.COMPLETE, "unit-1", "done"));
                    default -> AgentTurnStepResult.stop(snapshot.next(AgentTurnState.FAILED, snapshot.activeUnit(), "unexpected"));
                }
        );

        assertEquals(AgentTurnState.COMPLETE, result.state());
        assertEquals("unit-1", result.activeUnit());
    }

    @Test
    void stopsWhenTurnMarksFailure() {
        AgentTurnSnapshot result = loop.runUntilSettled(
                AgentTurnSnapshot.start(),
                snapshot -> switch (snapshot.state()) {
                    case IDLE -> AgentTurnStepResult.advance(snapshot.next(AgentTurnState.PREPARE_CONTEXT, null, "prepare"));
                    case PREPARE_CONTEXT -> AgentTurnStepResult.stop(snapshot.next(AgentTurnState.FAILED, null, "failure"));
                    default -> AgentTurnStepResult.stop(snapshot.next(AgentTurnState.FAILED, snapshot.activeUnit(), "failure"));
                }
        );

        assertEquals(AgentTurnState.FAILED, result.state());
        assertEquals("failure", result.summary());
    }
}
