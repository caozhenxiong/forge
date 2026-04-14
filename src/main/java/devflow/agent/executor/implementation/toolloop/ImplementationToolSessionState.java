package devflow.agent.executor.implementation.toolloop;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.LlmChatMessage;

import java.util.ArrayList;
import java.util.List;

import devflow.agent.executor.implementation.toolloop.FileMutationRecord;
/**
 * 子任务级 implementation tool session 唯一状态源。
 *
 * <p>这层收口：
 * 1. transcript；
 * 2. read file state；
 * 3. tool-result replacement；
 * 4. file mutations；
 * 5. diagnostics。
 *
 * <p>retry / continuation / restore 只允许沿用这一份状态，不再保留旧 runtime state 平行职责。
 */
public final class ImplementationToolSessionState {

    private final ArrayList<LlmChatMessage> transcript;
    private final ToolLoopReadFileStateLedger readFileStateLedger;
    private final ToolLoopResultReplacementState resultReplacementState;
    private final ArrayList<FileMutationRecord> mutationRecords;
    private final ImplementationDiagnosticLedger diagnosticLedger;

    public ImplementationToolSessionState() {
        this(
                List.of(),
                new ToolLoopReadFileStateLedger(),
                new ToolLoopResultReplacementState(),
                List.of(),
                new ImplementationDiagnosticLedger()
        );
    }

    public ImplementationToolSessionState(
            List<LlmChatMessage> transcript,
            ToolLoopReadFileStateLedger readFileStateLedger,
            ToolLoopResultReplacementState resultReplacementState,
            List<FileMutationRecord> mutationRecords,
            ImplementationDiagnosticLedger diagnosticLedger
    ) {
        this.transcript = new ArrayList<>(transcript == null ? List.of() : transcript);
        this.readFileStateLedger = readFileStateLedger == null ? new ToolLoopReadFileStateLedger() : readFileStateLedger;
        this.resultReplacementState = resultReplacementState == null ? new ToolLoopResultReplacementState() : resultReplacementState;
        this.mutationRecords = new ArrayList<>(mutationRecords == null ? List.of() : mutationRecords);
        this.diagnosticLedger = diagnosticLedger == null ? new ImplementationDiagnosticLedger() : diagnosticLedger;
    }

    public ImplementationToolSessionState copy() {
        return new ImplementationToolSessionState(
                transcript(),
                new ToolLoopReadFileStateLedger(
                        readFileStateLedger.maxEntries(),
                        readFileStateLedger.maxSizeBytes(),
                        readFileStateLedger.snapshotEntries()
                ),
                new ToolLoopResultReplacementState(
                        resultReplacementState.snapshotSeenIds(),
                        resultReplacementState.snapshotReplacements()
                ),
                mutationRecords(),
                diagnosticLedger.copy()
        );
    }

    public List<LlmChatMessage> transcript() {
        return List.copyOf(transcript);
    }

    public void resetTranscript(List<LlmChatMessage> messages) {
        transcript.clear();
        transcript.addAll(messages == null ? List.of() : messages);
    }

    public void appendTranscript(LlmChatMessage message) {
        if (message != null) {
            transcript.add(message);
        }
    }

    public void clearTranscript() {
        transcript.clear();
    }

    public ToolLoopReadFileStateLedger readFileStateLedger() {
        return readFileStateLedger;
    }

    public ToolLoopResultReplacementState resultReplacementState() {
        return resultReplacementState;
    }

    public List<FileMutationRecord> mutationRecords() {
        return List.copyOf(mutationRecords);
    }

    public void recordMutation(FileMutationRecord mutationRecord) {
        if (mutationRecord != null) {
            mutationRecords.add(mutationRecord);
        }
    }

    public ImplementationDiagnosticLedger diagnosticLedger() {
        return diagnosticLedger;
    }

    public List<ImplementationDiagnosticRecord> diagnostics() {
        return diagnosticLedger.records();
    }
}
