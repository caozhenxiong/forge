package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * implementation planning 单元级载荷解析器。
 *
 * <p>流程固定为：
 * 1. 先做本地机械修复；
 * 2. 解析失败时只修当前单元；
 * 3. 仍失败再抛出 typed exception。
 */
final class ImplementationPlanningPayloadParser {

    private final StructuredPayloadReader structuredPayloadReader;
    private final int maxPayloadRepairAttempts;
    private final ImplementationPlanningRepairSupport repairSupport;

    ImplementationPlanningPayloadParser(
            LlmProvider llmProvider,
            ObjectMapper objectMapper,
            int maxPayloadRepairAttempts
    ) {
        this.structuredPayloadReader = new StructuredPayloadReader(objectMapper);
        this.maxPayloadRepairAttempts = maxPayloadRepairAttempts;
        this.repairSupport = new ImplementationPlanningRepairSupport(llmProvider);
    }

    ParseResult<ImplementationOutline> parseOutline(
            String response,
            ImplementationEventJournal eventJournal,
            int attempt
    ) {
        return parseWithRepair(
                response,
                eventJournal,
                ImplementationPlanningUnitKind.OUTLINE,
                "outline",
                attempt,
                ImplementationOutline.class,
                repairSupport::repairOutline,
                this::validateOutline
        );
    }

    ParseResult<ImplementationSubtaskDetail> parseSubtaskDetail(
            String subtaskId,
            String response,
            ImplementationEventJournal eventJournal,
            int attempt
    ) {
        return parseWithRepair(
                response,
                eventJournal,
                ImplementationPlanningUnitKind.SUBTASK_DETAIL,
                subtaskId,
                attempt,
                ImplementationSubtaskDetail.class,
                repairSupport::repairSubtaskDetail,
                this::validateSubtaskDetail
        );
    }

    private <T> ParseResult<T> parseWithRepair(
            String response,
            ImplementationEventJournal eventJournal,
            ImplementationPlanningUnitKind unitKind,
            String unitId,
            int attempt,
            Class<T> type,
            RepairFunction repairFunction,
            PayloadValidator<T> validator
    ) {
        String candidate = locallyRepairJson(response);
        String repaired = null;
        Exception lastException = null;
        for (int repairAttempt = 1; repairAttempt <= maxPayloadRepairAttempts; repairAttempt++) {
            try {
                T payload = structuredPayloadReader.readJsonObject(candidate, type);
                validator.validate(payload);
                return new ParseResult<>(payload, repaired);
            } catch (Exception exception) {
                lastException = exception;
                if (repairAttempt == maxPayloadRepairAttempts) {
                    break;
                }
                repaired = repairFunction.repair(candidate, exception);
                if (eventJournal != null && repaired != null && !repaired.isBlank()) {
                    eventJournal.writeAttemptScopedPlanningArtifact(
                            ImplementationPlanningArtifactNames.repairResponse(unitKind, unitId),
                            attempt,
                            repaired
                    );
                }
                candidate = locallyRepairJson(repaired);
            }
        }
        throw new ImplementationPlanningException(
                ImplementationPlanningFailureReason.PLAN_PARSE_FAILED,
                "Failed to parse %s (%s): %s".formatted(
                        unitKind.artifactKey(),
                        blankIfNull(unitId),
                        lastException == null ? "unknown parse error" : lastException.getMessage()
                ),
                lastException
        );
    }

    private void validateOutline(ImplementationOutline outline) {
        if (outline == null || outline.subtasks() == null || outline.subtasks().isEmpty()) {
            throw new IllegalStateException("Implementation outline must contain subtasks");
        }
        List<String> seenIds = new ArrayList<>();
        for (ImplementationOutlineSubtask subtask : outline.subtasks()) {
            if (subtask == null) {
                throw new IllegalStateException("Implementation outline contains null subtask");
            }
            String id = blankIfNull(subtask.id()).trim();
            if (id.isBlank()) {
                throw new IllegalStateException("Implementation outline subtask must contain id");
            }
            if (!seenIds.add(id)) {
                throw new IllegalStateException("Implementation outline subtask id must be unique: " + id);
            }
            if (blankIfNull(subtask.title()).isBlank() || blankIfNull(subtask.goal()).isBlank()) {
                throw new IllegalStateException("Implementation outline subtask must contain title and goal");
            }
            if (subtask.deliveryMode() == null) {
                throw new IllegalStateException("Implementation outline subtask must contain deliveryMode");
            }
        }
    }

    private void validateSubtaskDetail(ImplementationSubtaskDetail detail) {
        if (detail == null || blankIfNull(detail.subtaskId()).isBlank()) {
            throw new IllegalStateException("Implementation subtask detail must contain subtaskId");
        }
        if (detail.changes() == null || detail.changes().isEmpty()) {
            throw new IllegalStateException("Implementation subtask detail must contain changes");
        }
        for (ImplementationSubtaskDetailChange change : detail.changes()) {
            if (change == null) {
                throw new IllegalStateException("Implementation subtask detail cannot contain null change");
            }
            if (blankIfNull(change.path()).isBlank()) {
                throw new IllegalStateException("Implementation subtask detail change must contain path");
            }
            if (change.action() == null) {
                throw new IllegalStateException("Implementation subtask detail change must contain action");
            }
            if (blankIfNull(change.reason()).isBlank()) {
                throw new IllegalStateException("Implementation subtask detail change must contain reason");
            }
        }
    }

    private String locallyRepairJson(String response) {
        String candidate = blankIfNull(response)
                .replace("```json", "")
                .replace("```", "")
                .trim();
        int start = candidate.indexOf('{');
        if (start > 0) {
            candidate = candidate.substring(start);
        }
        String strippedTrailing = candidate.stripTrailing();
        if (strippedTrailing.endsWith("}")) {
            int end = strippedTrailing.lastIndexOf('}');
            candidate = strippedTrailing.substring(0, end + 1);
        } else {
            candidate = strippedTrailing;
        }
        if (candidate.startsWith("{")) {
            int missingBrackets = count(candidate, '[') - count(candidate, ']');
            if (missingBrackets > 0) {
                candidate = candidate + "]".repeat(missingBrackets);
            }
            int missingBraces = count(candidate, '{') - count(candidate, '}');
            if (missingBraces > 0) {
                candidate = candidate + "}".repeat(missingBraces);
            }
        }
        return candidate;
    }

    private int count(String value, char target) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == target) {
                count++;
            }
        }
        return count;
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private interface RepairFunction {
        String repair(String brokenResponse, Exception exception);
    }

    private interface PayloadValidator<T> {
        void validate(T payload);
    }

    record ParseResult<T>(
            T payload,
            String repairedResponse
    ) {
    }
}
