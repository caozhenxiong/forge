package devflow.agent.artifact;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractExtractor;
import devflow.agent.executor.LlmProvider;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.prompt.PromptTemplateCatalog;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
@Component
public class DocumentStageComposer {

    private final LlmProvider llmProvider;
    private final ContractExtractor contractExtractor;
    private final DocumentStageIntake intake;
    private final AnalysisDocumentComposer analysisComposer;
    private final PrdDocumentComposer prdComposer;
    private final DesignDocumentComposer designComposer;

    @Autowired
    public DocumentStageComposer(
            ArtifactTemplateFactory artifactTemplateFactory,
            FileArtifactStore artifactStore,
            LlmProvider llmProvider,
            ContractExtractor contractExtractor,
            PromptTemplateCatalog promptTemplateCatalog,
            LanguagePolicy languagePolicy
    ) {
        this.llmProvider = llmProvider;
        this.contractExtractor = contractExtractor;
        DocumentDraftAssembler draftAssembler = new DocumentDraftAssembler();
        this.intake = new DocumentStageIntake(
                artifactTemplateFactory,
                artifactStore,
                contractExtractor,
                languagePolicy,
                draftAssembler
        );
        DocumentStagePostProcessor postProcessor = new DocumentStagePostProcessor(contractExtractor, draftAssembler);
        DocumentPromptAssembler promptAssembler = new DocumentPromptAssembler(promptTemplateCatalog, draftAssembler);
        DocumentGenerationSupport generationSupport = new DocumentGenerationSupport(llmProvider);
        this.analysisComposer = new AnalysisDocumentComposer(
                contractExtractor,
                intake,
                draftAssembler,
                postProcessor,
                promptAssembler,
                generationSupport
        );
        this.prdComposer = new PrdDocumentComposer(
                contractExtractor,
                intake,
                draftAssembler,
                postProcessor,
                promptAssembler,
                generationSupport
        );
        this.designComposer = new DesignDocumentComposer(
                contractExtractor,
                intake,
                draftAssembler,
                postProcessor,
                promptAssembler,
                generationSupport
        );
    }

    String composeAnalysis(RunRecord runRecord, String note) {
        return analysisComposer.compose(runRecord, note);
    }

    String composePrd(Path projectPath, RunRecord runRecord, String note) {
        return prdComposer.compose(projectPath, runRecord, note);
    }

    String composeDesign(Path projectPath, RunRecord runRecord, String note) {
        return designComposer.compose(projectPath, runRecord, note);
    }
}
