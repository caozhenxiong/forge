package devflow.agent.interfaceadapter.cli;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.orchestrator.DefaultWorkflowEngine;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageStatus;
import devflow.agent.orchestrator.StageType;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 负责 `devflow run ...` 子命令的编排。
 *
 * <p>CLI 顶层 runner 只保留顶层命令路由；这里集中维护 run 子命令的参数解析、
 * workflow 调用和 autopilot 审批循环，避免入口类继续膨胀成命令大全。
 */
final class CliRunCommandHandler {

    private final DefaultWorkflowEngine workflowEngine;
    private final FileArtifactStore artifactStore;
    private final EventLogStore eventLogStore;
    private final CliArgumentSupport argumentSupport;
    private final CliOutputRenderer outputRenderer;

    CliRunCommandHandler(
            DefaultWorkflowEngine workflowEngine,
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            CliArgumentSupport argumentSupport,
            CliOutputRenderer outputRenderer
    ) {
        this.workflowEngine = workflowEngine;
        this.artifactStore = artifactStore;
        this.eventLogStore = eventLogStore;
        this.argumentSupport = argumentSupport;
        this.outputRenderer = outputRenderer;
    }

    boolean handle(String[] args) {
        if (args.length < 2) {
            return false;
        }
        String subCommand = args[1];
        switch (subCommand) {
            case "autopilot" -> handleAutopilot(args);
            case "bootstrap" -> handleBootstrap(args);
            case "create" -> handleCreate(args);
            case "start" -> handleStart(args);
            case "status" -> handleStatus(args);
            case "approve" -> handleApprove(args);
            case "reject" -> handleReject(args);
            case "show" -> handleShow(args);
            case "resume" -> handleResume(args);
            case "logs" -> handleLogs(args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void handleAutopilot(String[] args) {
        Path projectPath = projectPath(args);
        String goal = argumentSupport.requiredOption(args, "--goal");
        String constraints = argumentSupport.option(args, "--constraints", "");
        String reviewer = argumentSupport.option(args, "--reviewer", "autopilot");
        RunRecord created = workflowEngine.createRun(projectPath, goal, constraints);
        RunRecord started = workflowEngine.startRun(projectPath, created.runId());
        printSummary(autoApproveToTerminal(projectPath, started, reviewer));
    }

    private void handleBootstrap(String[] args) {
        Path projectPath = projectPath(args);
        String goal = argumentSupport.requiredOption(args, "--goal");
        String constraints = argumentSupport.option(args, "--constraints", "");
        RunRecord created = workflowEngine.createRun(projectPath, goal, constraints);
        RunRecord started = workflowEngine.startRun(projectPath, created.runId());
        if (argumentSupport.hasFlag(args, "--auto-approve")) {
            String reviewer = argumentSupport.option(args, "--reviewer", "autopilot");
            printSummary(autoApproveToTerminal(projectPath, started, reviewer));
            return;
        }
        printSummary(started);
    }

    private void handleCreate(String[] args) {
        Path projectPath = projectPath(args);
        String goal = argumentSupport.requiredOption(args, "--goal");
        String constraints = argumentSupport.option(args, "--constraints", "");
        RunRecord runRecord = workflowEngine.createRun(projectPath, goal, constraints);
        System.out.println("Created run: " + runRecord.runId());
        System.out.println("Project: " + runRecord.projectPath());
        System.out.println("Current stage: " + runRecord.currentStage());
    }

    private void handleStart(String[] args) {
        printSummary(workflowEngine.startRun(projectPath(args), runId(args, 2)));
    }

    private void handleStatus(String[] args) {
        printSummary(workflowEngine.find(projectPath(args), runId(args, 2)));
    }

    private void handleApprove(String[] args) {
        Path projectPath = projectPath(args);
        UUID runId = runId(args, 2);
        StageType stageType = stage(args, 3);
        String reviewer = argumentSupport.option(args, "--reviewer", "human");
        printSummary(workflowEngine.approveStage(projectPath, runId, stageType, reviewer));
    }

    private void handleReject(String[] args) {
        Path projectPath = projectPath(args);
        UUID runId = runId(args, 2);
        StageType stageType = stage(args, 3);
        String reviewer = argumentSupport.option(args, "--reviewer", "human");
        String reason = argumentSupport.requiredOption(args, "--reason");
        printSummary(workflowEngine.rejectStage(projectPath, runId, stageType, reviewer, reason));
    }

    private void handleShow(String[] args) {
        Path projectPath = projectPath(args);
        UUID runId = runId(args, 2);
        StageType stageType = stage(args, 3);
        boolean review = argumentSupport.hasFlag(args, "--review");
        boolean history = argumentSupport.hasFlag(args, "--history");
        if (review && history) {
            System.out.println(artifactStore.readReviewHistory(projectPath, runId, stageType));
            return;
        }
        if (review) {
            System.out.println(artifactStore.readReviewArtifact(projectPath, runId, stageType));
            return;
        }
        System.out.println(artifactStore.readArtifact(projectPath, runId, stageType));
    }

    private void handleResume(String[] args) {
        Path projectPath = projectPath(args);
        RunRecord runRecord = workflowEngine.resumeRun(projectPath, runId(args, 2));
        if (argumentSupport.hasFlag(args, "--auto-approve")) {
            String reviewer = argumentSupport.option(args, "--reviewer", "autopilot");
            printSummary(autoApproveToTerminal(projectPath, runRecord, reviewer));
            return;
        }
        printSummary(runRecord);
    }

    private void handleLogs(String[] args) {
        System.out.println(eventLogStore.read(projectPath(args), runId(args, 2)));
    }

    private RunRecord autoApproveToTerminal(Path projectPath, RunRecord runRecord, String reviewer) {
        RunRecord current = runRecord;
        while (current.status() == RunStatus.BLOCKED) {
            StageExecution stageExecution = current.stageStates().get(current.currentStage());
            if (stageExecution == null || stageExecution.status() != StageStatus.AWAITING_HUMAN_REVIEW) {
                break;
            }
            current = workflowEngine.approveStage(projectPath, current.runId(), current.currentStage(), reviewer);
        }
        return current;
    }

    private Path projectPath(String[] args) {
        return argumentSupport.optionPath(args, "--project", Path.of(".").toAbsolutePath().normalize());
    }

    private UUID runId(String[] args, int position) {
        return UUID.fromString(argumentSupport.requiredPositional(args, position));
    }

    private StageType stage(String[] args, int position) {
        return argumentSupport.parseStage(argumentSupport.requiredPositional(args, position));
    }

    private void printSummary(RunRecord runRecord) {
        System.out.println(outputRenderer.renderSummary(runRecord));
    }
}
