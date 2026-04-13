package devflow.agent.loop;

import devflow.agent.domain.StageType;
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
