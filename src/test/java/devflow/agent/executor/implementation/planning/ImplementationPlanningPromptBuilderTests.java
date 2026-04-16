package devflow.agent.executor.implementation.planning;

import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
import devflow.agent.executor.gate.GateFailureDisposition;
import devflow.agent.executor.gate.GateIssue;
import devflow.agent.executor.gate.GateReport;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.DeliveryPolicyEnvelope;
import devflow.agent.executor.llm.ModelRole;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.testsupport.RequestBackedLlmProvider;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationPlanningPromptBuilderTests {

    @TempDir
    Path tempDir;

    @Test
    void outlineSystemPromptExplainsSharedFileDeferredBoundaryRule() {
        ImplementationOutlinePromptBuilder builder = new ImplementationOutlinePromptBuilder(3);

        String prompt = builder.systemPrompt(
                "",
                new DeliveryPolicyEnvelope(DeliveryMode.INCREMENTAL, 3, 4, true, false, true, List.of()),
                FixMode.NONE,
                ImplementationPatchTarget.NONE,
                ImplementationContinuationConstraints.empty()
        );

        assertTrue(prompt.contains("shared-file capability boundary 规则"));
        assertTrue(prompt.contains("共享同一路径"));
        assertTrue(prompt.contains("deferredCapabilities"));
        assertTrue(prompt.contains("无效示例"));
    }

    @Test
    void detailUserPromptRendersSharedFileBoundaryContextFromOutline() {
        ImplementationSubtaskDetailPromptBuilder builder = new ImplementationSubtaskDetailPromptBuilder(3);
        ImplementationOutlineSubtask current = new ImplementationOutlineSubtask(
                "subtask-1",
                "搭入口和壳层",
                "建立初始页面",
                List.of("CAP-1"),
                List.of("shell"),
                List.of("gameplay", "score"),
                List.of("页面可打开"),
                true,
                DeliveryMode.PATCH,
                List.of("index.html", "src/app.js")
        );
        ImplementationOutline outline = new ImplementationOutline(
                "summary",
                List.of(
                        current,
                        new ImplementationOutlineSubtask(
                                "subtask-2",
                                "补玩法",
                                "补充 gameplay 和 score",
                                List.of("CAP-2"),
                                List.of("gameplay", "score"),
                                List.of(),
                                List.of("玩法可工作"),
                                false,
                                DeliveryMode.PATCH,
                                List.of("src/app.js")
                        )
                )
        );

        String prompt = builder.userPrompt(
                runRecord(),
                null,
                null,
                DocumentLanguage.ZH,
                "",
                PlanningRuntimeFacts.empty(),
                outline,
                current,
                ""
        );

        assertTrue(prompt.contains("共享文件下游边界"));
        assertTrue(prompt.contains("src/app.js"));
        assertTrue(prompt.contains("subtask-2"));
        assertTrue(prompt.contains("gameplay"));
        assertTrue(prompt.contains("score"));
    }

    @Test
    void repairPromptPreservesPlanningBoundaryContractFields() {
        CapturingLlmProvider llmProvider = new CapturingLlmProvider();
        ImplementationPlanningRepairSupport support = new ImplementationPlanningRepairSupport(llmProvider);

        support.repairOutline("not a json payload", new IllegalStateException("broken json"));

        assertTrue(llmProvider.lastSystemPrompt.contains("ownedCapabilities / deferredCapabilities / targetPaths"));
        assertTrue(llmProvider.lastSystemPrompt.contains("boundary contract"));
        assertTrue(llmProvider.lastSystemPrompt.contains("不要删除、改名或用新字段替代"));
    }

    @Test
    void outlineRetryFeedbackRepeatsSharedFileBoundaryContract() {
        ImplementationOutlineGate gate = new ImplementationOutlineGate(new ImplementationPlanCoverageAnalyzer());

        String feedback = gate.toRetryFeedback(GateReport.failure(
                "outline failed",
                List.of(new GateIssue(
                        "PLAN_CAPABILITY_PARTITION_1",
                        "共享文件 deferredCapabilities 不完整",
                        GateFailureDisposition.REPLAN_CURRENT_STAGE
                ))
        ));

        assertTrue(feedback.contains("同一 capability 只能有一个 current owner"));
        assertTrue(feedback.contains("完整 ownedCapabilities 全量写进 deferredCapabilities"));
        assertTrue(feedback.contains("重新拆分 targetPaths"));
    }

    @Test
    void finalPlanRetryFeedbackRepeatsSharedFileBoundaryContract() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());

        String feedback = gate.toPlanningFeedback(GateReport.failure(
                "plan failed",
                List.of(new GateIssue(
                        "PLAN_CAPABILITY_PARTITION_1",
                        "共享文件 deferredCapabilities 不完整",
                        GateFailureDisposition.REPLAN_CURRENT_STAGE
                ))
        ));

        assertTrue(feedback.contains("capability partition 必须与 outline gate 的 shared-file boundary 规则完全一致"));
        assertTrue(feedback.contains("overlap owner、partial defer"));
        assertTrue(feedback.contains("deferredCapabilities"));
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

    private static final class CapturingLlmProvider extends RequestBackedLlmProvider {
        private String lastSystemPrompt = "";

        @Override
        public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
            this.lastSystemPrompt = systemPrompt;
            return "{}";
        }
    }
}
