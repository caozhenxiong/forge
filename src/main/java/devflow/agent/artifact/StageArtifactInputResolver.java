package devflow.agent.artifact;

import devflow.agent.context.ConstraintAuthoritySupport;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ExecutionContract;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageType;
import java.nio.file.Path;

/**
 * 阶段 artifact 的公共输入解析器。
 *
 * <p>统一负责：
 * - 读取上游 stage artifact
 * - 解析文档语言
 * - 构造 authority corpus
 *
 * <p>这样各个阶段子 composer 只关注自己的编排，不再各自维护输入装配逻辑。
 */
final class StageArtifactInputResolver {

    private final FileArtifactStore artifactStore;
    private final ContractExtractor contractExtractor;
    private final LanguagePolicy languagePolicy;

    StageArtifactInputResolver(
            FileArtifactStore artifactStore,
            ContractExtractor contractExtractor,
            LanguagePolicy languagePolicy
    ) {
        this.artifactStore = artifactStore;
        this.contractExtractor = contractExtractor;
        this.languagePolicy = languagePolicy;
    }

    String requiredStageArtifact(Path projectPath, RunRecord runRecord, StageType stageType) {
        StageExecution stageExecution = runRecord.stageStates().get(stageType);
        if (stageExecution == null || stageExecution.artifactPath() == null) {
            throw new IllegalStateException("Missing upstream artifact for stage " + stageType);
        }
        return artifactStore.readArtifact(projectPath, runRecord.runId(), stageType);
    }

    DocumentLanguage documentLanguage(RunRecord runRecord, String note) {
        return languagePolicy.resolve(runRecord.goal(), runRecord.constraints(), note);
    }

    String buildAuthorityCorpus(RunRecord runRecord, ExecutionContract executionContract) {
        return ConstraintAuthoritySupport.buildAuthorityCorpus(
                runRecord.goal(),
                runRecord.constraints(),
                contractExtractor.buildAuthoritativeSourceMetadata(runRecord.goal(), runRecord.constraints()),
                executionContract
        );
    }
}
