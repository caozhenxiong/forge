package devflow.agent.artifact;

import devflow.agent.context.ContractExtractor;
import devflow.agent.executor.GenerationBudgetProfile;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import devflow.agent.protocol.ReviewArtifactPayload;
import devflow.agent.protocol.ReviewArtifactPayloadSupport;
import devflow.agent.project.WorkspaceSnapshotStore;
import org.springframework.lang.Nullable;
import java.nio.file.Path;

/**
 * CODE_REVIEW 阶段子门面。
 *
 * <p>负责 code review 阶段的 artifact intake、结构化 contract 装配和 review prompt 调用。
 */
final class CodeReviewStageComposer {

    private final LlmProvider llmProvider;
    private final WorkspaceSnapshotStore snapshotStore;
    private final ContractExtractor contractExtractor;
    private final StageArtifactInputResolver inputResolver;
    private final CodeReviewPromptAssembler promptAssembler;

    CodeReviewStageComposer(
            LlmProvider llmProvider,
            WorkspaceSnapshotStore snapshotStore,
            ContractExtractor contractExtractor,
            StageArtifactInputResolver inputResolver
    ) {
        this.llmProvider = llmProvider;
        this.snapshotStore = snapshotStore;
        this.contractExtractor = contractExtractor;
        this.inputResolver = inputResolver;
        this.promptAssembler = new CodeReviewPromptAssembler();
    }

    String compose(Path projectPath, RunRecord runRecord, String note) {
        String prd = inputResolver.requiredStageArtifact(projectPath, runRecord, StageType.PRD);
        String design = inputResolver.requiredStageArtifact(projectPath, runRecord, StageType.DESIGN);
        String implementation = inputResolver.requiredStageArtifact(projectPath, runRecord, StageType.IMPLEMENTATION);
        String changes = snapshotStore.renderChanges(projectPath, runRecord.runId(), 8, 5000);
        DocumentLanguage language = inputResolver.documentLanguage(runRecord, note);
        String contractView = contractExtractor.extractContractView(runRecord.goal(), runRecord.constraints(), prd, design).toMarkdown(language);
        CodeReviewPrompt prompt = promptAssembler.build(note, implementation, contractView, changes);
        String content = llmProvider.generate(
                prompt.system(),
                prompt.user(),
                devflow.agent.executor.LlmOptions.outputBudgetRatio(GenerationBudgetProfile.codeReviewOutputRatio()),
                ModelRole.CODE_REVIEW
        );
        ReviewArtifactPayload payload = ReviewArtifactPayloadSupport.readFirstPayload(content);
        if (payload == null || payload.decision() == null || payload.decision().isBlank()) {
            throw new IllegalStateException("CODE_REVIEW artifact must contain a REVIEW_RESULT block");
        }
        return content;
    }
}
