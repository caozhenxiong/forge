package devflow.agent.context;

import devflow.agent.util.ProjectPathSupport;
import java.util.Locale;

/**
 * 执行契约里稳定的入口类型枚举。
 *
 * <p>这一层负责统一入口类型的协议值、默认语义和项目路径匹配规则，
 * 避免多处继续硬编码 "html-entry"、"main-script" 这类字符串。
 */
public enum ExecutionEntryKind {
    UNSPECIFIED("unspecified"),
    HTML_ENTRY("html-entry"),
    MAIN_SCRIPT("main-script"),
    MAIN_CLASS("main-class"),
    COMMAND("command"),
    HTTP_ENDPOINT("http-endpoint"),
    IMPORTABLE_API("importable-api");

    private final String wireValue;

    ExecutionEntryKind(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public boolean impliesLaunchableEntry() {
        return switch (this) {
            case HTML_ENTRY, MAIN_SCRIPT, MAIN_CLASS, COMMAND, HTTP_ENDPOINT -> true;
            default -> false;
        };
    }

    public boolean impliesInteractiveSurface() {
        return this == HTML_ENTRY;
    }

    public boolean requiresResolvedHtmlEntry() {
        return this == HTML_ENTRY;
    }

    public String defaultLaunchAcceptanceSignal() {
        return switch (this) {
            case HTML_ENTRY -> "page-opens";
            case MAIN_SCRIPT, MAIN_CLASS, COMMAND -> "process-starts";
            case HTTP_ENDPOINT -> "service-starts";
            case IMPORTABLE_API -> "api-imports";
            default -> "runtime-starts";
        };
    }

    public boolean matchesProjectPath(String projectRelativePath) {
        if (projectRelativePath == null || projectRelativePath.isBlank()) {
            return false;
        }
        String path = projectRelativePath.toLowerCase(Locale.ROOT);
        return switch (this) {
            case HTML_ENTRY -> ProjectPathSupport.isHtml(path);
            case MAIN_SCRIPT -> ProjectPathSupport.isRuntimeScript(path)
                    || ProjectPathSupport.isPython(path)
                    || ProjectPathSupport.isGo(path)
                    || ProjectPathSupport.isShell(path);
            case MAIN_CLASS -> ProjectPathSupport.isJvmSource(path);
            case COMMAND -> ProjectPathSupport.isCommandCandidate(path);
            case HTTP_ENDPOINT -> ProjectPathSupport.isHttpEndpointCandidate(path);
            case IMPORTABLE_API -> ProjectPathSupport.isPreciseCode(path);
            case UNSPECIFIED -> ProjectPathSupport.isHtml(path)
                    || ProjectPathSupport.isPreciseCode(path)
                    || ProjectPathSupport.isShell(path);
        };
    }

    public static ExecutionEntryKind fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return UNSPECIFIED;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ExecutionEntryKind candidate : values()) {
            if (candidate.wireValue.equals(normalized)) {
                return candidate;
            }
        }
        return UNSPECIFIED;
    }
}
