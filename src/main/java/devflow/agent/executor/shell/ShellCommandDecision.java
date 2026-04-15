package devflow.agent.executor.shell;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;
import java.util.List;

public record ShellCommandDecision(
        ShellCommandDisposition disposition,
        String reasonCode,
        String message,
        boolean retryable,
        String commandSummary,
        List<String> evidence,
        List<ShellPathIntent> pathIntents
) {

    public ShellCommandDecision {
        reasonCode = reasonCode == null ? "" : reasonCode.trim();
        message = message == null ? "" : message.trim();
        commandSummary = commandSummary == null ? "" : commandSummary.trim();
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        pathIntents = pathIntents == null ? List.of() : List.copyOf(pathIntents);
    }

    static ShellCommandDecision allowReadOnly(String commandSummary, List<String> evidence) {
        return allowReadOnly(commandSummary, evidence, List.of());
    }

    static ShellCommandDecision allowReadOnly(String commandSummary, List<String> evidence, List<ShellPathIntent> pathIntents) {
        return new ShellCommandDecision(
                ShellCommandDisposition.ALLOW_READ_ONLY,
                "",
                "",
                true,
                commandSummary,
                evidence,
                pathIntents
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
            List<String> evidence,
            List<ShellPathIntent> pathIntents
    ) {
        return new ShellCommandDecision(
                ShellCommandDisposition.DENY,
                reasonCode,
                message,
                retryable,
                commandSummary,
                evidence,
                pathIntents
        );
    }

    public boolean readOnly() {
        return disposition == ShellCommandDisposition.ALLOW_READ_ONLY;
    }

    public boolean allowsExecution() {
        return disposition != ShellCommandDisposition.DENY;
    }

    public List<Path> declaredWritePaths() {
        return pathIntents.stream()
                .filter(intent -> intent != null
                        && intent.path() != null
                        && (intent.kind() == ShellPathIntentKind.WRITE_FILE || intent.kind() == ShellPathIntentKind.DELETE_FILE))
                .map(ShellPathIntent::path)
                .distinct()
                .toList();
    }

    public List<Path> declaredTargetPaths() {
        return pathIntents.stream()
                .filter(intent -> intent != null && intent.path() != null)
                .map(ShellPathIntent::path)
                .distinct()
                .toList();
    }
}
