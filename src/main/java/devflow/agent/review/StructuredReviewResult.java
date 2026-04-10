package devflow.agent.review;

/**
 * review 结构化结果。
 *
 * <p>一份 review 现在分成两层：
 * 1. {@link ReviewResult}：流程真正消费的决策、修复模式和人工可读摘要；
 * 2. {@link ReviewSemantics}：供 normalizer/policy 做确定性裁决的语义标签。
 */
public record StructuredReviewResult(
        ReviewResult result,
        ReviewSemantics semantics
) {

    public StructuredReviewResult {
        result = result == null
                ? new ReviewResult(ReviewDecision.REVISION_REQUIRED, FixMode.PATCH, "", "")
                : result;
        semantics = semantics == null ? ReviewSemantics.empty() : semantics;
    }
}
