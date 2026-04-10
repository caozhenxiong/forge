package devflow.agent.context;

/**
 * 四层上下文中的 durable 层。
 *
 * <p>这层放稳定、可跨阶段复用、不会因为单次 edit/review 抖动的上下文，
 * 例如用户目标、约束与结构化 contract。
 */
public record DurableContextView(
        String goal,
        String constraints,
        String upstreamContractSummary,
        String structuredContractSummary,
        ContractView contractView,
        String repairSummary
) {
}
