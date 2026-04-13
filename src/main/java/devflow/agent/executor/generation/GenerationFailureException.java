package devflow.agent.executor.generation;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.FileEditAttemptState;

public class GenerationFailureException extends RuntimeException {

    private final GenerationFailureReport report;
    private final FileEditAttemptState editAttemptState;

    public GenerationFailureException(GenerationFailureReport report) {
        this(report, null);
    }

    public GenerationFailureException(GenerationFailureReport report, FileEditAttemptState editAttemptState) {
        super(report == null ? "Generation failure" : report.summary());
        this.report = report;
        this.editAttemptState = editAttemptState;
    }

    public GenerationFailureReport report() {
        return report;
    }

    public FileEditAttemptState editAttemptState() {
        return editAttemptState;
    }

    public GenerationFailureException withEditAttemptState(FileEditAttemptState progressState) {
        return new GenerationFailureException(report, progressState);
    }
}
