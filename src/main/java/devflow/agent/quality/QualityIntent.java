package devflow.agent.quality;

import devflow.agent.i18n.DocumentLanguage;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 当前任务的显式质量意图。
 *
 * <p>领域语义必须从 contract / planner 产物进入这里，而不是写进通用 profiler。
 */
public record QualityIntent(
        StructureIntent structureIntent,
        CoverageIntent coverageIntent,
        InteractionIntent interactionIntent
) {

    public QualityIntent {
        structureIntent = structureIntent == null ? StructureIntent.empty() : structureIntent;
        coverageIntent = coverageIntent == null ? CoverageIntent.empty() : coverageIntent;
        interactionIntent = interactionIntent == null ? InteractionIntent.empty() : interactionIntent;
    }

    public static QualityIntent empty() {
        return new QualityIntent(StructureIntent.empty(), CoverageIntent.empty(), InteractionIntent.empty());
    }

    public Set<CapabilitySurface> requiredCapabilitySurfaces() {
        LinkedHashSet<CapabilitySurface> surfaces = new LinkedHashSet<>();
        surfaces.addAll(coverageIntent.requiredSurfaces());
        surfaces.addAll(interactionIntent.expectedSurfaces());
        return Set.copyOf(surfaces);
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                ### %s
                - preferLogicExternalization: %s
                - requireStructureJustification: %s
                - requiredCapabilitySurfaces: %s
                """.formatted(
                language.choose("质量意图", "Quality Intent"),
                structureIntent.preferLogicExternalization(),
                structureIntent.requireStructureJustification(),
                requiredCapabilitySurfaces().isEmpty()
                        ? language.choose("(无)", "(none)")
                        : requiredCapabilitySurfaces().stream().map(CapabilitySurface::wireValue).sorted().toList()
        ).trim();
    }
}
