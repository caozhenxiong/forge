package devflow.agent.review;

import devflow.agent.artifact.ArtifactSectionKind;
import devflow.agent.artifact.ArtifactSectionSupport;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.executor.generation.GenerationTelemetry;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.prompt.PromptTemplateCatalog;
import devflow.agent.protocol.StructuredArtifactBlocks;
import java.util.EnumSet;

final class DocumentStageReviewer {

    private final PromptTemplateCatalog promptTemplateCatalog;
    private final LanguagePolicy languagePolicy;
    private final ReviewArtifactLoader reviewArtifactLoader;
    private final DocumentReviewTurnExecutor documentReviewTurnExecutor;
    private final DocumentStructureGuard documentStructureGuard;

    DocumentStageReviewer(
            PromptTemplateCatalog promptTemplateCatalog,
            LanguagePolicy languagePolicy,
            ReviewArtifactLoader reviewArtifactLoader,
            DocumentReviewTurnExecutor documentReviewTurnExecutor,
            DocumentStructureGuard documentStructureGuard
    ) {
        this.promptTemplateCatalog = promptTemplateCatalog;
        this.languagePolicy = languagePolicy;
        this.reviewArtifactLoader = reviewArtifactLoader;
        this.documentReviewTurnExecutor = documentReviewTurnExecutor;
        this.documentStructureGuard = documentStructureGuard;
    }

    ReviewResult review(RunRecord runRecord, StageType stageType, String candidateContent, String artifactLabel) {
        String guardedCandidate = stripProcessNoteSection(candidateContent);
        String reviewerCandidate = stripMachineBlocks(guardedCandidate);
        String reviewerContext = reviewArtifactLoader.readReviewerContext(runRecord);
        DocumentLanguage language = languagePolicy.resolve(
                reviewerCandidate,
                runRecord.goal(),
                runRecord.constraints()
        );
        String systemPrompt = promptTemplateCatalog.documentReviewerSystemPrompt(stageType, language);
        ReviewResult normalized = documentReviewTurnExecutor.run(
                runRecord,
                stageType,
                reviewerCandidate,
                reviewerContext,
                systemPrompt
        );
        return documentStructureGuard.enforce(runRecord, stageType, guardedCandidate, artifactLabel, normalized);
    }

    GenerationTelemetry consumeLastTelemetry() {
        return documentReviewTurnExecutor.consumeLastTelemetry();
    }

    private String stripProcessNoteSection(String content) {
        String rawBlocks = StructuredArtifactBlocks.collectAllKnownBlocks(content);
        String prose = StructuredArtifactBlocks.stripAllKnownBlocks(content);
        String sanitizedProse = ArtifactSectionSupport.removeSections(
                prose,
                EnumSet.of(ArtifactSectionKind.CURRENT_NOTES)
        ).trim();
        if (rawBlocks.isBlank()) {
            return sanitizedProse;
        }
        if (sanitizedProse.isBlank()) {
            return rawBlocks;
        }
        return sanitizedProse + "\n\n" + rawBlocks;
    }

    private String stripMachineBlocks(String content) {
        return StructuredArtifactBlocks.stripAllKnownBlocks(content).trim();
    }
}
