package devflow.agent.executor;

import java.util.List;

/**
 * 当前子任务的责任域摘要。
 * 它把 title/goal/ownedCapabilities/acceptanceCriteria 收成统一词表，用于判断发现是否属于本轮阻塞项。
 */
final class ImplementationCompletenessScopeProfile {

    private final String ownedCorpus;
    private final boolean alwaysBlock;

    private ImplementationCompletenessScopeProfile(String ownedCorpus, boolean alwaysBlock) {
        this.ownedCorpus = ownedCorpus;
        this.alwaysBlock = alwaysBlock;
    }

    static ImplementationCompletenessScopeProfile from(Subtask subtask) {
        if (subtask == null) {
            return new ImplementationCompletenessScopeProfile("", true);
        }
        List<String> deferred = subtask.deferredCapabilities() == null ? List.of() : subtask.deferredCapabilities();
        boolean alwaysBlock = deferred.isEmpty();
        StringBuilder builder = new StringBuilder();
        appendValues(builder, List.of(subtask.title(), subtask.goal()));
        appendValues(builder, subtask.ownedCapabilities());
        appendValues(builder, subtask.acceptanceCriteria());
        return new ImplementationCompletenessScopeProfile(normalize(builder.toString()), alwaysBlock);
    }

    boolean matches(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return alwaysBlock;
        }
        if (alwaysBlock) {
            return true;
        }
        if (ownedCorpus.isBlank()) {
            return false;
        }
        return ownedCorpus.contains(normalized)
                || normalized.contains(ownedCorpus)
                || ownedCorpus.contains(tokenHead(normalized))
                || tokenHead(ownedCorpus).contains(normalized);
    }

    boolean alwaysBlock() {
        return alwaysBlock;
    }

    private static void appendValues(StringBuilder builder, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(value);
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean previousSeparator = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isAlphabetic(current)
                    || Character.isDigit(current)
                    || Character.UnicodeScript.of(current) == Character.UnicodeScript.HAN
                    || current == '_') {
                builder.append(Character.toLowerCase(current));
                previousSeparator = false;
                continue;
            }
            if (!previousSeparator && !builder.isEmpty()) {
                builder.append(' ');
            }
            previousSeparator = true;
        }
        return builder.toString().trim();
    }

    private static String tokenHead(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return "";
        }
        int space = normalized.indexOf(' ');
        return space < 0 ? normalized : normalized.substring(0, space);
    }
}
