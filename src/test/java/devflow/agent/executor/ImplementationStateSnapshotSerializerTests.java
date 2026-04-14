package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.toolloop.*;

import devflow.agent.executor.llm.LlmChatMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.i18n.DocumentLanguage;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devflow.agent.executor.implementation.toolloop.CoderReadFileState;
import devflow.agent.executor.implementation.toolloop.ImplementationDiagnosticLedger;
import devflow.agent.executor.implementation.toolloop.ImplementationDiagnosticRecord;
import devflow.agent.executor.implementation.toolloop.ImplementationDiagnosticSource;
import devflow.agent.executor.implementation.toolloop.ImplementationToolSessionState;
import devflow.agent.executor.implementation.toolloop.ToolLoopDiagnosticStatus;
import devflow.agent.executor.implementation.toolloop.ToolLoopMutationOperation;
import devflow.agent.executor.implementation.toolloop.ToolLoopReadFileStateLedger;
import devflow.agent.executor.implementation.toolloop.ToolLoopResultReplacementState;
import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
import devflow.agent.executor.subtask.SubtaskExecutionState;
class ImplementationStateSnapshotSerializerTests {

    @Test
    void preservesDiagnosticsAcrossStateJsonRoundTrip() throws Exception {
        ToolLoopReadFileStateLedger readLedger = new ToolLoopReadFileStateLedger();
        readLedger.put(
                Path.of("/tmp/project/src/app.js"),
                new CoderReadFileState("export const broken = ;", 42L, null, null, false)
        );
        ImplementationDiagnosticLedger diagnosticLedger = new ImplementationDiagnosticLedger();
        diagnosticLedger.restore(new ImplementationDiagnosticRecord(
                "diag-1",
                Path.of(""),
                ToolLoopDiagnosticStatus.FAILED,
                ImplementationDiagnosticSource.TOOL_FAILURE,
                ToolFailureCode.COMMAND_FAILED,
                "shell command rejected",
                123L
        ));
        ImplementationToolSessionState sessionState = new ImplementationToolSessionState(
                List.of(LlmChatMessage.user("继续修复")),
                readLedger,
                new ToolLoopResultReplacementState(),
                List.of(new FileMutationRecord(
                        ToolLoopMutationOperation.UPDATE,
                        Path.of("src/app.js"),
                        true,
                        "before-hash",
                        true,
                        "after-hash",
                        List.of(),
                        123L
                )),
                diagnosticLedger
        );
        FileChange change = new FileChange("src/app.js", ChangeAction.WRITE, "修复语法错误");
        Subtask subtask = new Subtask(
                "修复 app",
                "修复语法错误",
                List.of(),
                List.of(),
                List.of(),
                List.of("语法正确"),
                false,
                DeliveryMode.PATCH,
                List.of(change)
        );
        SubtaskExecutionState executionState = SubtaskExecutionState.restore(
                DeliveryMode.PATCH.name(),
                false,
                List.of(),
                List.of(change),
                sessionState
        );
        ImplementationRuntimeSnapshot snapshot = new ImplementationRuntimeSnapshot(
                new ImplementationPlan("snapshot", List.of(subtask)),
                List.of(),
                List.of(new SubtaskExecutionReport(subtask, false, List.of(), executionState)),
                List.of(),
                List.of(),
                "",
                DocumentLanguage.ZH,
                DeliveryPolicyEnvelope.defaultPolicy(),
                null,
                new ImplementationStageStatus(1, 1, 0, false, false, List.of("修复 app")),
                "修复 app"
        );

        ObjectMapper objectMapper = new ObjectMapper();
        ImplementationStateSnapshotSerializer serializer = new ImplementationStateSnapshotSerializer(objectMapper);
        String json = serializer.renderStateJson(snapshot);
        ImplementationStateSnapshot parsed = objectMapper.readValue(json, ImplementationStateSnapshot.class);

        assertEquals(1, parsed.reports().size());
        assertNotNull(parsed.reports().getFirst().toolSessionState());
        assertEquals(1, parsed.reports().getFirst().toolSessionState().diagnostics().size());
        assertEquals("FAILED", parsed.reports().getFirst().toolSessionState().diagnostics().getFirst().status());
        assertEquals("", parsed.reports().getFirst().toolSessionState().diagnostics().getFirst().relativePath());
        assertEquals("COMMAND_FAILED", parsed.reports().getFirst().toolSessionState().diagnostics().getFirst().failureCode());

        ImplementationSnapshotRestorer restorer = new ImplementationSnapshotRestorer();
        List<Subtask> restoredSubtasks = restorer.restoreSubtasks(parsed.subtasks());
        List<SubtaskExecutionReport> restoredReports = restorer.restoreReports(parsed.reports(), restoredSubtasks);
        assertEquals(1, restoredReports.size());
        assertEquals(1, restoredReports.getFirst().executionState().toolSessionState().diagnostics().size());
        assertEquals(
                ToolLoopDiagnosticStatus.FAILED,
                restoredReports.getFirst().executionState().toolSessionState().diagnostics().getFirst().status()
        );
        assertEquals(Path.of(""), restoredReports.getFirst().executionState().toolSessionState().diagnostics().getFirst().relativePath());
        assertEquals(
                ToolFailureCode.COMMAND_FAILED,
                restoredReports.getFirst().executionState().toolSessionState().diagnostics().getFirst().failureCode()
        );
        assertTrue(restoredReports.getFirst().executionState().toolSessionState().transcript().isEmpty());
    }
}
