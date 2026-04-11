package devflow.agent.executor;

import devflow.agent.review.ImplementationPatchTarget;

/**
 * runtime wiring 失败后的结构化修复决策。
 *
 * <p>PATCH target 只描述修复形态；runtime contract 描述入口所有权；
 * htmlEntryOverride 描述下一轮 HTML 入口应该使用的唯一编辑骨架。
 * 三者必须一起传递，避免继续出现“知道要修 wiring，但执行层仍停留在旧 scope” 的矛盾状态。
 */
record RuntimeWiringPatchDecision(
        ImplementationPatchTarget patchTarget,
        HtmlRuntimeOwnershipContract runtimeContract,
        FileChange htmlEntryOverride
) {

    RuntimeWiringPatchDecision {
        patchTarget = patchTarget == null ? ImplementationPatchTarget.PATCH_RUNTIME_WIRING : patchTarget;
    }
}
