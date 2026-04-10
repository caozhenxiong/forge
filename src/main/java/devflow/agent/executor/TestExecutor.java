package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContractView;
import devflow.agent.context.ExecutionContract;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.QualityLedger;
import devflow.agent.validation.ProjectFingerprint;
import devflow.agent.validation.ProjectInspector;
import devflow.agent.validation.ValidationExecutionReport;
import devflow.agent.validation.ValidationExecutor;
import devflow.agent.validation.ValidationPlan;
import devflow.agent.validation.ValidationStrategyPlanner;
import java.nio.file.Path;
import java.util.ArrayList;
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
        TestRunReport testRunReport = testRunner.executeDetailed(normalized, testCasePlan, toolSelection);
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

        String testCasesMarkdown = testArtifactRenderer.renderTestCases(testCasePlan, language);
        String executionMarkdown = testArtifactRenderer.renderExecution(collectedEvidence, language);
        String reportMarkdown = testArtifactRenderer.renderReport(note, collectedEvidence, evidenceOutcome, language);
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

}
