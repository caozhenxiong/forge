package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import devflow.agent.executor.subtask.Subtask;
/**
 * 本地把 accepted outline 与 accepted detail 组装成最终 {@link ImplementationPlan}。
 *
 * <p>最终计划的权威状态来自本地组装结果，而不是要求模型一次性返回完整 plan JSON。
 */
final class ImplementationPlanAssembler {

    ImplementationPlan assemble(
            ImplementationOutline outline,
            Map<String, ImplementationSubtaskDetail> acceptedDetails
    ) {
        if (outline == null || outline.subtasks() == null || outline.subtasks().isEmpty()) {
            throw new IllegalStateException("Implementation outline must contain subtasks");
        }
        Map<String, ImplementationSubtaskDetail> details = acceptedDetails == null
                ? Map.of()
                : new LinkedHashMap<>(acceptedDetails);
        List<Subtask> subtasks = outline.subtasks().stream()
                .map(subtask -> toSubtask(subtask, details.get(blankIfNull(subtask.id()).trim())))
                .toList();
        return new ImplementationPlan(blankIfNull(outline.summary()).trim(), subtasks);
    }

    private Subtask toSubtask(ImplementationOutlineSubtask outlineSubtask, ImplementationSubtaskDetail detail) {
        if (outlineSubtask == null) {
            throw new IllegalStateException("Implementation outline contains null subtask");
        }
        if (detail == null || detail.changes() == null || detail.changes().isEmpty()) {
            throw new IllegalStateException("Missing accepted detail for implementation subtask: " + outlineSubtask.id());
        }
        return new Subtask(
                outlineSubtask.title(),
                outlineSubtask.goal(),
                safeList(outlineSubtask.coverageRefs()),
                safeList(outlineSubtask.ownedCapabilities()),
                safeList(outlineSubtask.deferredCapabilities()),
                safeList(outlineSubtask.acceptanceCriteria()),
                outlineSubtask.runnableMilestone(),
                outlineSubtask.deliveryMode(),
                detail.changes().stream()
                        .map(change -> new FileChange(
                                change.path(),
                                change.action(),
                                change.reason()
                        ))
                        .toList()
        );
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
