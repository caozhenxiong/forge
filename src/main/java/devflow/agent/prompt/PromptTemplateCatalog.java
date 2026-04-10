package devflow.agent.prompt;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.StageType;
import org.springframework.stereotype.Component;

/**
 * Prompt 目录门面。
 *
 * <p>这里只保留稳定入口，阶段 prompt 与约束/metadata prompt
 * 分别下沉到独立 catalog，避免再长成一个总类。
 */
@Component
public class PromptTemplateCatalog {

    private final DocumentStagePromptCatalog documentStagePromptCatalog = new DocumentStagePromptCatalog();
    private final ConstraintPromptCatalog constraintPromptCatalog = new ConstraintPromptCatalog();

    public String documentGenerationSystemPrompt(StageType stageType, DocumentLanguage language) {
        return documentStagePromptCatalog.documentGenerationSystemPrompt(stageType, language);
    }

    public String documentReviewerSystemPrompt(StageType stageType, DocumentLanguage language) {
        return documentStagePromptCatalog.documentReviewerSystemPrompt(stageType, language);
    }

    public String directLaunchClarification(DocumentLanguage language) {
        return constraintPromptCatalog.directLaunchClarification(language);
    }

    public String sourceMetadataRequirement(DocumentLanguage language, int sectionNumber) {
        return constraintPromptCatalog.sourceMetadataRequirement(language, sectionNumber);
    }

    public String sourceMetadataSemantics(DocumentLanguage language) {
        return constraintPromptCatalog.sourceMetadataSemantics(language);
    }

    public String sourceAndConstraintGuidance(DocumentLanguage language, StageType stageType) {
        return constraintPromptCatalog.sourceAndConstraintGuidance(language, stageType);
    }

    public String contractMetadataRequirement(DocumentLanguage language, int sectionNumber, String acceptanceSignalExample) {
        return constraintPromptCatalog.contractMetadataRequirement(language, sectionNumber, acceptanceSignalExample);
    }

    public String contractMetadataRuntimeKeyReminder(DocumentLanguage language) {
        return constraintPromptCatalog.contractMetadataRuntimeKeyReminder(language);
    }
}
