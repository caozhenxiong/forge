package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.review.ImplementationPatchTarget;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devflow.agent.executor.implementation.toolloop.ImplementationDiagnosticSource;
import devflow.agent.executor.implementation.toolloop.ImplementationToolSessionState;
import devflow.agent.executor.implementation.toolloop.ToolLoopDiagnosticStatus;
import devflow.agent.executor.implementation.toolloop.ToolLoopMutationOperation;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
class ImplementationSnapshotRestorerTests {

    private final ImplementationSnapshotRestorer restorer = new ImplementationSnapshotRestorer();

    @Test
    void restoresExplicitContractGateWithoutDerivingOwnershipFromPlanShape() {
        ArchitectIntegrationCheckResult contractGate = restorer.restoreContractGate(
                new ImplementationStateSnapshot.ContractGateState(
                        ArchitectIntegrationCheckScope.STAGE_COMPLETION.name(),
                        false,
                        ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID.name(),
                        "index.app.js 存在，但 index.html 未接线",
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING.name(),
                        new ImplementationStateSnapshot.RuntimeContractState(
                                "index.html",
                                RuntimeOwnershipMode.EXTERNAL_COMPANION.name(),
                                List.of("index.app.js")
                        )
                )
        );

        assertNotNull(contractGate);
        assertFalse(contractGate.passed());
        assertEquals(ArchitectIntegrationCheckScope.STAGE_COMPLETION, contractGate.scope());
        assertEquals(ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID, contractGate.failureReason());
        assertEquals(ImplementationPatchTarget.PATCH_RUNTIME_WIRING, contractGate.implementationPatchTarget());
        assertNotNull(contractGate.runtimeContract());
        assertEquals(Path.of("index.html"), contractGate.runtimeContract().htmlEntryPath());
        assertEquals(RuntimeOwnershipMode.EXTERNAL_COMPANION, contractGate.runtimeContract().runtimeOwnership());
        assertEquals(List.of(Path.of("index.app.js")), contractGate.runtimeContract().runtimePaths());
    }

    @Test
    void restoresDeterministicToolSessionStateButClearsTranscriptAcrossAttempts() {
        Subtask subtask = new Subtask(
                "修复 app",
                "修复语法错误",
                List.of(),
                List.of(),
                List.of(),
                List.of("语法正确"),
                false,
                DeliveryMode.PATCH,
                List.of(new FileChange("src/app.js", ChangeAction.WRITE, "修复语法错误"))
        );
        List<SubtaskExecutionReport> reports = restorer.restoreReports(
                List.of(new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                        "修复 app",
                        false,
                        List.of(),
                        "PATCH",
                        true,
                        List.of(new ImplementationStateSnapshot.FileEditAttemptStateSnapshot(
                                "src/app.js",
                                FileEditProtocolNames.TARGETED_REWRITE,
                                FileEditStrategyNames.PRECISE_CODE,
                                "export const broken = ;",
                                "hash-1",
                                List.of("code-unit-1"),
                                "code-unit-2"
                        )),
                        List.of(new ImplementationStateSnapshot.FileChangeState("src/app.js", "WRITE", "修复语法错误")),
                        new ImplementationStateSnapshot.ToolSessionStateSnapshot(
                                100L,
                                1024L,
                                List.of(new ImplementationStateSnapshot.ReadFileStateEntry(
                                        "/tmp/project/src/app.js",
                                        "export const broken = ;",
                                        42L,
                                        null,
                                        null,
                                        false
                                )),
                                List.of("tool-1"),
                                List.of(new ImplementationStateSnapshot.ToolResultReplacementEntry("tool-1", "stored://tool-1")),
                                List.of(new ImplementationStateSnapshot.FileMutationState(
                                        ToolLoopMutationOperation.UPDATE.name(),
                                        "src/app.js",
                                        true,
                                        "before",
                                        true,
                                        "after",
                                        List.of(),
                                        123L
                                )),
                                List.of(new ImplementationStateSnapshot.DiagnosticState(
                                        "diag-1",
                                        "src/app.js",
                                        ToolLoopDiagnosticStatus.SYNTAX_INVALID.name(),
                                        ImplementationDiagnosticSource.TREE_SITTER_PARSE.name(),
                                        "unexpected token",
                                        123L
                                ))
                        )
                )),
                List.of(subtask)
        );

        assertEquals(1, reports.size());
        assertNotNull(reports.getFirst().executionState());
        ImplementationToolSessionState sessionState = reports.getFirst().executionState().toolSessionState();
        assertTrue(sessionState.transcript().isEmpty());
        assertNotNull(sessionState.readFileStateLedger().get(Path.of("/tmp/project/src/app.js")));
        assertTrue(sessionState.resultReplacementState().seen("tool-1"));
        assertEquals("stored://tool-1", sessionState.resultReplacementState().replacement("tool-1"));
        assertEquals(1, sessionState.mutationRecords().size());
        assertEquals(1, sessionState.diagnostics().size());
        assertEquals(ToolLoopDiagnosticStatus.SYNTAX_INVALID, sessionState.diagnostics().getFirst().status());
    }
}
