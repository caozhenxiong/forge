package devflow.agent.executor.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractExtractor;
import devflow.agent.executor.gate.ArchitectIntegrationCheck;
import devflow.agent.executor.gate.TestEvidenceGate;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.testsupport.QualityPlanFactoryTestSupport;
import devflow.agent.validation.ProjectInspector;
import devflow.agent.validation.ValidationExecutor;
import devflow.agent.validation.ValidationStrategyPlanner;

public class TestExecutorHarness extends TestExecutor {

    public TestExecutorHarness(
            FileProjectWorkspace workspace,
            LlmProvider llmProvider,
            ObjectMapper objectMapper
    ) {
        super(
                planningComponents(workspace, llmProvider, objectMapper),
                runComponents(workspace, objectMapper),
                evidenceComponents(),
                new ContractExtractor(),
                new LanguagePolicy()
        );
    }

    private static TestPlanningComponents planningComponents(
            FileProjectWorkspace workspace,
            LlmProvider llmProvider,
            ObjectMapper objectMapper
    ) {
        TestPlanningPolicy testPlanningPolicy = defaultTestPlanningPolicy();
        return new TestPlanningComponents(
                new ProjectInspector(workspace),
                new ValidationStrategyPlanner(llmProvider, objectMapper),
                planner(workspace, llmProvider, objectMapper, testPlanningPolicy),
                new TestToolSelector()
        );
    }

    private static TestRunComponents runComponents(
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper
    ) {
        PlaywrightExecutionPolicy playwrightExecutionPolicy = defaultPlaywrightExecutionPolicy();
        return new TestRunComponents(
                new ValidationExecutor(workspace, playwrightExecutionPolicy),
                new TestRunner(new PlaywrightCaseExecutor(workspace, objectMapper, playwrightExecutionPolicy)),
                new ArchitectIntegrationCheck(workspace, new TreeSitterSupport())
        );
    }

    private static TestEvidenceComponents evidenceComponents() {
        TestPlanningPolicy testPlanningPolicy = defaultTestPlanningPolicy();
        return new TestEvidenceComponents(
                new TestEvidenceCollector(),
                new TestEvidenceGate(),
                new CoverageLedgerBuilder(),
                new ExperienceFailureDispositionResolver(testPlanningPolicy),
                new TestArtifactRenderer()
        );
    }

    private static TestCasePlanner planner(
            FileProjectWorkspace workspace,
            LlmProvider llmProvider,
            ObjectMapper objectMapper,
            TestPlanningPolicy testPlanningPolicy
    ) {
        ContractExtractor contractExtractor = new ContractExtractor();
        TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
        LanguagePolicy languagePolicy = new LanguagePolicy();
        return new TestCasePlanner(
                workspace,
                llmProvider,
                objectMapper,
                treeSitterSupport,
                contractExtractor,
                languagePolicy,
                testPlanningPolicy,
                QualityPlanFactoryTestSupport.qualityPlanFactory(workspace, treeSitterSupport)
        );
    }

    private static TestPlanningPolicy defaultTestPlanningPolicy() {
        return new TestPlanningPolicy();
    }

    private static PlaywrightExecutionPolicy defaultPlaywrightExecutionPolicy() {
        return new PlaywrightExecutionPolicy();
    }
}
