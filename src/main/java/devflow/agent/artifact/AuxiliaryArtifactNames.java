package devflow.agent.artifact;

import devflow.agent.orchestrator.StageType;

/**
 * 集中维护 run 目录下的辅助产物文件名。
 *
 * <p>这些名称会被 orchestrator / artifact / review / executor 多层同时读写；
 * 如果散落成裸字符串，后续改名或排查不同步问题会非常痛苦。
 */
public final class AuxiliaryArtifactNames {

    public static final String PROJECTED_CONTEXT = "projected_context.md";
    public static final String PLANNER_CONTEXT = "planner_context.md";
    public static final String CODER_CONTEXT = "coder_context.md";
    public static final String REVIEWER_CONTEXT = "reviewer_context.md";
    public static final String FLOW_CONTROLLER_CONTEXT = "flow_controller_context.md";
    public static final String SUPERVISOR_CONTEXT = "supervisor_context.md";
    public static final String TASK_MEMORY = "task_memory.md";
    public static final String SUPERVISOR_DECISION = "supervisor_decision.md";
    public static final String TRANSITION_DECISION = "transition_decision.md";
    public static final String REPAIR_BRIEF = "repair_brief.md";
    public static final String IMPLEMENTATION_BACKLOG = "implementation_backlog.md";
    public static final String IMPLEMENTATION_PROGRESS = "implementation_progress.md";
    public static final String IMPLEMENTATION_EVENTS = "implementation_events.md";
    public static final String IMPLEMENTATION_DIAGNOSTICS = "implementation_diagnostics.md";
    public static final String IMPLEMENTATION_SHARED_CONTEXT = "implementation_shared_context.md";
    public static final String IMPLEMENTATION_STATE = "implementation_state.json";
    public static final String IMPLEMENTATION_STAGE_STATUS = "implementation_stage_status.md";
    public static final String SYNTAX_REPAIR_FAILURES = "syntax_repair_failures.md";
    public static final String REPAIR_ALIGNMENT = "repair_alignment.md";
    public static final String TASK_PACKAGES = "task_packages.md";
    public static final String WORKER_RESULTS = "worker_results.md";
    public static final String TEST_CASES = "test_cases.md";
    public static final String TEST_RUNTIME_SNAPSHOT = "test_runtime_snapshot.md";
    public static final String TEST_EXECUTION = "test_execution.md";

    private AuxiliaryArtifactNames() {
    }

    public static String stageDirective(StageType stageType) {
        return switch (stageType) {
            case ANALYSIS -> "analysis_directive.md";
            case PRD -> "prd_directive.md";
            case DESIGN -> "design_directive.md";
            case IMPLEMENTATION -> "implementation_directive.md";
            case CODE_REVIEW -> "code_review_directive.md";
            case TEST -> "test_directive.md";
        };
    }
}
