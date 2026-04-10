package devflow.agent.artifact;

import devflow.agent.context.ArtifactContextSanitizer;
import devflow.agent.context.ConstraintAuthoritySupport;
import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ExecutionContract;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageType;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import java.nio.file.Path;
import java.util.List;

/**
 * 文档阶段 intake 支撑。
 *
 * <p>负责把上游 artifact、语言、修订目标、authority corpus 和上一轮草稿
 * 统一整理成稳定的 `DocumentDraftContext`，避免这些步骤散在 composer 内部。
 */
final class DocumentStageIntake {

    private final ArtifactTemplateFactory artifactTemplateFactory;
    private final FileArtifactStore artifactStore;
    private final ContractExtractor contractExtractor;
    private final LanguagePolicy languagePolicy;
    private final DocumentDraftAssembler draftAssembler;

    DocumentStageIntake(
            ArtifactTemplateFactory artifactTemplateFactory,
            FileArtifactStore artifactStore,
            ContractExtractor contractExtractor,
            LanguagePolicy languagePolicy,
            DocumentDraftAssembler draftAssembler
    ) {
        this.artifactTemplateFactory = artifactTemplateFactory;
        this.artifactStore = artifactStore;
        this.contractExtractor = contractExtractor;
        this.languagePolicy = languagePolicy;
        this.draftAssembler = draftAssembler;
    }

    DocumentDraftContext buildContext(
            StageType stageType,
            RunRecord runRecord,
            String note,
            DocumentLanguage language,
            ConstraintSourceMetadata authoritativeSourceMetadata
    ) {
        String template = artifactTemplateFactory.create(stageType, runRecord, note, language);
        String previousDraft = currentStageArtifactOrEmpty(runRecord, stageType);
        List<Integer> targetSections = extractRequestedSections(note);
        String previousOutline = draftAssembler.documentOutline(previousDraft);
        DocumentDraftMode mode = resolveDocumentDraftMode(previousDraft, targetSections);
        return new DocumentDraftContext(
                language,
                template,
                authoritativeSourceMetadata,
                authoritativeSourceMetadata.toMarkdown(draftAssembler.topLevelSectionNumbers(stageType).size(), language),
                previousDraft,
                targetSections,
                previousOutline,
                mode
        );
    }

    String requiredStageArtifact(Path projectPath, RunRecord runRecord, StageType stageType) {
        StageExecution stageExecution = runRecord.stageStates().get(stageType);
        if (stageExecution == null || stageExecution.artifactPath() == null) {
            throw new IllegalStateException("Missing upstream artifact for stage " + stageType);
        }
        return artifactStore.readArtifact(projectPath, runRecord.runId(), stageType);
    }

    String currentStageArtifactOrEmpty(RunRecord runRecord, StageType stageType) {
        StageExecution stageExecution = runRecord.stageStates().get(stageType);
        if (stageExecution == null || stageExecution.artifactPath() == null) {
            return "";
        }
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(stageExecution.artifactPath()));
        } catch (Exception exception) {
            return "";
        }
    }

    String filterUpstreamPromptContext(String artifact, StageType sourceStage, String authorityCorpus) {
        return ArtifactContextSanitizer.sanitizeForPrompt(artifact, sourceStage, authorityCorpus);
    }

    String buildAuthorityCorpus(RunRecord runRecord, ExecutionContract executionContract) {
        return ConstraintAuthoritySupport.buildAuthorityCorpus(
                runRecord.goal(),
                runRecord.constraints(),
                contractExtractor.buildAuthoritativeSourceMetadata(runRecord.goal(), runRecord.constraints()),
                executionContract
        );
    }

    DocumentLanguage documentLanguage(RunRecord runRecord, String note) {
        return languagePolicy.resolve(runRecord.goal(), runRecord.constraints(), note);
    }

    private List<Integer> extractRequestedSections(String note) {
        if (note == null || note.isBlank()) {
            return List.of();
        }
        return ExecutionDirectiveProtocol.parseMerged(note).targetSections();
    }

    private DocumentDraftMode resolveDocumentDraftMode(String previousDraft, List<Integer> targetSections) {
        if (previousDraft == null || previousDraft.isBlank()) {
            return DocumentDraftMode.FULL_DRAFT;
        }
        if (targetSections != null && !targetSections.isEmpty()) {
            return DocumentDraftMode.FILL_MISSING_SECTIONS;
        }
        return DocumentDraftMode.REVISE_WITH_EXISTING_DRAFT;
    }
}
