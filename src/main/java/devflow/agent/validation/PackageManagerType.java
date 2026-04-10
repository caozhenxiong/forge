package devflow.agent.validation;

/**
 * 统一维护项目包管理器的稳定枚举。
 *
 * <p>主流程代码不再直接依赖 "npm"/"pnpm"/"yarn" 这类散落字符串。
 */
public enum PackageManagerType {
    NONE("none"),
    NPM("npm"),
    PNPM("pnpm"),
    YARN("yarn");

    private final String key;

    PackageManagerType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static PackageManagerType fromKey(String key) {
        if (key == null || key.isBlank()) {
            return NONE;
        }
        for (PackageManagerType value : values()) {
            if (value.key.equalsIgnoreCase(key.trim())) {
                return value;
            }
        }
        return NONE;
    }
}
