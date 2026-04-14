package devflow.agent.supervisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.executor.llm.StructuredPayloadReader;
import devflow.agent.orchestrator.StageFlowPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SupervisorConfiguration {

    @Bean
    SupervisorPayloadNormalizer supervisorPayloadNormalizer() {
        return new SupervisorPayloadNormalizer();
    }

    @Bean
    SupervisorArtifactRenderer supervisorArtifactRenderer() {
        return new SupervisorArtifactRenderer();
    }

    @Bean
    SupervisorStageFallbackSupport supervisorStageFallbackSupport(StageFlowPolicy stageFlowPolicy) {
        return new SupervisorStageFallbackSupport(stageFlowPolicy);
    }

    @Bean
    SupervisorGenerationRecoverySupport supervisorGenerationRecoverySupport() {
        return new SupervisorGenerationRecoverySupport();
    }

    @Bean
    SupervisorFallbackPolicy supervisorFallbackPolicy(
            SupervisorStageFallbackSupport supervisorStageFallbackSupport,
            SupervisorGenerationRecoverySupport supervisorGenerationRecoverySupport
    ) {
        return new SupervisorFallbackPolicy(
                supervisorStageFallbackSupport,
                supervisorGenerationRecoverySupport
        );
    }

    @Bean
    SupervisorPromptAssembler supervisorPromptAssembler(SupervisorArtifactRenderer supervisorArtifactRenderer) {
        return new SupervisorPromptAssembler(supervisorArtifactRenderer);
    }

    @Bean
    StructuredPayloadReader supervisorStructuredPayloadReader(ObjectMapper objectMapper) {
        return new StructuredPayloadReader(objectMapper);
    }

    @Bean
    SupervisorDecisionSanitizer supervisorDecisionSanitizer(
            StageFlowPolicy stageFlowPolicy,
            SupervisorPayloadNormalizer supervisorPayloadNormalizer,
            SupervisorFallbackPolicy supervisorFallbackPolicy
    ) {
        return new SupervisorDecisionSanitizer(
                stageFlowPolicy,
                supervisorPayloadNormalizer,
                supervisorFallbackPolicy
        );
    }
}
