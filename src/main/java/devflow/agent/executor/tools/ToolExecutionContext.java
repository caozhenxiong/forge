package devflow.agent.executor.tools;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.shell.ShellCommandDecision;
import devflow.agent.executor.shell.ShellPathIntent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import devflow.agent.executor.implementation.toolloop.ImplementationToolContext;
/**
 * implementation tool 与 tool loop 之间的稳定执行上下文契约。
 *
 * <p>tools 只允许通过这组能力读写工作区、记录 mutation、做 shell 校验；
 * 不直接依赖 orchestration 级的 {@code ImplementationToolContext} 具体实现。
 */
public interface ToolExecutionContext {

    ObjectMapper objectMapper();

    Path projectPath();

    Path requireProjectAbsolutePath(String rawPath);

    boolean exists(Path absolutePath);

    String readFile(Path absolutePath);

    void writeFile(Path absolutePath, String content);

    void deleteFile(Path absolutePath);

    long modificationTime(Path absolutePath);

    ToolReadState readState(Path absolutePath);

    void rememberReadState(Path absolutePath, ToolReadState state);

    void clearReadState(Path absolutePath);

    void assertWritable(Path absolutePath);

    void assertFreshReadBeforeOverwrite(Path absolutePath);

    void assertExistingFileWholeRewriteAllowed(Path absolutePath, String toolName);

    void assertMutationContract(Path absolutePath, String content);

    void recordCreateMutation(Path absolutePath, String afterContent, List<StructuredPatchHunk> structuredPatch);

    void recordUpdateMutation(
            Path absolutePath,
            String beforeContent,
            String afterContent,
            List<StructuredPatchHunk> structuredPatch
    );

    void recordDeleteMutation(Path absolutePath, String beforeContent);

    void appendEvent(String message);

    long resolveShellTimeout(Long requestedTimeoutMs);

    ShellCommandDecision decideShellCommand(String command);

    void assertShellWriteTargets(List<ShellPathIntent> pathIntents);

    ShellWorkspaceSnapshot captureShellWorkspaceSnapshot();

    ShellMutationAccountingResult recordShellWorkspaceChanges(
            ShellWorkspaceSnapshot beforeSnapshot,
            boolean readOnlyExpected
    );

    /**
     * 工具层读取状态的最小快照。
     *
     * <p>tools 只需要知道模型最近看到的内容、时间戳以及是否 partial view，
     * 不需要直接接触 toolloop 内部 ledger 实现。
     */
    record ToolReadState(
            String content,
            long timestamp,
            Integer offset,
            Integer limit,
            boolean partialView
    ) {

        public ToolReadState {
            content = content == null ? "" : content;
        }

        public boolean fullView() {
            return !partialView && offset == null && limit == null;
        }
    }

    record ShellWorkspaceSnapshot(
            Map<Path, ShellWorkspaceFileState> fileStates
    ) {

        public ShellWorkspaceSnapshot {
            fileStates = fileStates == null ? Map.of() : Map.copyOf(fileStates);
        }
    }

    record ShellWorkspaceFileState(
            Path relativePath,
            boolean exists,
            String contentHash,
            String content
    ) {

        public ShellWorkspaceFileState {
            contentHash = contentHash == null ? "" : contentHash;
        }
    }

    record ShellMutationAccountingResult(
            List<Path> changedPaths,
            List<Path> scopeViolationPaths
    ) {

        public ShellMutationAccountingResult {
            changedPaths = changedPaths == null ? List.of() : List.copyOf(changedPaths);
            scopeViolationPaths = scopeViolationPaths == null ? List.of() : List.copyOf(scopeViolationPaths);
        }
    }
}
