package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContractView;
import devflow.agent.context.ExecutionContract;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.CapabilityMatrix;
import devflow.agent.quality.CapabilityMatrixEntry;
import devflow.agent.quality.CapabilitySurface;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.QualityPlan;
import devflow.agent.quality.QualityLedger;
import devflow.agent.validation.ProjectFingerprint;
import devflow.agent.validation.ProjectInspector;
import devflow.agent.validation.ValidationExecutionReport;
import devflow.agent.validation.ValidationExecutor;
import devflow.agent.validation.ValidationPlan;
import devflow.agent.validation.ValidationStrategyPlanner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
/**
 * 测试阶段门面编排器。
 *
 * <p>它负责串联：
 * 1. 自检与整体可运行检查；
 * 2. testcase 规划；
 * 3. 测试工具选择与执行；
 * 4. 测试证据 gate 与产物装配。
 *
 * <p>它不再负责：
 * 1. 直接决定选哪个测试工具；
 * 2. 内联渲染所有 markdown 文本；
 * 3. 解释测试阶段之后的流程跳转。
 */
public class TestExecutor {

    private final ProjectInspector projectInspector;
    private final ValidationStrategyPlanner strategyPlanner;
    private final ValidationExecutor validationExecutor;
    private final TestCasePlanner testCasePlanner;
    private final TestToolSelector testToolSelector;
    private final TestRunner testRunner;
    private final TestEvidenceCollector testEvidenceCollector;
    private final TestArtifactRenderer testArtifactRenderer;
    private final ContractExtractor contractExtractor;
    private final ArchitectIntegrationCheck architectIntegrationCheck;
    private final TestEvidenceGate testEvidenceGate;
    private final CoverageLedgerBuilder coverageLedgerBuilder;
    private final ExperienceFailureDispositionResolver experienceFailureDispositionResolver;
    private final Map<Path, ValidationPlan> cachedPlans = new ConcurrentHashMap<>();

    public TestExecutor(FileProjectWorkspace workspace, LlmProvider llmProvider, ObjectMapper objectMapper) {
        TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
        this.projectInspector = new ProjectInspector(workspace);
        this.strategyPlanner = new ValidationStrategyPlanner(llmProvider, objectMapper);
        this.validationExecutor = new ValidationExecutor(workspace);
        this.testCasePlanner = new TestCasePlanner(workspace, llmProvider, objectMapper, treeSitterSupport, new ContractExtractor());
        PlaywrightCaseExecutor playwrightCaseExecutor = new PlaywrightCaseExecutor(workspace, objectMapper);
        this.testToolSelector = new TestToolSelector();
        this.testRunner = new TestRunner(playwrightCaseExecutor);
        this.testEvidenceCollector = new TestEvidenceCollector();
        this.testArtifactRenderer = new TestArtifactRenderer();
        this.contractExtractor = new ContractExtractor();
        this.architectIntegrationCheck = new ArchitectIntegrationCheck(workspace, treeSitterSupport);
        this.testEvidenceGate = new TestEvidenceGate();
        this.coverageLedgerBuilder = new CoverageLedgerBuilder();
        this.experienceFailureDispositionResolver = new ExperienceFailureDispositionResolver();
    }

    public TestExecutionBundle execute(
            Path projectPath,
            String goal,
            String constraints,
            String prd,
            String design,
            String implementationReport,
            String note
    ) {
        DocumentLanguage language = DocumentLanguage.detect(goal, constraints, note);
        Path normalized = projectPath.toAbsolutePath().normalize();
        ProjectFingerprint fingerprint = projectInspector.inspect(normalized);
        ContractView contractView = contractExtractor.extractContractView(goal, constraints, prd, design);
        ExecutionContract executionContract = contractView.executionContract();
        ValidationExecutionReport selfCheckReport = selfCheckDetailed(normalized, fingerprint);
        SelfCheckResult selfCheck = selfCheckReport.selfCheckResult();
        ArchitectIntegrationCheckResult architectCheck = architectIntegrationCheck.verify(normalized, executionContract);
        TestToolSelection toolSelection = testToolSelector.select(fingerprint, executionContract, architectCheck);
        RuntimeSnapshotCaptureResult runtimeSnapshotCapture = testRunner.captureRuntimeSnapshotDetailed(normalized, toolSelection);
        RuntimeSnapshot runtimeSnapshot = runtimeSnapshotCapture.runtimeSnapshot();
        TestCasePlan testCasePlan = testCasePlanner.plan(normalized, fingerprint, goal, constraints, prd, design, implementationReport, runtimeSnapshot);
        TestRunReport testRunReport = !toolSelection.executable()
                ? testRunner.executeDetailed(normalized, testCasePlan, toolSelection)
                : testCasePlan.uiRuntimeContractValidation().valid()
                ? testRunner.executeDetailed(normalized, testCasePlan, toolSelection)
                : testRunner.blockedByValidation(testCasePlan, testCasePlan.uiRuntimeContractValidation());
        CoverageLedger coverageLedger = coverageLedgerBuilder.build(testCasePlan, testRunReport.caseResults());
        QualityLedger qualityLedger = new QualityLedger(
                testCasePlan.qualityPlan().structureRiskReport(),
                testCasePlan.qualityPlan().capabilityMatrix(),
                coverageLedger
        );
        CollectedTestEvidence collectedEvidence = testEvidenceCollector.collect(
                selfCheck,
                architectCheck,
                runtimeSnapshot,
                testRunReport.caseResults(),
                mergeToolResults(
                        selfCheckReport.toolResults(),
                        runtimeSnapshotCapture.toolResult() == null ? List.of() : List.of(runtimeSnapshotCapture.toolResult()),
                        testRunReport.toolResults()
                ),
                coverageLedger,
                qualityLedger
        );
        TestEvidenceGateOutcome evidenceOutcome = testEvidenceGate.evaluateDetailed(
                new TestEvidenceGateInput(
                        collectedEvidence.selfCheck(),
                        collectedEvidence.architectCheck(),
                        collectedEvidence.caseResults(),
                        collectedEvidence.coverageLedger()
                ),
                language
        );
        ExperienceFailureDisposition disposition = experienceFailureDispositionResolver.resolve(
                testCasePlan.uiRuntimeContract(),
                testCasePlan.uiRuntimeContractValidation(),
                testCasePlan.cases(),
                collectedEvidence.caseResults(),
                collectedEvidence.coverageLedger()
        );

        String testCasesMarkdown = testArtifactRenderer.renderTestCases(testCasePlan, language);
        String executionMarkdown = testArtifactRenderer.renderExecution(
                collectedEvidence,
                testCasePlan.uiRuntimeContract(),
                disposition,
                language
        );
        String reportMarkdown = testArtifactRenderer.renderReport(
                note,
                collectedEvidence,
                evidenceOutcome,
                testCasePlan.uiRuntimeContract(),
                disposition,
                language
        );
        return new TestExecutionBundle(
                testCasesMarkdown,
                collectedEvidence.runtimeSnapshot() == null
                        ? language.choose("# 运行时快照\n\n- status: unavailable", "# Runtime Snapshot\n\n- status: unavailable")
                        : collectedEvidence.runtimeSnapshot().toMarkdown(language),
                executionMarkdown,
                reportMarkdown
        );
    }

    public SelfCheckResult selfCheck(Path projectPath) {
        Path normalized = projectPath.toAbsolutePath().normalize();
        ProjectFingerprint fingerprint = projectInspector.inspect(normalized);
        return selfCheckDetailed(normalized, fingerprint).selfCheckResult();
    }

    ValidationExecutionReport selfCheckDetailed(Path projectPath) {
        Path normalized = projectPath.toAbsolutePath().normalize();
        ProjectFingerprint fingerprint = projectInspector.inspect(normalized);
        return selfCheckDetailed(normalized, fingerprint);
    }

    SubtaskVerificationOutcome verifyImplementationSubtask(
            Path projectPath,
            Subtask subtask,
            ContractView contractView,
            QualityPlan qualityPlan,
            ProjectFingerprint fingerprint,
            boolean finalSubtask,
            DocumentLanguage language
    ) {
        if (!shouldRunImplementationFunctionalVerification(subtask, qualityPlan, fingerprint, finalSubtask)) {
            return null;
        }
        ExecutionContract executionContract = contractView == null ? null : contractView.executionContract();
        TestToolSelection toolSelection = testToolSelector.select(
                fingerprint,
                executionContract,
                ArchitectIntegrationCheckResult.success()
        );
        if (!toolSelection.executable()) {
            return null;
        }
        RuntimeSnapshot runtimeSnapshot = testRunner.captureRuntimeSnapshotDetailed(projectPath, toolSelection).runtimeSnapshot();
        QualityPlan scopedQualityPlan = scopedImplementationVerificationPlan(qualityPlan, finalSubtask);
        TestCasePlan testCasePlan = testCasePlanner.planForImplementationVerification(
                projectPath,
                fingerprint,
                scopedQualityPlan,
                runtimeSnapshot,
                language
        );
        TestRunReport testRunReport = testCasePlan.uiRuntimeContractValidation().valid()
                ? testRunner.executeDetailed(projectPath, testCasePlan, toolSelection)
                : testRunner.blockedByValidation(testCasePlan, testCasePlan.uiRuntimeContractValidation());
        CoverageLedger coverageLedger = coverageLedgerBuilder.build(testCasePlan, testRunReport.caseResults());
        ExperienceFailureDisposition disposition = experienceFailureDispositionResolver.resolve(
                testCasePlan.uiRuntimeContract(),
                testCasePlan.uiRuntimeContractValidation(),
                testCasePlan.cases(),
                testRunReport.caseResults(),
                coverageLedger
        );
        if (disposition.passed()
                || (disposition.implementationPatchTarget() == devflow.agent.review.ImplementationPatchTarget.NONE
                && disposition.overrideChanges().isEmpty())) {
            return null;
        }
        devflow.agent.review.ReviewResult review = new devflow.agent.review.ReviewResult(
                devflow.agent.review.ReviewDecision.REVISION_REQUIRED,
                devflow.agent.review.FixMode.PATCH,
                disposition.summary(),
                disposition.changeRequest(),
                disposition.evidence(),
                language.choose(
                        "先修复当前 active runtime 的功能缺口，再重新验证当前子任务。",
                        "Repair the active runtime functional gap and rerun verification for the current subtask."
                ),
                disposition.implementationPatchTarget(),
                disposition.overrideChanges(),
                disposition.revisionRoute(),
                disposition.reasonCode()
        );
        return SubtaskVerificationOutcome.of(review, SubtaskRevisionDirective.retry(disposition.overrideChanges()));
    }

    private ValidationExecutionReport selfCheckDetailed(Path projectPath, ProjectFingerprint fingerprint) {
        ValidationPlan plan = cachedPlans.computeIfAbsent(projectPath, ignored -> strategyPlanner.plan(fingerprint));
        return validationExecutor.executeDetailed(projectPath, fingerprint, plan);
    }

    private List<ToolResult> mergeToolResults(List<ToolResult>... groups) {
        List<ToolResult> merged = new ArrayList<>();
        if (groups == null || groups.length == 0) {
            return List.of();
        }
        for (List<ToolResult> group : groups) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            merged.addAll(group);
        }
        return List.copyOf(merged);
    }

    private boolean shouldRunImplementationFunctionalVerification(
            Subtask subtask,
            QualityPlan qualityPlan,
            ProjectFingerprint fingerprint,
            boolean finalSubtask
    ) {
        if (subtask == null
                || fingerprint == null
                || !fingerprint.hasResolvedHtmlEntry()
                || subtask.changes() == null
                || subtask.changes().isEmpty()) {
            return false;
        }
        if (!touchesHtmlRuntimeOwner(subtask, fingerprint)) {
            return false;
        }
        if (finalSubtask || subtask.runnableMilestone()) {
            return true;
        }
        if (qualityPlan == null) {
            return false;
        }
        return qualityPlan.featureProfile().hasDiscreteUserInput()
                || qualityPlan.featureProfile().hasTimedProgression()
                || qualityPlan.capabilityMatrix().requiredSurfaces().stream().anyMatch(surface ->
                surface == CapabilitySurface.PRIMARY_INTERACTION || surface.isExperienceSurface());
    }

    private boolean touchesHtmlRuntimeOwner(Subtask subtask, ProjectFingerprint fingerprint) {
        Path htmlEntryPath = Path.of(fingerprint.resolvedHtmlEntryPath()).normalize();
        Path htmlParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
        for (FileChange change : subtask.changes()) {
            if (change == null || change.path() == null || change.path().isBlank()) {
                continue;
            }
            Path relativePath = Path.of(change.path()).normalize();
            if (relativePath.equals(htmlEntryPath) || change.runtimeOwnership() != null) {
                return true;
            }
            if (devflow.agent.util.ProjectPathSupport.isRuntimeScript(relativePath)) {
                Path parent = relativePath.getParent() == null ? Path.of("") : relativePath.getParent().normalize();
                if (htmlParent.toString().isBlank() || parent.equals(htmlParent) || parent.startsWith(htmlParent)) {
                    return true;
                }
            }
        }
        return false;
    }

    private QualityPlan scopedImplementationVerificationPlan(QualityPlan qualityPlan, boolean finalSubtask) {
        if (qualityPlan == null || finalSubtask) {
            return qualityPlan == null ? QualityPlan.empty() : qualityPlan;
        }
        LinkedHashSet<CapabilitySurface> allowed = new LinkedHashSet<>();
        allowed.add(CapabilitySurface.PAGE_LOAD);
        allowed.add(CapabilitySurface.RUNTIME_STABILITY);
        allowed.add(CapabilitySurface.PRIMARY_VISUAL_SURFACE);
        if (qualityPlan.featureProfile().hasDiscreteUserInput()) {
            allowed.add(CapabilitySurface.PRIMARY_INTERACTION);
        }
        List<CapabilityMatrixEntry> scopedEntries = qualityPlan.capabilityMatrix().entries().stream()
                .filter(entry -> entry != null && entry.surface() != null && allowed.contains(entry.surface()))
                .toList();
        return new QualityPlan(
                qualityPlan.featureProfile(),
                qualityPlan.qualityIntent(),
                qualityPlan.structureRiskReport(),
                qualityPlan.structurePolicy(),
                qualityPlan.coveragePolicy(),
                qualityPlan.experiencePolicy(),
                new CapabilityMatrix(scopedEntries),
                qualityPlan.qualityChecklist()
        );
    }

}
