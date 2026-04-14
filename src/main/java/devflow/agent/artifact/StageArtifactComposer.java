package devflow.agent.artifact;

import devflow.agent.executor.generation.GenerationTelemetry;
import devflow.agent.executor.llm.LlmProvider;

import devflow.agent.context.ContractExtractor;
import devflow.agent.executor.ImplementationExecutor;
import devflow.agent.executor.implementation.state.ImplementationStateArtifactSupport;
import devflow.agent.executor.testing.TestExecutor;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.project.WorkspaceSnapshotStore;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 阶段 artifact 的顶层门面。
 *
 * <p>文档阶段、实现阶段、测试阶段都从这里进入，但这里只负责阶段级路由和少量装配。
 * 具体的文档 intake / plan / generation 已下沉到 {@link DocumentStageComposer}，
 * implementation / test 也各自由对应 executor 负责。
 */
@Component
public class StageArtifactComposer {

    private final LlmProvider llmProvider;
    private final DocumentStageComposer documentStageComposer;
    private final ImplementationStageComposer implementationStageComposer;
    private final CodeReviewStageComposer codeReviewStageComposer;
    private final TestStageComposer testStageComposer;

    @Autowired
    public StageArtifactComposer(
            FileArtifactStore artifactStore,
            LlmProvider llmProvider,
            ImplementationExecutor implementationExecutor,
            TestExecutor testExecutor,
            WorkspaceSnapshotStore snapshotStore,
            ContractExtractor contractExtractor,
            DocumentStageComposer documentStageComposer,
            LanguagePolicy languagePolicy,
            ImplementationStateArtifactSupport implementationStateArtifactSupport
    ) {
        this.llmProvider = llmProvider;
        this.documentStageComposer = documentStageComposer;
        StageArtifactInputResolver inputResolver = new StageArtifactInputResolver(
                artifactStore,
                contractExtractor,
                languagePolicy
        );
        this.implementationStageComposer = new ImplementationStageComposer(
                artifactStore,
                implementationExecutor,
                contractExtractor,
                inputResolver,
                new ImplementationArtifactPersister(artifactStore)
        );
        this.codeReviewStageComposer = new CodeReviewStageComposer(
                llmProvider,
                artifactStore,
                snapshotStore,
                contractExtractor,
                inputResolver,
                implementationStateArtifactSupport
        );
        this.testStageComposer = new TestStageComposer(
                artifactStore,
                testExecutor,
                inputResolver
        );
    }

    public String compose(Path projectPath, RunRecord runRecord, StageType stageType, String note) {
        return switch (stageType) {
            case ANALYSIS -> documentStageComposer.composeAnalysis(projectPath, runRecord, note);
            case PRD -> documentStageComposer.composePrd(projectPath, runRecord, note);
            case DESIGN -> documentStageComposer.composeDesign(projectPath, runRecord, note);
            case IMPLEMENTATION -> implementationStageComposer.compose(projectPath, runRecord, note);
            case CODE_REVIEW -> codeReviewStageComposer.compose(projectPath, runRecord, note);
            case TEST -> testStageComposer.compose(projectPath, runRecord, note);
        };
    }

    public devflow.agent.executor.generation.GenerationTelemetry consumeLastTelemetry() {
        return llmProvider.consumeLastTelemetry();
    }
}
