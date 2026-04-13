package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.LlmChatMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devflow.agent.executor.implementation.toolloop.ImplementationToolResultBudgetManager;
import devflow.agent.executor.implementation.toolloop.ImplementationToolResultMessage;
import devflow.agent.executor.implementation.toolloop.ToolLoopResultReplacementState;
class ImplementationToolResultBudgetManagerTests {

    @TempDir
    Path tempDir;

    @Test
    void persistsOversizedToolResultOnceAndReusesReplacement() throws Exception {
        ImplementationToolResultBudgetManager manager = new ImplementationToolResultBudgetManager();
        ToolLoopResultReplacementState replacementState = new ToolLoopResultReplacementState();
        ImplementationToolResultMessage largeResult = new ImplementationToolResultMessage(
                "tool-1",
                "Grep",
                "x".repeat(60_000),
                60_000
        );

        List<LlmChatMessage> firstTranscript = manager.appendToolResults(
                List.of(LlmChatMessage.assistantToolCalls("", List.of())),
                tempDir,
                List.of(largeResult),
                replacementState
        );
        List<LlmChatMessage> secondTranscript = manager.appendToolResults(
                firstTranscript,
                tempDir,
                List.of(new ImplementationToolResultMessage("tool-1", "Grep", "x".repeat(60_000), 60_000)),
                replacementState
        );

        assertEquals(1, replacementState.snapshotReplacements().size());
        assertTrue(secondTranscript.get(1).content().contains("<persisted-output>"));
        assertTrue(Files.exists(tempDir.resolve("tool-1.txt")));
    }
}
