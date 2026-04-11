package devflow.agent.executor;

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
                ReviewRevisionRoute.PATCH_CURRENT_STAGE,
                ReviewReasonCode.NONE
        );
    }

    public boolean passed() {
        return kind == ExperienceFailureKind.NONE;
    }

    private static List<String> normalizeSurfaces(List<String> surfaces) {
        if (surfaces == null || surfaces.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String surface : surfaces) {
            if (surface == null || surface.isBlank()) {
                continue;
            }
            normalized.add(surface.trim());
        }
        return List.copyOf(normalized);
    }
}
