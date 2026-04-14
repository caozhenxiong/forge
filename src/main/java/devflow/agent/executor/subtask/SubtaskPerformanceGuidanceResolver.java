package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageType;
import devflow.agent.protocol.ExecutionDirectiveNarrativeRenderer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 负责从 DESIGN 产物中解析子任务验证所需的性能校验提示。
 * 这里只消费结构化 validation metadata，不再在 verifier 编排里直接读文件。
 */
public final class SubtaskPerformanceGuidanceResolver {

    private final ContractExtractor contractExtractor;

    public SubtaskPerformanceGuidanceResolver(ContractExtractor contractExtractor) {
        this.contractExtractor = contractExtractor;
    }

    String resolve(RunRecord runRecord) {
        String design = designFromRun(runRecord);
        ValidationMetadata metadata = contractExtractor.extractValidationMetadata("", design);
        if (!metadata.performanceMeasurementRequired()) {
            return "";
        }
        return ExecutionDirectiveNarrativeRenderer.renderPerformanceValidationGuidance(
                metadata.pageLoadMaxMs(),
                metadata.interactionMaxMs()
        );
    }

    private String designFromRun(RunRecord runRecord) {
        StageExecution execution = runRecord.stageStates().get(StageType.DESIGN);
        if (execution == null || execution.artifactPath() == null) {
            return "";
        }
        try {
            return Files.readString(Path.of(execution.artifactPath()));
        } catch (Exception ignored) {
            return "";
        }
    }
}
