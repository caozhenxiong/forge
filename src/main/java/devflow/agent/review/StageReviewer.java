package devflow.agent.review;

import devflow.agent.artifact.ArtifactSectionKind;
import devflow.agent.artifact.ArtifactSectionSupport;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.executor.GenerationBudgetProfile;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.TestExecutor;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContractView;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import devflow.agent.prompt.PromptTemplateCatalog;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.ExperienceGateEvaluator;
import devflow.agent.quality.ExperienceGateOutcome;
import devflow.agent.quality.QualityLedger;
import devflow.agent.quality.QualityPlan;
import devflow.agent.quality.QualityPlanFactory;
import devflow.agent.quality.StructureGateEvaluator;
import devflow.agent.quality.StructureGateOutcome;
import devflow.agent.validation.ProjectInspector;
import devflow.agent.project.WorkspaceSnapshotStore;
import devflow.agent.protocol.ArtifactBlockKind;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 统一阶段评审入口。模型负责提出 findings，这个类负责把评审结果规范化，
 * 并补上程序化护栏，防止跨阶段越界、把低权重建议升级成硬约束，或把假阳性
 * 直接变成阻塞项。
 */
@Component
public class StageReviewer {

    private static final EnumSet<ArtifactSectionKind> STRIPPED_REVIEW_SECTIONS = EnumSet.of(ArtifactSectionKind.CURRENT_NOTES);
    private final LlmProvider llmProvider;
    private final WorkspaceSnapshotStore snapshotStore;
    private final TestExecutor testExecutor;
    private final PromptTemplateCatalog promptTemplateCatalog;
    private final LanguagePolicy languagePolicy;
    private final ReviewDecisionArtifactParser reviewDecisionArtifactParser;
    private final DocumentStructureGuard documentStructureGuard;
    private final DocumentReviewNormalizer documentReviewNormalizer;
    private final ImplementationReviewNormalizer implementationReviewNormalizer;
    private final ReviewArtifactLoader reviewArtifactLoader;
    private final DocumentReviewTurnExecutor documentReviewTurnExecutor;
    private final ImplementationReviewTurnExecutor implementationReviewTurnExecutor;
    private final ProjectInspector projectInspector;
    private final ContractExtractor contractExtractor;
    private final QualityPlanFactory qualityPlanFactory;
    private final StructureGateEvaluator structureGateEvaluator;
    private final ExperienceGateEvaluator experienceGateEvaluator;

    @Autowired
    public StageReviewer(
            LlmProvider llmProvider,
            WorkspaceSnapshotStore snapshotStore,
            TestExecutor testExecutor,
            PromptTemplateCatalog promptTemplateCatalog,
            LanguagePolicy languagePolicy,
            devflow.agent.loop.AgentTurnLoop agentTurnLoop
    ) {
        this.llmProvider = llmProvider;
        this.snapshotStore = snapshotStore;
        this.testExecutor = testExecutor;
        this.promptTemplateCatalog = promptTemplateCatalog;
        this.languagePolicy = languagePolicy;
        this.reviewDecisionArtifactParser = new ReviewDecisionArtifactParser();
        this.documentStructureGuard = new DocumentStructureGuard();
        this.documentReviewNormalizer = new DocumentReviewNormalizer();
        this.implementationReviewNormalizer = new ImplementationReviewNormalizer();
        this.reviewArtifactLoader = new ReviewArtifactLoader();
        this.documentReviewTurnExecutor = new DocumentReviewTurnExecutor(llmProvider, agentTurnLoop, documentReviewNormalizer);
        this.implementationReviewTurnExecutor = new ImplementationReviewTurnExecutor(
                llmProvider,
                agentTurnLoop,
                implementationReviewNormalizer,
                reviewArtifactLoader
        );
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        this.projectInspector = new ProjectInspector(workspace);
        this.contractExtractor = new ContractExtractor();
        this.qualityPlanFactory = new QualityPlanFactory();
        this.structureGateEvaluator = new StructureGateEvaluator();
        this.experienceGateEvaluator = new ExperienceGateEvaluator();
    }

    /**
     * 兼容旧的直接构造方式，避免测试和轻量装配在这一轮重构中全部联动。
     */
    public StageReviewer(LlmProvider llmProvider, WorkspaceSnapshotStore snapshotStore, TestExecutor testExecutor) {
        this(llmProvider, snapshotStore, testExecutor, new PromptTemplateCatalog(), new LanguagePolicy(), new AgentTurnLoop());
    }

    public ReviewResult review(Path projectPath, RunRecord runRecord, StageType stageType, String artifactContent) {
        if (stageType == StageType.ANALYSIS) {
            return reviewDocument(runRecord, stageType, artifactContent, "需求分析");
        }
        if (stageType == StageType.PRD) {
            return reviewDocument(runRecord, stageType, artifactContent, "PRD");
        }
        if (stageType == StageType.DESIGN) {
            return reviewDocument(runRecord, stageType, artifactContent, "技术方案");
        }
        if (stageType == StageType.IMPLEMENTATION) {
            return reviewImplementation(projectPath, runRecord, artifactContent);
        }
        if (stageType == StageType.CODE_REVIEW) {
            return reviewDecisionArtifactParser.parseDecisionArtifact(
                    artifactContent,
                    ReviewDecision.REVISION_REQUIRED,
                    "代码审阅发现问题，需要修改。"
            );
        }
        ReviewResult testResult = reviewDecisionArtifactParser.parseTestArtifact(artifactContent);
        return enforceExperienceGate(artifactContent, testResult);
    }

    public devflow.agent.executor.GenerationTelemetry consumeLastTelemetry() {
        return llmProvider.consumeLastTelemetry();
    }

    /**
     * 文档阶段统一走共享模板评审，再叠加确定性的结构校验与降噪规则，
     * 避免 reviewer 只靠自然语言判断而产生阶段越界。
     */
    private ReviewResult reviewDocument(RunRecord runRecord, StageType stageType, String candidateContent, String artifactLabel) {
        String sanitizedCandidate = stripProcessNoteSection(candidateContent);
        String reviewerContext = reviewArtifactLoader.readReviewerContext(runRecord);
        DocumentLanguage language = languagePolicy.resolve(
                sanitizedCandidate,
                runRecord.goal(),
                runRecord.constraints()
        );
        String systemPrompt = promptTemplateCatalog.documentReviewerSystemPrompt(stageType, language);
        ReviewResult normalized = documentReviewTurnExecutor.run(
                runRecord,
                stageType,
                sanitizedCandidate,
                reviewerContext,
                systemPrompt
        );
        return documentStructureGuard.enforce(runRecord, stageType, sanitizedCandidate, artifactLabel, normalized);
    }

    private ReviewResult reviewImplementation(Path projectPath, RunRecord runRecord, String artifactContent) {
        SelfCheckResult selfCheck = testExecutor.selfCheck(projectPath);
        if (!selfCheck.passed()) {
            return new ReviewResult(
                    ReviewDecision.REVISION_REQUIRED,
                    FixMode.PATCH,
                selfCheck.summary(),
                selfCheck.details()
            );
        }
        ReviewResult structureGateResult = enforceStructureGate(projectPath, runRecord);
        if (structureGateResult != null) {
            return structureGateResult;
        }

        String changes = snapshotStore.renderChanges(projectPath, runRecord.runId(), 8, 5000);
        String candidate = artifactContent
                + "\n\n## 实现自检\n\n"
                + selfCheck.summary()
                + "\n\n"
                + selfCheck.details()
                + "\n\n## 实际代码变更\n\n"
                + changes;
        String reviewerContext = reviewArtifactLoader.readReviewerContext(runRecord);
        return implementationReviewTurnExecutor.run(runRecord, candidate, reviewerContext);
    }

    private ReviewResult enforceStructureGate(Path projectPath, RunRecord runRecord) {
        String prd = reviewArtifactLoader.readStageArtifact(runRecord, StageType.PRD);
        String design = reviewArtifactLoader.readStageArtifact(runRecord, StageType.DESIGN);
        ContractView contractView = contractExtractor.extractContractView(runRecord.goal(), runRecord.constraints(), "", prd, design);
        ValidationMetadata validationMetadata = contractExtractor.extractValidationMetadata(prd, design);
        QualityPlan qualityPlan = qualityPlanFactory.build(
                projectPath,
                projectInspector.inspect(projectPath),
                contractView,
                validationMetadata,
                null,
                java.util.List.of()
        );
        StructureGateOutcome gateOutcome = structureGateEvaluator.evaluate(projectInspector.inspect(projectPath), qualityPlan);
        if (gateOutcome.passed()) {
            return null;
        }
        return new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                gateOutcome.summary(),
                gateOutcome.changeRequest(),
                gateOutcome.evidence(),
                ""
        );
    }

    private ReviewResult enforceExperienceGate(String artifactContent, ReviewResult current) {
        QualityLedger qualityLedger = StructuredArtifactBlocks.readFirstJsonBlock(
                artifactContent,
                ArtifactBlockKind.QUALITY_LEDGER,
                QualityLedger.class
        );
        ExperienceGateOutcome gateOutcome = experienceGateEvaluator.evaluate(qualityLedger);
        if (gateOutcome.passed()) {
            return current;
        }
        return new ReviewResult(
                ReviewDecision.REJECTED,
                FixMode.PATCH,
                gateOutcome.summary(),
                gateOutcome.changeRequest(),
                gateOutcome.evidence(),
                ""
        );
    }

    private String stripProcessNoteSection(String content) {
        String withoutNotes = ArtifactSectionSupport.removeSections(content, STRIPPED_REVIEW_SECTIONS);
        return StructuredArtifactBlocks.stripAllKnownBlocks(withoutNotes).trim();
    }
}
