package devflow.agent.project;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileProjectWorkspaceTests {

    @TempDir
    Path tempDir;

    @Test
    void deleteFileRemovesEmptyParentDirectories() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        Path nestedFile = Path.of("src", "engine.js");
        workspace.writeFile(tempDir, nestedFile, "console.log('x');");

        assertTrue(Files.exists(tempDir.resolve("src")));
        workspace.deleteFile(tempDir, nestedFile);

        assertFalse(Files.exists(tempDir.resolve("src")));
    }
}
