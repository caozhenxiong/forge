package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.util.List;

public class PlaywrightCaseExecutor {

    private final PlaywrightCaseRunSupport caseRunSupport;
    private final PlaywrightRuntimeSnapshotSupport runtimeSnapshotSupport;

    public PlaywrightCaseExecutor(FileProjectWorkspace workspace, ObjectMapper objectMapper) {
        PlaywrightSupport support = new PlaywrightSupport();
        this.caseRunSupport = new PlaywrightCaseRunSupport(workspace, objectMapper, support);
        this.runtimeSnapshotSupport = new PlaywrightRuntimeSnapshotSupport(workspace, objectMapper);
    }

    public List<TestCaseResult> execute(Path projectPath, TestCasePlan plan) {
        return caseRunSupport.execute(projectPath, plan);
    }

    public RuntimeSnapshot captureRuntimeSnapshot(Path projectPath, String entry) {
        return runtimeSnapshotSupport.capture(projectPath, entry);
    }
}
