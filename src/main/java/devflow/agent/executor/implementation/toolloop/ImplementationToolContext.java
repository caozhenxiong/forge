package devflow.agent.executor.implementation.toolloop;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ImplementationToolPermissionContext;
import devflow.agent.executor.tools.ImplementationToolPermissionPolicy;
import devflow.agent.executor.tools.StructuredPatchHunk;
import devflow.agent.executor.tools.StructuredPatchSupport;
import devflow.agent.executor.tools.ToolExecutionContext;

import devflow.agent.executor.shell.ShellCommandAnalyzer;
import devflow.agent.executor.shell.ShellCommandDecision;
import devflow.agent.executor.shell.ShellPathIntent;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractView;
import devflow.agent.editing.precise.FileStateLedger;
import devflow.agent.editing.precise.FileStateSnapshot;
import devflow.agent.domain.RunRecord;
import devflow.agent.parsing.TreeSitterParseSummary;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.quality.QualityPlan;
import devflow.agent.util.DevflowPathSupport;
import devflow.agent.util.ProjectPathSupport;
import devflow.agent.validation.ProjectFingerprint;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.implementation.toolloop.FileMutationRecord;
/**
 * coding tool runtime 的共享上下文。
 *
 * <p>路径归一化、权限校验、tool session state、tool result 目录都集中在这里，
 * 避免工具层重复实现同一套环境判断。
 */
public final class ImplementationToolContext implements ToolExecutionContext {

    private final Path projectPath;
    private final RunRecord runRecord;
    private final ObjectMapper objectMapper;
    private final ContractView contractView;
    private final QualityPlan qualityPlan;
    private final ProjectFingerprint fingerprint;
    private final ImplementationEventJournal eventJournal;
    private final ImplementationToolSessionState toolSessionState;
    private final ImplementationToolPermissionContext permissionContext;
    private final ImplementationToolPermissionPolicy permissionPolicy;
    private final DeliveryMode deliveryMode;
    private final List<FileChange> scopedChanges;
    private final ImplementationMutationContractGuard mutationContractGuard = new ImplementationMutationContractGuard();
    private final FileStateLedger fileStateLedger = new FileStateLedger();
    private final TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
    private final StructuredPatchSupport structuredPatchSupport = new StructuredPatchSupport();
    private final ShellCommandAnalyzer shellCommandAnalyzer = new ShellCommandAnalyzer();

    public ImplementationToolContext(
            Path projectPath,
            RunRecord runRecord,
            ObjectMapper objectMapper,
            ContractView contractView,
            QualityPlan qualityPlan,
            ProjectFingerprint fingerprint,
            ImplementationEventJournal eventJournal,
            ImplementationToolSessionState toolSessionState,
            ImplementationToolPermissionContext permissionContext,
            ImplementationToolPermissionPolicy permissionPolicy,
            DeliveryMode deliveryMode,
            List<FileChange> scopedChanges
    ) {
        this.projectPath = projectPath.toAbsolutePath().normalize();
        this.runRecord = runRecord;
        this.objectMapper = objectMapper;
        this.contractView = contractView;
        this.qualityPlan = qualityPlan;
        this.fingerprint = fingerprint;
        this.eventJournal = eventJournal;
        this.toolSessionState = toolSessionState == null ? new ImplementationToolSessionState() : toolSessionState;
        this.permissionContext = permissionContext;
        this.permissionPolicy = Objects.requireNonNull(permissionPolicy, "permissionPolicy");
        this.deliveryMode = deliveryMode == null ? DeliveryMode.PATCH : deliveryMode;
        this.scopedChanges = scopedChanges == null ? List.of() : List.copyOf(scopedChanges);
    }

    @Override
    public Path projectPath() {
        return projectPath;
    }

    RunRecord runRecord() {
        return runRecord;
    }

    @Override
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    ContractView contractView() {
        return contractView;
    }

    QualityPlan qualityPlan() {
        return qualityPlan;
    }

    ProjectFingerprint fingerprint() {
        return fingerprint;
    }

    ImplementationToolSessionState toolSessionState() {
        return toolSessionState;
    }

    ImplementationToolPermissionContext permissionContext() {
        return permissionContext;
    }

    public ToolLoopReadFileStateLedger readFileStateLedger() {
        return toolSessionState.readFileStateLedger();
    }

    Path toolResultsDirectory() {
        return DevflowPathSupport.runDirectory(projectPath, runRecord.runId()).resolve("tool-results");
    }

    public Set<Path> touchedPaths() {
        LinkedHashSet<Path> touchedPaths = new LinkedHashSet<>();
        for (FileMutationRecord mutationRecord : toolSessionState.mutationRecords()) {
            if (mutationRecord == null || mutationRecord.relativePath() == null) {
                continue;
            }
            touchedPaths.add(mutationRecord.relativePath());
        }
        return Set.copyOf(touchedPaths);
    }

    public List<FileMutationRecord> mutationRecords() {
        return toolSessionState.mutationRecords();
    }

    List<ImplementationDiagnosticRecord> diagnostics() {
        return toolSessionState.diagnostics();
    }

    @Override
    public Path requireProjectAbsolutePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("file_path is required.");
        }
        Path absolutePath = Path.of(rawPath).toAbsolutePath().normalize();
        if (!absolutePath.startsWith(projectPath)) {
            throw new IllegalArgumentException("Path must stay inside the current project root.");
        }
        return absolutePath;
    }

    Path relativize(Path absolutePath) {
        return projectPath.relativize(absolutePath.toAbsolutePath().normalize()).normalize();
    }

    @Override
    public void assertWritable(Path absolutePath) {
        permissionPolicy.assertWritablePath(relativize(absolutePath), permissionContext);
    }

    @Override
    public void assertMutationContract(Path absolutePath, String content) {
        mutationContractGuard.validate(
                projectPath,
                relativize(absolutePath),
                content == null ? "" : content,
                permissionContext.ownedPaths(),
                scopedChanges
        );
    }

    @Override
    public long resolveShellTimeout(Long requestedTimeoutMs) {
        return permissionPolicy.resolveShellTimeout(requestedTimeoutMs, permissionContext);
    }

    @Override
    public ShellCommandDecision decideShellCommand(String command) {
        return permissionPolicy.decideShellCommand(command, permissionContext, shellCommandAnalyzer);
    }

    @Override
    public boolean exists(Path absolutePath) {
        return Files.exists(absolutePath);
    }

    @Override
    public String readFile(Path absolutePath) {
        try {
            return Files.readString(absolutePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file: " + absolutePath, exception);
        }
    }

    @Override
    public void writeFile(Path absolutePath, String content) {
        try {
            Path parent = absolutePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(absolutePath, content == null ? "" : content);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write file: " + absolutePath, exception);
        }
    }

    @Override
    public void deleteFile(Path absolutePath) {
        try {
            Files.deleteIfExists(absolutePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete file: " + absolutePath, exception);
        }
    }

    @Override
    public long modificationTime(Path absolutePath) {
        try {
            if (!Files.exists(absolutePath)) {
                return -1L;
            }
            return Files.getLastModifiedTime(absolutePath).toMillis();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file timestamp: " + absolutePath, exception);
        }
    }

    void assertFreshFullRead(Path absolutePath, String actionLabel) {
        if (absolutePath == null || !exists(absolutePath)) {
            throw new IllegalArgumentException(
                    (actionLabel == null || actionLabel.isBlank() ? "This action" : actionLabel)
                            + " requires an existing file inside the project root."
            );
        }
        CoderReadFileState readState = readFileStateLedger().get(absolutePath);
        if (readState == null || readState.partialView()) {
            throw new IllegalArgumentException(
                    (actionLabel == null || actionLabel.isBlank() ? "This action" : actionLabel)
                            + " requires a prior full Read on "
                            + relativize(absolutePath).toString().replace('\\', '/')
                            + "."
            );
        }
        String currentContent = readFile(absolutePath);
        if (modificationTime(absolutePath) > readState.timestamp()
                && !(readState.fullView() && currentContent.equals(readState.content()))) {
            throw new IllegalArgumentException(
                    "File has been modified since read. Read it again before "
                            + ((actionLabel == null || actionLabel.isBlank()) ? "continuing" : actionLabel.toLowerCase()) + "."
            );
        }
    }

    @Override
    public void assertFreshReadBeforeOverwrite(Path absolutePath) {
        if (absolutePath == null || !exists(absolutePath)) {
            return;
        }
        assertFreshFullRead(absolutePath, "Overwriting existing file");
    }

    @Override
    public void assertExistingFileWholeRewriteAllowed(Path absolutePath, String toolName) {
        if (absolutePath == null || !exists(absolutePath)) {
            return;
        }
        if (deliveryMode == DeliveryMode.REWORK) {
            return;
        }
        throw new IllegalArgumentException(
                (toolName == null || toolName.isBlank() ? "Whole-file overwrite" : toolName)
                        + " is only allowed for new files unless the current delivery mode is REWORK. "
                        + "Use Read + Edit for existing files in " + deliveryMode.name() + "."
        );
    }

    @Override
    public void assertShellWriteTargets(List<ShellPathIntent> pathIntents) {
        if (pathIntents == null || pathIntents.isEmpty()) {
            return;
        }
        for (ShellPathIntent pathIntent : pathIntents) {
            if (pathIntent == null || pathIntent.path() == null || pathIntent.kind() == null) {
                continue;
            }
            Path absolutePath = projectPath.resolve(pathIntent.path()).normalize();
            switch (pathIntent.kind()) {
                case READ_FILE -> assertFreshFullRead(absolutePath, "Bash copy source");
                case WRITE_FILE -> {
                    assertWritable(absolutePath);
                    assertExistingFileWholeRewriteAllowed(absolutePath, "Bash write");
                    assertFreshReadBeforeOverwrite(absolutePath);
                }
                case DELETE_FILE -> assertWritable(absolutePath);
                case PREPARE_DIRECTORY -> {
                    if (!isOwnedDirectory(pathIntent.path())) {
                        throw new IllegalArgumentException(
                                "Shell directory preparation is only allowed for directories that contain current owned paths: "
                                        + pathIntent.path().toString().replace('\\', '/')
                        );
                    }
                }
            }
        }
    }

    @Override
    public void appendEvent(String message) {
        if (eventJournal != null && message != null && !message.isBlank()) {
            eventJournal.append(message);
        }
    }

    @Override
    public ToolExecutionContext.ToolReadState readState(Path absolutePath) {
        CoderReadFileState state = readFileStateLedger().get(absolutePath);
        if (state == null) {
            return null;
        }
        return new ToolExecutionContext.ToolReadState(
                state.content(),
                state.timestamp(),
                state.offset(),
                state.limit(),
                state.partialView()
        );
    }

    @Override
    public void rememberReadState(Path absolutePath, ToolExecutionContext.ToolReadState state) {
        if (absolutePath == null || state == null) {
            return;
        }
        readFileStateLedger().put(
                absolutePath,
                new CoderReadFileState(
                        state.content(),
                        state.timestamp(),
                        state.offset(),
                        state.limit(),
                        state.partialView()
                )
        );
    }

    @Override
    public void clearReadState(Path absolutePath) {
        if (absolutePath != null) {
            readFileStateLedger().invalidate(absolutePath);
        }
    }

    @Override
    public void recordCreateMutation(Path absolutePath, String afterContent, List<StructuredPatchHunk> structuredPatch) {
        recordMutation(ToolLoopMutationOperation.CREATE, absolutePath, false, "", true, afterContent, structuredPatch);
    }

    @Override
    public void recordUpdateMutation(
            Path absolutePath,
            String beforeContent,
            String afterContent,
            List<StructuredPatchHunk> structuredPatch
    ) {
        recordMutation(ToolLoopMutationOperation.UPDATE, absolutePath, true, beforeContent, true, afterContent, structuredPatch);
    }

    @Override
    public void recordDeleteMutation(Path absolutePath, String beforeContent) {
        recordMutation(ToolLoopMutationOperation.DELETE, absolutePath, true, beforeContent, false, "", List.of());
    }

    void recordMutation(
            ToolLoopMutationOperation operation,
            Path absolutePath,
            boolean beforeExists,
            String beforeContent,
            boolean afterExists,
            String afterContent,
            List<StructuredPatchHunk> structuredPatch
    ) {
        if (operation == null || absolutePath == null) {
            return;
        }
        Path relativePath = relativize(absolutePath);
        String normalizedBefore = beforeContent == null ? "" : beforeContent;
        String normalizedAfter = afterContent == null ? "" : afterContent;
        ComputedDiagnostic diagnostic = computeDiagnostic(relativePath, operation, normalizedAfter);
        FileMutationRecord mutationRecord = new FileMutationRecord(
                operation,
                relativePath,
                beforeExists,
                fileStateLedger.capture(relativePath, beforeExists, normalizedBefore).contentHash(),
                afterExists,
                fileStateLedger.capture(relativePath, afterExists, normalizedAfter).contentHash(),
                structuredPatch,
                System.currentTimeMillis()
        );
        toolSessionState.recordMutation(mutationRecord);
        toolSessionState.diagnosticLedger().record(
                relativePath,
                diagnostic.status(),
                diagnostic.source(),
                diagnostic.evidence()
        );
        appendEvent("实现阶段｜mutation｜操作=%s｜文件=%s｜诊断=%s"
                .formatted(operation.name(), relativePath, diagnostic.status().name()));
    }

    FileStateSnapshot captureFileState(Path absolutePath) {
        if (absolutePath == null || !exists(absolutePath)) {
            Path relativePath = absolutePath == null ? Path.of("") : relativize(absolutePath);
            return fileStateLedger.capture(relativePath, false, "");
        }
        Path relativePath = relativize(absolutePath);
        return fileStateLedger.capture(relativePath, true, readFile(absolutePath));
    }

    @Override
    public ToolExecutionContext.ShellWorkspaceSnapshot captureShellWorkspaceSnapshot() {
        LinkedHashMap<Path, ToolExecutionContext.ShellWorkspaceFileState> states = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(projectPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !isIgnoredShellWorkspacePath(path))
                    .sorted()
                    .forEach(path -> {
                        Path relativePath = projectPath.relativize(path).normalize();
                        states.put(relativePath, new ToolExecutionContext.ShellWorkspaceFileState(
                                relativePath,
                                true,
                                hashFile(path),
                                permissionContext.ownedPaths().contains(relativePath) ? readFile(path) : null
                        ));
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to capture shell workspace snapshot: " + projectPath, exception);
        }
        return new ToolExecutionContext.ShellWorkspaceSnapshot(Map.copyOf(states));
    }

    @Override
    public ToolExecutionContext.ShellMutationAccountingResult recordShellWorkspaceChanges(
            ToolExecutionContext.ShellWorkspaceSnapshot beforeSnapshot,
            boolean readOnlyExpected
    ) {
        ToolExecutionContext.ShellWorkspaceSnapshot before = beforeSnapshot == null
                ? new ToolExecutionContext.ShellWorkspaceSnapshot(Map.of())
                : beforeSnapshot;
        ToolExecutionContext.ShellWorkspaceSnapshot after = captureShellWorkspaceSnapshot();
        LinkedHashSet<Path> candidatePaths = new LinkedHashSet<>();
        candidatePaths.addAll(before.fileStates().keySet());
        candidatePaths.addAll(after.fileStates().keySet());

        ArrayList<Path> changedPaths = new ArrayList<>();
        ArrayList<Path> scopeViolationPaths = new ArrayList<>();
        for (Path relativePath : candidatePaths) {
            ToolExecutionContext.ShellWorkspaceFileState beforeState = before.fileStates().get(relativePath);
            ToolExecutionContext.ShellWorkspaceFileState afterState = after.fileStates().get(relativePath);
            boolean beforeExists = beforeState != null && beforeState.exists();
            boolean afterExists = afterState != null && afterState.exists();
            String beforeHash = beforeState == null ? "" : beforeState.contentHash();
            String afterHash = afterState == null ? "" : afterState.contentHash();
            if (beforeExists == afterExists && Objects.equals(beforeHash, afterHash)) {
                continue;
            }
            changedPaths.add(relativePath);
            if (readOnlyExpected || !permissionContext.ownedPaths().contains(relativePath)) {
                scopeViolationPaths.add(relativePath);
                continue;
            }
            String beforeContent = beforeState == null || beforeState.content() == null ? "" : beforeState.content();
            String afterContent = afterState == null || afterState.content() == null ? "" : afterState.content();
            ToolLoopMutationOperation operation = !beforeExists && afterExists
                    ? ToolLoopMutationOperation.CREATE
                    : beforeExists && !afterExists
                    ? ToolLoopMutationOperation.DELETE
                    : ToolLoopMutationOperation.UPDATE;
            if (afterExists) {
                assertMutationContract(projectPath.resolve(relativePath).normalize(), afterContent);
            }
            List<StructuredPatchHunk> structuredPatch = operation == ToolLoopMutationOperation.DELETE
                    ? List.of()
                    : structuredPatchSupport.build(beforeContent, afterContent);
            recordMutation(
                    operation,
                    projectPath.resolve(relativePath).normalize(),
                    beforeExists,
                    beforeContent,
                    afterExists,
                    afterContent,
                    structuredPatch
            );
            if (operation == ToolLoopMutationOperation.DELETE) {
                readFileStateLedger().invalidate(projectPath.resolve(relativePath).normalize());
            } else {
                readFileStateLedger().put(
                        projectPath.resolve(relativePath).normalize(),
                        new CoderReadFileState(afterContent, modificationTime(projectPath.resolve(relativePath).normalize()), null, null, false)
                );
            }
        }
        return new ToolExecutionContext.ShellMutationAccountingResult(List.copyOf(changedPaths), List.copyOf(scopeViolationPaths));
    }

    private ComputedDiagnostic computeDiagnostic(
            Path relativePath,
            ToolLoopMutationOperation operation,
            String normalizedAfter
    ) {
        if (operation == ToolLoopMutationOperation.DELETE) {
            return new ComputedDiagnostic(
                    ToolLoopDiagnosticStatus.DELETED,
                    ImplementationDiagnosticSource.FILE_DELETED,
                    "file deleted"
            );
        }
        TreeSitterParseSummary summary = treeSitterSupport.analyze(relativePath, normalizedAfter);
        if (!summary.supported()) {
            return new ComputedDiagnostic(
                    ToolLoopDiagnosticStatus.UNSUPPORTED,
                    ImplementationDiagnosticSource.UNSUPPORTED_LANGUAGE,
                    "tree-sitter unsupported for " + relativePath
            );
        }
        if (!summary.valid()) {
            return new ComputedDiagnostic(
                    ToolLoopDiagnosticStatus.SYNTAX_INVALID,
                    ImplementationDiagnosticSource.TREE_SITTER_PARSE,
                    summary.describe()
            );
        }
        return new ComputedDiagnostic(
                ToolLoopDiagnosticStatus.VALID,
                ImplementationDiagnosticSource.TREE_SITTER_PARSE,
                summary.describe()
        );
    }

    private record ComputedDiagnostic(
            ToolLoopDiagnosticStatus status,
            ImplementationDiagnosticSource source,
            String evidence
    ) {
    }

    private boolean isOwnedDirectory(Path directoryPath) {
        if (directoryPath == null || permissionContext == null || permissionContext.ownedPaths().isEmpty()) {
            return false;
        }
        Path normalizedDirectory = directoryPath.normalize();
        for (Path ownedPath : permissionContext.ownedPaths()) {
            if (ownedPath == null) {
                continue;
            }
            Path parent = ownedPath.getParent() == null ? Path.of("") : ownedPath.getParent().normalize();
            if (parent.equals(normalizedDirectory) || parent.startsWith(normalizedDirectory)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIgnoredShellWorkspacePath(Path absolutePath) {
        Path relativePath = projectPath.relativize(absolutePath);
        for (Path part : relativePath) {
            if (ProjectPathSupport.isIgnoredWorkspaceDirectoryName(part.toString())) {
                return true;
            }
        }
        return relativePath.getFileName() != null
                && ProjectPathSupport.isIgnoredWorkspaceArtifact(relativePath.getFileName().toString());
    }

    private String hashFile(Path absolutePath) {
        try (InputStream inputStream = Files.newInputStream(absolutePath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to hash file: " + absolutePath, exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

}
