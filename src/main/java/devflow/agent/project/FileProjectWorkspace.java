package devflow.agent.project;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 基于本地文件系统的项目工作区门面。
 *
 * <p>这个类只负责把外部接口路由到更细粒度的支撑组件，
 * 不再内联维护事务、遍历、采样和命令执行细节。
 */
@Component
public class FileProjectWorkspace implements ProjectWorkspace {

    private final WorkspacePathGuard pathGuard;
    private final WorkspaceWriteSupport writeSupport;
    private final WorkspaceFileListingSupport listingSupport;
    private final WorkspaceCommandRunner commandRunner;

    public FileProjectWorkspace() {
        this(new WorkspacePathGuard());
    }

    FileProjectWorkspace(WorkspacePathGuard pathGuard) {
        this.pathGuard = pathGuard;
        this.writeSupport = new WorkspaceWriteSupport(pathGuard);
        this.listingSupport = new WorkspaceFileListingSupport();
        this.commandRunner = new WorkspaceCommandRunner();
    }

    @Override
    public String readFile(Path projectPath, Path relativePath) {
        Path path = pathGuard.resolve(projectPath, relativePath);
        try {
            return java.nio.file.Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to read file: " + path, exception);
        }
    }

    @Override
    public void writeFile(Path projectPath, Path relativePath, String content) {
        writeSupport.writeFile(projectPath, relativePath, content);
    }

    public WriteTransaction stageWrite(Path projectPath, Path relativePath, String content) {
        return writeSupport.stageWrite(projectPath, relativePath, content);
    }

    public String readStagedContent(WriteTransaction transaction) {
        return writeSupport.readStagedContent(transaction);
    }

    public void commitWrite(WriteTransaction transaction) {
        writeSupport.commitWrite(transaction);
    }

    public void failWrite(WriteTransaction transaction, String reason) {
        writeSupport.failWrite(transaction, reason);
    }

    @Override
    public void deleteFile(Path projectPath, Path relativePath) {
        writeSupport.deleteFile(projectPath, relativePath);
    }

    @Override
    public List<Path> listProjectFiles(Path projectPath) {
        return listingSupport.listProjectFiles(projectPath);
    }

    @Override
    public String collectContext(Path projectPath, int maxFiles, int maxCharsPerFile, int maxTotalChars) {
        return listingSupport.collectContext(projectPath, maxFiles, maxCharsPerFile, maxTotalChars, this);
    }

    @Override
    public CommandResult runCommand(Path projectPath, List<String> command, Duration timeout) {
        return commandRunner.runCommand(projectPath, command, timeout);
    }
}
