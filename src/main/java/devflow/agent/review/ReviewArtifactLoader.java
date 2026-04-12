package devflow.agent.review;

import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.executor.ExperienceFailureDisposition;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageType;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.util.DevflowPathSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * reviewer 层读取历史 stage artifact 的轻量支持类。
 *
 * <p>这层只做：
 * 1. 根据 `RunRecord` 找到指定阶段的 artifactPath；
 * 2. 尽量安全地读取文本内容；
 * 3. 读取失败时返回空串，避免把 reviewer 门面和 IO 细节继续耦在一起。
 */
class ReviewArtifactLoader {

    String readStageArtifact(RunRecord runRecord, StageType stageType) {
        StageExecution stageExecution = runRecord.stageStates().get(stageType);
        if (stageExecution == null || stageExecution.artifactPath() == null) {
            return "";
        }
        try {
            return Files.readString(Path.of(stageExecution.artifactPath()));
        } catch (IOException ignored) {
            return "";
        }
    }

    String readReviewerContext(RunRecord runRecord) {
        return readAuxiliaryArtifact(runRecord, AuxiliaryArtifactNames.REVIEWER_CONTEXT);
    }

    String readRepairBrief(RunRecord runRecord) {
        return readAuxiliaryArtifact(runRecord, AuxiliaryArtifactNames.REPAIR_BRIEF);
    }

    String readRepairAlignment(RunRecord runRecord) {
        return readAuxiliaryArtifact(runRecord, AuxiliaryArtifactNames.REPAIR_ALIGNMENT);
    }

    String readTestExecution(RunRecord runRecord) {
        String execution = readAuxiliaryArtifact(runRecord, AuxiliaryArtifactNames.TEST_EXECUTION);
        if (!execution.isBlank()) {
            return execution;
        }
        return readStageArtifact(runRecord, StageType.TEST);
    }

    ExperienceFailureDisposition readTestFailureDisposition(RunRecord runRecord) {
        String content = readTestExecution(runRecord);
        if (content.isBlank()) {
            return ExperienceFailureDisposition.pass();
        }
        ExperienceFailureDisposition disposition = StructuredArtifactBlocks.readFirstJsonBlock(
                content,
                ArtifactBlockKind.EXPERIENCE_FAILURE_DISPOSITION,
                ExperienceFailureDisposition.class
        );
        return disposition == null ? ExperienceFailureDisposition.pass() : disposition;
    }

    private String readAuxiliaryArtifact(RunRecord runRecord, String fileName) {
        Path path = DevflowPathSupport.auxiliaryArtifact(runRecord.projectPath(), runRecord.runId(), fileName);
        try {
            if (!Files.exists(path)) {
                return "";
            }
            return Files.readString(path);
        } catch (IOException ignored) {
            return "";
        }
    }
}
