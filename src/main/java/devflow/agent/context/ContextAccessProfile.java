package devflow.agent.context;

/**
 * 定义不同角色允许读取的上下文切片。
 * 这层不是新的业务状态，而是四层上下文上的访问边界声明。
 */
public enum ContextAccessProfile {
    PLANNER,
    CODER,
    REVIEWER,
    FLOW_CONTROLLER,
    SUPERVISOR
}
