package devflow.agent.executor;

import java.util.List;

/**
 * 统一维护 implementation 完整性检查的稳定策略。
 *
 * <p>这里收敛的是两类容易散落的字面量：
 * 1. 显式占位/未实现标记的启发式列表；
 * 2. 证据采样上限这类稳定阈值。
 *
 * <p>后续如果要继续配置化，应优先从这里演进，而不是把字符串和数字重新写回检查器主体。
 */
public final class ImplementationCompletenessPolicy {

    private static final List<String> PLACEHOLDER_WORD_MARKERS = List.of("todo", "fixme", "placeholder", "stub");
    private static final List<String> PLACEHOLDER_PHRASE_MARKERS = List.of(
            "not implemented",
            "implement later",
            "to be implemented",
            "implementation goes here",
            "will be added here"
    );
    private static final List<String> PLACEHOLDER_CJK_MARKERS = List.of(
            "占位",
            "待实现",
            "后续补充",
            "将在这里添加",
            "稍后实现",
            "后续实现",
            "待补齐"
    );
    private static final List<String> NOT_IMPLEMENTED_MARKERS = List.of(
            "unsupportedoperationexception",
            "notimplementederror",
            "notimplementedexception",
            "thrownewerror(\"notimplemented",
            "thrownewerror('notimplemented",
            "panic(\"todo",
            "panic('todo"
    );
    private static final List<String> LOGGING_ONLY_CALL_PREFIXES = List.of(
            "console.log(",
            "console.info(",
            "console.warn(",
            "console.error(",
            "console.debug(",
            "console.trace(",
            "logger.debug(",
            "logger.info(",
            "logger.warn(",
            "logger.error(",
            "logger.trace("
    );
    private static final int MAX_EVIDENCE_ITEMS = 8;

    private ImplementationCompletenessPolicy() {
    }

    public static List<String> placeholderWordMarkers() {
        return PLACEHOLDER_WORD_MARKERS;
    }

    public static List<String> placeholderPhraseMarkers() {
        return PLACEHOLDER_PHRASE_MARKERS;
    }

    public static List<String> placeholderCjkMarkers() {
        return PLACEHOLDER_CJK_MARKERS;
    }

    public static List<String> notImplementedMarkers() {
        return NOT_IMPLEMENTED_MARKERS;
    }

    public static List<String> loggingOnlyCallPrefixes() {
        return LOGGING_ONLY_CALL_PREFIXES;
    }

    public static int maxEvidenceItems() {
        return MAX_EVIDENCE_ITEMS;
    }
}
