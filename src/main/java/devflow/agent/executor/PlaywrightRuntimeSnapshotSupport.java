package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;

/**
 * 负责抓取 Playwright 运行时快照。
 */
final class PlaywrightRuntimeSnapshotSupport {

    private final PlaywrightProbeRunner probeRunner;

    PlaywrightRuntimeSnapshotSupport(FileProjectWorkspace workspace, ObjectMapper objectMapper) {
        this.probeRunner = new PlaywrightProbeRunner(workspace, objectMapper);
    }

    RuntimeSnapshot capture(Path projectPath, String entry) {
        return probeRunner.probe(projectPath, entry).runtimeSnapshot();
    }
}
