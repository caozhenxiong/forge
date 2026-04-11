package devflow.agent.quality;

import devflow.agent.context.ContractView;
import devflow.agent.context.ValidationMetadata;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 从结构化 contract 提取质量意图。
 *
 * <p>这层只消费已经结构化的输入，不从 selector 文案或自由文本正文推断领域语义。
 */
public final class QualityIntentResolver {

    public QualityIntent resolve(
            QualityRules rules,
            ContractView contractView,
            ValidationMetadata validationMetadata,
            Collection<String> requiredCapabilitySurfaces
    ) {
        if (contractView == null && validationMetadata == null
                && (requiredCapabilitySurfaces == null || requiredCapabilitySurfaces.isEmpty())) {
            return QualityIntent.empty();
        }
        LinkedHashSet<CapabilitySurface> requiredSurfaces = new LinkedHashSet<>();
        if (rules != null && rules.verificationRules() != null) {
            requiredSurfaces.addAll(rules.verificationRules().requiredCapabilitySurfaces());
        }
        if (validationMetadata != null) {
            if (validationMetadata.pageLoadMaxMs() != null) {
                requiredSurfaces.add(CapabilitySurface.PERFORMANCE_LOAD);
            }
            if (validationMetadata.interactionMaxMs() != null) {
                requiredSurfaces.add(CapabilitySurface.PERFORMANCE_INTERACTION);
            }
        }
        if (requiredCapabilitySurfaces != null) {
            requiredCapabilitySurfaces.stream()
                    .map(CapabilitySurface::fromWireValue)
                    .filter(surface -> surface != null)
                    .forEach(requiredSurfaces::add);
        }
        return new QualityIntent(
                StructureIntent.empty(),
                new CoverageIntent(Set.copyOf(requiredSurfaces)),
                new InteractionIntent(requiredSurfaces.stream()
                        .filter(CapabilitySurface::isExperienceSurface)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()))
        );
    }
}
