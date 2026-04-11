package devflow.agent.review;

/**
 * implementation 阶段 PATCH continuation 的结构化修复目标。
 *
 * <p>这个枚举只描述“当前这轮 PATCH 要修哪一类已知闭环问题”，
 * 不承担自然语言解释职责，也不允许用来表达开放式重构建议。
 */
public enum ImplementationPatchTarget {
    NONE,
    PATCH_EXISTING_IMPLEMENTATION,
    PATCH_RUNTIME_WIRING;

    public boolean concretePatch() {
        return this != NONE;
    }
}
