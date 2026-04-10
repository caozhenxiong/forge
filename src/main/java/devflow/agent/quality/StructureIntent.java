package devflow.agent.quality;

/**
 * 当前任务对实现结构的显式意图。
 *
 * <p>它承载的是“是否偏好外提主逻辑”“是否需要 justification”这类任务级要求，
 * 不应由通用 profiler 自己猜出来。
 */
public record StructureIntent(
        boolean preferLogicExternalization,
        boolean requireStructureJustification
) {

    public static StructureIntent empty() {
        return new StructureIntent(false, false);
    }
}
