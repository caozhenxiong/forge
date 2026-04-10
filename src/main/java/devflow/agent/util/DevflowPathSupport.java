package devflow.agent.util;

import devflow.agent.orchestrator.RunFileNames;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 集中维护 devflow 运行目录的稳定路径布局。
 *
 * <p>这类目录名和文件名会被 orchestrator / artifact / review / workspace
 * 多层同时读写；如果继续散落成裸字符串，后续改名、排查不同步或迁移目录
 * 结构时会非常痛苦。
 */
public final class DevflowPathSupport {

    public static final String DEVFLOW_DIRECTORY = ".devflow";
    public static final String RUNS_DIRECTORY = "runs";
    public static final String BASELINE_DIRECTORY = "baseline";
    public static final String WRITE_TRANSACTIONS_DIRECTORY = "write-transactions";
    public static final String ACTIVE_DIRECTORY = "active";
    public static final String FAILED_DIRECTORY = "failed";

    private DevflowPathSupport() {
    }

    public static Path workspaceRoot(Path projectPath) {
        return projectPath.resolve(DEVFLOW_DIRECTORY);
    }

    public static Path runsRoot(Path projectPath) {
        return workspaceRoot(projectPath).resolve(RUNS_DIRECTORY);
    }

    public static Path runDirectory(Path projectPath, UUID runId) {
        return runsRoot(projectPath).resolve(runId.toString());
    }

    public static Path runRecord(Path projectPath, UUID runId) {
        return runDirectory(projectPath, runId).resolve(RunFileNames.RUN_RECORD);
    }

    public static Path baselineRoot(Path projectPath, UUID runId) {
        return runDirectory(projectPath, runId).resolve(BASELINE_DIRECTORY);
    }

    public static Path auxiliaryArtifact(Path projectPath, UUID runId, String fileName) {
        return runDirectory(projectPath, runId).resolve(fileName);
    }

    public static Path writeTransactionsRoot(Path projectPath) {
        return workspaceRoot(projectPath).resolve(WRITE_TRANSACTIONS_DIRECTORY);
    }

    public static Path writeTransactionsActiveRoot(Path projectPath) {
        return writeTransactionsRoot(projectPath).resolve(ACTIVE_DIRECTORY);
    }

    public static Path writeTransactionsFailedRoot(Path projectPath) {
        return writeTransactionsRoot(projectPath).resolve(FAILED_DIRECTORY);
    }

    public static Path writeTransactionStagingRoot(Path projectPath, String transactionId) {
        return writeTransactionsActiveRoot(projectPath).resolve(transactionId);
    }
}
