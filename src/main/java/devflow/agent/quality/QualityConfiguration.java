package devflow.agent.quality;

import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class QualityConfiguration {

    @Bean
    QualityRulesLoader qualityRulesLoader() {
        return new QualityRulesLoader();
    }

    @Bean
    FeatureProfiler featureProfiler() {
        return new FeatureProfiler();
    }

    @Bean
    QualityIntentResolver qualityIntentResolver() {
        return new QualityIntentResolver();
    }

    @Bean
    QualityPolicyResolver qualityPolicyResolver() {
        return new QualityPolicyResolver();
    }

    @Bean
    HtmlStructureRuntimeSignalResolver htmlStructureRuntimeSignalResolver(
            FileProjectWorkspace workspace,
            TreeSitterSupport treeSitterSupport
    ) {
        return new HtmlStructureRuntimeSignalResolver(workspace, treeSitterSupport);
    }

    @Bean
    QualityPlanFactory qualityPlanFactory(
            QualityRulesLoader qualityRulesLoader,
            FeatureProfiler featureProfiler,
            QualityIntentResolver qualityIntentResolver,
            QualityPolicyResolver qualityPolicyResolver,
            HtmlStructureRuntimeSignalResolver htmlStructureRuntimeSignalResolver
    ) {
        return new QualityPlanFactory(
                qualityRulesLoader,
                featureProfiler,
                qualityIntentResolver,
                qualityPolicyResolver,
                htmlStructureRuntimeSignalResolver
        );
    }
}
