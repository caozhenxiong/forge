package devflow.agent.project;

import java.nio.file.Path;

public record WorkspaceChange(
        ChangeType type,
        Path path,
        String baselineContent,
        String currentContent
) {

    public enum ChangeType {
        ADDED,
        MODIFIED,
        DELETED
    }
}
