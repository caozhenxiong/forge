package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.implementation.ImplementationExecutionPolicy;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.testing.TestExecutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ContractExtractor;
import devflow.agent.executor.editing.RuntimeWorkingSetPolicy;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.QualityPlanFactory;
import devflow.agent.supervisor.SupervisorAgent;
import devflow.agent.executor.subtask.SubtaskReviewPolicy;
import devflow.agent.executor.tools.ImplementationToolPermissionProperties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
            SupervisorAgent supervisorAgent,
            ContractExtractor contractExtractor,
            EventLogStore eventLogStore,
            FileArtifactStore fileArtifactStore,
            LanguagePolicy languagePolicy,
            ImplementationExecutionPolicy implementationExecutionPolicy,
            RuntimeWorkingSetPolicy runtimeWorkingSetPolicy,
            SubtaskReviewPolicy subtaskReviewPolicy,
            ImplementationToolPermissionProperties implementationToolPermissionProperties,
            QualityPlanFactory qualityPlanFactory
    ) {
        ExecutorService toolExecutor = Executors.newVirtualThreadPerTaskExecutor();
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
                toolExecutor,
                languagePolicy,
                implementationExecutionPolicy,
                runtimeWorkingSetPolicy,
                subtaskReviewPolicy,
                implementationToolPermissionProperties,
                qualityPlanFactory
        );
    }
}
