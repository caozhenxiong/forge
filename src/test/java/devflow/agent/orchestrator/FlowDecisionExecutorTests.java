package devflow.agent.orchestrator;

import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
import devflow.agent.domain.WorkflowAction;
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.DeliveryPolicy;
import devflow.agent.supervisor.DeliveryPolicyMode;
import devflow.agent.supervisor.SupervisorDecision;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowDecisionExecutorTests {

    @TempDir
    Path tempDir;

    @Test
    void retryStageBuildsRevisionContextWithoutForceRepair() {
        AtomicReference<RevisionContext> capturedContext = new AtomicReference<>();
        FlowDecisionExecutor executor = newExecutor(capturedContext);
        ReviewResult reviewResult = new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                "summary",
                "change request",
                "evidence",
                "review action items",
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                List.of(new FileChange("index.html", ChangeAction.WRITE, "patch current entry"))
        );
        SupervisorDecision supervisorDecision = new SupervisorDecision(
                WorkflowAction.RETRY_STAGE,
                StageType.IMPLEMENTATION,
                FixMode.PATCH,
                "supervisor reason",
                List.of("focus current stage"),
                List.of(),
                List.of("required evidence"),
                new DeliveryPolicy(DeliveryPolicyMode.PATCH, 2, 4, true, false, true),
                false
        );

        executor.apply(
                tempDir,
                runRecord(StageType.IMPLEMENTATION),
                StageType.IMPLEMENTATION,
                reviewResult,
                true,
                supervisorDecision,
                new FlowDecision(WorkflowAction.RETRY_STAGE, StageType.IMPLEMENTATION, null)
        );

        RevisionContext revisionContext = capturedContext.get();
        assertEquals(ReviewDecision.REVISION_REQUIRED, revisionContext.decision());
        assertEquals(FixMode.PATCH, revisionContext.fixMode());
        assertEquals(ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION, revisionContext.implementationPatchTarget());
        assertEquals("summary", revisionContext.summary());
        assertTrue(revisionContext.actionItems().contains("review action items"));
        assertTrue(revisionContext.actionItems().contains("supervisor reason"));
        assertEquals(StageType.IMPLEMENTATION, revisionContext.rerouteStage());
        assertFalse(revisionContext.forceRepair());
        assertTrue(revisionContext.repeatedIssue());
    }

    @Test
    void routeToRepairBuildsRevisionContextWithForceRepair() {
        AtomicReference<RevisionContext> capturedContext = new AtomicReference<>();
        FlowDecisionExecutor executor = newExecutor(capturedContext);
        ReviewResult reviewResult = new ReviewResult(
                ReviewDecision.REJECTED,
                FixMode.PATCH,
                "test failed",
                "repair implementation",
                "missing runtime evidence",
                "",
                ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                List.of()
        );
        SupervisorDecision supervisorDecision = new SupervisorDecision(
                WorkflowAction.ROUTE_TO_REPAIR,
                StageType.IMPLEMENTATION,
                FixMode.REWORK,
                "route repair",
                List.of(),
                List.of("do not reset stage"),
                List.of(),
                new DeliveryPolicy(DeliveryPolicyMode.REWORK, 3, 6, true, false, true),
                false
        );

        executor.apply(
                tempDir,
                runRecord(StageType.TEST),
                StageType.TEST,
                reviewResult,
                false,
                supervisorDecision,
                new FlowDecision(WorkflowAction.ROUTE_TO_REPAIR, StageType.IMPLEMENTATION, null)
        );

        RevisionContext revisionContext = capturedContext.get();
        assertEquals(FixMode.REWORK, revisionContext.fixMode());
        assertEquals(ImplementationPatchTarget.PATCH_RUNTIME_WIRING, revisionContext.implementationPatchTarget());
        assertEquals(StageType.IMPLEMENTATION, revisionContext.rerouteStage());
        assertTrue(revisionContext.forceRepair());
        assertFalse(revisionContext.repeatedIssue());
    }

    private FlowDecisionExecutor newExecutor(AtomicReference<RevisionContext> capturedContext) {
        StageTransitionSupport stageTransitionSupport = new StageTransitionSupport(null, null, null, null, null) {
            @Override
            public RunRecord rerouteForRevision(
                    Path projectPath,
                    RunRecord runRecord,
                    StageType stageType,
                    RevisionContext revisionContext,
                    StageEntryAction stageEntryAction
            ) {
                capturedContext.set(revisionContext);
                return runRecord;
            }

            @Override
            public String mergeActionItems(String actionItems, SupervisorDecision supervisorDecision) {
                String reviewItems = actionItems == null ? "" : actionItems;
                String supervisorReason = supervisorDecision == null ? "" : supervisorDecision.reason();
                return reviewItems + "\n" + supervisorReason;
            }
        };
        return new FlowDecisionExecutor(stageTransitionSupport, new StageEntryExecutor(null, null, null, null, null));
    }

    private RunRecord runRecord(StageType currentStage) {
        EnumMap<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        stageStates.put(currentStage, new StageExecution(currentStage, StageStatus.RUNNING, 1, null, null, null, null));
        return new RunRecord(
                java.util.UUID.randomUUID(),
                tempDir,
                "goal",
                "",
                RunConfig.defaultConfig(),
                currentStage,
                RunStatus.IN_PROGRESS,
                stageStates,
                Instant.now(),
                Instant.now()
        );
    }
}
