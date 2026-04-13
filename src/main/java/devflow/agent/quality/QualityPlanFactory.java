package devflow.agent.quality;

import devflow.agent.context.ContractView;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.executor.testing.RuntimeSnapshot;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.Collection;

/**
 * 统一构建当前轮质量计划。
 *
 * <p>这样 quality 主线的输入汇聚在一个地方，避免 planner / reviewer / tester 各自拼装后继续分叉。
 */
public final class QualityPlanFactory {

    private final QualityRulesLoader qualityRulesLoader = new QualityRulesLoader();
    private final FeatureProfiler featureProfiler = new FeatureProfiler();
    private final QualityIntentResolver qualityIntentResolver = new QualityIntentResolver();
    private final QualityPolicyResolver qualityPolicyResolver = new QualityPolicyResolver();
    private final HtmlStructureRuntimeSignalResolver htmlStructureRuntimeSignalResolver =
            new HtmlStructureRuntimeSignalResolver();

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
