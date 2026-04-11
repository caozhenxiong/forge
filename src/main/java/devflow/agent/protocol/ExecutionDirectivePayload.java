package devflow.agent.protocol;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * implementation/repair/supervisor/generation feedback 共用的结构化指令协议。
 *
 * <p>这层承载 fix mode、delivery policy、repair brief 约束、必需证据等真正驱动流程的数据。
 * 执行链只读取这里的字段，不再从 note 里的标题、标签和自然语言 prose 反推流程语义。
 */
public record ExecutionDirectivePayload(
        String fixMode,
        String implementationPatchTarget,
        List<FileChangePayload> overrideChanges,
        Boolean repairBriefPresent,
        Boolean repairBriefEnforced,
        String deliveryMode,
        Integer deliveryMaxFiles,
        Integer deliveryMaxSymbols,
        Boolean deliveryPreferPreciseEditing,
        Boolean deliveryForceBacklogSplit,
        Boolean deliveryRequireVerificationBeforeReview,
        List<String> requiredEvidence,
        List<String> mustFixFirst,
        List<String> forbiddenDirections,
        List<String> acceptanceChecks,
        List<String> requiredCapabilitySurfaces,
        List<Integer> targetSections,
        String summary,
        String changeRequest,
        String evidence,
        String actionItems,
        String supervisorAction,
        String supervisorReason,
        List<String> focus,
        List<String> constraints,
        String generationFailureSummary,
        String generationFailureType,
        String generationFailureEvidence,
        String generationFailureRetryHint
) {

    public static ExecutionDirectivePayload empty() {
        return new ExecutionDirectivePayload(
                null,
                null,
                List.of(),
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null
        );
    }

    public ExecutionDirectivePayload merge(ExecutionDirectivePayload override) {
        if (override == null) {
            return this;
        }
        return new ExecutionDirectivePayload(
                chooseText(fixMode, override.fixMode),
                chooseText(implementationPatchTarget, override.implementationPatchTarget),
                chooseFileChanges(overrideChanges, override.overrideChanges),
                chooseBoolean(repairBriefPresent, override.repairBriefPresent),
                chooseBoolean(repairBriefEnforced, override.repairBriefEnforced),
                chooseText(deliveryMode, override.deliveryMode),
                chooseInteger(deliveryMaxFiles, override.deliveryMaxFiles),
                chooseInteger(deliveryMaxSymbols, override.deliveryMaxSymbols),
                chooseBoolean(deliveryPreferPreciseEditing, override.deliveryPreferPreciseEditing),
                chooseBoolean(deliveryForceBacklogSplit, override.deliveryForceBacklogSplit),
                chooseBoolean(deliveryRequireVerificationBeforeReview, override.deliveryRequireVerificationBeforeReview),
                mergeList(requiredEvidence, override.requiredEvidence),
                mergeList(mustFixFirst, override.mustFixFirst),
                mergeList(forbiddenDirections, override.forbiddenDirections),
                mergeList(acceptanceChecks, override.acceptanceChecks),
                mergeList(requiredCapabilitySurfaces, override.requiredCapabilitySurfaces),
                mergeIntegerList(targetSections, override.targetSections),
                chooseText(summary, override.summary),
                chooseText(changeRequest, override.changeRequest),
                chooseText(evidence, override.evidence),
                chooseText(actionItems, override.actionItems),
                chooseText(supervisorAction, override.supervisorAction),
                chooseText(supervisorReason, override.supervisorReason),
                mergeList(focus, override.focus),
                mergeList(constraints, override.constraints),
                chooseText(generationFailureSummary, override.generationFailureSummary),
                chooseText(generationFailureType, override.generationFailureType),
                chooseText(generationFailureEvidence, override.generationFailureEvidence),
                chooseText(generationFailureRetryHint, override.generationFailureRetryHint)
        );
    }

    private static List<FileChangePayload> chooseFileChanges(List<FileChangePayload> base, List<FileChangePayload> override) {
        if (override != null && !override.isEmpty()) {
            return List.copyOf(override);
        }
        return base == null ? List.of() : List.copyOf(base);
    }

    private static String chooseText(String base, String override) {
        return override != null && !override.isBlank() ? override : base;
    }

    private static Boolean chooseBoolean(Boolean base, Boolean override) {
        return override != null ? override : base;
    }

    private static Integer chooseInteger(Integer base, Integer override) {
        return override != null ? override : base;
    }

    private static List<String> mergeList(List<String> base, List<String> override) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (base != null) {
            base.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(String::trim)
                    .forEach(merged::add);
        }
        if (override != null) {
            override.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(String::trim)
                    .forEach(merged::add);
        }
        return List.copyOf(merged);
    }

    private static List<Integer> mergeIntegerList(List<Integer> base, List<Integer> override) {
        LinkedHashSet<Integer> merged = new LinkedHashSet<>();
        if (base != null) {
            base.stream()
                    .filter(item -> item != null && item > 0)
                    .forEach(merged::add);
        }
        if (override != null) {
            override.stream()
                    .filter(item -> item != null && item > 0)
                    .forEach(merged::add);
        }
        return List.copyOf(merged);
    }
}
