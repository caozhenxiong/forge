package devflow.agent.executor;

public class GenerationFailureException extends RuntimeException {

    private final GenerationFailureReport report;
    private final FilePatchProgressState patchProgressState;

    public GenerationFailureException(GenerationFailureReport report) {
        this(report, null);
    }

    public GenerationFailureException(GenerationFailureReport report, FilePatchProgressState patchProgressState) {
        super(report == null ? "Generation failure" : report.summary());
        this.report = report;
        this.patchProgressState = patchProgressState;
    }

    public GenerationFailureReport report() {
        return report;
    }

    public FilePatchProgressState patchProgressState() {
        return patchProgressState;
    }

    public GenerationFailureException withPatchProgressState(FilePatchProgressState progressState) {
        return new GenerationFailureException(report, progressState);
    }
}
