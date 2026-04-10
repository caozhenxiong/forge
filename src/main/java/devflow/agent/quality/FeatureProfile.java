package devflow.agent.quality;

/**
 * 项目/实现特征画像。
 *
 * <p>这里只记录事实，不直接给出 gate 结论。
 */
public record FeatureProfile(
        boolean hasHtmlEntry,
        boolean hasExternalLogicModule,
        boolean hasEmbeddedLogic,
        boolean hasDiscreteUserInput,
        boolean hasCanvasSurface,
        boolean hasVisibleRuntimeSurface,
        boolean hasBackgroundLoop,
        boolean hasTimedProgression,
        boolean hasPerformanceRequirements
) {
}
