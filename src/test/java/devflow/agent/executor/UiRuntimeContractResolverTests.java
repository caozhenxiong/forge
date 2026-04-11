package devflow.agent.executor;

import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.QualityPlan;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiRuntimeContractResolverTests {

    @TempDir
    Path tempDir;

    @Test
    void validateMarksProbeFailureAsProbeInvalid() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), "<!doctype html><html><body><main id='app'></main></body></html>");

        UiRuntimeContractResolver resolver = new UiRuntimeContractResolver(new FileProjectWorkspace(), new TreeSitterSupport());
        UiRuntimeContractValidation validation = resolver.validate(
                tempDir,
                QualityPlan.empty(),
                new RuntimeSnapshot(
                        "index.html",
                        "",
                        null,
                        0,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        RuntimeSnapshotCaptureStatus.COLLECTOR_FAILED,
                        RuntimeSnapshotFailureCode.COLLECTOR_EXECUTION_FAILED,
                        List.of("probe crashed")
                ),
                new UiRuntimeContract("index.html", List.of("index.html"), List.of(), List.of())
        );

        assertFalse(validation.valid());
        assertEquals(UiRuntimeContractValidationKind.PROBE_INVALID, validation.kind());
        assertTrue(validation.issues().contains("probe crashed"));
    }
}
