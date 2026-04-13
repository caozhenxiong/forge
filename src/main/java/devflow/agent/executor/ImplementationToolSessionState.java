package devflow.agent.executor;

import java.util.ArrayList;
import java.util.List;

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
final class ImplementationToolSessionState {

    private final ArrayList<LlmChatMessage> transcript;
    private final ToolLoopReadFileStateLedger readFileStateLedger;
    private final ToolLoopResultReplacementState resultReplacementState;
    private final ArrayList<FileMutationRecord> mutationRecords;
    private final ImplementationDiagnosticLedger diagnosticLedger;

    ImplementationToolSessionState() {
        this(
                List.of(),
                new ToolLoopReadFileStateLedger(),
                new ToolLoopResultReplacementState(),
                List.of(),
                new ImplementationDiagnosticLedger()
        );
    }

    ImplementationToolSessionState(
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

    ImplementationToolSessionState copy() {
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

    List<LlmChatMessage> transcript() {
        return List.copyOf(transcript);
    }

    void resetTranscript(List<LlmChatMessage> messages) {
        transcript.clear();
        transcript.addAll(messages == null ? List.of() : messages);
    }

    void appendTranscript(LlmChatMessage message) {
        if (message != null) {
            transcript.add(message);
        }
    }

    void clearTranscript() {
        transcript.clear();
    }

    ToolLoopReadFileStateLedger readFileStateLedger() {
        return readFileStateLedger;
    }

    ToolLoopResultReplacementState resultReplacementState() {
        return resultReplacementState;
    }

    List<FileMutationRecord> mutationRecords() {
        return List.copyOf(mutationRecords);
    }

    void recordMutation(FileMutationRecord mutationRecord) {
        if (mutationRecord != null) {
            mutationRecords.add(mutationRecord);
        }
    }

    ImplementationDiagnosticLedger diagnosticLedger() {
        return diagnosticLedger;
    }

    List<ImplementationDiagnosticRecord> diagnostics() {
        return diagnosticLedger.records();
    }
}
