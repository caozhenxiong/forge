package devflow.agent.artifact;

import devflow.agent.domain.RunRecord;
import java.nio.file.Path;

/**
 * 文档阶段的严格流程门面。
 *
 * <p>这里统一承担文档阶段的三步：
 * 1. Intake：准备上游 artifact、authority corpus、上轮草稿和修订目标；
 * 2. Plan：决定本轮是 full draft、补缺章节还是带旧稿修订；
 * 3. Generate：调用模型生成，并在本地完成 merge / stabilize / sanitize / upsert。
 *
 * <p>这样可以把 `ANALYSIS / PRD / DESIGN` 从 `StageArtifactComposer` 里整体抽离出来，
 * 避免同一个类同时扮演阶段路由器、文档 intake、文档 planner 和 artifact composer。
 */
public class DocumentStageComposer {

    private final DocumentCompositionTemplate template;
    private final AnalysisDocumentComposition analysisComposer;
    private final PrdDocumentComposition prdComposer;
    private final DesignDocumentComposition designComposer;

    public DocumentStageComposer(
            DocumentCompositionTemplate template,
            AnalysisDocumentComposition analysisComposer,
            PrdDocumentComposition prdComposer,
            DesignDocumentComposition designComposer
    ) {
        this.template = template;
        this.analysisComposer = analysisComposer;
        this.prdComposer = prdComposer;
        this.designComposer = designComposer;
    }

    String composeAnalysis(RunRecord runRecord, String note) {
        return template.compose(runRecord.projectPath(), runRecord, note, analysisComposer);
    }

    String composePrd(Path projectPath, RunRecord runRecord, String note) {
        return template.compose(projectPath, runRecord, note, prdComposer);
    }

    String composeDesign(Path projectPath, RunRecord runRecord, String note) {
        return template.compose(projectPath, runRecord, note, designComposer);
    }
}
