package devflow.agent.interfaceadapter.cli;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageType;
import java.util.Locale;
import java.util.Map;

/**
 * CLI 输出渲染支撑。
 */
final class CliOutputRenderer {

    String renderSummary(RunRecord runRecord) {
        StringBuilder builder = new StringBuilder();
        builder.append("runId: ").append(runRecord.runId()).append('\n');
        builder.append("status: ").append(runRecord.status()).append('\n');
        builder.append("currentStage: ").append(runRecord.currentStage()).append('\n');
        builder.append("projectPath: ").append(runRecord.projectPath()).append('\n');
        builder.append("goal: ").append(runRecord.goal()).append('\n');
        if (!runRecord.constraints().isBlank()) {
            builder.append("constraints: ").append(runRecord.constraints()).append('\n');
        }
        builder.append("stages:\n");
        for (Map.Entry<StageType, StageExecution> entry : runRecord.stageStates().entrySet()) {
            StageExecution execution = entry.getValue();
            builder.append(String.format(
                    Locale.ROOT,
                    "  - %-16s %-22s attempt=%d artifact=%s%n",
                    entry.getKey(),
                    execution.status(),
                    execution.attempt(),
                    execution.artifactPath() == null ? "-" : execution.artifactPath()
            ));
            if (execution.reviewDecision() != null) {
                builder.append(String.format(
                        Locale.ROOT,
                        "    review=%s summary=%s changeRequest=%s%n",
                        execution.reviewDecision(),
                        safe(execution.reviewSummary()),
                        safe(execution.changeRequest())
                ));
            }
        }
        return builder.toString().stripTrailing();
    }

    String renderUsage() {
        return """
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
                """;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
