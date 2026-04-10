package devflow.agent.artifact;

import devflow.agent.orchestrator.RunRecord;
import devflow.agent.prompt.PromptTemplateCatalog;

/**
 * 文档阶段 prompt 组装器。
 *
 * <p>这里专门负责把 stage + draft mode + intake 上下文转成生成 prompt，
 * 避免 `DocumentStageComposer` 继续同时承担编排和 prompt 规划两类职责。
 */
final class DocumentPromptAssembler {

    private final AnalysisDocumentPromptBuilder analysisPromptBuilder;
    private final PrdDocumentPromptBuilder prdPromptBuilder;
    private final DesignDocumentPromptBuilder designPromptBuilder;

    DocumentPromptAssembler(PromptTemplateCatalog promptTemplateCatalog, DocumentDraftAssembler draftAssembler) {
        this.analysisPromptBuilder = new AnalysisDocumentPromptBuilder(promptTemplateCatalog, draftAssembler);
        this.prdPromptBuilder = new PrdDocumentPromptBuilder(promptTemplateCatalog, draftAssembler);
        this.designPromptBuilder = new DesignDocumentPromptBuilder(promptTemplateCatalog, draftAssembler);
    }

    DocumentGenerationPrompt buildAnalysisPrompt(RunRecord runRecord, String note, DocumentDraftContext context) {
        return analysisPromptBuilder.build(runRecord, note, context);
    }

    DocumentGenerationPrompt buildPrdPrompt(
            RunRecord runRecord,
            String note,
            DocumentDraftContext context,
            String analysisForPrompt
    ) {
        return prdPromptBuilder.build(runRecord, note, context, analysisForPrompt);
    }

    DocumentGenerationPrompt buildDesignPrompt(
            RunRecord runRecord,
            String note,
            DocumentDraftContext context,
            String prdForPrompt
    ) {
        return designPromptBuilder.build(runRecord, note, context, prdForPrompt);
    }

    String designPerformanceGuidance() {
        return designPromptBuilder.designPerformanceGuidance();
    }
}
