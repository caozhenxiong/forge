package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.orchestrator.RunConfig;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageStatus;
import devflow.agent.orchestrator.StageType;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationPlanningRetryPolicyTests {

    @TempDir
    Path tempDir;

    @Test
    void retriesRecoverablePlanningFailuresAndPublishesEvents() {
        AtomicInteger calls = new AtomicInteger();
        ImplementationPlanner planner = new ImplementationPlanner(
                null,
                new ObjectMapper(),
                new ImplementationPlanCoverageAnalyzer(),
                new AgentTurnLoop(),
                1,
                2,
                3
        ) {
            @Override
            ImplementationPlan plan(
                    Path projectPath,
                    RunRecord runRecord,
                    String analysis,
                    String prd,
                    String design,
                    String note,
                    String workspaceContext,
                    String plannerContextMarkdown,
                    String performanceValidationGuidance,
                    boolean preferSkeletonFlow,
                    DeliveryPolicyEnvelope deliveryPolicy,
                    ContractView contractView,
                    QualityPlan qualityPlan,
                    ProjectFingerprint fingerprint,
                    DocumentLanguage language,
                    FixMode fixMode,
                    ImplementationPatchTarget implementationPatchTarget,
                    String requirementCatalog,
                    ImplementationContinuationConstraints continuationConstraints
            ) {
                if (calls.incrementAndGet() == 1) {
                    throw new ImplementationPlanningException(
                            ImplementationPlanningFailureReason.COVERAGE_MISMATCH,
                            "Implementation plan does not satisfy execution contract: missing runnable milestone",
                            null,
                            new GenerationTelemetry("qwen3-coder:30b", "IMPLEMENTATION", 1200, 1180, 640, 36864, 1800, 33884, 4400, 4400, "stop")
                    );
                }
                return new ImplementationPlan("ok", List.of());
            }
        };
        ImplementationPlanningRetryPolicy retryPolicy = new ImplementationPlanningRetryPolicy(planner, 3);
        List<String> events = new ArrayList<>();

        ImplementationPlan plan = retryPolicy.planWithInternalRetries(
                tempDir,
                runRecord(),
                "# analysis",
                "# prd",
                "# design",
                "",
                "",
                "",
                "",
                false,
                new DeliveryPolicyEnvelope(DeliveryMode.INCREMENTAL, 2, 4, true, false, true, List.of()),
                null,
                QualityPlan.empty(),
                null,
                DocumentLanguage.ZH,
                FixMode.NONE,
                ImplementationPatchTarget.NONE,
                "",
                ImplementationContinuationConstraints.empty(),
                events::add
        );

        assertEquals("ok", plan.summary());
        assertEquals(2, calls.get());
        assertEquals(1, events.size());
        assertTrue(events.get(0).contains("IMPLEMENTATION planning retry attempt=1/3"));
        assertTrue(events.get(0).contains("输入token=估算1200/实际1180"));
        assertTrue(events.get(0).contains("输出token=640"));
    }

    @Test
    void throwsStableExhaustionErrorAfterRecoverableFailures() {
        ImplementationPlanner planner = new ImplementationPlanner(
                null,
                new ObjectMapper(),
                new ImplementationPlanCoverageAnalyzer(),
                new AgentTurnLoop(),
                1,
                2,
                3
        ) {
            @Override
            ImplementationPlan plan(
                    Path projectPath,
                    RunRecord runRecord,
                    String analysis,
                    String prd,
                    String design,
                    String note,
                    String workspaceContext,
                    String plannerContextMarkdown,
                    String performanceValidationGuidance,
                    boolean preferSkeletonFlow,
                    DeliveryPolicyEnvelope deliveryPolicy,
                    ContractView contractView,
                    QualityPlan qualityPlan,
                    ProjectFingerprint fingerprint,
                    DocumentLanguage language,
                    FixMode fixMode,
                    ImplementationPatchTarget implementationPatchTarget,
                    String requirementCatalog,
                    ImplementationContinuationConstraints continuationConstraints
            ) {
                throw new ImplementationPlanningException(
                        ImplementationPlanningFailureReason.PLAN_PARSE_FAILED,
                        "Failed to parse implementation plan: schema mismatch"
                );
            }
        };
        ImplementationPlanningRetryPolicy retryPolicy = new ImplementationPlanningRetryPolicy(planner, 2);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> retryPolicy.planWithInternalRetries(
                tempDir,
                runRecord(),
                "# analysis",
                "# prd",
                "# design",
                "",
                "",
                "",
                "",
                false,
                new DeliveryPolicyEnvelope(DeliveryMode.INCREMENTAL, 2, 4, true, false, true, List.of()),
                null,
                QualityPlan.empty(),
                null,
                DocumentLanguage.ZH,
                FixMode.NONE,
                ImplementationPatchTarget.NONE,
                "",
                ImplementationContinuationConstraints.empty(),
                event -> {}
        ));

        assertTrue(exception.getMessage().contains("Implementation planning exhausted internal retries"));
        assertTrue(exception.getMessage().contains("Failed to parse implementation plan"));
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
}
