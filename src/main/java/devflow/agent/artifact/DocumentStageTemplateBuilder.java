package devflow.agent.artifact;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;

/**
 * 文档阶段模板构造器。
 *
 * <p>负责 `ANALYSIS / PRD / DESIGN` 的模板生成，
 * 避免 `ArtifactTemplateFactory` 继续内嵌三份长文档模板。
 */
final class DocumentStageTemplateBuilder {

    private final AnalysisDocumentTemplateBuilder analysisTemplateBuilder;
    private final PrdDocumentTemplateBuilder prdTemplateBuilder;
    private final DesignDocumentTemplateBuilder designTemplateBuilder;

    DocumentStageTemplateBuilder(ArtifactTemplateSupport support) {
        this.analysisTemplateBuilder = new AnalysisDocumentTemplateBuilder(support);
        this.prdTemplateBuilder = new PrdDocumentTemplateBuilder(support);
        this.designTemplateBuilder = new DesignDocumentTemplateBuilder(support);
    }

    String create(StageType stageType, RunRecord runRecord, String note, DocumentLanguage language) {
        return switch (stageType) {
            case ANALYSIS -> analysisTemplateBuilder.build(runRecord, note, language);
            case PRD -> prdTemplateBuilder.build(runRecord, note, language);
            case DESIGN -> designTemplateBuilder.build(runRecord, note, language);
            default -> throw new IllegalArgumentException("Unsupported document stage: " + stageType);
        };
    }
}
