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
                new ProjectInspector(workspace),
                new ValidationStrategyPlanner(llmProvider, objectMapper),
                new ValidationExecutor(workspace, defaultPlaywrightExecutionPolicy()),
                planner(workspace, llmProvider, objectMapper, defaultTestPlanningPolicy()),
                new TestToolSelector(),
                new TestRunner(new PlaywrightCaseExecutor(workspace, objectMapper, defaultPlaywrightExecutionPolicy())),
                new TestEvidenceCollector(),
                new TestArtifactRenderer(),
                new ContractExtractor(),
                new ArchitectIntegrationCheck(workspace, new TreeSitterSupport()),
                new TestEvidenceGate(),
                new CoverageLedgerBuilder(),
                new ExperienceFailureDispositionResolver(defaultTestPlanningPolicy()),
                new LanguagePolicy()
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
