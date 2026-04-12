package devflow.agent.executor;

import java.util.ArrayList;
import java.util.List;

/**
 * 子任务级 tool loop 唯一运行时状态。
 *
 * <p>该对象在 retry/continuation 间直接复用，避免：
 * 1. transcript 冷启动；
 * 2. read file state 丢失；
 * 3. tool result replacement 丢失；
 * 4. mutation side effect 出现第二份平行状态。
 */
final class ToolLoopRuntimeState {

    private final ArrayList<LlmChatMessage> transcript;
    private final ToolLoopReadFileStateLedger readFileStateLedger;
    private final ToolLoopResultReplacementState resultReplacementState;
    private final ArrayList<FileMutationRecord> mutationRecords;

    ToolLoopRuntimeState() {
        this(List.of(), new ToolLoopReadFileStateLedger(), new ToolLoopResultReplacementState(), List.of());
    }

    ToolLoopRuntimeState(
            List<LlmChatMessage> transcript,
            ToolLoopReadFileStateLedger readFileStateLedger,
            ToolLoopResultReplacementState resultReplacementState,
            List<FileMutationRecord> mutationRecords
    ) {
        this.transcript = new ArrayList<>(transcript == null ? List.of() : transcript);
        this.readFileStateLedger = readFileStateLedger == null ? new ToolLoopReadFileStateLedger() : readFileStateLedger;
        this.resultReplacementState = resultReplacementState == null ? new ToolLoopResultReplacementState() : resultReplacementState;
        this.mutationRecords = new ArrayList<>(mutationRecords == null ? List.of() : mutationRecords);
    }

    ToolLoopRuntimeState copy() {
        return new ToolLoopRuntimeState(
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
                mutationRecords()
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
}
