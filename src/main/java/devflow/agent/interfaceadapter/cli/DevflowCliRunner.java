package devflow.agent.interfaceadapter.cli;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.orchestrator.DefaultWorkflowEngine;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageType;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DevflowCliRunner implements CommandLineRunner {

    private final DefaultWorkflowEngine workflowEngine;
    private final FileArtifactStore artifactStore;
    private final EventLogStore eventLogStore;

    public DevflowCliRunner(DefaultWorkflowEngine workflowEngine, FileArtifactStore artifactStore, EventLogStore eventLogStore) {
        this.workflowEngine = workflowEngine;
        this.artifactStore = artifactStore;
        this.eventLogStore = eventLogStore;
    }

    @Override
    public void run(String... args) {
        if (args.length == 0) {
            return;
        }

        String command = args[0];
        if ("init".equals(command)) {
            Path projectPath = optionPath(args, "--project", Path.of(".").toAbsolutePath().normalize());
            workflowEngine.initialize(projectPath);
            System.out.println("Initialized devflow workspace: " + projectPath.resolve(".devflow"));
            return;
        }

        if (!"run".equals(command) || args.length < 2) {
            printUsage();
            return;
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
            default -> printUsage();
        }
    }

    private void handleAutopilot(String[] args) {
        Path projectPath = optionPath(args, "--project", Path.of(".").toAbsolutePath().normalize());
        String goal = requiredOption(args, "--goal");
        String constraints = option(args, "--constraints", "");
        String reviewer = option(args, "--reviewer", "autopilot");
        RunRecord created = workflowEngine.createRun(projectPath, goal, constraints);
        RunRecord started = workflowEngine.startRun(projectPath, created.runId());
        RunRecord finished = autoApproveToTerminal(projectPath, started, reviewer);
        printSummary(finished);
    }

    private void handleBootstrap(String[] args) {
        Path projectPath = optionPath(args, "--project", Path.of(".").toAbsolutePath().normalize());
        String goal = requiredOption(args, "--goal");
        String constraints = option(args, "--constraints", "");
        RunRecord created = workflowEngine.createRun(projectPath, goal, constraints);
        RunRecord started = workflowEngine.startRun(projectPath, created.runId());
        if (hasFlag(args, "--auto-approve")) {
            String reviewer = option(args, "--reviewer", "autopilot");
            printSummary(autoApproveToTerminal(projectPath, started, reviewer));
            return;
        }
        printSummary(started);
    }

    private void handleCreate(String[] args) {
        Path projectPath = optionPath(args, "--project", Path.of(".").toAbsolutePath().normalize());
        String goal = requiredOption(args, "--goal");
        String constraints = option(args, "--constraints", "");
        RunRecord runRecord = workflowEngine.createRun(projectPath, goal, constraints);
        System.out.println("Created run: " + runRecord.runId());
        System.out.println("Project: " + runRecord.projectPath());
        System.out.println("Current stage: " + runRecord.currentStage());
    }

    private void handleStart(String[] args) {
        Path projectPath = optionPath(args, "--project", Path.of(".").toAbsolutePath().normalize());
        UUID runId = UUID.fromString(requiredPositional(args, 2));
        RunRecord runRecord = workflowEngine.startRun(projectPath, runId);
        printSummary(runRecord);
    }

    private void handleStatus(String[] args) {
        Path projectPath = optionPath(args, "--project", Path.of(".").toAbsolutePath().normalize());
        UUID runId = UUID.fromString(requiredPositional(args, 2));
        RunRecord runRecord = workflowEngine.find(projectPath, runId);
        printSummary(runRecord);
    }

    private void handleApprove(String[] args) {
        Path projectPath = optionPath(args, "--project", Path.of(".").toAbsolutePath().normalize());
        UUID runId = UUID.fromString(requiredPositional(args, 2));
        StageType stageType = parseStage(requiredPositional(args, 3));
        String reviewer = option(args, "--reviewer", "human");
        RunRecord runRecord = workflowEngine.approveStage(projectPath, runId, stageType, reviewer);
        printSummary(runRecord);
    }

    private void handleReject(String[] args) {
        Path projectPath = optionPath(args, "--project", Path.of(".").toAbsolutePath().normalize());
        UUID runId = UUID.fromString(requiredPositional(args, 2));
        StageType stageType = parseStage(requiredPositional(args, 3));
        String reviewer = option(args, "--reviewer", "human");
        String reason = requiredOption(args, "--reason");
        RunRecord runRecord = workflowEngine.rejectStage(projectPath, runId, stageType, reviewer, reason);
        printSummary(runRecord);
    }

    private void handleShow(String[] args) {
        Path projectPath = optionPath(args, "--project", Path.of(".").toAbsolutePath().normalize());
        UUID runId = UUID.fromString(requiredPositional(args, 2));
        StageType stageType = parseStage(requiredPositional(args, 3));
        boolean review = hasFlag(args, "--review");
        boolean history = hasFlag(args, "--history");
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
        Path projectPath = optionPath(args, "--project", Path.of(".").toAbsolutePath().normalize());
        UUID runId = UUID.fromString(requiredPositional(args, 2));
        RunRecord runRecord = workflowEngine.resumeRun(projectPath, runId);
        if (hasFlag(args, "--auto-approve")) {
            String reviewer = option(args, "--reviewer", "autopilot");
            printSummary(autoApproveToTerminal(projectPath, runRecord, reviewer));
            return;
        }
        printSummary(runRecord);
    }

    private void handleLogs(String[] args) {
        Path projectPath = optionPath(args, "--project", Path.of(".").toAbsolutePath().normalize());
        UUID runId = UUID.fromString(requiredPositional(args, 2));
        System.out.println(eventLogStore.read(projectPath, runId));
    }

    private void printSummary(RunRecord runRecord) {
        System.out.println("runId: " + runRecord.runId());
        System.out.println("status: " + runRecord.status());
        System.out.println("currentStage: " + runRecord.currentStage());
        System.out.println("projectPath: " + runRecord.projectPath());
        System.out.println("goal: " + runRecord.goal());
        if (!runRecord.constraints().isBlank()) {
            System.out.println("constraints: " + runRecord.constraints());
        }
        System.out.println("stages:");
        for (Map.Entry<StageType, StageExecution> entry : runRecord.stageStates().entrySet()) {
            StageExecution execution = entry.getValue();
            System.out.printf(
                    Locale.ROOT,
                    "  - %-16s %-22s attempt=%d artifact=%s%n",
                    entry.getKey(),
                    execution.status(),
                    execution.attempt(),
                    execution.artifactPath() == null ? "-" : execution.artifactPath()
            );
            if (execution.reviewDecision() != null) {
                System.out.printf(
                        Locale.ROOT,
                        "    review=%s summary=%s changeRequest=%s%n",
                        execution.reviewDecision(),
                        safe(execution.reviewSummary()),
                        safe(execution.changeRequest())
                );
            }
        }
    }

    private StageType parseStage(String value) {
        return StageType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private Path optionPath(String[] args, String optionName, Path defaultValue) {
        return Path.of(option(args, optionName, defaultValue.toString())).toAbsolutePath().normalize();
    }

    private String option(String[] args, String optionName, String defaultValue) {
        for (int index = 0; index < args.length - 1; index++) {
            if (optionName.equals(args[index])) {
                StringBuilder value = new StringBuilder();
                for (int next = index + 1; next < args.length; next++) {
                    String token = args[next];
                    if (token.startsWith("--")) {
                        break;
                    }
                    if (!value.isEmpty()) {
                        value.append(' ');
                    }
                    value.append(token);
                }
                return value.isEmpty() ? defaultValue : value.toString();
            }
        }
        return defaultValue;
    }

    private String requiredOption(String[] args, String optionName) {
        String value = option(args, optionName, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option: " + optionName);
        }
        return value;
    }

    private String requiredPositional(String[] args, int position) {
        if (args.length <= position) {
            throw new IllegalArgumentException("Missing required positional argument at index " + position + ": " + Arrays.toString(args));
        }
        return args[position];
    }

    private boolean hasFlag(String[] args, String flag) {
        return Arrays.stream(args).anyMatch(flag::equals);
    }

    private RunRecord autoApproveToTerminal(Path projectPath, RunRecord runRecord, String reviewer) {
        RunRecord current = runRecord;
        while (current.status() == devflow.agent.orchestrator.RunStatus.BLOCKED) {
            StageExecution stageExecution = current.stageStates().get(current.currentStage());
            if (stageExecution == null || stageExecution.status() != devflow.agent.orchestrator.StageStatus.AWAITING_HUMAN_REVIEW) {
                break;
            }
            current = workflowEngine.approveStage(projectPath, current.runId(), current.currentStage(), reviewer);
        }
        return current;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void printUsage() {
        System.out.println("""
                Usage:
                  init [--project PATH]
                  run autopilot --goal TEXT [--constraints TEXT] [--reviewer NAME] [--project PATH]
                  run bootstrap --goal TEXT [--constraints TEXT] [--project PATH]
                  run bootstrap --goal TEXT [--constraints TEXT] [--auto-approve] [--reviewer NAME] [--project PATH]
                  run create --goal TEXT [--constraints TEXT] [--project PATH]
                  run start <runId> [--project PATH]
                  run status <runId> [--project PATH]
                  run approve <runId> <stage> [--reviewer NAME] [--project PATH]
                  run reject <runId> <stage> --reason TEXT [--reviewer NAME] [--project PATH]
                  run show <runId> <stage> [--review] [--project PATH]
                  run resume <runId> [--auto-approve] [--reviewer NAME] [--project PATH]
                  run logs <runId> [--project PATH]
                """);
    }
}
