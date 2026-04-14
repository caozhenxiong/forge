package devflow.agent.executor.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractExtractor;
import devflow.agent.executor.gate.ArchitectIntegrationCheck;
import devflow.agent.executor.gate.TestEvidenceGate;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectInspector;
import devflow.agent.validation.ValidationExecutor;
import devflow.agent.validation.ValidationStrategyPlanner;

public final class TestExecutorTestSupport {

    private TestExecutorTestSupport() {
    }

    public static TestExecutor create(
            FileProjectWorkspace workspace,
            LlmProvider llmProvider,
            ObjectMapper objectMapper
    ) {
        return new TestExecutorHarness(workspace, llmProvider, objectMapper);
    }

}
