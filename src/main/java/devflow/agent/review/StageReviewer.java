package devflow.agent.review;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.executor.ArchitectIntegrationCheck;
import devflow.agent.executor.ArchitectIntegrationCheckResult;
import devflow.agent.executor.ArchitectIntegrationFailureReason;
import devflow.agent.executor.ExperienceFailureDisposition;
import devflow.agent.executor.GenerationBudgetProfile;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.TestExecutor;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ExecutionContract;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import devflow.agent.prompt.PromptTemplateCatalog;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.QualityLedger;
import devflow.agent.project.WorkspaceSnapshotStore;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 统一阶段评审入口。模型负责提出 findings，这个类负责把评审结果规范化，
 * 并补上程序化护栏，防止跨阶段越界、把低权重建议升级成硬约束，或把假阳性
 * 直接变成阻塞项。
 */
@Component
public class StageReviewer {

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
    private final ContractExtractor contractExtractor;
    private final ArchitectIntegrationCheck architectIntegrationCheck;

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
        this.contractExtractor = new ContractExtractor();
        this.architectIntegrationCheck = new ArchitectIntegrationCheck(workspace, new TreeSitterSupport());
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
        ReviewResult parsed = reviewDecisionArtifactParser.parseDecisionArtifact(
                artifactContent,
                ReviewDecision.REJECTED,
                "测试失败，需要修复并重新执行。"
        );
        return enforceStructuredTestCoverageGate(artifactContent, parsed);
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
        ReviewResult contractGateResult = enforceImplementationContractGate(projectPath, runRecord);
        if (contractGateResult != null) {
            return contractGateResult;
        }
        ReviewResult targetedVerificationGate = enforceImplementationFailureVerification(projectPath, runRecord, artifactContent);
        if (targetedVerificationGate != null) {
            return targetedVerificationGate;
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

    /**
     * 如果 implementation 是从 TEST 失败回流回来的，review 不能只看 smoke/self-check。
     *
     * <p>这里会在 reviewer 之前先针对上一轮失败目标做一次确定性复核。
     * 只有当前失败 case / capability 真正重新通过，implementation review 才允许进入语义审批。
     */
    private ReviewResult enforceImplementationFailureVerification(Path projectPath, RunRecord runRecord, String artifactContent) {
        ExperienceFailureDisposition previousFailure = reviewArtifactLoader.readTestFailureDisposition(runRecord);
        if (previousFailure == null || !previousFailure.requiresImplementationReverification()) {
            return null;
        }
        String prd = reviewArtifactLoader.readStageArtifact(runRecord, StageType.PRD);
        String design = reviewArtifactLoader.readStageArtifact(runRecord, StageType.DESIGN);
        DocumentLanguage language = languagePolicy.resolve(
                artifactContent,
                runRecord.goal(),
                runRecord.constraints()
        );
        return testExecutor.verifyImplementationRepairTargets(
                projectPath,
                runRecord.goal(),
                runRecord.constraints(),
                prd,
                design,
                artifactContent,
                previousFailure,
                language
        );
    }

    /**
     * IMPLEMENTATION 阶段只校验“当前实现是否满足已冻结 contract”，
     * 不再根据 single html / externalized script 这类交付形态直接越权打回 DESIGN。
     * 只要 DESIGN 已经把 contract 冻结清楚，当前阶段就只能补齐实现。
     */
    private ReviewResult enforceImplementationContractGate(Path projectPath, RunRecord runRecord) {
        String prd = reviewArtifactLoader.readStageArtifact(runRecord, StageType.PRD);
        String design = reviewArtifactLoader.readStageArtifact(runRecord, StageType.DESIGN);
        ExecutionContract executionContract = contractExtractor.extractExecutionContract(
                runRecord.goal(),
                runRecord.constraints(),
                prd,
                design
        );
        ArchitectIntegrationCheckResult architectCheckResult = architectIntegrationCheck.verify(projectPath, executionContract);
        if (architectCheckResult.passed()) {
            return null;
        }
        ReviewReasonCode reasonCode = mapImplementationReasonCode(architectCheckResult);
        String summary = buildImplementationGateSummary(architectCheckResult);
        String changeRequest = buildImplementationGateChangeRequest(architectCheckResult);
        return new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                summary,
                changeRequest,
                architectCheckResult.details(),
                "",
                architectCheckResult.implementationPatchTarget(),
                java.util.List.of(),
                ReviewRevisionRoute.PATCH_CURRENT_STAGE,
                reasonCode
        );
    }

    private String stripProcessNoteSection(String content) {
        return StructuredArtifactBlocks.stripAllKnownBlocks(content).trim();
    }

    private ReviewResult enforceStructuredTestCoverageGate(String artifactContent, ReviewResult parsed) {
        if (parsed == null || parsed.decision() != ReviewDecision.APPROVED) {
            return parsed;
        }
        QualityLedger qualityLedger = StructuredArtifactBlocks.readFirstJsonBlock(
                artifactContent,
                ArtifactBlockKind.QUALITY_LEDGER,
                QualityLedger.class
        );
        CoverageLedger coverageLedger = qualityLedger == null ? null : qualityLedger.coverageLedger();
        if (coverageLedger == null || !coverageLedger.hasMissingRequiredCoverage()) {
            return parsed;
        }
        String missingSurfaces = coverageLedger.missingRequiredCapabilityIds().stream()
                .sorted()
                .collect(Collectors.joining(", "));
        String evidence = missingSurfaces.isBlank()
                ? "quality-ledger reports missing required coverage"
                : "missingRequiredCoverage=" + missingSurfaces;
        return new ReviewResult(
                ReviewDecision.REJECTED,
                FixMode.PATCH,
                "测试阶段仍缺少必需能力的通过证据。",
                "请补齐缺失的体验能力覆盖并重新执行测试。",
                evidence,
                "",
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                java.util.List.of(),
                ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET,
                ReviewReasonCode.IMPLEMENTATION_GAP
        );
    }

    private ReviewReasonCode mapImplementationReasonCode(ArchitectIntegrationCheckResult architectCheckResult) {
        if (architectCheckResult == null || architectCheckResult.failureReason() == null) {
            return ReviewReasonCode.NONE;
        }
        if (architectCheckResult.failureReason() == ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID
                && architectCheckResult.implementationPatchTarget() == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            return ReviewReasonCode.RUNTIME_WIRING_GAP;
        }
        return ReviewReasonCode.IMPLEMENTATION_GAP;
    }

    private String buildImplementationGateSummary(ArchitectIntegrationCheckResult architectCheckResult) {
        if (architectCheckResult == null || architectCheckResult.failureReason() == null) {
            return "当前实现未满足 approved contract。";
        }
        return switch (architectCheckResult.failureReason()) {
            case ENTRY_MISSING -> "当前实现缺少 approved contract 要求的可启动入口。";
            case RUNTIME_WIRING_INVALID -> "当前实现的入口接线或运行时所有权未满足 approved contract。";
            case SURFACE_MISSING -> "当前实现缺少 approved contract 要求的可见运行表面。";
            case IMPLEMENTATION_INCOMPLETE -> "当前实现仍未补齐 approved contract 要求的能力。";
        };
    }

    private String buildImplementationGateChangeRequest(ArchitectIntegrationCheckResult architectCheckResult) {
        if (architectCheckResult == null || architectCheckResult.failureReason() == null) {
            return "请在当前 IMPLEMENTATION 阶段继续补齐实现，并保持 approved contract 不变。";
        }
        return switch (architectCheckResult.failureReason()) {
            case ENTRY_MISSING ->
                    "请在当前 IMPLEMENTATION 阶段补齐可启动入口，不要回退或重写已批准的设计契约。";
            case RUNTIME_WIRING_INVALID ->
                    "请在当前 IMPLEMENTATION 阶段修复入口接线与运行时所有权，使交付结果满足 approved contract。";
            case SURFACE_MISSING ->
                    "请在当前 IMPLEMENTATION 阶段补齐可见运行表面，并保持 approved contract 不变。";
            case IMPLEMENTATION_INCOMPLETE ->
                    "请在当前 IMPLEMENTATION 阶段补齐缺失实现，不要把当前缺口回退成设计问题。";
        };
    }
}
