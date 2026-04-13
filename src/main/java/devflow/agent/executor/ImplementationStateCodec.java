package devflow.agent.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * implementation_state.json 的唯一编解码入口。
 *
 * <p>implementation live control flow 依赖的字段必须在这里一次性校验，
 * 读取侧不允许再通过默认值、推导或 fallback 去猜状态。
 */
final class ImplementationStateCodec {

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

    ImplementationStateCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    String write(ImplementationStateSnapshot snapshot) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to render implementation state.", exception);
        }
    }

    ImplementationStateSnapshot readRequired(String stateJson) {
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
    }
}
