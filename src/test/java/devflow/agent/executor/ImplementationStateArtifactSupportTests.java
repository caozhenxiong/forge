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

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewReasonCode;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImplementationStateArtifactSupportTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readStageStatusDoesNotDeriveStageReadyFromOtherFields() throws Exception {
        ImplementationStateArtifactSupport support = new ImplementationStateArtifactSupport();
        LinkedHashMap<String, Object> root = baseState();
        root.put("planCompleted", true);
        root.put("stageReady", false);
        root.put("incompleteSubtasks", List.of());
        LinkedHashMap<String, Object> contractGate = new LinkedHashMap<>();
        contractGate.put("scope", "STAGE_COMPLETION");
        contractGate.put("passed", true);
        contractGate.put("failureReason", "");
        contractGate.put("details", "");
        contractGate.put("implementationPatchTarget", ImplementationPatchTarget.NONE.name());
        contractGate.put("runtimeContract", null);
        root.put("contractGate", contractGate);
        root.put("continuationSummary", "继续修当前子任务");
        root.put("continuationChangeRequest", "继续修补当前实现。");
        root.put("continuationEvidence", "deterministic continuation payload");
        root.put("continuationActionItems", "1. 继续修复。 2. 再验证。");

        ImplementationStageStatusPayload payload = support.readStageStatus(objectMapper.writeValueAsString(root));

        assertFalse(payload.stageReady());
        assertEquals(ImplementationContinuationMode.CONTINUE_SUBTASKS, payload.continuationMode());
        assertEquals("继续修当前子任务", payload.continuationSummary());
    }

    @Test
    void readStageStatusRejectsMissingLiveControlFields() throws Exception {
        ImplementationStateArtifactSupport support = new ImplementationStateArtifactSupport();
        LinkedHashMap<String, Object> root = baseState();
        root.remove("continuationMode");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> support.readStageStatus(objectMapper.writeValueAsString(root))
        );

        assertEquals(
                "Invalid implementation_state auxiliary artifact: missing field 'continuationMode'.",
                exception.getMessage()
        );
    }

    @Test
    void reviewSummaryRendersToolFailureDiagnosticsWithFailureCodeAndToolLoopPlaceholder() throws Exception {
        ImplementationStateArtifactSupport support = new ImplementationStateArtifactSupport();
        LinkedHashMap<String, Object> root = baseState();
        LinkedHashMap<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("diagnosticId", "diag-1");
        diagnostic.put("relativePath", "");
        diagnostic.put("status", "FAILED");
        diagnostic.put("source", "TOOL_FAILURE");
        diagnostic.put("failureCode", "COMMAND_FAILED");
        diagnostic.put("evidence", "shell command rejected");
        diagnostic.put("timestamp", 123L);

        LinkedHashMap<String, Object> toolSessionState = new LinkedHashMap<>();
        toolSessionState.put("readFileStateMaxEntries", 100L);
        toolSessionState.put("readFileStateMaxSizeBytes", 1024L);
        toolSessionState.put("readFileStates", List.of());
        toolSessionState.put("seenToolResultIds", List.of());
        toolSessionState.put("toolResultReplacements", List.of());
        toolSessionState.put("fileMutations", List.of());
        toolSessionState.put("diagnostics", List.of(diagnostic));

        LinkedHashMap<String, Object> report = new LinkedHashMap<>();
        report.put("title", "修复 app");
        report.put("completed", false);
        report.put("attempts", List.of());
        report.put("deliveryMode", "PATCH");
        report.put("preferPreciseEditing", false);
        report.put("fileEditAttemptStates", List.of());
        report.put("effectiveChanges", List.of());
        report.put("toolSessionState", toolSessionState);
        root.put("reports", List.of(report));

        String summary = support.renderReviewSummary(objectMapper.writeValueAsString(root));

        assertTrue(summary.contains("## Diagnostics"));
        assertTrue(summary.contains("(tool-loop) | FAILED | TOOL_FAILURE | COMMAND_FAILED | shell command rejected"));
    }

    private LinkedHashMap<String, Object> baseState() {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("summary", "");
        root.put("subtasks", List.of());
        root.put("reports", List.of());
        root.put("events", List.of());
        root.put("currentSubtaskTitle", "");
        root.put("planCompleted", false);
        root.put("stageReady", false);
        root.put("contractGate", null);
        root.put("continuationMode", ImplementationContinuationMode.CONTINUE_SUBTASKS.name());
        root.put("continuationSummary", "继续修当前子任务");
        root.put("continuationChangeRequest", "继续修补当前实现。");
        root.put("continuationEvidence", "deterministic continuation payload");
        root.put("continuationActionItems", "1. 继续修复。 2. 再验证。");
        root.put("continuationOverrideChanges", List.of());
        root.put("continuationPatchTarget", ImplementationPatchTarget.NONE.name());
        root.put("continuationReasonCode", ReviewReasonCode.NONE.name());
        root.put("incompleteSubtasks", List.of("补齐交互"));
        return root;
    }
}
