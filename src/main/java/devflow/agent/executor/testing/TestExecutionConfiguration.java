package devflow.agent.executor.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractExtractor;
import devflow.agent.executor.gate.ArchitectIntegrationCheck;
import devflow.agent.executor.gate.TestEvidenceGate;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.QualityPlanFactory;
import devflow.agent.validation.ProjectInspector;
import devflow.agent.validation.ValidationExecutor;
import devflow.agent.validation.ValidationStrategyPlanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TestExecutionConfiguration {

    @Bean
    ProjectInspector projectInspector(FileProjectWorkspace workspace) {
        return new ProjectInspector(workspace);
    }

    @Bean
    ValidationStrategyPlanner validationStrategyPlanner(LlmProvider llmProvider, ObjectMapper objectMapper) {
        return new ValidationStrategyPlanner(llmProvider, objectMapper);
    }

    @Bean
    ValidationExecutor validationExecutor(
            FileProjectWorkspace workspace,
            PlaywrightExecutionPolicy playwrightExecutionPolicy
    ) {
        return new ValidationExecutor(workspace, playwrightExecutionPolicy);
    }

    @Bean
    TestCasePlanner testCasePlanner(
            FileProjectWorkspace workspace,
            LlmProvider llmProvider,
            ObjectMapper objectMapper,
            TreeSitterSupport treeSitterSupport,
            ContractExtractor contractExtractor,
            LanguagePolicy languagePolicy,
            TestPlanningPolicy testPlanningPolicy,
            QualityPlanFactory qualityPlanFactory
    ) {
        return new TestCasePlanner(
                workspace,
                llmProvider,
                objectMapper,
                treeSitterSupport,
                contractExtractor,
                languagePolicy,
                testPlanningPolicy,
                qualityPlanFactory
        );
    }

    @Bean
    PlaywrightCaseExecutor playwrightCaseExecutor(
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper,
            PlaywrightExecutionPolicy playwrightExecutionPolicy
    ) {
        return new PlaywrightCaseExecutor(workspace, objectMapper, playwrightExecutionPolicy);
    }

    @Bean
    TestToolSelector testToolSelector() {
        return new TestToolSelector();
    }

    @Bean
    TestRunner testRunner(PlaywrightCaseExecutor playwrightCaseExecutor) {
        return new TestRunner(playwrightCaseExecutor);
    }

    @Bean
    TestEvidenceCollector testEvidenceCollector() {
        return new TestEvidenceCollector();
    }

    @Bean
    TestArtifactRenderer testArtifactRenderer() {
        return new TestArtifactRenderer();
    }

    @Bean
    ArchitectIntegrationCheck architectIntegrationCheck(
            FileProjectWorkspace workspace,
            TreeSitterSupport treeSitterSupport
    ) {
        return new ArchitectIntegrationCheck(workspace, treeSitterSupport);
    }

    @Bean
    TestEvidenceGate testEvidenceGate() {
        return new TestEvidenceGate();
    }

    @Bean
    CoverageLedgerBuilder coverageLedgerBuilder() {
        return new CoverageLedgerBuilder();
    }

    @Bean
    ExperienceFailureDispositionResolver experienceFailureDispositionResolver(TestPlanningPolicy testPlanningPolicy) {
        return new ExperienceFailureDispositionResolver(testPlanningPolicy);
    }
}
