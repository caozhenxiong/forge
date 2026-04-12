package devflow.agent.quality;

import devflow.agent.context.ContractView;
import devflow.agent.context.RequirementReference;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.executor.RuntimeSnapshot;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;

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
        Collection<String> effectiveRequiredCapabilitySurfaces = mergeRequiredCapabilitySurfaces(
                contractView,
                requiredCapabilitySurfaces
        );
        return qualityPolicyResolver.resolve(
                rules,
                featureProfiler.profile(fingerprint, contractView, validationMetadata, normalizedRuntimeSnapshot),
                qualityIntentResolver.resolve(rules, contractView, validationMetadata, effectiveRequiredCapabilitySurfaces),
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

    /**
     * product contract 里被标记为 planning-required 的 requirement refs，
     * 需要直接进入 quality required capability 集合。
     *
     * <p>否则 planning 已经要求覆盖的能力，到 test 阶段又会退化成“参考 prose”，
     * coverage ledger 无法稳定发现缺口。
     */
    private Collection<String> mergeRequiredCapabilitySurfaces(
            ContractView contractView,
            Collection<String> requiredCapabilitySurfaces
    ) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (requiredCapabilitySurfaces != null) {
            merged.addAll(requiredCapabilitySurfaces);
        }
        if (contractView != null && contractView.productContract() != null) {
            for (RequirementReference reference : contractView.productContract().planningCoverageRequirements()) {
                if (reference == null || reference.id() == null || reference.id().isBlank()) {
                    continue;
                }
                merged.add(reference.id().trim());
            }
        }
        return merged;
    }
}
