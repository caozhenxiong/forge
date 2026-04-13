package devflow.agent.executor.testing;

import devflow.agent.executor.FileChange;
import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewRevisionRoute;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * TEST 失败后的唯一结构化归因结果。
 */
public record ExperienceFailureDisposition(
        ExperienceFailureKind kind,
        String summary,
        String changeRequest,
        String evidence,
        ImplementationPatchTarget implementationPatchTarget,
        List<FileChange> overrideChanges,
        List<String> failingCaseIds,
        List<String> failureCapabilitySurfaces,
        List<String> requiredCapabilitySurfaces,
        ReviewRevisionRoute revisionRoute,
        ReviewReasonCode reasonCode
) {

    public ExperienceFailureDisposition {
        kind = kind == null ? ExperienceFailureKind.NONE : kind;
        summary = summary == null ? "" : summary.trim();
        changeRequest = changeRequest == null ? "" : changeRequest.trim();
        evidence = evidence == null ? "" : evidence.trim();
        implementationPatchTarget = implementationPatchTarget == null ? ImplementationPatchTarget.NONE : implementationPatchTarget;
        overrideChanges = overrideChanges == null ? List.of() : List.copyOf(overrideChanges);
        failingCaseIds = normalizeValues(failingCaseIds);
        failureCapabilitySurfaces = normalizeValues(failureCapabilitySurfaces);
        requiredCapabilitySurfaces = normalizeSurfaces(requiredCapabilitySurfaces);
        revisionRoute = revisionRoute == null ? ReviewRevisionRoute.PATCH_CURRENT_STAGE : revisionRoute;
        reasonCode = reasonCode == null ? ReviewReasonCode.NONE : reasonCode;
    }

    public static ExperienceFailureDisposition pass() {
        return new ExperienceFailureDisposition(
                ExperienceFailureKind.NONE,
                "",
                "",
                "",
                ImplementationPatchTarget.NONE,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ReviewRevisionRoute.PATCH_CURRENT_STAGE,
                ReviewReasonCode.NONE
        );
    }

    public boolean passed() {
        return kind == ExperienceFailureKind.NONE;
    }

    public boolean requiresImplementationReverification() {
        return kind.requiresImplementationReverification();
    }

    private static List<String> normalizeSurfaces(List<String> surfaces) {
        return normalizeValues(surfaces);
    }

    private static List<String> normalizeValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }
}
