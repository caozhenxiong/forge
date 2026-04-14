package devflow.agent.quality;

import devflow.agent.context.ContractView;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.executor.runtime.RuntimeSnapshot;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.Collection;

/**
 * 统一构建当前轮质量计划。
 *
 * <p>这样 quality 主线的输入汇聚在一个地方，避免 planner / reviewer / tester 各自拼装后继续分叉。
 */
public final class QualityPlanFactory {

    private final QualityRulesLoader qualityRulesLoader;
    private final FeatureProfiler featureProfiler;
    private final QualityIntentResolver qualityIntentResolver;
    private final QualityPolicyResolver qualityPolicyResolver;
    private final HtmlStructureRuntimeSignalResolver htmlStructureRuntimeSignalResolver;

    public QualityPlanFactory(
            QualityRulesLoader qualityRulesLoader,
            FeatureProfiler featureProfiler,
            QualityIntentResolver qualityIntentResolver,
            QualityPolicyResolver qualityPolicyResolver,
            HtmlStructureRuntimeSignalResolver htmlStructureRuntimeSignalResolver
    ) {
        this.qualityRulesLoader = qualityRulesLoader;
        this.featureProfiler = featureProfiler;
        this.qualityIntentResolver = qualityIntentResolver;
        this.qualityPolicyResolver = qualityPolicyResolver;
        this.htmlStructureRuntimeSignalResolver = htmlStructureRuntimeSignalResolver;
    }

    public QualityPlan build(
            Path projectPath,
            ProjectFingerprint fingerprint,
            ContractView contractView,
            ValidationMetadata validationMetadata,
            RuntimeSnapshot runtimeSnapshot,
            Collection<String> requiredCapabilitySurfaces
    ) {
        QualityRules rules = qualityRulesLoader.load(projectPath);
        RuntimeSnapshot normalizedRuntimeSnapshot = htmlStructureRuntimeSignalResolver.resolve(
                projectPath,
                fingerprint,
                runtimeSnapshot
        );
        return qualityPolicyResolver.resolve(
                rules,
                featureProfiler.profile(fingerprint, contractView, validationMetadata, normalizedRuntimeSnapshot),
                qualityIntentResolver.resolve(rules, contractView, validationMetadata, requiredCapabilitySurfaces),
                contractView,
                validationMetadata
        );
    }

    public QualityPlan build(
            ProjectFingerprint fingerprint,
            ContractView contractView,
            ValidationMetadata validationMetadata,
            RuntimeSnapshot runtimeSnapshot,
            Collection<String> requiredCapabilitySurfaces
    ) {
        return build(null, fingerprint, contractView, validationMetadata, runtimeSnapshot, requiredCapabilitySurfaces);
    }
}
