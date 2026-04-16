package devflow.agent.interfaceadapter.cli;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.domain.HumanReviewResolutionContext;
import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
import devflow.agent.orchestrator.TerminalHumanApprovalRejectedException;
import devflow.agent.orchestrator.WorkflowEngine;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DevflowCliRunnerTests {

    @TempDir
    Path tempDir;

    @Test
    void rendersTerminalApprovalDiagnosticFromCliRunner() throws Exception {
        WorkflowEngine workflowEngine = new WorkflowEngine() {
            @Override
            public void initialize(Path projectPath) {
            }

            @Override
            public RunRecord createRun(Path projectPath, String goal, String constraints) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RunRecord find(Path projectPath, UUID runId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RunRecord startRun(Path projectPath, UUID runId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RunRecord resumeRun(Path projectPath, UUID runId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RunRecord approveStage(Path projectPath, UUID runId, StageType stageType, String reviewer) {
                RunRecord runRecord = blockedTerminalRun(projectPath, runId, stageType);
                throw new TerminalHumanApprovalRejectedException(
                        runRecord,
                        stageType,
                        runRecord.humanReviewResolutionContext(),
                        reviewer
                );
            }

            @Override
            public RunRecord rejectStage(Path projectPath, UUID runId, StageType stageType, String reviewer, String reason) {
                throw new UnsupportedOperationException();
            }
        };

        DevflowCliRunner runner = new DevflowCliRunner(
                workflowEngine,
                new FileArtifactStore(new devflow.agent.orchestrator.FileRunRepository()),
                new EventLogStore(new devflow.agent.orchestrator.FileRunRepository())
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output));
        try {
            runner.run("run", "approve", UUID.randomUUID().toString(), "TEST", "--project", tempDir.toString());
        } finally {
            System.setOut(originalOut);
        }

        String rendered = output.toString();
        assertTrue(rendered.contains("terminalApprovalRejected: true"));
        assertTrue(rendered.contains("stage: TEST"));
        assertTrue(rendered.contains("message: terminal diagnostic"));
    }

    private RunRecord blockedTerminalRun(Path projectPath, UUID runId, StageType stageType) {
        Map<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType type : StageType.values()) {
            stageStates.put(type, new StageExecution(type, StageStatus.PENDING, 0, null, null, null, null));
        }
        stageStates.put(stageType, new StageExecution(stageType, StageStatus.AWAITING_HUMAN_REVIEW, 1, null, null, "blocked", "repair"));
        HumanReviewResolutionContext context = HumanReviewResolutionContext.confirmRepairRoute(
                StageType.IMPLEMENTATION,
                devflow.agent.review.FixMode.PATCH,
                devflow.agent.review.ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                java.util.List.of(),
                "summary",
                "change",
                "evidence",
                "actions",
                devflow.agent.protocol.ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK
        ).asTerminal("terminal diagnostic");
        return new RunRecord(
                runId,
                projectPath,
                "goal",
                "",
                RunConfig.defaultConfig(),
                stageType,
                RunStatus.BLOCKED,
                stageStates,
                context,
                Instant.now(),
                Instant.now()
        );
    }
}
