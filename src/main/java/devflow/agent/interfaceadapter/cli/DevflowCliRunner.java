package devflow.agent.interfaceadapter.cli;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.orchestrator.TerminalHumanApprovalRejectedException;
import devflow.agent.orchestrator.WorkflowEngine;
import devflow.agent.util.DevflowPathSupport;
import java.nio.file.Path;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
/**
 * CLI 顶层入口。
 *
 * <p>这里只保留两类职责：
 * 1. 顶层命令分流，例如 `init` 与 `run`；
 * 2. 兜底 usage 输出。
 *
 * <p>具体 `run ...` 子命令已经下沉到 {@link CliRunCommandHandler}，
 * 避免入口类继续膨胀成参数解析、workflow 调用和 autopilot 循环的混合体。
 */
public class DevflowCliRunner implements CommandLineRunner {

    private final WorkflowEngine workflowEngine;
    private final CliArgumentSupport argumentSupport;
    private final CliOutputRenderer outputRenderer;
    private final CliRunCommandHandler runCommandHandler;

    public DevflowCliRunner(WorkflowEngine workflowEngine, FileArtifactStore artifactStore, EventLogStore eventLogStore) {
        this.workflowEngine = workflowEngine;
        this.argumentSupport = new CliArgumentSupport();
        this.outputRenderer = new CliOutputRenderer();
        this.runCommandHandler = new CliRunCommandHandler(
                workflowEngine,
                artifactStore,
                eventLogStore,
                argumentSupport,
                outputRenderer
        );
    }

    @Override
    public void run(String... args) {
        if (args.length == 0) {
            return;
        }

        String command = args[0];
        if ("init".equals(command)) {
            Path projectPath = argumentSupport.optionPath(args, "--project", Path.of(".").toAbsolutePath().normalize());
            workflowEngine.initialize(projectPath);
            System.out.println("Initialized devflow workspace: " + DevflowPathSupport.workspaceRoot(projectPath));
            return;
        }

        if (!"run".equals(command) || args.length < 2) {
            printUsage();
            return;
        }
        try {
            if (!runCommandHandler.handle(args)) {
                printUsage();
            }
        } catch (TerminalHumanApprovalRejectedException exception) {
            System.out.println(outputRenderer.renderTerminalApprovalRejected(exception));
        }
    }

    private void printUsage() {
        System.out.println(outputRenderer.renderUsage());
    }
}
