package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.testing.TestExecutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ContractExtractor;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.supervisor.SupervisorAgent;

public final class ImplementationExecutorTestSupport {

    private ImplementationExecutorTestSupport() {
    }

    public static ImplementationExecutor create(
            LlmProvider llmProvider,
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper,
            TestExecutor testExecutor
    ) {
        return create(
                llmProvider,
                workspace,
                objectMapper,
                testExecutor,
                new TreeSitterSupport(),
                null,
                new ContractExtractor(),
                null,
                null
        );
    }

    public static ImplementationExecutor create(
            LlmProvider llmProvider,
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper,
            TestExecutor testExecutor,
            TreeSitterSupport treeSitterSupport,
            SupervisorAgent supervisorAgent,
            ContractExtractor contractExtractor,
            EventLogStore eventLogStore,
            FileArtifactStore fileArtifactStore
    ) {
        return ImplementationExecutorWiring.create(
                llmProvider,
                workspace,
                objectMapper,
                testExecutor,
                treeSitterSupport,
                supervisorAgent,
                contractExtractor,
                eventLogStore,
                fileArtifactStore,
                TestExecutorServices.directExecutorService()
        );
    }
}
