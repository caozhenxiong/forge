package devflow.agent.orchestrator;

import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.protocol.ToolResultPayload;
import devflow.agent.protocol.ToolResultsArtifactPayload;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 从阶段产物中提取结构化工具结果摘要。
 *
 * <p>当前第一版只对 TEST 阶段生效，因为测试阶段已经把
 * testcase/self-check/runtime/工具结果统一落到了辅助产物里。
 * 后续其他阶段接入时，应继续扩这里，而不是退回到 prose 解析。
 */
public class StageToolResultLoader {

    private final FileArtifactStore artifactStore;

    public StageToolResultLoader(FileArtifactStore artifactStore) {
        this.artifactStore = artifactStore;
    }

    public StageToolResultSummary load(Path projectPath, RunRecord runRecord, StageType stageType) {
        if (stageType != StageType.TEST) {
            return StageToolResultSummary.none();
        }
        String executionArtifact = artifactStore.readAuxiliaryArtifact(
                projectPath,
                runRecord.runId(),
                AuxiliaryArtifactNames.TEST_EXECUTION
        );
        ToolResultsArtifactPayload payload = StructuredArtifactBlocks.readFirstJsonBlock(
                executionArtifact,
                ArtifactBlockKind.TOOL_RESULTS,
                ToolResultsArtifactPayload.class
        );
        if (payload == null || payload.toolResults().isEmpty()) {
            return StageToolResultSummary.none();
        }
        List<String> failedTools = new ArrayList<>();
        List<String> failureCodes = new ArrayList<>();
        List<String> evidenceItems = new ArrayList<>();
        List<String> actionItems = new ArrayList<>();
        for (ToolResultPayload toolResult : payload.toolResults()) {
            if (!"FAILED".equalsIgnoreCase(toolResult.status())) {
                continue;
            }
            if (toolResult.toolName() != null && !toolResult.toolName().isBlank()) {
                failedTools.add(toolResult.toolName().trim());
            }
            if (toolResult.failureCode() != null && !toolResult.failureCode().isBlank()) {
                failureCodes.add(toolResult.failureCode().trim());
            }
            if (toolResult.evidence() != null && !toolResult.evidence().isBlank()) {
                evidenceItems.add(toolResult.evidence().trim());
            }
            if (toolResult.recommendedNextAction() != null && !toolResult.recommendedNextAction().isBlank()) {
                actionItems.add(toolResult.recommendedNextAction().trim());
            }
        }
        if (failedTools.isEmpty() && failureCodes.isEmpty()) {
            return StageToolResultSummary.none();
        }
        int failedCount = Math.max(failedTools.size(), failureCodes.size());
        String summary = "Structured tool results still contain blocking failures.";
        String evidence = evidenceItems.isEmpty() ? String.join(", ", failedTools) : String.join(" | ", evidenceItems);
        String recommendedAction = actionItems.isEmpty()
                ? "Fix the failed tools or blocked validations and rerun the current stage."
                : String.join(" | ", actionItems);
        return new StageToolResultSummary(
                true,
                failedCount,
                List.copyOf(failedTools),
                List.copyOf(failureCodes),
                summary,
                evidence,
                recommendedAction
        );
    }
}
