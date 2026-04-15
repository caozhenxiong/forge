package devflow.agent.protocol;

import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.util.EnumParsers;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * execution directive feedback 的单一规范化/合并入口。
 *
 * <p>反馈链上的机器真相只能来自结构化 directive block；
 * prose 只作为给模型看的补充说明，不允许再单独承担 patch package owner。
 */
public final class ExecutionDirectiveFeedbackSupport {

    private ExecutionDirectiveFeedbackSupport() {
    }

    public static String merge(String inheritedFeedback, String newFeedback) {
        String inherited = blank(inheritedFeedback);
        String fresh = blank(newFeedback);
        if (inherited.isBlank()) {
            return normalize(fresh);
        }
        if (fresh.isBlank()) {
            return normalize(inherited);
        }
        ExecutionDirectivePayload mergedPayload = ExecutionDirectiveProtocol.parseMerged(inherited)
                .merge(ExecutionDirectiveProtocol.parseMerged(fresh));
        LinkedHashSet<String> proseParts = new LinkedHashSet<>();
        addProse(proseParts, inherited);
        addProse(proseParts, fresh);
        return joinPayloadAndProse(mergedPayload, proseParts);
    }

    public static String normalize(String feedback) {
        String normalized = blank(feedback);
        if (normalized.isBlank()) {
            return "";
        }
        ExecutionDirectivePayload payload = ExecutionDirectiveProtocol.parseMerged(normalized);
        LinkedHashSet<String> proseParts = new LinkedHashSet<>();
        addProse(proseParts, normalized);
        return joinPayloadAndProse(payload, proseParts);
    }

    public static String persistentRetryFeedback(String feedback) {
        String normalized = normalize(feedback);
        if (normalized.isBlank()) {
            return "";
        }
        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(normalized);
        if (Boolean.TRUE.equals(directives.repairBriefEnforced()) || carriesConcretePatchPackage(directives)) {
            return normalized;
        }
        return "";
    }

    public static String repairBriefFeedback(String feedback) {
        String normalized = normalize(feedback);
        if (normalized.isBlank()) {
            return "";
        }
        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(normalized);
        return Boolean.TRUE.equals(directives.repairBriefEnforced()) ? normalized : "";
    }

    public static boolean carriesConcretePatchPackage(String feedback) {
        if (feedback == null || feedback.isBlank()) {
            return false;
        }
        return carriesConcretePatchPackage(ExecutionDirectiveProtocol.parseMerged(feedback));
    }

    private static boolean carriesConcretePatchPackage(ExecutionDirectivePayload directives) {
        ImplementationPatchTarget patchTarget = EnumParsers.parseIgnoreCase(
                ImplementationPatchTarget.class,
                directives.implementationPatchTarget(),
                ImplementationPatchTarget.NONE
        );
        return patchTarget.concretePatch()
                && directives.overrideChanges() != null
                && !directives.overrideChanges().isEmpty();
    }

    private static String joinPayloadAndProse(ExecutionDirectivePayload payload, LinkedHashSet<String> proseParts) {
        LinkedHashSet<String> sections = new LinkedHashSet<>();
        if (!ExecutionDirectivePayload.empty().equals(payload)) {
            sections.add(ExecutionDirectiveProtocol.renderBlock(payload));
        }
        proseParts.stream()
                .filter(part -> part != null && !part.isBlank())
                .forEach(sections::add);
        return String.join("\n\n", sections).trim();
    }

    private static void addProse(LinkedHashSet<String> target, String feedback) {
        String prose = StructuredArtifactBlocks.stripAllKnownBlocks(blank(feedback)).trim();
        if (!prose.isBlank()) {
            target.add(prose);
        }
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }
}
