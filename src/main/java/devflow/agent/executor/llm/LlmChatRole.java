package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 统一维护 chat provider 的稳定消息角色。
 *
 * <p>Forge 的 coding runtime 现在以 conversation/tool loop 为唯一主路径，
 * 因此 role 不能再在 provider、tool runtime、prompt 组装器里各写一份字符串。
 */
public enum LlmChatRole {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");

    private final String wireValue;

    LlmChatRole(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
