package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.toolloop.*;

import devflow.agent.executor.generation.GenerationTelemetry;
import devflow.agent.executor.llm.LlmProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
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

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.implementation.planning.ImplementationContinuationConstraints;
import devflow.agent.executor.implementation.planning.ImplementationPlanCoverageAnalyzer;
import devflow.agent.executor.implementation.planning.ImplementationPlanner;
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
                              "reason": "update a"
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
                              "reason": "wrong path"
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
                              "reason": "update b"
                            }
                          ]
                        }
                        """)
        ));
        ImplementationPlanner planner = newPlanner(llmProvider);
        ImplementationEventJournal eventJournal = newEventJournal();

        ImplementationPlan plan = planner.plan(new PlanningRequest(
                runRecord(),
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
                null,
                eventJournal
        ));

        assertEquals(2, plan.subtasks().size());
        assertEquals(1, llmProvider.outlinePromptCount());
        assertEquals(1, llmProvider.subtaskPromptCount("subtask-1"));
        assertEquals(2, llmProvider.subtaskPromptCount("subtask-2"));
        assertTrue(eventJournal.snapshot().stream()
                .anyMatch(entry -> entry.message().contains("实现规划｜单元驳回｜类型=SUBTASK_DETAIL｜单元=subtask-2")));
        assertTrue(eventJournal.snapshot().stream()
                .anyMatch(entry -> entry.message().contains("实现规划｜单元通过｜类型=OUTLINE｜单元=outline")));
    }

    @Test
    void rejectsContinuationReworkAtOutlineBeforeEnteringSubtaskDetail() {
        SequenceLlmProvider llmProvider = new SequenceLlmProvider(List.of(
                response("""
                        {
                          "summary": "invalid continuation outline",
                          "subtasks": [
                            {
                              "id": "subtask-1",
                              "title": "repair host html",
                              "goal": "rewrite host html",
                              "deliveryMode": "REWORK",
                              "runnableMilestone": true,
                              "coverageRefs": [],
                              "ownedCapabilities": ["cap-1"],
                              "deferredCapabilities": [],
                              "acceptanceCriteria": ["acc-1"],
                              "targetPaths": ["index.html"]
                            }
                          ]
                        }
                        """),
                response("""
                        {
                          "summary": "valid continuation outline",
                          "subtasks": [
                            {
                              "id": "subtask-1",
                              "title": "repair host html",
                              "goal": "patch host html wiring only",
                              "deliveryMode": "PATCH",
                              "runnableMilestone": true,
                              "coverageRefs": [],
                              "ownedCapabilities": ["cap-1"],
                              "deferredCapabilities": [],
                              "acceptanceCriteria": ["acc-1"],
                              "targetPaths": ["index.html"]
                            }
                          ]
                        }
                        """),
                response("""
                        {
                          "subtaskId": "subtask-1",
                          "changes": [
                            {
                              "path": "index.html",
                              "action": "WRITE",
                              "reason": "repair host html wiring"
                            }
                          ]
                        }
                        """)
        ));
        ImplementationPlanner planner = newPlanner(llmProvider);
        ImplementationEventJournal eventJournal = newEventJournal();

        ImplementationPlan plan = planner.plan(new PlanningRequest(
                runRecord(),
                "",
                "",
                "",
                "",
                false,
                new DeliveryPolicyEnvelope(DeliveryMode.PATCH, 2, 4, true, false, true, List.of()),
                null,
                QualityPlan.empty(),
                null,
                devflow.agent.i18n.DocumentLanguage.ZH,
                devflow.agent.review.FixMode.PATCH,
                devflow.agent.review.ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                "",
                new ImplementationContinuationConstraints(
                        List.of("index.html"),
                        List.of(new ImplementationContinuationConstraints.ProtectedHtmlEntryConstraint("index.html"))
                ),
                null,
                eventJournal
        ));

        assertEquals(1, plan.subtasks().size());
        assertEquals(2, llmProvider.outlinePromptCount());
        assertEquals(1, llmProvider.subtaskPromptCount("subtask-1"));
        assertTrue(eventJournal.snapshot().stream().anyMatch(entry ->
                entry.message().contains("实现规划｜单元驳回｜类型=OUTLINE｜单元=outline")
                        && entry.message().contains("REWORK/整页重写: index.html")));
        assertTrue(eventJournal.snapshot().stream().noneMatch(entry ->
                entry.message().contains("实现规划｜单元驳回｜类型=SUBTASK_DETAIL｜单元=subtask-1")));
    }

    @Test
    void replansOutlineWhenRuntimeSplitOmitsHostHtmlPatch() throws Exception {
        java.nio.file.Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!doctype html>
                        <html>
                        <body>
                          <main id="app"></main>
                        </body>
                        </html>
                        """
        );

        SequenceLlmProvider llmProvider = new SequenceLlmProvider(List.of(
                response("""
                        {
                          "summary": "invalid runtime split",
                          "subtasks": [
                            {
                              "id": "subtask-1",
                              "title": "add runtime file",
                              "goal": "create companion runtime only",
                              "deliveryMode": "PATCH",
                              "runnableMilestone": false,
                              "coverageRefs": [],
                              "ownedCapabilities": ["cap-1"],
                              "deferredCapabilities": [],
                              "acceptanceCriteria": ["acc-1"],
                              "targetPaths": ["src/engine.js"]
                            }
                          ]
                        }
                        """),
                response("""
                        {
                          "summary": "valid runtime split",
                          "subtasks": [
                            {
                              "id": "subtask-1",
                              "title": "wire host and runtime",
                              "goal": "patch host html and add runtime companion",
                              "deliveryMode": "PATCH",
                              "runnableMilestone": true,
                              "coverageRefs": [],
                              "ownedCapabilities": ["cap-1"],
                              "deferredCapabilities": [],
                              "acceptanceCriteria": ["acc-1"],
                              "targetPaths": ["index.html", "src/engine.js"]
                            }
                          ]
                        }
                        """),
                response("""
                        {
                          "subtaskId": "subtask-1",
                          "changes": [
                            {
                              "path": "index.html",
                              "action": "WRITE",
                              "reason": "wire host html"
                            },
                            {
                              "path": "src/engine.js",
                              "action": "WRITE",
                              "reason": "add runtime companion",
                              "runtimeScriptRole": "ROOT"
                            }
                          ]
                        }
                        """)
        ));
        ImplementationPlanner planner = newPlanner(llmProvider);
        ImplementationEventJournal eventJournal = newEventJournal();

        ImplementationPlan plan = planner.plan(new PlanningRequest(
                runRecord(),
                "",
                "",
                "",
                "",
                false,
                new DeliveryPolicyEnvelope(DeliveryMode.PATCH, 2, 4, true, false, true, List.of()),
                null,
                QualityPlan.empty(),
                new devflow.agent.validation.ProjectInspector(new devflow.agent.project.FileProjectWorkspace()).inspect(tempDir),
                devflow.agent.i18n.DocumentLanguage.ZH,
                devflow.agent.review.FixMode.NONE,
                devflow.agent.review.ImplementationPatchTarget.NONE,
                "",
                ImplementationContinuationConstraints.empty(),
                null,
                eventJournal
        ));

        assertEquals(1, plan.subtasks().size());
        assertEquals(2, llmProvider.outlinePromptCount());
        assertTrue(plan.subtasks().getFirst().changes().stream().anyMatch(change -> "index.html".equals(change.path())));
        assertTrue(eventJournal.snapshot().stream().anyMatch(entry ->
                entry.message().contains("实现规划｜单元驳回｜类型=OUTLINE｜单元=outline")
                        && entry.message().contains("宿主 HTML patch")));
    }

    @Test
    void retriesCurrentSubtaskDetailWhenNewRuntimeScriptRoleIsMissing() throws Exception {
        java.nio.file.Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!doctype html>
                        <html>
                        <body>
                          <script type="module" src="./index.app.js"></script>
                        </body>
                        </html>
                        """
        );
        java.nio.file.Files.writeString(
                tempDir.resolve("index.app.js"),
                """
                        export function boot() {
                          return true;
                        }
                        """
        );

        SequenceLlmProvider llmProvider = new SequenceLlmProvider(List.of(
                response("""
                        {
                          "summary": "extend runtime",
                          "subtasks": [
                            {
                              "id": "subtask-1",
                              "title": "extend runtime",
                              "goal": "patch current runtime and add a new module",
                              "deliveryMode": "PATCH",
                              "runnableMilestone": false,
                              "coverageRefs": [],
                              "ownedCapabilities": ["cap-1"],
                              "deferredCapabilities": [],
                              "acceptanceCriteria": ["acc-1"],
                              "targetPaths": ["index.app.js", "src/engine.js"]
                            }
                          ]
                        }
                        """),
                response("""
                        {
                          "subtaskId": "subtask-1",
                          "changes": [
                            {
                              "path": "index.app.js",
                              "action": "WRITE",
                              "reason": "extend current runtime"
                            },
                            {
                              "path": "src/engine.js",
                              "action": "WRITE",
                              "reason": "add new module"
                            }
                          ]
                        }
                        """),
                response("""
                        {
                          "subtaskId": "subtask-1",
                          "changes": [
                            {
                              "path": "index.app.js",
                              "action": "WRITE",
                              "reason": "extend current runtime"
                            },
                            {
                              "path": "src/engine.js",
                              "action": "WRITE",
                              "reason": "add new module",
                              "runtimeScriptRole": "LEAF"
                            }
                          ]
                        }
                        """)
        ));
        ImplementationPlanner planner = newPlanner(llmProvider);
        ImplementationEventJournal eventJournal = newEventJournal();

        ImplementationPlan plan = planner.plan(new PlanningRequest(
                runRecord(),
                "",
                "",
                "",
                "",
                false,
                new DeliveryPolicyEnvelope(DeliveryMode.PATCH, 3, 4, true, false, true, List.of()),
                null,
                QualityPlan.empty(),
                new devflow.agent.validation.ProjectInspector(new devflow.agent.project.FileProjectWorkspace()).inspect(tempDir),
                devflow.agent.i18n.DocumentLanguage.ZH,
                devflow.agent.review.FixMode.NONE,
                devflow.agent.review.ImplementationPatchTarget.NONE,
                "",
                ImplementationContinuationConstraints.empty(),
                null,
                eventJournal
        ));

        assertEquals(1, plan.subtasks().size());
        assertEquals(1, llmProvider.outlinePromptCount());
        assertEquals(2, llmProvider.subtaskPromptCount("subtask-1"));
        assertTrue(eventJournal.snapshot().stream().anyMatch(entry ->
                entry.message().contains("实现规划｜单元驳回｜类型=SUBTASK_DETAIL｜单元=subtask-1")
                        && entry.message().contains("runtimeScriptRole=ROOT|LEAF")));
    }

    private ImplementationPlanner newPlanner(SequenceLlmProvider llmProvider) {
        return ImplementationPlanningWiring.createPlanner(
                llmProvider,
                new devflow.agent.project.FileProjectWorkspace(),
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

    private static final class SequenceLlmProvider extends devflow.agent.testsupport.RequestBackedLlmProvider {
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
