package devflow.agent.project;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void writeTransactionsCommitAtomicallyAndCleanActiveStage() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        Path file = Path.of("src", "main.js");
        workspace.writeFile(tempDir, file, "console.log('old');");

        WriteTransaction transaction = workspace.stageWrite(tempDir, file, "console.log('new');");
        assertEquals("console.log('new');", workspace.readStagedContent(transaction));

        workspace.commitWrite(transaction);

        assertEquals("console.log('new');", Files.readString(tempDir.resolve(file)));
        assertFalse(Files.exists(transaction.stagingRoot()));
    }

    @Test
    void failedWriteTransactionsAreRetainedForDebugging() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        Path file = Path.of("index.html");
        workspace.writeFile(tempDir, file, "<html></html>");

        WriteTransaction transaction = workspace.stageWrite(tempDir, file, "<html>");
        workspace.failWrite(transaction, "HTML 结构不完整");

        Path failedRoot = tempDir.resolve(".devflow").resolve("write-transactions").resolve("failed").resolve(transaction.id());
        assertTrue(Files.exists(failedRoot.resolve("candidate").resolve(file)));
        assertTrue(Files.exists(failedRoot.resolve("original").resolve(file)));
        assertEquals("HTML 结构不完整", Files.readString(failedRoot.resolve("failure.txt")));
        assertEquals("<html></html>", Files.readString(tempDir.resolve(file)));
    }
}
