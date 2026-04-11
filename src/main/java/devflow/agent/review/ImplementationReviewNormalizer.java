package devflow.agent.review;

import devflow.agent.context.ContractExtractor;

/**
 * implementation review 的确定性归一化器。
 *
 * <p>这里不再从 reviewer prose 或 DESIGN 正文里猜“是不是性能问题”。
 * 性能语义必须来自结构化 {@link ReviewSemantics}，而性能验证要求只来自
 * `Contract Metadata.validation.*` 这类稳定机器字段。
 */
class ImplementationReviewNormalizer {
    private final ContractExtractor contractExtractor = new ContractExtractor();

    ReviewResult normalize(ReviewResult raw, String designArtifact) {
        return normalize(raw, designArtifact, ReviewSemantics.empty());
    }

    ReviewResult normalize(ReviewResult raw, String designArtifact, ReviewSemantics semantics) {
        if (raw.decision() == ReviewDecision.APPROVED) {
            return withPatchTarget(raw, ImplementationPatchTarget.NONE);
        }

        boolean hasPerformanceClaim = semantics.provided() && semantics.performanceClaim();
        boolean hasMeasurementEvidence = semantics.provided() && semantics.measurementEvidencePresent();
        boolean designRequiresPerformanceMeasurement = contractExtractor
                .extractValidationMetadata("", designArtifact)
                .performanceMeasurementRequired();

        if (hasPerformanceClaim && !hasMeasurementEvidence) {
            if (designRequiresPerformanceMeasurement) {
                return enforcePatchTargetContract(
                        new ReviewResult(
                                ReviewDecision.REVISION_REQUIRED,
                                FixMode.PATCH,
                                "技术方案已要求性能测量，但当前实现未提供对应实测数据。",
                                "请按 DESIGN 中定义的性能验证策略补充 6x6/9x9 的基础测量结果，再决定是否需要进一步优化。",
                                raw.evidence().isBlank()
                                        ? "当前 review 未提供来自自检或测试的耗时测量数据；而 DESIGN 已包含性能/耗时验证要求。"
                                        : raw.evidence(),
                                """
                                1. 按技术方案中的指标记录 6x6 和 9x9 的实际耗时。
                                2. 补充关键路径（如生成、唯一解校验、模式切换）的基础测量。
                                3. 将测量结果写入实现报告，再判断是否需要性能优化。
                                """.replace("\n", " ").trim(),
                                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                                raw.overrideChanges(),
                                raw.revisionRoute(),
                                raw.reasonCode()
                        ),
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
                );
            }
            return withPatchTarget(new ReviewResult(
                    ReviewDecision.APPROVED,
                    FixMode.NONE,
                    "当前实现存在性能风险，但缺少明确的性能测量证据；该项下放到 TEST 阶段验证。",
                    "",
                    raw.evidence().isBlank()
                            ? "当前 review 未提供来自自检或测试的耗时测量数据，无法支持“性能未达标”的确定性结论。"
                            : raw.evidence(),
                    """
                    1. 在生成入口记录 6x6 和 9x9 的实际耗时。
                    2. 单独测量唯一解校验和回溯关键路径耗时。
                    3. 在 TEST 阶段基于实测数据判断是否需要性能优化。
                    """.replace("\n", " ").trim()
            ), ImplementationPatchTarget.NONE);
        }

        if (!raw.evidence().isBlank() && !raw.actionItems().isBlank()) {
            return enforcePatchTargetContract(raw, raw.implementationPatchTarget());
        }

        return enforcePatchTargetContract(new ReviewResult(
                raw.decision(),
                raw.fixMode(),
                raw.summary(),
                raw.changeRequest(),
                raw.evidence().isBlank()
                        ? "请结合实际代码变更、自检或测试结果补充支持该结论的直接证据。"
                        : raw.evidence(),
                raw.actionItems().isBlank()
                        ? "1. 根据 changeRequest 定位受影响文件和函数。 2. 先修复最小闭环问题，再重新执行自检和验证。"
                        : raw.actionItems(),
                raw.implementationPatchTarget(),
                raw.overrideChanges(),
                raw.revisionRoute(),
                raw.reasonCode()
        ), raw.implementationPatchTarget());
    }

    private ReviewResult enforcePatchTargetContract(
            ReviewResult result,
            ImplementationPatchTarget implementationPatchTarget
    ) {
        if (result.decision() == ReviewDecision.APPROVED || result.fixMode() != FixMode.PATCH) {
            return withPatchTarget(result, ImplementationPatchTarget.NONE);
        }
        if (implementationPatchTarget == null || !implementationPatchTarget.concretePatch()) {
            throw new ImplementationReviewProtocolException(
                    "Implementation review returned PATCH without implementationPatchTarget"
            );
        }
        if (implementationPatchTarget == ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
                && (result.overrideChanges() == null || result.overrideChanges().isEmpty())) {
            throw new ImplementationReviewProtocolException(
                    "PATCH_EXISTING_IMPLEMENTATION requires structured overrideChanges"
            );
        }
        return withPatchTarget(result, implementationPatchTarget);
    }

    private ReviewResult withPatchTarget(ReviewResult result, ImplementationPatchTarget implementationPatchTarget) {
        return new ReviewResult(
                result.decision(),
                result.fixMode(),
                result.summary(),
                result.changeRequest(),
                result.evidence(),
                result.actionItems(),
                implementationPatchTarget,
                implementationPatchTarget == ImplementationPatchTarget.NONE ? java.util.List.of() : result.overrideChanges(),
                result.revisionRoute(),
                result.reasonCode()
        );
    }
}
