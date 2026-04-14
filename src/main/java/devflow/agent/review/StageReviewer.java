package devflow.agent.review;

import devflow.agent.executor.generation.GenerationTelemetry;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 统一阶段评审入口。模型负责提出 findings，这个类负责把评审结果规范化，
 * 并补上程序化护栏，防止跨阶段越界、把低权重建议升级成硬约束，或把假阳性
 * 直接变成阻塞项。
 */
@Component
public class StageReviewer {

    private final DocumentStageReviewer documentStageReviewer;
    private final ImplementationStageReviewer implementationStageReviewer;
    private final ExecutionStageReviewer executionStageReviewer;

    @Autowired
    public StageReviewer(
            DocumentStageReviewer documentStageReviewer,
            ImplementationStageReviewer implementationStageReviewer,
            ExecutionStageReviewer executionStageReviewer
    ) {
        this.documentStageReviewer = documentStageReviewer;
        this.implementationStageReviewer = implementationStageReviewer;
        this.executionStageReviewer = executionStageReviewer;
    }

    public ReviewResult review(Path projectPath, RunRecord runRecord, StageType stageType, String artifactContent) {
        return switch (stageType) {
            case ANALYSIS -> documentStageReviewer.review(runRecord, stageType, artifactContent, "需求分析");
            case PRD -> documentStageReviewer.review(runRecord, stageType, artifactContent, "PRD");
            case DESIGN -> documentStageReviewer.review(runRecord, stageType, artifactContent, "技术方案");
            case IMPLEMENTATION -> implementationStageReviewer.review(projectPath, runRecord, artifactContent);
            case CODE_REVIEW, TEST -> executionStageReviewer.review(stageType, artifactContent);
        };
    }

    public GenerationTelemetry consumeLastTelemetry() {
        return documentStageReviewer.consumeLastTelemetry();
    }
}
