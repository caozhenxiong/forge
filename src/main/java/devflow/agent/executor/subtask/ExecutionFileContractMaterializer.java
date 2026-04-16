package devflow.agent.executor.subtask;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 把 accepted/effective structured change-set 基于当前 workspace state
 * materialize 为当前 attempt 的 live execution file contract。
 *
 * <p>它是 execution file contract 的唯一 materializer：
 * 1. 不读取 snapshot/state 持久化；
 * 2. 不缓存上一轮结果；
 * 3. 每次 attempt 入口基于 live workspace state 现算。
 */
public final class ExecutionFileContractMaterializer {

    public ExecutionFileContractSet materialize(Path projectPath, List<FileChange> changes) {
        if (projectPath == null || changes == null || changes.isEmpty()) {
            return ExecutionFileContractSet.empty();
        }
        Path normalizedProjectPath = projectPath.toAbsolutePath().normalize();
        List<ExecutionFileContract> contracts = new ArrayList<>();
        for (FileChange change : changes) {
            if (change == null || change.path() == null || change.path().isBlank() || change.action() == null) {
                continue;
            }
            Path relativePath = Path.of(change.path()).normalize();
            Path absolutePath = normalizedProjectPath.resolve(relativePath).normalize();
            ExecutionFileContractMode mode = switch (change.action()) {
                case DELETE -> ExecutionFileContractMode.DELETE;
                case WRITE -> Files.exists(absolutePath)
                        ? ExecutionFileContractMode.PATCH_EXISTING
                        : ExecutionFileContractMode.CREATE_NEW;
            };
            contracts.add(new ExecutionFileContract(relativePath, mode, change));
        }
        return new ExecutionFileContractSet(contracts);
    }
}
