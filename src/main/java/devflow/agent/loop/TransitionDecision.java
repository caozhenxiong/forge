package devflow.agent.loop;

import devflow.agent.orchestrator.StageType;
import devflow.agent.supervisor.SupervisorDecision;

public record TransitionDecision(
        TransitionReason reason,
        StageType fromStage,
        StageType targetStage,
        boolean repeatedIssue,
        String summary,
        SupervisorDecision supervisorDecision
) {
}
