package devflow.agent.review;

import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;

/**
 * 文档评审的确定性归一化器。
 *
 * <p>这层不再从 reviewer 的自然语言里猜语义。低权重内容、开放问题、
 * 下游细节、量化/实现硬化等语义，都必须来自结构化 {@link ReviewSemantics}。
 *
 * <p>这层也不再通过正文自然语言推断“是否已经存在量化硬约束”。
 * 量化/性能类 authority 必须来自稳定的 Contract Metadata.validation.* 字段。
 */
class DocumentReviewNormalizer {
    private final ContractExtractor contractExtractor;

    DocumentReviewNormalizer(ContractExtractor contractExtractor) {
        this.contractExtractor = contractExtractor;
    }

    ReviewResult normalize(RunRecord runRecord, StageType stageType, String candidateContent, ReviewResult raw) {
        return normalize(runRecord, stageType, candidateContent, raw, ReviewSemantics.empty());
    }

    ReviewResult normalize(
            RunRecord runRecord,
            StageType stageType,
            String candidateContent,
            ReviewResult raw,
            ReviewSemantics semantics
    ) {
        ReviewResult rejectedUnsupportedDocumentConstraints = rejectUnsupportedDocumentConstraints(raw, semantics);
        if (rejectedUnsupportedDocumentConstraints != raw) {
            return rejectedUnsupportedDocumentConstraints;
        }

        if (raw.decision() == ReviewDecision.APPROVED) {
            return new ReviewResult(
                    ReviewDecision.APPROVED,
                    FixMode.NONE,
                    raw.summary(),
                    "",
                    raw.evidence(),
                    "",
                    ImplementationPatchTarget.NONE,
                    java.util.List.of(),
                    raw.revisionRoute(),
                    raw.reasonCode()
            );
        }

        if (!semantics.provided()) {
            return raw;
        }

        ReviewResult relaxedLowAuthorityFalsePositive = relaxLowAuthorityFalsePositive(raw, semantics);
        if (relaxedLowAuthorityFalsePositive != raw) {
            return relaxedLowAuthorityFalsePositive;
        }

        ReviewResult relaxedOpenQuestionFalsePositive = relaxOpenQuestionClarificationFalsePositive(raw, semantics);
        if (relaxedOpenQuestionFalsePositive != raw) {
            return relaxedOpenQuestionFalsePositive;
        }

        if ((stageType == StageType.ANALYSIS || stageType == StageType.PRD)
                && semantics.downstreamDetailOnly()
                && !semantics.coreStageGap()) {
            if (stageType == StageType.ANALYSIS) {
                return new ReviewResult(
                        ReviewDecision.APPROVED,
                        FixMode.NONE,
                        "需求分析已满足当前阶段要求；更细的算法、性能验证和原型细节下放到 DESIGN/TEST_CASE 阶段。",
                        "",
                        raw.evidence(),
                        raw.actionItems(),
                        ImplementationPatchTarget.NONE,
                        java.util.List.of(),
                        raw.revisionRoute(),
                        raw.reasonCode()
                );
            }
            if (stageType == StageType.PRD) {
                return new ReviewResult(
                        ReviewDecision.APPROVED,
                        FixMode.NONE,
                        "PRD 已满足当前阶段要求；算法、模块和性能测试细节下放到 DESIGN/TEST_CASE 阶段。",
                        "",
                        raw.evidence(),
                        raw.actionItems(),
                        ImplementationPatchTarget.NONE,
                        java.util.List.of(),
                        raw.revisionRoute(),
                        raw.reasonCode()
                );
            }
        }

        return relaxUnsupportedHardeningRequests(runRecord, candidateContent, raw, semantics);
    }

    /**
     * reviewer 即使整体给出 APPROVED，只要结构化语义明确指出“候选文档本身
     * 已经写入了无来源支撑的量化或实现硬约束”，当前阶段也不能放行。
     */
    private ReviewResult rejectUnsupportedDocumentConstraints(ReviewResult raw, ReviewSemantics semantics) {
        if (!semantics.provided()) {
            return raw;
        }
        if (!semantics.unsupportedQuantitativeConstraintPresent()
                && !semantics.unsupportedImplementationConstraintPresent()) {
            return raw;
        }
        String changeRequest = unsupportedConstraintChangeRequest(semantics);
        return new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                "文档引入了无来源支撑的量化指标或实现约束，当前阶段不能直接通过。",
                changeRequest,
                raw.evidence(),
                unsupportedConstraintActionItems(semantics),
                ImplementationPatchTarget.NONE,
                java.util.List.of(),
                raw.revisionRoute(),
                raw.reasonCode()
        );
    }

    /**
     * 低权重内容是否被错误升级，由 reviewer 结构化语义显式给出。
     * 这层不再试图从 prose 里猜“它是不是在追打 design choice / recommendation”。
     */
    private ReviewResult relaxLowAuthorityFalsePositive(ReviewResult raw, ReviewSemantics semantics) {
        if (!semantics.targetsLowAuthorityContent()
                || semantics.backedByHardAuthority()
                || semantics.coreStageGap()) {
            return raw;
        }
        return new ReviewResult(
                ReviewDecision.APPROVED,
                FixMode.NONE,
                "当前文档相关内容属于设计选择/建议/低权重内容；本轮审阅已自动下调，不再阻塞当前阶段。",
                "",
                raw.evidence(),
                "",
                ImplementationPatchTarget.NONE,
                java.util.List.of(),
                raw.revisionRoute(),
                raw.reasonCode()
        );
    }

    /**
     * reviewer 如果只是试图把“已显式追踪的开放问题”定死，而这些问题又没有
     * 来自 hard authority 的来源支撑，就不应阻塞当前阶段。
     */
    private ReviewResult relaxOpenQuestionClarificationFalsePositive(ReviewResult raw, ReviewSemantics semantics) {
        if (!semantics.targetsTrackedOpenQuestion()
                || !semantics.clarificationRequest()
                || semantics.backedByHardAuthority()
                || semantics.coreStageGap()) {
            return raw;
        }
        return new ReviewResult(
                ReviewDecision.APPROVED,
                FixMode.NONE,
                "当前审阅主要要求补充已标记为待确认问题或低权重建议的细节；这些内容不应阻塞当前阶段。",
                "",
                raw.evidence(),
                "",
                ImplementationPatchTarget.NONE,
                java.util.List.of(),
                raw.revisionRoute(),
                raw.reasonCode()
        );
    }

    /**
     * 无来源支撑的量化指标或实现约束不应被 reviewer 自动升级成硬 gate。
     * 这里继续做 authority 侧交叉检查，但不再通过 regex 猜 reviewer prose 的意图。
     */
    private ReviewResult relaxUnsupportedHardeningRequests(
            RunRecord runRecord,
            String candidateContent,
            ReviewResult raw,
            ReviewSemantics semantics
    ) {
        ValidationMetadata validationMetadata = contractExtractor.extractValidationMetadata(candidateContent, candidateContent);
        boolean asksForUnsupportedQuantification = semantics.requestsQuantitativeHardening()
                && !hasStructuredQuantitativeAuthority(validationMetadata);
        boolean asksForUnsupportedImplementationHardening = semantics.requestsImplementationHardening()
                && !semantics.backedByHardAuthority();
        if (!asksForUnsupportedQuantification && !asksForUnsupportedImplementationHardening) {
            return raw;
        }
        return new ReviewResult(
                ReviewDecision.APPROVED,
                FixMode.NONE,
                "当前文档已满足本阶段要求；审阅意见要求补入无来源支撑的量化指标或实现约束，这些内容已下调为后续建议。",
                "",
                raw.evidence(),
                "",
                ImplementationPatchTarget.NONE,
                java.util.List.of(),
                raw.revisionRoute(),
                raw.reasonCode()
        );
    }

    private boolean hasStructuredQuantitativeAuthority(ValidationMetadata validationMetadata) {
        if (validationMetadata == null) {
            return false;
        }
        return validationMetadata.performanceMeasurementRequired()
                || validationMetadata.pageLoadMaxMs() != null
                || validationMetadata.interactionMaxMs() != null;
    }

    private String unsupportedConstraintChangeRequest(ReviewSemantics semantics) {
        if (semantics.unsupportedQuantitativeConstraintPresent()
                && semantics.unsupportedImplementationConstraintPresent()) {
            return "请删除、降级或改写这些无来源支撑的量化指标与实现硬约束，只保留 hard.* 或 Contract Metadata 已明确支撑的绑定内容。";
        }
        if (semantics.unsupportedImplementationConstraintPresent()) {
            return "请删除、降级或改写这些无来源支撑的实现硬约束，只保留 hard.* 或 Contract Metadata 已明确支撑的绑定内容。";
        }
        return "请删除、降级或改写这些无来源支撑的量化指标，只保留 hard.* 或 Contract Metadata 已明确支撑的绑定内容。";
    }

    private String unsupportedConstraintActionItems(ReviewSemantics semantics) {
        if (semantics.unsupportedQuantitativeConstraintPresent()
                && semantics.unsupportedImplementationConstraintPresent()) {
            return """
                    1. 删除候选文档中无来源支撑的量化阈值与实现硬约束。
                    2. 仅保留 hard.* 或 Contract Metadata 已明确给出的绑定内容。
                    3. 不要新增新的 hard.* 或 validation.* 来为本轮问题补来源。
                    """.trim();
        }
        if (semantics.unsupportedImplementationConstraintPresent()) {
            return """
                    1. 删除候选文档中无来源支撑的实现硬约束。
                    2. 仅保留 hard.* 或 Contract Metadata 已明确给出的绑定内容。
                    3. 不要新增新的 hard.* 或 validation.* 来为本轮问题补来源。
                    """.trim();
        }
        return """
                1. 删除候选文档中无来源支撑的量化阈值。
                2. 仅保留 hard.* 或 Contract Metadata 已明确给出的绑定内容。
                3. 不要新增新的 hard.* 或 validation.* 来为本轮问题补来源。
                """.trim();
    }
}
