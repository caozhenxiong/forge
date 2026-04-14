package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;

/**
 * 负责抓取 Playwright 运行时快照。
 */
final class PlaywrightRuntimeSnapshotSupport {

    private final PlaywrightProbeRunner probeRunner;

    PlaywrightRuntimeSnapshotSupport(
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper,
            PlaywrightExecutionPolicy playwrightExecutionPolicy
    ) {
        this.probeRunner = new PlaywrightProbeRunner(workspace, objectMapper, playwrightExecutionPolicy);
    }

    RuntimeSnapshot capture(Path projectPath, String entry) {
        return probeRunner.probe(projectPath, entry).runtimeSnapshot();
    }
}
