package devflow.agent.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 统一读取模型返回的结构化 JSON 载荷。
 *
 * <p>这里把“提取 JSON 对象”和“反序列化 JSON”从调用方里抽出来，
 * 避免各处再通过异常文本判断载荷失败类型。
 */
public final class StructuredPayloadReader {

    private final ObjectMapper objectMapper;

    public StructuredPayloadReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String extractJsonObject(String response) {
        int start = response == null ? -1 : response.indexOf('{');
        int end = response == null ? -1 : response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new StructuredPayloadException(
                    StructuredPayloadFailureReason.JSON_OBJECT_MISSING,
                    "No JSON object found in model response"
            );
        }
        return response.substring(start, end + 1);
    }

    public <T> T readJsonObject(String response, Class<T> type) {
        try {
            return objectMapper.readValue(extractJsonObject(response), type);
        } catch (StructuredPayloadException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new StructuredPayloadException(
                    StructuredPayloadFailureReason.JSON_PAYLOAD_INVALID,
                    "Failed to parse structured payload as " + type.getSimpleName(),
                    exception
            );
        }
    }
}
