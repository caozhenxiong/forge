package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractView;
import devflow.agent.editing.FileStateLedger;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.parsing.TreeSitterParseSummary;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.quality.QualityPlan;
import devflow.agent.util.DevflowPathSupport;
import devflow.agent.validation.ProjectFingerprint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * coding tool runtime 的共享上下文。
 *
 * <p>路径归一化、owned path 校验、tool loop runtime state、tool result 目录都集中在这里，
 * 避免工具层重复实现同一套环境判断。
 */
final class ImplementationToolContext {

    private final Path projectPath;
    private final RunRecord runRecord;
    private final ObjectMapper objectMapper;
    private final ContractView contractView;
    private final QualityPlan qualityPlan;
    private final ProjectFingerprint fingerprint;
    private final ImplementationEventJournal eventJournal;
    private final Set<Path> ownedPaths;
    private final ToolLoopRuntimeState runtimeState;
    private final FileStateLedger fileStateLedger = new FileStateLedger();
    private final TreeSitterSupport treeSitterSupport = new TreeSitterSupport();

    ImplementationToolContext(
            Path projectPath,
            RunRecord runRecord,
            ObjectMapper objectMapper,
            ContractView contractView,
            QualityPlan qualityPlan,
            ProjectFingerprint fingerprint,
            ImplementationEventJournal eventJournal,
            Set<Path> ownedPaths,
            ToolLoopRuntimeState runtimeState
    ) {
        this.projectPath = projectPath.toAbsolutePath().normalize();
        this.runRecord = runRecord;
        this.objectMapper = objectMapper;
        this.contractView = contractView;
        this.qualityPlan = qualityPlan;
        this.fingerprint = fingerprint;
        this.eventJournal = eventJournal;
        this.ownedPaths = ownedPaths == null ? Set.of() : Set.copyOf(ownedPaths);
        this.runtimeState = runtimeState == null ? new ToolLoopRuntimeState() : runtimeState;
    }

    Path projectPath() {
        return projectPath;
    }

    RunRecord runRecord() {
        return runRecord;
    }

    ObjectMapper objectMapper() {
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

    ToolLoopRuntimeState runtimeState() {
        return runtimeState;
    }

    ToolLoopReadFileStateLedger readFileStateLedger() {
        return runtimeState.readFileStateLedger();
    }

    Path toolResultsDirectory() {
        return DevflowPathSupport.runDirectory(projectPath, runRecord.runId()).resolve("tool-results");
    }

    Set<Path> touchedPaths() {
        LinkedHashSet<Path> touchedPaths = new LinkedHashSet<>();
        for (FileMutationRecord mutationRecord : runtimeState.mutationRecords()) {
            if (mutationRecord == null || mutationRecord.relativePath() == null) {
                continue;
            }
            touchedPaths.add(mutationRecord.relativePath());
        }
        return Set.copyOf(touchedPaths);
    }

    List<FileMutationRecord> mutationRecords() {
        return runtimeState.mutationRecords();
    }

    Path requireProjectAbsolutePath(String rawPath) {
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

    void assertWritable(Path absolutePath) {
        Path relativePath = relativize(absolutePath);
        if (!ownedPaths.contains(relativePath)) {
            throw new IllegalArgumentException("Write is only allowed for current owned paths: " + ownedPaths);
        }
    }

    boolean exists(Path absolutePath) {
        return Files.exists(absolutePath);
    }

    String readFile(Path absolutePath) {
        try {
            return Files.readString(absolutePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file: " + absolutePath, exception);
        }
    }

    void writeFile(Path absolutePath, String content) {
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

    void deleteFile(Path absolutePath) {
        try {
            Files.deleteIfExists(absolutePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete file: " + absolutePath, exception);
        }
    }

    long modificationTime(Path absolutePath) {
        try {
            if (!Files.exists(absolutePath)) {
                return -1L;
            }
            return Files.getLastModifiedTime(absolutePath).toMillis();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file timestamp: " + absolutePath, exception);
        }
    }

    void appendEvent(String message) {
        if (eventJournal != null && message != null && !message.isBlank()) {
            eventJournal.append(message);
        }
    }

    void recordMutation(
            ToolLoopMutationOperation operation,
            Path absolutePath,
            String beforeContent,
            String afterContent,
            List<StructuredPatchHunk> structuredPatch
    ) {
        if (operation == null || absolutePath == null) {
            return;
        }
        Path relativePath = relativize(absolutePath);
        String normalizedBefore = beforeContent == null ? "" : beforeContent;
        String normalizedAfter = afterContent == null ? "" : afterContent;
        ToolLoopDiagnosticStatus diagnosticStatus;
        String diagnosticEvidence;
        if (operation == ToolLoopMutationOperation.DELETE) {
            diagnosticStatus = ToolLoopDiagnosticStatus.DELETED;
            diagnosticEvidence = "file deleted";
        } else {
            TreeSitterParseSummary summary = treeSitterSupport.analyze(relativePath, normalizedAfter);
            if (!summary.supported()) {
                diagnosticStatus = ToolLoopDiagnosticStatus.UNSUPPORTED;
                diagnosticEvidence = "tree-sitter unsupported for " + relativePath;
            } else if (!summary.valid()) {
                diagnosticStatus = ToolLoopDiagnosticStatus.SYNTAX_INVALID;
                diagnosticEvidence = summary.describe();
            } else {
                diagnosticStatus = ToolLoopDiagnosticStatus.VALID;
                diagnosticEvidence = summary.describe();
            }
        }
        FileMutationRecord mutationRecord = new FileMutationRecord(
                operation,
                relativePath,
                fileStateLedger.capture(relativePath, operation != ToolLoopMutationOperation.CREATE, normalizedBefore).contentHash(),
                fileStateLedger.capture(relativePath, operation != ToolLoopMutationOperation.DELETE, normalizedAfter).contentHash(),
                structuredPatch,
                System.currentTimeMillis(),
                diagnosticStatus,
                diagnosticEvidence
        );
        runtimeState.recordMutation(mutationRecord);
        appendEvent("实现阶段｜mutation｜操作=%s｜文件=%s｜诊断=%s"
                .formatted(operation.name(), relativePath, diagnosticStatus.name()));
    }
}
