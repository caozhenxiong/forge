package devflow.agent.artifact;

import devflow.agent.executor.generation.GenerationBudgetProfile;
import devflow.agent.executor.llm.LlmOptions;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;

import devflow.agent.context.ContractExtractor;
import devflow.agent.executor.ImplementationStateArtifactSupport;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.protocol.ReviewArtifactPayload;
import devflow.agent.protocol.ReviewArtifactPayloadSupport;
import devflow.agent.project.WorkspaceSnapshotStore;
import java.nio.file.Path;

/**
 * CODE_REVIEW 阶段子门面。
 *
 * <p>负责 code review 阶段的 artifact intake、结构化 contract 装配和 review prompt 调用。
 */
final class CodeReviewStageComposer {

    private final LlmProvider llmProvider;
    private final FileArtifactStore artifactStore;
    private final WorkspaceSnapshotStore snapshotStore;
    private final ContractExtractor contractExtractor;
    private final StageArtifactInputResolver inputResolver;
    private final CodeReviewPromptAssembler promptAssembler;
    private final ImplementationStateArtifactSupport implementationStateSupport;

    CodeReviewStageComposer(
            LlmProvider llmProvider,
            FileArtifactStore artifactStore,
            WorkspaceSnapshotStore snapshotStore,
            ContractExtractor contractExtractor,
            StageArtifactInputResolver inputResolver
    ) {
        this.llmProvider = llmProvider;
        this.artifactStore = artifactStore;
        this.snapshotStore = snapshotStore;
        this.contractExtractor = contractExtractor;
        this.inputResolver = inputResolver;
        this.promptAssembler = new CodeReviewPromptAssembler();
        this.implementationStateSupport = new ImplementationStateArtifactSupport();
    }

    String compose(Path projectPath, RunRecord runRecord, String note) {
        String prd = inputResolver.requiredStageArtifact(projectPath, runRecord, StageType.PRD);
        String design = inputResolver.requiredStageArtifact(projectPath, runRecord, StageType.DESIGN);
        String changes = snapshotStore.buildReviewChangePack(projectPath, runRecord.runId()).toMarkdown();
        String implementationSummary = buildImplementationSummary(projectPath, runRecord);
        DocumentLanguage language = inputResolver.documentLanguage(runRecord, note);
        String contractView = contractExtractor.extractContractView(runRecord.goal(), runRecord.constraints(), prd, design).toMarkdown(language);
        CodeReviewPrompt prompt = promptAssembler.build(note, implementationSummary, contractView, changes);
        String content = llmProvider.generate(
                prompt.system(),
                prompt.user(),
                devflow.agent.executor.llm.LlmOptions.outputBudgetRatio(GenerationBudgetProfile.codeReviewOutputRatio()),
                ModelRole.CODE_REVIEW
        );
        ReviewArtifactPayload payload = ReviewArtifactPayloadSupport.readFirstPayload(content);
        if (payload == null || payload.decision() == null || payload.decision().isBlank()) {
            throw new IllegalStateException("CODE_REVIEW artifact must contain a REVIEW_RESULT block");
        }
        return content;
    }

    private String buildImplementationSummary(Path projectPath, RunRecord runRecord) {
        String stateArtifact = artifactStore.readAuxiliaryArtifact(
                projectPath,
                runRecord.runId(),
                AuxiliaryArtifactNames.IMPLEMENTATION_STATE
        );
        if (stateArtifact == null || stateArtifact.isBlank()) {
            throw new IllegalStateException("Missing implementation_state auxiliary artifact for code review.");
        }
        return implementationStateSupport.renderReviewSummary(stateArtifact);
    }
}
