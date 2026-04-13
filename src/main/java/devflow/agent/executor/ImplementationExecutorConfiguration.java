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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ImplementationExecutorConfiguration {

    @Bean(destroyMethod = "close")
    public ImplementationExecutor implementationExecutor(
            LlmProvider llmProvider,
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper,
            TestExecutor testExecutor,
            TreeSitterSupport treeSitterSupport,
            ObjectProvider<SupervisorAgent> supervisorAgentProvider,
            ObjectProvider<ContractExtractor> contractExtractorProvider,
            ObjectProvider<EventLogStore> eventLogStoreProvider,
            ObjectProvider<FileArtifactStore> fileArtifactStoreProvider
    ) {
        ExecutorService toolExecutor = Executors.newVirtualThreadPerTaskExecutor();
        return ImplementationExecutorWiring.create(
                llmProvider,
                workspace,
                objectMapper,
                testExecutor,
                treeSitterSupport,
                supervisorAgentProvider.getIfAvailable(),
                contractExtractorProvider.getIfAvailable(),
                eventLogStoreProvider.getIfAvailable(),
                fileArtifactStoreProvider.getIfAvailable(),
                toolExecutor
        );
    }
}
