package devflow.agent.artifact;

import devflow.agent.executor.testing.TestExecutionBundle;
import devflow.agent.executor.testing.TestExecutor;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import java.nio.file.Path;

/**
 * TEST 阶段子门面。
 *
 * <p>负责 test 阶段的上游 artifact intake、测试执行和测试产物落盘。
 */
final class TestStageComposer {

    private final FileArtifactStore artifactStore;
    private final TestExecutor testExecutor;
    private final StageArtifactInputResolver inputResolver;

    TestStageComposer(
            FileArtifactStore artifactStore,
            TestExecutor testExecutor,
            StageArtifactInputResolver inputResolver
    ) {
        this.artifactStore = artifactStore;
        this.testExecutor = testExecutor;
        this.inputResolver = inputResolver;
    }

    String compose(Path projectPath, RunRecord runRecord, String note) {
        String prd = inputResolver.requiredStageArtifact(projectPath, runRecord, StageType.PRD);
        String design = inputResolver.requiredStageArtifact(projectPath, runRecord, StageType.DESIGN);
        String implementation = inputResolver.requiredStageArtifact(projectPath, runRecord, StageType.IMPLEMENTATION);
        TestExecutionBundle bundle = testExecutor.execute(
                projectPath,
                runRecord.goal(),
                runRecord.constraints(),
                prd,
                design,
                implementation,
                note
        );
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.TEST_CASES, bundle.testCasesMarkdown());
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.TEST_RUNTIME_SNAPSHOT, bundle.runtimeSnapshotMarkdown());
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.TEST_EXECUTION, bundle.executionMarkdown());
        return bundle.reportMarkdown();
    }
}
