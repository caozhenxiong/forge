package devflow.agent.executor;

import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.project.WriteTransaction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责生成结果的事务写盘与本地校验。
 *
 * <p>文件级 patch 主链在提交前必须经过两步：
 * 1. 把候选内容写入 staging；
 * 2. 用 GeneratedContentGate 校验候选内容是否合法。
 *
 * <p>把这段流程抽出来后，`FileEditCoordinator` 不再直接管理事务列表和失败回滚细节，
 * 只保留“生成什么内容、何时提交”的编排职责。
 */
final class GeneratedFileCommitSupport {

    private final FileProjectWorkspace workspace;
    private final GeneratedContentGate generatedContentGate;

    GeneratedFileCommitSupport(FileProjectWorkspace workspace, GeneratedContentGate generatedContentGate) {
        this.workspace = workspace;
        this.generatedContentGate = generatedContentGate;
    }

    void commitGeneratedOutput(Path projectPath, Subtask subtask, FileChange primaryChange, GeneratedFileOutput output) {
        Path relativePath = primaryChange == null || primaryChange.path() == null
                ? null
                : Path.of(primaryChange.path()).normalize();
        List<WriteTransaction> transactions = new ArrayList<>();
        try {
            transactions.add(stageValidatedWrite(
                    projectPath,
                    relativePath,
                    output.primaryContent(),
                    buildGateInput(projectPath, relativePath, output.primaryContent(), subtask, primaryChange, output)
            ));
            for (GeneratedAuxiliaryWrite auxiliaryWrite : output.auxiliaryWrites()) {
                transactions.add(stageValidatedWrite(
                        projectPath,
                        auxiliaryWrite.relativePath(),
                        auxiliaryWrite.content(),
                        new GeneratedContentGateInput(projectPath, auxiliaryWrite.relativePath(), auxiliaryWrite.content())
                ));
            }
            for (WriteTransaction transaction : transactions) {
                workspace.commitWrite(transaction);
            }
        } catch (RuntimeException exception) {
            for (WriteTransaction transaction : transactions) {
                workspace.failWrite(transaction, "Commit failure: " + exception.getMessage());
            }
            throw exception;
        }
    }

    private GeneratedContentGateInput buildGateInput(
            Path projectPath,
            Path relativePath,
            String content,
            Subtask subtask,
            FileChange primaryChange,
            GeneratedFileOutput output
    ) {
        List<Path> relatedPaths = new ArrayList<>();
        if (subtask != null && subtask.changes() != null) {
            for (FileChange change : subtask.changes()) {
                if (change == null || change.path() == null || change.path().isBlank()) {
                    continue;
                }
                relatedPaths.add(Path.of(change.path()).normalize());
            }
        }
        if (output != null && output.auxiliaryWrites() != null) {
            for (GeneratedAuxiliaryWrite auxiliaryWrite : output.auxiliaryWrites()) {
                if (auxiliaryWrite != null && auxiliaryWrite.relativePath() != null) {
                    relatedPaths.add(auxiliaryWrite.relativePath().normalize());
                }
            }
        }
        return new GeneratedContentGateInput(
                projectPath,
                relativePath,
                content,
                primaryChange == null ? null : primaryChange.runtimeOwnership(),
                List.copyOf(relatedPaths)
        );
    }

    private WriteTransaction stageValidatedWrite(
            Path projectPath,
            Path relativePath,
            String content,
            GeneratedContentGateInput gateInput
    ) {
        WriteTransaction transaction = workspace.stageWrite(projectPath, relativePath, content);
        String stagedContent = workspace.readStagedContent(transaction);
        GateReport validationReport = generatedContentGate.evaluate(new GeneratedContentGateInput(
                gateInput.projectPath(),
                gateInput.relativePath(),
                stagedContent,
                gateInput.runtimeOwnership(),
                gateInput.relatedPaths()
        ));
        if (!validationReport.passed()) {
            String failure = generatedContentGate.renderFailure(validationReport);
            workspace.failWrite(transaction, failure);
            throw new IllegalStateException(
                    "Generated file content is incomplete or invalid for " + relativePath + ": " + failure
            );
        }
        return transaction;
    }
}
