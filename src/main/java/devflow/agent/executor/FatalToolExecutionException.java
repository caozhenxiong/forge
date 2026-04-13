package devflow.agent.executor;

final class FatalToolExecutionException extends RuntimeException {

    private final String summary;
    private final String evidence;
    private final String retryHint;

    FatalToolExecutionException(String summary, String evidence, String retryHint) {
        super(summary);
        this.summary = summary == null ? "Tool execution failed fatally." : summary;
        this.evidence = evidence == null ? "" : evidence;
        this.retryHint = retryHint == null ? "" : retryHint;
    }

    String summary() {
        return summary;
    }

    String evidence() {
        return evidence;
    }

    String retryHint() {
        return retryHint;
    }
}
