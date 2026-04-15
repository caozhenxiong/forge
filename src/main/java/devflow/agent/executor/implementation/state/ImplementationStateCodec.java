package devflow.agent.executor.implementation.state;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.review.ImplementationPatchTarget;
import java.util.List;

/**
 * implementation_state.json 的唯一编解码入口。
 *
 * <p>implementation live control flow 依赖的字段必须在这里一次性校验，
 * 读取侧不允许再通过默认值、推导或 fallback 去猜状态。
 */
public final class ImplementationStateCodec {

    private static final List<String> REQUIRED_LIVE_CONTROL_FIELDS = List.of(
            "planCompleted",
            "stageReady",
            "incompleteSubtasks",
            "continuationMode",
            "continuationSummary",
            "continuationChangeRequest",
            "continuationEvidence",
            "continuationActionItems",
            "continuationOverrideChanges",
            "continuationPatchTarget",
            "continuationReasonCode"
    );

    private final ObjectMapper objectMapper;

    public ImplementationStateCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public String write(ImplementationStateSnapshot snapshot) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to render implementation state.", exception);
        }
    }

    public ImplementationStateSnapshot readRequired(String stateJson) {
        if (stateJson == null || stateJson.isBlank()) {
            throw new IllegalStateException("Missing implementation_state auxiliary artifact.");
        }
        try {
            JsonNode root = objectMapper.readTree(stateJson);
            validateRequiredFields(root);
            return objectMapper.treeToValue(root, ImplementationStateSnapshot.class);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse implementation_state auxiliary artifact.", exception);
        }
    }

    private void validateRequiredFields(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("Invalid implementation_state auxiliary artifact.");
        }
        for (String fieldName : REQUIRED_LIVE_CONTROL_FIELDS) {
            if (!root.has(fieldName)) {
                throw new IllegalStateException(
                        "Invalid implementation_state auxiliary artifact: missing field '" + fieldName + "'."
                );
            }
        }
        validateCanonicalRepairPackage(root);
    }

    private void validateCanonicalRepairPackage(JsonNode root) {
        JsonNode modeNode = root.get("continuationMode");
        JsonNode patchTargetNode = root.get("continuationPatchTarget");
        JsonNode overrideChangesNode = root.get("continuationOverrideChanges");
        if (modeNode == null || patchTargetNode == null) {
            return;
        }
        ImplementationContinuationMode continuationMode = parseEnum(
                ImplementationContinuationMode.class,
                modeNode.asText(),
                "continuationMode"
        );
        ImplementationPatchTarget patchTarget = parseEnum(
                ImplementationPatchTarget.class,
                patchTargetNode.asText(),
                "continuationPatchTarget"
        );
        if (continuationMode.patchContinue()) {
            if (!patchTarget.concretePatch()) {
                throw new IllegalStateException(
                        "Invalid implementation_state auxiliary artifact: PATCH_CONTINUE requires a concrete continuationPatchTarget."
                );
            }
            if (overrideChangesNode == null || !overrideChangesNode.isArray() || overrideChangesNode.isEmpty()) {
                throw new IllegalStateException(
                        "Invalid implementation_state auxiliary artifact: concrete continuation patch requires continuationOverrideChanges."
                );
            }
            return;
        }
        if (continuationMode == ImplementationContinuationMode.MID_PLAN_CONTINUE
                && (patchTarget.concretePatch()
                || (overrideChangesNode != null && overrideChangesNode.isArray() && !overrideChangesNode.isEmpty()))) {
            throw new IllegalStateException(
                    "Invalid implementation_state auxiliary artifact: MID_PLAN_CONTINUE cannot carry a concrete continuation patch."
            );
        }
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String rawValue, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalStateException(
                    "Invalid implementation_state auxiliary artifact: missing field '" + fieldName + "'."
            );
        }
        try {
            return Enum.valueOf(enumType, rawValue.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Invalid implementation_state auxiliary artifact: unknown " + fieldName + " '" + rawValue + "'.",
                    exception
            );
        }
    }
}
