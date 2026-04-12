package devflow.agent.review;

import devflow.agent.executor.ExperienceFailureDisposition;
import devflow.agent.orchestrator.RunRecord;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一组装 implementation review 的补充上下文。
 *
 * <p>实现阶段 review 不应只看到实现报告和 reviewer context；
 * 如果当前轮次是 repair continuation，还必须显式看到 repair brief / alignment，
 * 这样 reviewer 才能基于真正的修复约束做结构化判断。
 */
final class ImplementationReviewContextAssembler {

    private final ReviewArtifactLoader reviewArtifactLoader;

    ImplementationReviewContextAssembler(ReviewArtifactLoader reviewArtifactLoader) {
        this.reviewArtifactLoader = reviewArtifactLoader;
    }

    String assemble(RunRecord runRecord, String candidate, String reviewerContext) {
        StringBuilder builder = new StringBuilder(candidate == null ? "" : candidate);
        appendSection(builder, "Reviewer Context", reviewerContext);
        appendSection(builder, "Targeted Test Failure", renderTargetedTestFailure(runRecord));
        appendSection(builder, "Repair Brief", reviewArtifactLoader.readRepairBrief(runRecord));
        appendSection(builder, "Repair Alignment", reviewArtifactLoader.readRepairAlignment(runRecord));
        return builder.toString();
    }

    private String renderTargetedTestFailure(RunRecord runRecord) {
        ExperienceFailureDisposition disposition = reviewArtifactLoader.readTestFailureDisposition(runRecord);
        if (disposition == null || disposition.passed()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add("- kind: " + disposition.kind());
        if (!disposition.failingCaseIds().isEmpty()) {
            lines.add("- failingCaseIds: " + String.join(", ", disposition.failingCaseIds()));
        }
        if (!disposition.failureCapabilitySurfaces().isEmpty()) {
            lines.add("- failureCapabilitySurfaces: " + String.join(", ", disposition.failureCapabilitySurfaces()));
        }
        if (!disposition.requiredCapabilitySurfaces().isEmpty()) {
            lines.add("- requiredCapabilitySurfaces: " + String.join(", ", disposition.requiredCapabilitySurfaces()));
        }
        if (!disposition.changeRequest().isBlank()) {
            lines.add("- changeRequest: " + disposition.changeRequest());
        }
        if (!disposition.evidence().isBlank()) {
            lines.add("- evidence: " + disposition.evidence().replace('\n', ' '));
        }
        return String.join("\n", lines);
    }

    private void appendSection(StringBuilder builder, String title, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        builder.append("\n\n## ")
                .append(title)
                .append("\n\n")
                .append(content.trim());
    }
}
