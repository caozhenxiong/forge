package devflow.agent.executor.tools;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

public final class FatalToolExecutionException extends RuntimeException {

    private final String summary;
    private final String evidence;
    private final String retryHint;

    public FatalToolExecutionException(String summary, String evidence, String retryHint) {
        super(summary);
        this.summary = summary == null ? "Tool execution failed fatally." : summary;
        this.evidence = evidence == null ? "" : evidence;
        this.retryHint = retryHint == null ? "" : retryHint;
    }

    public String summary() {
        return summary;
    }

    public String evidence() {
        return evidence;
    }

    public String retryHint() {
        return retryHint;
    }
}
