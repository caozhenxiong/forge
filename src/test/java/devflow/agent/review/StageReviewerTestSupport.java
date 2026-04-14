package devflow.agent.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractExtractor;
import devflow.agent.executor.gate.ArchitectIntegrationCheck;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.testing.TestExecutor;
import devflow.agent.executor.testing.TestExecutorTestSupport;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.project.WorkspaceSnapshotStore;
import devflow.agent.prompt.PromptTemplateCatalog;

public final class StageReviewerTestSupport {

    private StageReviewerTestSupport() {
    }

    public static StageReviewer create(
            LlmProvider provider,
            WorkspaceSnapshotStore snapshotStore,
            TestExecutor testExecutor
    ) {
        return new StageReviewerHarness(provider, snapshotStore, testExecutor);
    }

    private static ContractExtractor contractExtractor() {
        return new ContractExtractor();
    }

    public static StageReviewer create(LlmProvider provider, TestExecutor testExecutor) {
        return create(
                provider,
                new WorkspaceSnapshotStore(new devflow.agent.orchestrator.FileRunRepository(), new FileProjectWorkspace()),
                testExecutor
        );
    }

    public static StageReviewer create(LlmProvider provider) {
        return create(
                provider,
                TestExecutorTestSupport.create(new FileProjectWorkspace(), provider, new ObjectMapper())
        );
    }
}
