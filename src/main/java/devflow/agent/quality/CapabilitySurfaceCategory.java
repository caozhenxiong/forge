package devflow.agent.quality;

/**
 * Capability surface 的通用分类。
 *
 * <p>核心层只依赖这类通用分类做 gate/checklist，不再按 pause/reset 等具体领域语义写分支。
 */
public enum CapabilitySurfaceCategory {
    CORE_RUNTIME,
    EXPERIENCE,
    PERFORMANCE
}
