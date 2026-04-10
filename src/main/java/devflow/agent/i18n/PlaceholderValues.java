package devflow.agent.i18n;

/**
 * 统一维护跨模块复用的占位值，避免在流程、协议和渲染层到处散落
 * "(none)"、"(无)" 这类难维护的魔法字符串。
 *
 * <p>规则：
 * 1. 机器可消费的协议占位统一使用英文 {@value #MACHINE_NONE}；
 * 2. 面向人的展示占位根据文档语言选择；
 * 3. 判断“空占位”时统一走这里的方法，而不是各处手写字符串比较。
 */
public final class PlaceholderValues {

    public static final String MACHINE_NONE = "(none)";
    public static final String HUMAN_NONE_ZH = "(无)";
    public static final String MACHINE_EMPTY = "(empty)";
    public static final String MACHINE_UNKNOWN = "(unknown)";
    public static final String MACHINE_NULL = "null";
    public static final String MACHINE_NEW_FILE = "(new file)";
    public static final String MACHINE_NO_RELATED_FILES = "(no related files)";
    public static final String HUMAN_UNKNOWN_ZH = "(未知)";
    public static final String UNKNOWN_FAILURE = "(unknown failure)";
    public static final String INLINE_TRUNCATED_SUFFIX = " ...<truncated>";
    public static final String TRUNCATED_SUFFIX = "\n...<truncated>";
    public static final String TRUNCATED_MIDDLE = "\n...<truncated middle>...\n";

    private PlaceholderValues() {
    }

    public static String machineNone() {
        return MACHINE_NONE;
    }

    public static String machineEmpty() {
        return MACHINE_EMPTY;
    }

    public static String machineUnknown() {
        return MACHINE_UNKNOWN;
    }

    public static String machineNull() {
        return MACHINE_NULL;
    }

    public static String machineNewFile() {
        return MACHINE_NEW_FILE;
    }

    public static String machineNoRelatedFiles() {
        return MACHINE_NO_RELATED_FILES;
    }

    public static String bulletMachineNone() {
        return "- " + MACHINE_NONE;
    }

    public static String none(DocumentLanguage language) {
        return language == null ? MACHINE_NONE : language.choose(HUMAN_NONE_ZH, MACHINE_NONE);
    }

    public static String bulletNone(DocumentLanguage language) {
        return "- " + none(language);
    }

    public static String orMachineNone(String value) {
        return value == null || value.isBlank() ? MACHINE_NONE : value.trim();
    }

    public static String orMachineEmpty(String value) {
        return value == null || value.isBlank() ? MACHINE_EMPTY : value.trim();
    }

    public static String orMachineUnknown(String value) {
        return value == null || value.isBlank() ? MACHINE_UNKNOWN : value.trim();
    }

    public static String orMachineNull(String value) {
        return value == null || value.isBlank() ? MACHINE_NULL : value.trim();
    }

    public static String orNone(String value, DocumentLanguage language) {
        return value == null || value.isBlank() ? none(language) : value.trim();
    }

    public static String unknown(DocumentLanguage language) {
        return language == null ? MACHINE_UNKNOWN : language.choose(HUMAN_UNKNOWN_ZH, MACHINE_UNKNOWN);
    }

    public static String orUnknown(String value, DocumentLanguage language) {
        return value == null || value.isBlank() ? unknown(language) : value.trim();
    }

    public static String orBulletNone(String rendered, DocumentLanguage language) {
        return rendered == null || rendered.isBlank() ? bulletNone(language) : rendered;
    }

    public static String truncateTail(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return MACHINE_EMPTY;
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + TRUNCATED_SUFFIX;
    }

    public static String truncateInline(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return MACHINE_EMPTY;
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + INLINE_TRUNCATED_SUFFIX;
    }

    public static String truncateMiddle(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return MACHINE_EMPTY;
        }
        if (value.length() <= maxChars) {
            return value;
        }
        int half = maxChars / 2;
        return value.substring(0, half) + TRUNCATED_MIDDLE + value.substring(value.length() - half);
    }

    public static boolean isNoneLiteral(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return MACHINE_NONE.equalsIgnoreCase(trimmed) || HUMAN_NONE_ZH.equals(trimmed);
    }

    public static boolean isNullLiteral(String value) {
        if (value == null) {
            return false;
        }
        return MACHINE_NULL.equalsIgnoreCase(value.trim());
    }

    public static boolean isNewFileLiteral(String value) {
        if (value == null) {
            return false;
        }
        return MACHINE_NEW_FILE.equalsIgnoreCase(value.trim());
    }
}
