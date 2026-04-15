package devflow.agent.protocol;

/**
 * implementation 阶段在 stage artifact 中声明的下一步动作。
 *
 * <p>这层只表达 implementation 续跑的唯一分类：
 * 1. `MID_PLAN_CONTINUE`：计划仍在正常推进，还没有进入具体 patch continuation；
 * 2. `PATCH_CONTINUE`：已经拿到 canonical repair package，只允许按该 patch package 续跑；
 * 3. `BLOCKED_EXHAUSTED_SUBTASK`：当前 implementation 没有安全自动续跑路径，必须阻断到人工或更高层 reroute。
 */
public enum ImplementationContinuationMode {
    MID_PLAN_CONTINUE,
    PATCH_CONTINUE,
    BLOCKED_EXHAUSTED_SUBTASK;

    public boolean autoContinue() {
        return this == MID_PLAN_CONTINUE || this == PATCH_CONTINUE;
    }

    public boolean blocked() {
        return this == BLOCKED_EXHAUSTED_SUBTASK;
    }

    public boolean patchContinue() {
        return this == PATCH_CONTINUE;
    }
}
