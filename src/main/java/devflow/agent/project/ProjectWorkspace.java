package devflow.agent.project;

import devflow.agent.executor.shell.CommandResult;

import java.time.Duration;
import java.nio.file.Path;
import java.util.List;

public interface ProjectWorkspace {

    String readFile(Path projectPath, Path relativePath);

    void writeFile(Path projectPath, Path relativePath, String content);

    void deleteFile(Path projectPath, Path relativePath);

    List<Path> listProjectFiles(Path projectPath);

    String collectContext(Path projectPath, int maxFiles, int maxCharsPerFile, int maxTotalChars);

    CommandResult runCommand(Path projectPath, List<String> command, Duration timeout);
}
