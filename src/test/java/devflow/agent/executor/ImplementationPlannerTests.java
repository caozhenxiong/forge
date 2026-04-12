package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.orchestrator.RunConfig;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageStatus;
import devflow.agent.orchestrator.StageType;
import devflow.agent.quality.QualityPlan;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationPlannerTests {

    @TempDir
    Path tempDir;

    @Test
    void retriesOnlyCurrentSubtaskDetailWithoutRestartingOutline() {
        SequenceLlmProvider llmProvider = new SequenceLlmProvider(List.of(
                response("""
                        {
                          "summary": "summary",
                          "subtasks": [
                            {
                              "id": "subtask-1",
                              "title": "first",
                              "goal": "goal-1",
                              "deliveryMode": "INCREMENTAL",
                              "runnableMilestone": false,
                              "coverageRefs": [],
                              "ownedCapabilities": ["cap-1"],
                              "deferredCapabilities": [],
                              "acceptanceCriteria": ["acc-1"],
                              "targetPaths": ["src/a.js"]
                            },
                            {
                              "id": "subtask-2",
                              "title": "second",
                              "goal": "goal-2",
                              "deliveryMode": "INCREMENTAL",
                              "runnableMilestone": false,
                              "coverageRefs": [],
                              "ownedCapabilities": ["cap-2"],
                              "deferredCapabilities": [],
                              "acceptanceCriteria": ["acc-2"],
                              "targetPaths": ["src/b.js"]
                            }
                          ]
                        }
                        """),
                response("""
                        {
                          "subtaskId": "subtask-1",
                          "changes": [
                            {
                              "path": "src/a.js",
                              "action": "WRITE",
                              "reason": "update a",
                              "editScope": "AUTO",
                              "runtimeOwnership": null,
                              "hostHtmlPatchRequired": false
                            }
                          ]
                        }
                        """),
                response("""
                        {
                          "subtaskId": "subtask-2",
                          "changes": [
                            {
                              "path": "src/c.js",
                              "action": "WRITE",
                              "reason": "wrong path",
                              "editScope": "AUTO",
                              "runtimeOwnership": null,
                              "hostHtmlPatchRequired": false
                            }
                          ]
                        }
                        """),
                response("""
                        {
                          "subtaskId": "subtask-2",
                          "changes": [
                            {
                              "path": "src/b.js",
                              "action": "WRITE",
                              "reason": "update b",
                              "editScope": "AUTO",
                              "runtimeOwnership": null,
                              "hostHtmlPatchRequired": false
                            }
                          ]
                        }
                        """)
        ));
        ImplementationPlanner planner = newPlanner(llmProvider);
        ImplementationEventJournal eventJournal = newEventJournal();

        ImplementationPlan plan = planner.plan(
                tempDir,
                runRecord(),
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                false,
                new DeliveryPolicyEnvelope(DeliveryMode.INCREMENTAL, 2, 4, true, false, true, List.of()),
                null,
                QualityPlan.empty(),
                null,
                devflow.agent.i18n.DocumentLanguage.ZH,
                devflow.agent.review.FixMode.NONE,
                devflow.agent.review.ImplementationPatchTarget.NONE,
                "",
                ImplementationContinuationConstraints.empty(),
                eventJournal
        );

        assertEquals(2, plan.subtasks().size());
        assertEquals(1, llmProvider.outlinePromptCount());
        assertEquals(1, llmProvider.subtaskPromptCount("subtask-1"));
        assertEquals(2, llmProvider.subtaskPromptCount("subtask-2"));
        assertTrue(eventJournal.snapshot().stream()
                .anyMatch(entry -> entry.message().contains("实现规划｜单元驳回｜类型=SUBTASK_DETAIL｜单元=subtask-2")));
        assertTrue(eventJournal.snapshot().stream()
                .anyMatch(entry -> entry.message().contains("实现规划｜单元通过｜类型=OUTLINE｜单元=outline")));
    }

    private ImplementationPlanner newPlanner(SequenceLlmProvider llmProvider) {
        return new ImplementationPlanner(
                llmProvider,
                new ObjectMapper(),
                new ImplementationPlanCoverageAnalyzer(),
                new devflow.agent.loop.AgentTurnLoop(),
                2,
                3,
                2,
                3
        );
    }

    private ImplementationEventJournal newEventJournal() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        return new ImplementationEventJournal(
                null,
                new FileArtifactStore(runRepository),
                tempDir,
                runRecord()
        );
    }

    private RunRecord runRecord() {
        EnumMap<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        return new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "实现一个可玩的网页版俄罗斯方块",
                "需要纯网页版、可直接打开运行",
                RunConfig.defaultConfig(),
                StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                stageStates,
                Instant.now(),
                Instant.now()
        );
    }

    private SequenceLlmProvider.Response response(String content) {
        return new SequenceLlmProvider.Response(
                content,
                new GenerationTelemetry("qwen3-coder", "IMPLEMENTATION", 1200, 1180, 640, 36864, 1800, 33884, 4400, 4400, "stop")
        );
    }

    private static final class SequenceLlmProvider implements LlmProvider {
        private final Queue<Response> responses;
        private final List<String> userPrompts = new ArrayList<>();
        private GenerationTelemetry lastTelemetry;

        private SequenceLlmProvider(List<Response> responses) {
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
            Response response = responses.remove();
            userPrompts.add(userPrompt);
            lastTelemetry = response.telemetry();
            return response.content();
        }

        @Override
        public GenerationTelemetry consumeLastTelemetry() {
            GenerationTelemetry telemetry = lastTelemetry;
            lastTelemetry = null;
            return telemetry;
        }

        private int outlinePromptCount() {
            return (int) userPrompts.stream()
                    .filter(prompt -> prompt.contains("上一轮 outline 反馈"))
                    .count();
        }

        private int subtaskPromptCount(String subtaskId) {
            return (int) userPrompts.stream()
                    .filter(prompt -> prompt.contains("- id: " + subtaskId))
                    .count();
        }

        private record Response(
                String content,
                GenerationTelemetry telemetry
        ) {
        }
    }
}
