package devflow.agent.executor;

import java.nio.file.Path;
import java.util.List;

record ShellCommandDecision(
        ShellCommandDisposition disposition,
        String reasonCode,
        String message,
        boolean retryable,
        String commandSummary,
        List<String> evidence,
        List<ShellPathIntent> pathIntents
) {

    ShellCommandDecision {
        reasonCode = reasonCode == null ? "" : reasonCode.trim();
        message = message == null ? "" : message.trim();
        commandSummary = commandSummary == null ? "" : commandSummary.trim();
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        pathIntents = pathIntents == null ? List.of() : List.copyOf(pathIntents);
    }

    static ShellCommandDecision allowReadOnly(String commandSummary, List<String> evidence) {
        return new ShellCommandDecision(
                ShellCommandDisposition.ALLOW_READ_ONLY,
                "",
                "",
                true,
                commandSummary,
                evidence,
                List.of()
        );
    }

    static ShellCommandDecision allowWrite(String commandSummary, List<String> evidence, List<ShellPathIntent> pathIntents) {
        return new ShellCommandDecision(
                ShellCommandDisposition.ALLOW_WRITE,
                "",
                "",
                true,
                commandSummary,
                evidence,
                pathIntents
        );
    }

    static ShellCommandDecision deny(
            String commandSummary,
            String reasonCode,
            String message,
            boolean retryable,
            List<String> evidence
    ) {
        return new ShellCommandDecision(
                ShellCommandDisposition.DENY,
                reasonCode,
                message,
                retryable,
                commandSummary,
                evidence,
                List.of()
        );
    }

    boolean readOnly() {
        return disposition == ShellCommandDisposition.ALLOW_READ_ONLY;
    }

    boolean allowsExecution() {
        return disposition != ShellCommandDisposition.DENY;
    }

    List<Path> declaredWritePaths() {
        return pathIntents.stream()
                .filter(intent -> intent != null
                        && intent.path() != null
                        && (intent.kind() == ShellPathIntentKind.WRITE_FILE || intent.kind() == ShellPathIntentKind.DELETE_FILE))
                .map(ShellPathIntent::path)
                .distinct()
                .toList();
    }
}

enum ShellCommandDisposition {
    ALLOW_READ_ONLY,
    ALLOW_WRITE,
    DENY
}

record ShellPathIntent(
        Path path,
        ShellPathIntentKind kind
) {
}

enum ShellPathIntentKind {
    READ_FILE,
    WRITE_FILE,
    DELETE_FILE,
    PREPARE_DIRECTORY
}
