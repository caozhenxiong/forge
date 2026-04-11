package devflow.agent.quality;

import devflow.agent.validation.ProjectFingerprint;

/**
 * 结构风险目前只作为 planning / review 的提示信息，不再直接驱动实现阶段的阻断。
 *
 * <p>真正的阻断应来自显式 contract、一致性检查和可运行性验证，
 * 而不是从“是否外提脚本”“是否单文件”这类实现形态本身做价值判断。
 */
public final class StructureGateEvaluator {

    public StructureGateOutcome evaluate(ProjectFingerprint fingerprint, QualityPlan qualityPlan) {
        return StructureGateOutcome.pass();
    }
}
