package devflow.agent.review;

/**
 * reviewer 语义标签。
 *
 * <p>这里承载的是“模型已经结构化判断出的语义”，例如：
 * 1. 这条 finding 是否只是在追打低权重内容；
 * 2. 是否只是想把开放问题定死；
 * 3. 是否在要求量化指标或实现细节；
 * 4. 是否真的给出了性能结论以及对应测量证据。
 * 5. 候选文档本身是否已经引入了无来源支撑的量化或实现硬约束。
 *
 * <p>这样后续的 normalizer/policy 就不需要再从自然语言 prose 里猜这些语义。
 */
public record ReviewSemantics(
        boolean provided,
        boolean targetsLowAuthorityContent,
        boolean targetsTrackedOpenQuestion,
        boolean clarificationRequest,
        boolean backedByHardAuthority,
        boolean requestsQuantitativeHardening,
        boolean requestsImplementationHardening,
        boolean downstreamDetailOnly,
        boolean coreStageGap,
        boolean performanceClaim,
        boolean measurementEvidencePresent,
        boolean unsupportedQuantitativeConstraintPresent,
        boolean unsupportedImplementationConstraintPresent
) {

    public static ReviewSemantics empty() {
        return new ReviewSemantics(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }
}
