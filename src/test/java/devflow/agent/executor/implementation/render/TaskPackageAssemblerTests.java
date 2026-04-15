package devflow.agent.executor.implementation.render;

import devflow.agent.context.SharedContextBundle;
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.implementation.planning.ImplementationPlan;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.TaskPackage;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPackageAssemblerTests {

    @Test
    void buildTaskPackagesPreservesAcceptedBoundaryContract() {
        TaskPackageAssembler assembler = new TaskPackageAssembler(null);
        ImplementationPlan plan = new ImplementationPlan(
                "summary",
                List.of(new Subtask(
                        "subtask-1",
                        "build shell",
                        List.of("CAP-1"),
                        List.of("shell", "shell"),
                        List.of("gameplay"),
                        List.of("页面可打开"),
                        true,
                        DeliveryMode.PATCH,
                        List.of(
                                new FileChange("index.html", ChangeAction.WRITE, "host entry"),
                                new FileChange("src/app.js", ChangeAction.WRITE, "runtime root")
                        )
                ))
        );

        List<TaskPackage> packages = assembler.buildTaskPackages(
                Path.of("."),
                plan,
                sharedContextBundle(),
                null,
                null
        );

        assertEquals(1, packages.size());
        TaskPackage taskPackage = packages.get(0);
        assertEquals(List.of("index.html", "src/app.js"), taskPackage.ownedFiles());
        assertEquals(List.of("shell"), taskPackage.ownedCapabilities());
        assertEquals(List.of("gameplay"), taskPackage.deferredCapabilities());
        assertTrue(taskPackage.toMarkdown().contains("Boundary Contract Reminder"));
    }

    private SharedContextBundle sharedContextBundle() {
        return new SharedContextBundle(
                "goal",
                "constraints",
                null,
                List.of(),
                List.of("must-fix"),
                List.of("forbidden"),
                "",
                ""
        );
    }
}
