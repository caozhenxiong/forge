package devflow.agent.executor.subtask;

import devflow.agent.executor.FileChange;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewRevisionRoute;
import devflow.agent.review.StructuredReviewResult;
import devflow.agent.review.SubtaskBoundaryReviewPayload;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 子任务级 patch repair package 的唯一收口 owner。
 *
 * <p>这层负责把 patch review 转成唯一的 canonical repair package，
 * 避免 review、helper、executor 各自再生 scope。
 */
public final class SubtaskRepairDirectiveResolver {

    public SubtaskVerificationOutcome resolveCurrentScopePatch(
            Subtask subtask,
            ReviewResult review,
            DocumentLanguage language
    ) {
        if (review == null || !requiresPatch(review)) {
            return SubtaskVerificationOutcome.of(review, SubtaskRevisionDirective.empty());
        }
        if (!review.implementationPatchTarget().concretePatch()) {
            return SubtaskVerificationOutcome.of(review, SubtaskRevisionDirective.empty());
        }
        if (review.implementationPatchTarget() == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            return missingRuntimeRepairPackage(review, language);
        }
        List<FileChange> allowedScope = effectiveChanges(subtask);
        if (allowedScope.isEmpty()) {
            return missingStructuredPatchScope(review, language);
        }
        return patchOutcome(withOverrideChanges(review, allowedScope), allowedScope);
    }

    public SubtaskVerificationOutcome resolveStructuredPatch(
            Subtask subtask,
            ReviewResult review,
            StructuredReviewResult structuredReview,
            DocumentLanguage language
    ) {
        return resolveWithinSubtask(subtask, review, structuredReview, language, true);
    }

    public SubtaskVerificationOutcome resolveExplicitPatch(
            Subtask subtask,
            ReviewResult review,
            DocumentLanguage language
    ) {
        if (review == null || !requiresPatch(review)) {
            return SubtaskVerificationOutcome.of(review, SubtaskRevisionDirective.empty());
        }
        if (!review.implementationPatchTarget().concretePatch()) {
            return SubtaskVerificationOutcome.of(review, SubtaskRevisionDirective.empty());
        }
        if (review.implementationPatchTarget() == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            return missingRuntimeRepairPackage(review, language);
        }
        List<FileChange> allowedScope = effectiveChanges(subtask);
        if (allowedScope.isEmpty()) {
            return missingStructuredPatchScope(review, language);
        }
        if (review.overrideChanges().isEmpty()) {
            return missingStructuredPatchScope(review, language);
        }
        List<FileChange> canonicalChanges = matchScopedChanges(allowedScope, review.overrideChanges());
        if (canonicalChanges.size() != review.overrideChanges().size()) {
            return invalidScopedPatch(review, language);
        }
        return patchOutcome(withOverrideChanges(review, canonicalChanges), canonicalChanges);
    }

    public SubtaskVerificationOutcome resolveRuntimeWiringPatch(
            ReviewResult review,
            List<FileChange> runtimeChanges,
            DocumentLanguage language
    ) {
        if (review == null || !requiresPatch(review)) {
            return SubtaskVerificationOutcome.of(review, SubtaskRevisionDirective.empty());
        }
        if (review.implementationPatchTarget() != ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            return SubtaskVerificationOutcome.of(review, SubtaskRevisionDirective.empty());
        }
        if (runtimeChanges == null || runtimeChanges.isEmpty()) {
            return missingRuntimeRepairPackage(review, language);
        }
        List<FileChange> canonicalChanges = List.copyOf(runtimeChanges);
        return patchOutcome(withOverrideChanges(review, canonicalChanges), canonicalChanges);
    }

    private SubtaskVerificationOutcome resolveWithinSubtask(
            Subtask subtask,
            ReviewResult review,
            StructuredReviewResult structuredReview,
            DocumentLanguage language,
            boolean boundaryAware
    ) {
        if (review == null || !requiresPatch(review)) {
            return SubtaskVerificationOutcome.of(review, SubtaskRevisionDirective.empty());
        }
        if (!review.implementationPatchTarget().concretePatch()) {
            return SubtaskVerificationOutcome.of(review, SubtaskRevisionDirective.empty());
        }
        if (review.implementationPatchTarget() == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            return missingRuntimeRepairPackage(review, language);
        }

        List<FileChange> allowedScope = effectiveChanges(subtask);
        if (allowedScope.isEmpty()) {
            return missingStructuredPatchScope(review, language);
        }

        if (boundaryAware && review.reasonCode() == devflow.agent.review.ReviewReasonCode.CONTRACT_BOUNDARY_VIOLATION) {
            return resolveBoundaryPatch(review, structuredReview, allowedScope, language);
        }

        if (!review.overrideChanges().isEmpty()) {
            List<FileChange> canonicalChanges = matchScopedChanges(allowedScope, review.overrideChanges());
            if (canonicalChanges.size() != review.overrideChanges().size()) {
                return invalidScopedPatch(review, language);
            }
            return patchOutcome(withOverrideChanges(review, canonicalChanges), canonicalChanges);
        }

        return missingStructuredPatchScope(review, language);
    }

    private SubtaskVerificationOutcome resolveBoundaryPatch(
            ReviewResult review,
            StructuredReviewResult structuredReview,
            List<FileChange> allowedScope,
            DocumentLanguage language
    ) {
        SubtaskBoundaryReviewPayload payload = structuredReview == null ? null : structuredReview.subtaskBoundary();
        if (payload == null || !payload.provided() || !payload.boundaryViolation() || payload.offendingPaths().isEmpty()) {
            return missingBoundaryScope(review, language);
        }
        List<FileChange> canonicalChanges = matchScopedPaths(allowedScope, payload.offendingPaths());
        if (canonicalChanges.size() != payload.offendingPaths().size()) {
            return invalidBoundaryScope(review, language);
        }
        return patchOutcome(withOverrideChanges(review, canonicalChanges), canonicalChanges);
    }

    private boolean requiresPatch(ReviewResult review) {
        return review != null && review.fixMode() == FixMode.PATCH;
    }

    private List<FileChange> effectiveChanges(Subtask subtask) {
        if (subtask == null || subtask.changes() == null || subtask.changes().isEmpty()) {
            return List.of();
        }
        LinkedHashMap<Path, FileChange> normalized = new LinkedHashMap<>();
        for (FileChange change : subtask.changes()) {
            if (change == null || change.path() == null || change.path().isBlank()) {
                continue;
            }
            normalized.put(Path.of(change.path()).normalize(), change);
        }
        return List.copyOf(normalized.values());
    }

    private List<FileChange> matchScopedChanges(List<FileChange> allowedScope, List<FileChange> selectors) {
        if (selectors == null || selectors.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<Path, FileChange> allowedByPath = allowedByPath(allowedScope);
        LinkedHashSet<FileChange> matched = new LinkedHashSet<>();
        for (FileChange change : selectors) {
            if (change == null || change.path() == null || change.path().isBlank()) {
                continue;
            }
            FileChange scoped = allowedByPath.get(Path.of(change.path()).normalize());
            if (scoped == null) {
                continue;
            }
            matched.add(scoped);
        }
        return List.copyOf(matched);
    }

    private List<FileChange> matchScopedPaths(List<FileChange> allowedScope, List<String> relativePaths) {
        if (relativePaths == null || relativePaths.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<Path, FileChange> allowedByPath = allowedByPath(allowedScope);
        LinkedHashSet<FileChange> matched = new LinkedHashSet<>();
        for (String path : relativePaths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            FileChange scoped = allowedByPath.get(Path.of(path).normalize());
            if (scoped == null) {
                continue;
            }
            matched.add(scoped);
        }
        return List.copyOf(matched);
    }

    private LinkedHashMap<Path, FileChange> allowedByPath(List<FileChange> allowedScope) {
        LinkedHashMap<Path, FileChange> allowedByPath = new LinkedHashMap<>();
        if (allowedScope == null || allowedScope.isEmpty()) {
            return allowedByPath;
        }
        for (FileChange change : allowedScope) {
            if (change == null || change.path() == null || change.path().isBlank()) {
                continue;
            }
            allowedByPath.put(Path.of(change.path()).normalize(), change);
        }
        return allowedByPath;
    }

    private SubtaskVerificationOutcome patchOutcome(ReviewResult review, List<FileChange> canonicalChanges) {
        return SubtaskVerificationOutcome.of(
                review,
                SubtaskRevisionDirective.patch(canonicalChanges)
        );
    }

    private SubtaskVerificationOutcome missingBoundaryScope(ReviewResult review, DocumentLanguage language) {
        return SubtaskVerificationOutcome.of(
                requestHuman(
                        review,
                        language.choose(
                                "当前子任务命中了 capability boundary，但结构化 review 没有给出 offendingPaths。",
                                "The current subtask hit a capability boundary, but the structured review did not provide offendingPaths."
                        ),
                        language.choose(
                                "请先明确越界实现对应的具体文件范围，再决定是否继续自动修复。",
                                "Identify the concrete offending file scope before continuing automatic repair."
                        )
                ),
                SubtaskRevisionDirective.empty()
        );
    }

    private SubtaskVerificationOutcome invalidBoundaryScope(ReviewResult review, DocumentLanguage language) {
        return SubtaskVerificationOutcome.of(
                requestHuman(
                        review,
                        language.choose(
                                "当前子任务的 offendingPaths 超出了 effective change-set。",
                                "The offendingPaths for the current subtask fall outside the effective change-set."
                        ),
                        language.choose(
                                "请先把 boundary finding 收敛到当前子任务结构化变更范围内，再继续自动修复。",
                                "Constrain the boundary finding to the current subtask's structured change-set before continuing automatic repair."
                        )
                ),
                SubtaskRevisionDirective.empty()
        );
    }

    private SubtaskVerificationOutcome invalidScopedPatch(ReviewResult review, DocumentLanguage language) {
        return SubtaskVerificationOutcome.of(
                requestHuman(
                        review,
                        language.choose(
                                "当前 patch review 给出的文件范围超出了当前子任务 effective change-set。",
                                "The file scope returned by the current patch review falls outside the active subtask effective change-set."
                        ),
                        language.choose(
                                "请先把 patch scope 收敛到当前子任务负责文件，再继续自动修复。",
                                "Constrain the patch scope to the current subtask owned files before continuing automatic repair."
                        )
                ),
                SubtaskRevisionDirective.empty()
        );
    }

    private SubtaskVerificationOutcome missingRuntimeRepairPackage(ReviewResult review, DocumentLanguage language) {
        return SubtaskVerificationOutcome.of(
                requestHuman(
                        review,
                        language.choose(
                                "当前子任务需要继续修复 runtime wiring，但没有 canonical runtime repair package。",
                                "The current subtask still needs runtime wiring repair, but no canonical runtime repair package is available."
                        ),
                        language.choose(
                                "请先由 runtime wiring contract 链生成当前子任务的 canonical runtime repair package；缺少结构化 repair package 时不要继续自动续跑。",
                                "Generate the canonical runtime repair package from the runtime wiring contract chain before continuing automatic repair."
                        )
                ),
                SubtaskRevisionDirective.empty()
        );
    }

    private SubtaskVerificationOutcome missingStructuredPatchScope(ReviewResult review, DocumentLanguage language) {
        return SubtaskVerificationOutcome.of(
                requestHuman(
                        review,
                        language.choose(
                                "当前实现需要继续 patch，但缺少结构化文件范围。",
                                "The current implementation still needs a patch, but no structured file scope is available."
                        ),
                        language.choose(
                                "请先明确本轮需要修补的实现文件范围，确认 owner 后再继续自动修复。",
                                "Define the structured repair scope for this patch before continuing automatic repair."
                        )
                ),
                SubtaskRevisionDirective.empty()
        );
    }

    private ReviewResult requestHuman(ReviewResult review, String summary, String changeRequest) {
        return new ReviewResult(
                review.decision(),
                review.fixMode(),
                summary,
                changeRequest,
                review.evidence(),
                review.actionItems(),
                ImplementationPatchTarget.NONE,
                List.of(),
                ReviewRevisionRoute.REQUEST_HUMAN,
                review.reasonCode()
        );
    }

    private ReviewResult withOverrideChanges(ReviewResult review, List<FileChange> overrideChanges) {
        return new ReviewResult(
                review.decision(),
                review.fixMode(),
                review.summary(),
                review.changeRequest(),
                review.evidence(),
                review.actionItems(),
                review.implementationPatchTarget(),
                overrideChanges,
                review.revisionRoute(),
                review.reasonCode()
        );
    }
}
