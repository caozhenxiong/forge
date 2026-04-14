package devflow.agent.review;

import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.domain.StageType;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.QualityLedger;
import java.util.stream.Collectors;

final class ExecutionStageReviewer {

    private final ReviewDecisionArtifactParser reviewDecisionArtifactParser;

    ExecutionStageReviewer(ReviewDecisionArtifactParser reviewDecisionArtifactParser) {
        this.reviewDecisionArtifactParser = reviewDecisionArtifactParser;
    }

    ReviewResult review(StageType stageType, String artifactContent) {
        if (stageType == StageType.CODE_REVIEW) {
            return reviewDecisionArtifactParser.parseDecisionArtifact(
                    artifactContent,
                    ReviewDecision.REVISION_REQUIRED,
                    "代码审阅发现问题，需要修改。"
            );
        }
        ReviewResult parsed = reviewDecisionArtifactParser.parseDecisionArtifact(
                artifactContent,
                ReviewDecision.REJECTED,
                "测试失败，需要修复并重新执行。"
        );
        return enforceStructuredTestCoverageGate(artifactContent, parsed);
    }

    private ReviewResult enforceStructuredTestCoverageGate(String artifactContent, ReviewResult parsed) {
        if (parsed == null || parsed.decision() != ReviewDecision.APPROVED) {
            return parsed;
        }
        QualityLedger qualityLedger = StructuredArtifactBlocks.readFirstJsonBlock(
                artifactContent,
                ArtifactBlockKind.QUALITY_LEDGER,
                QualityLedger.class
        );
        CoverageLedger coverageLedger = qualityLedger == null ? null : qualityLedger.coverageLedger();
        if (coverageLedger == null || !coverageLedger.hasMissingRequiredCoverage()) {
            return parsed;
        }
        String missingSurfaces = coverageLedger.missingRequiredCapabilityIds().stream()
                .sorted()
                .collect(Collectors.joining(", "));
        String evidence = missingSurfaces.isBlank()
                ? "quality-ledger reports missing required coverage"
                : "missingRequiredCoverage=" + missingSurfaces;
        return new ReviewResult(
                ReviewDecision.REJECTED,
                FixMode.PATCH,
                "测试阶段仍缺少必需能力的通过证据。",
                "请补齐缺失的体验能力覆盖并重新执行测试。",
                evidence,
                "",
                ImplementationPatchTarget.NONE,
                java.util.List.of(),
                ReviewRevisionRoute.REQUEST_HUMAN,
                ReviewReasonCode.IMPLEMENTATION_GAP
        );
    }
}
