package devflow.agent.repair;

import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ReviewHistoryEntryPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import java.util.List;

/**
 * diagnosis review history 读取器。
 *
 * <p>负责把 review history artifact 转成 diagnosis 需要的连续失败轨迹，
 * 避免 `DiagnosisAgent` 自己同时承担 artifact 解析职责。
 */
final class DiagnosisHistoryReader {

    List<DiagnosisAgent.FailureEntry> parse(String history) {
        if (history == null || history.isBlank()) {
            return List.of();
        }
        List<ReviewHistoryEntryPayload> payloads = StructuredArtifactBlocks.readAllJsonBlocks(
                history,
                ArtifactBlockKind.REVIEW_HISTORY_ENTRY,
                ReviewHistoryEntryPayload.class
        );
        if (payloads.isEmpty()) {
            return List.of();
        }
        return payloads.stream()
                .map(payload -> new DiagnosisAgent.FailureEntry(
                        payload.attempt(),
                        blank(payload.summary()),
                        blank(payload.changeRequest()),
                        blank(payload.evidence()),
                        blank(payload.actionItems())
                ))
                .toList();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
