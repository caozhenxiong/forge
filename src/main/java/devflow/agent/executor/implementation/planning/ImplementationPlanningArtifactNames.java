package devflow.agent.executor.implementation.planning;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 统一维护 implementation planning 的辅助产物文件名。
 *
 * <p>planning 已拆成 outline/detail 两层后，raw / repair / accepted 产物也必须按同一套命名规则落盘，
 * 避免日志和 run 目录再次散落裸字符串。
 */
final class ImplementationPlanningArtifactNames {

    private ImplementationPlanningArtifactNames() {
    }

    static String acceptedOutline() {
        return "implementation_planning_outline.accepted.json";
    }

    static String acceptedSubtaskDetail(String subtaskId) {
        return "implementation_planning_subtask-%s.accepted.json".formatted(normalizeId(subtaskId));
    }

    static String rawResponse(ImplementationPlanningUnitKind unitKind, String unitId) {
        return "implementation_planning_%s-%s.raw.txt".formatted(unitKind.artifactKey(), normalizeId(unitId));
    }

    static String repairResponse(ImplementationPlanningUnitKind unitKind, String unitId) {
        return "implementation_planning_%s-%s.repair.txt".formatted(unitKind.artifactKey(), normalizeId(unitId));
    }

    private static String normalizeId(String value) {
        String trimmed = value == null || value.isBlank() ? "unknown" : value.trim();
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int index = 0; index < trimmed.length(); index++) {
            char ch = trimmed.charAt(index);
            if ((ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')) {
                builder.append(Character.toLowerCase(ch));
                continue;
            }
            if (ch == '-' || ch == '_') {
                builder.append(ch);
                continue;
            }
            builder.append('-');
        }
        return builder.toString().replaceAll("-{2,}", "-");
    }
}
