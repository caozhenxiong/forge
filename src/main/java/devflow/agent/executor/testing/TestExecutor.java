package devflow.agent.executor.testing;

import devflow.agent.executor.FileChange;
import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.tools.ToolResult;
import devflow.agent.executor.llm.LlmProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContractView;
import devflow.agent.context.ExecutionContract;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.CapabilityMatrix;
import devflow.agent.quality.CapabilityMatrixEntry;
import devflow.agent.quality.CapabilityIds;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.CoverageLedgerEntry;
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

import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskRevisionDirective;
import devflow.agent.executor.subtask.SubtaskVerificationOutcome;

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
    private final Map<ValidationPlanCacheKey, ValidationPlan> cachedPlans = new ConcurrentHashMap<>();

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
        TestExecutionSnapshot snapshot = buildExecutionSnapshot(
                projectPath,
                goal,
                constraints,
                prd,
                design,
                implementationReport,
                language
        );
        String testCasesMarkdown = testArtifactRenderer.renderTestCases(snapshot.testCasePlan(), language);
        String executionMarkdown = testArtifactRenderer.renderExecution(
                snapshot.collectedEvidence(),
                snapshot.testCasePlan().uiRuntimeContract(),
                snapshot.disposition(),
                language
        );
        String reportMarkdown = testArtifactRenderer.renderReport(
                note,
                snapshot.collectedEvidence(),
                snapshot.evidenceOutcome(),
                snapshot.testCasePlan().uiRuntimeContract(),
                snapshot.disposition(),
                language
        );
        return new TestExecutionBundle(
                testCasesMarkdown,
                snapshot.collectedEvidence().runtimeSnapshot() == null
                        ? language.choose("# 运行时快照\n\n- status: unavailable", "# Runtime Snapshot\n\n- status: unavailable")
                        : snapshot.collectedEvidence().runtimeSnapshot().toMarkdown(language),
                executionMarkdown,
                reportMarkdown
        );
    }

    public SelfCheckResult selfCheck(Path projectPath) {
        Path normalized = projectPath.toAbsolutePath().normalize();
        ProjectFingerprint fingerprint = projectInspector.inspect(normalized);
        return selfCheckDetailed(normalized, fingerprint).selfCheckResult();
    }

    public ValidationExecutionReport selfCheckDetailed(Path projectPath) {
        Path normalized = projectPath.toAbsolutePath().normalize();
        ProjectFingerprint fingerprint = projectInspector.inspect(normalized);
        return selfCheckDetailed(normalized, fingerprint);
    }

    public devflow.agent.review.ReviewResult verifyImplementationRepairTargets(
            Path projectPath,
            String goal,
            String constraints,
            String prd,
            String design,
            String implementationReport,
            ExperienceFailureDisposition previousFailure,
            DocumentLanguage language
    ) {
        if (previousFailure == null || !previousFailure.requiresImplementationReverification()) {
            return null;
        }
        DocumentLanguage resolvedLanguage = language == null
                ? DocumentLanguage.detect(goal, constraints, implementationReport)
                : language;
        TestExecutionSnapshot snapshot = buildExecutionSnapshot(
                projectPath,
                goal,
                constraints,
                prd,
                design,
                implementationReport,
                resolvedLanguage
        );
        ExperienceFailureDisposition currentFailure = snapshot.disposition();
        if (!currentFailure.passed() && !currentFailure.requiresImplementationReverification()) {
            return new devflow.agent.review.ReviewResult(
                    devflow.agent.review.ReviewDecision.REVISION_REQUIRED,
                    devflow.agent.review.FixMode.PATCH,
                    "当前实现暂不能批准，因为针对性复核没有产出可用于实现放行的有效测试证据。",
                    "请先修复 TEST 侧验证缺陷或探针问题，再重新验证上一轮失败能力。",
                    currentFailure.evidence(),
                    resolvedLanguage.choose(
                            "1. 先让 TEST 侧重新产出有效的 targeted verification 结果。 2. 确认上一轮失败 case/capability 有明确通过证据后再进入 implementation review。",
                            "1. Restore valid targeted verification evidence from TEST. 2. Re-enter implementation review only after the previously failing case or capability has explicit passing evidence."
                    ),
                    devflow.agent.review.ImplementationPatchTarget.NONE,
                    List.of(),
                    devflow.agent.review.ReviewRevisionRoute.REQUEST_HUMAN,
                    currentFailure.reasonCode()
            );
        }
        List<String> unresolvedCases = unresolvedTargetCases(previousFailure, snapshot.collectedEvidence().caseResults());
        List<String> unresolvedSurfaces = unresolvedTargetSurfaces(previousFailure, snapshot.collectedEvidence().coverageLedger());
        if (unresolvedCases.isEmpty() && unresolvedSurfaces.isEmpty() && currentFailure.passed()) {
            return null;
        }
        devflow.agent.review.ImplementationPatchTarget patchTarget = effectivePatchTarget(previousFailure, currentFailure);
        List<FileChange> overrideChanges = !currentFailure.overrideChanges().isEmpty()
                ? currentFailure.overrideChanges()
                : previousFailure.overrideChanges();
        return new devflow.agent.review.ReviewResult(
                devflow.agent.review.ReviewDecision.REVISION_REQUIRED,
                devflow.agent.review.FixMode.PATCH,
                "当前实现尚未用针对性复核关闭上一轮 TEST 失败项。",
                buildTargetedVerificationChangeRequest(previousFailure, unresolvedCases, unresolvedSurfaces, currentFailure),
                buildTargetedVerificationEvidence(previousFailure, unresolvedCases, unresolvedSurfaces, currentFailure),
                resolvedLanguage.choose(
                        "1. 先修复上一轮失败 case/capability 对应的实现缺口。 2. 重新执行针对性验证，确认这些目标全部通过。 3. 只有当前失败项闭环后才能批准 implementation。",
                        "1. Fix the implementation gap behind the previously failing case or capability. 2. Rerun targeted verification until those targets pass. 3. Approve implementation only after the active failure targets are closed."
                ),
                patchTarget,
                overrideChanges,
                currentFailure.passed() ? previousFailure.revisionRoute() : currentFailure.revisionRoute(),
                currentFailure.passed() ? previousFailure.reasonCode() : currentFailure.reasonCode()
        );
    }

    public SubtaskVerificationOutcome verifyImplementationSubtask(
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
        return toImplementationVerificationOutcome(disposition, language);
    }

    SubtaskVerificationOutcome toImplementationVerificationOutcome(
            ExperienceFailureDisposition disposition,
            DocumentLanguage language
    ) {
        if (disposition == null || disposition.passed()) {
            return null;
        }
        if (!disposition.requiresImplementationReverification()) {
            devflow.agent.review.ReviewResult review = new devflow.agent.review.ReviewResult(
                    devflow.agent.review.ReviewDecision.REVISION_REQUIRED,
                    devflow.agent.review.FixMode.PATCH,
                    "当前实现暂不能批准，因为针对性复核没有产出可用于实现放行的有效测试证据。",
                    "请先修复 TEST 侧验证缺陷或探针问题，再重新验证当前子任务。",
                    disposition.evidence(),
                    language.choose(
                            "1. 先让 TEST 侧重新产出有效的 targeted verification 结果。 2. 只有确认失败 case/capability 有明确通过证据后，才能继续 implementation 自动续跑。",
                            "1. Restore valid targeted verification evidence from TEST first. 2. Continue implementation automation only after the failing case or capability has explicit passing evidence."
                    ),
                    devflow.agent.review.ImplementationPatchTarget.NONE,
                    List.of(),
                    devflow.agent.review.ReviewRevisionRoute.REQUEST_HUMAN,
                    disposition.reasonCode()
            );
            return SubtaskVerificationOutcome.of(review, SubtaskRevisionDirective.empty());
        }
        if (disposition.implementationPatchTarget() == devflow.agent.review.ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
                && disposition.overrideChanges().isEmpty()) {
            devflow.agent.review.ReviewResult review = new devflow.agent.review.ReviewResult(
                    devflow.agent.review.ReviewDecision.REVISION_REQUIRED,
                    devflow.agent.review.FixMode.PATCH,
                    "当前实现需要继续 patch，但测试侧没有给出结构化文件范围。",
                    "请先明确本轮需要修补的实现文件范围，确认 owner 后再继续自动修复。",
                    disposition.evidence(),
                    language.choose(
                            "1. 先补齐结构化 overrideChanges。 2. 确认这些文件仍归当前 implementation 子任务负责。 3. 没有确定范围前不要继续自动续跑。",
                            "1. Provide structured overrideChanges first. 2. Confirm the files still belong to the current implementation subtask. 3. Do not continue automatic retry without a deterministic scope."
                    ),
                    devflow.agent.review.ImplementationPatchTarget.NONE,
                    List.of(),
                    devflow.agent.review.ReviewRevisionRoute.REQUEST_HUMAN,
                    disposition.reasonCode()
            );
            return SubtaskVerificationOutcome.of(review, SubtaskRevisionDirective.empty());
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
        return SubtaskVerificationOutcome.of(review);
    }

    private TestExecutionSnapshot buildExecutionSnapshot(
            Path projectPath,
            String goal,
            String constraints,
            String prd,
            String design,
            String implementationReport,
            DocumentLanguage language
    ) {
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
        return new TestExecutionSnapshot(testCasePlan, collectedEvidence, evidenceOutcome, disposition);
    }

    private ValidationExecutionReport selfCheckDetailed(Path projectPath, ProjectFingerprint fingerprint) {
        ValidationPlanCacheKey cacheKey = new ValidationPlanCacheKey(projectPath, fingerprint);
        cachedPlans.keySet().removeIf(existing -> existing.projectPath().equals(projectPath) && !existing.equals(cacheKey));
        ValidationPlan plan = cachedPlans.computeIfAbsent(cacheKey, ignored -> strategyPlanner.plan(fingerprint));
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
                || qualityPlan.capabilityMatrix().entries().stream().anyMatch(entry ->
                entry != null && entry.required() && (entry.requiresObservationTarget() || entry.requiresObservableStateChange()));
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
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        allowed.add(CapabilityIds.PAGE_LOAD);
        allowed.add(CapabilityIds.RUNTIME_STABILITY);
        allowed.add(CapabilityIds.PRIMARY_VISUAL_SURFACE);
        if (qualityPlan.featureProfile().hasDiscreteUserInput()) {
            allowed.add(CapabilityIds.PRIMARY_INTERACTION);
        }
        List<CapabilityMatrixEntry> scopedEntries = qualityPlan.capabilityMatrix().entries().stream()
                .filter(entry -> entry != null && !entry.capabilityId().isBlank() && allowed.contains(entry.capabilityId()))
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

    private List<String> unresolvedTargetCases(
            ExperienceFailureDisposition previousFailure,
            List<TestCaseResult> caseResults
    ) {
        if (previousFailure == null || previousFailure.failingCaseIds().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unresolved = new LinkedHashSet<>();
        for (String caseId : previousFailure.failingCaseIds()) {
            TestCaseResult result = findCaseResult(caseResults, caseId);
            if (result == null || result.status() != TestCaseStatus.PASSED) {
                unresolved.add(caseId);
            }
        }
        return List.copyOf(unresolved);
    }

    private List<String> unresolvedTargetSurfaces(
            ExperienceFailureDisposition previousFailure,
            CoverageLedger coverageLedger
    ) {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        if (previousFailure != null) {
            targets.addAll(previousFailure.failureCapabilitySurfaces());
            targets.addAll(previousFailure.requiredCapabilitySurfaces());
        }
        if (targets.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unresolved = new LinkedHashSet<>();
        for (String surface : targets) {
            if (surface == null || surface.isBlank()) {
                continue;
            }
            CoverageLedgerEntry entry = coverageEntryFor(coverageLedger, surface);
            if (entry == null || !entry.covered()) {
                unresolved.add(surface.trim());
            }
        }
        return List.copyOf(unresolved);
    }

    private CoverageLedgerEntry coverageEntryFor(CoverageLedger coverageLedger, String surfaceWireValue) {
        if (coverageLedger == null || surfaceWireValue == null || surfaceWireValue.isBlank()) {
            return null;
        }
        return coverageLedger.entries().stream()
                .filter(entry -> entry != null && !entry.capabilityId().isBlank())
                .filter(entry -> surfaceWireValue.equals(entry.capabilityId()))
                .findFirst()
                .orElse(null);
    }

    private record ValidationPlanCacheKey(Path projectPath, ProjectFingerprint fingerprint) {
    }

    private TestCaseResult findCaseResult(List<TestCaseResult> caseResults, String caseId) {
        if (caseResults == null || caseId == null || caseId.isBlank()) {
            return null;
        }
        return caseResults.stream()
                .filter(result -> result != null && caseId.equals(result.id()))
                .findFirst()
                .orElse(null);
    }

    private devflow.agent.review.ImplementationPatchTarget effectivePatchTarget(
            ExperienceFailureDisposition previousFailure,
            ExperienceFailureDisposition currentFailure
    ) {
        if (currentFailure != null && currentFailure.implementationPatchTarget().concretePatch()) {
            return currentFailure.implementationPatchTarget();
        }
        if (previousFailure != null && previousFailure.implementationPatchTarget().concretePatch()) {
            return previousFailure.implementationPatchTarget();
        }
        return devflow.agent.review.ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION;
    }

    private String buildTargetedVerificationChangeRequest(
            ExperienceFailureDisposition previousFailure,
            List<String> unresolvedCases,
            List<String> unresolvedSurfaces,
            ExperienceFailureDisposition currentFailure
    ) {
        List<String> parts = new ArrayList<>();
        if (!unresolvedCases.isEmpty()) {
            parts.add("未通过的目标用例: " + String.join(", ", unresolvedCases));
        }
        if (!unresolvedSurfaces.isEmpty()) {
            parts.add("未闭环的目标能力: " + String.join(", ", unresolvedSurfaces));
        }
        if (parts.isEmpty() && currentFailure != null && !currentFailure.changeRequest().isBlank()) {
            parts.add(currentFailure.changeRequest());
        }
        if (parts.isEmpty() && previousFailure != null && !previousFailure.changeRequest().isBlank()) {
            parts.add(previousFailure.changeRequest());
        }
        return parts.isEmpty()
                ? "请继续修复上一轮 TEST 失败项，并重新执行针对性复核。"
                : String.join("；", parts);
    }

    private String buildTargetedVerificationEvidence(
            ExperienceFailureDisposition previousFailure,
            List<String> unresolvedCases,
            List<String> unresolvedSurfaces,
            ExperienceFailureDisposition currentFailure
    ) {
        StringBuilder builder = new StringBuilder();
        if (previousFailure != null) {
            builder.append("previousKind=").append(previousFailure.kind());
            if (!previousFailure.failingCaseIds().isEmpty()) {
                builder.append(" | previousCases=").append(String.join(", ", previousFailure.failingCaseIds()));
            }
            if (!previousFailure.failureCapabilitySurfaces().isEmpty()) {
                builder.append(" | previousSurfaces=").append(String.join(", ", previousFailure.failureCapabilitySurfaces()));
            }
        }
        if (!unresolvedCases.isEmpty()) {
            builder.append("\ncurrentUnresolvedCases=").append(String.join(", ", unresolvedCases));
        }
        if (!unresolvedSurfaces.isEmpty()) {
            builder.append("\ncurrentUnresolvedSurfaces=").append(String.join(", ", unresolvedSurfaces));
        }
        if (currentFailure != null && !currentFailure.evidence().isBlank()) {
            builder.append("\n").append(currentFailure.evidence());
        }
        return builder.toString().trim();
    }

    private record TestExecutionSnapshot(
            TestCasePlan testCasePlan,
            CollectedTestEvidence collectedEvidence,
            TestEvidenceGateOutcome evidenceOutcome,
            ExperienceFailureDisposition disposition
    ) {
    }

}
