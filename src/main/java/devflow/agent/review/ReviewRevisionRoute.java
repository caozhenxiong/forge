package devflow.agent.review;

/**
 * review 结论后的确定性回流路径。
 *
 * <p>这层把“继续修当前阶段”和“必须回退设计”显式区分，
 * 避免 implementation review 越权改 approved contract。
 */
public enum ReviewRevisionRoute {
    PATCH_CURRENT_STAGE,
    ROUTE_TO_REPAIR_TARGET,
    ROLLBACK_TO_DESIGN,
    REQUEST_HUMAN
}
