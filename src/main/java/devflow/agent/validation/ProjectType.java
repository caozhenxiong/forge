package devflow.agent.validation;

/**
 * 统一维护项目类型的稳定枚举。
 *
 * <p>这些值会被验证、测试工具选择和上下文投影共同消费，
 * 因此不应继续在多个模块里散落硬写。
 */
public enum ProjectType {
    UNKNOWN("unknown"),
    JAVA_MAVEN("java-maven"),
    JAVA_GRADLE("java-gradle"),
    WEB_APP("web-app"),
    NODE_APP("node-app"),
    WEB_STATIC("web-static");

    private final String key;

    ProjectType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static ProjectType fromKey(String key) {
        if (key == null || key.isBlank()) {
            return UNKNOWN;
        }
        for (ProjectType value : values()) {
            if (value.key.equalsIgnoreCase(key.trim())) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
