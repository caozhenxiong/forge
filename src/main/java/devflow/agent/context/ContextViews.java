package devflow.agent.context;

/**
 * 四层上下文视图的统一入口。
 *
 * <p>第一版先把四层边界正式放进运行时数据模型，
 * 后续再逐步把调用方从字符串摘要迁移到分层视图。
 */
public record ContextViews(
        DurableContextView durableContext,
        WorkingContextView workingContext,
        EvidenceContextView evidenceContext,
        TraceContextView traceContext
) {

    /**
     * 按角色读取受限上下文，而不是把四层上下文无差别暴露给所有调用方。
     */
    public ContextSlice forProfile(ContextAccessProfile profile) {
        if (profile == null) {
            return new ContextSlice(durableContext, workingContext, evidenceContext, traceContext);
        }
        if (profile == ContextAccessProfile.PLANNER) {
            return new ContextSlice(durableContext, null, evidenceContext, null);
        }
        if (profile == ContextAccessProfile.CODER || profile == ContextAccessProfile.REVIEWER) {
            return new ContextSlice(durableContext, workingContext, evidenceContext, null);
        }
        if (profile == ContextAccessProfile.FLOW_CONTROLLER) {
            return new ContextSlice(null, null, evidenceContext, traceContext);
        }
        return new ContextSlice(durableContext, workingContext, evidenceContext, traceContext);
    }
}
