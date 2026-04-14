package devflow.agent.supervisor;

import devflow.agent.executor.generation.GenerationFailureReport;
import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.llm.LlmProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ArtifactSummaryBuilder;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContextProjector;
import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.domain.GatePolicy;
import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.orchestrator.StageFlowPolicy;
import devflow.agent.domain.StageType;
import devflow.agent.domain.WorkflowAction;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisorAgentTests {

    @TempDir
    Path tempDir;

    private SupervisorAgent newSupervisorAgent(LlmProvider provider, FileArtifactStore artifactStore) {
        StageFlowPolicy stageFlowPolicy = new StageFlowPolicy();
        return new SupervisorAgent(
                provider,
                new ObjectMapper(),
                new ContextProjector(
                        artifactStore,
                        new FileProjectWorkspace(),
                        new ArtifactSummaryBuilder(),
                        new ContractExtractor(),
                        new devflow.agent.context.ContextLayerAssembler()
                ),
                stageFlowPolicy,
                new SupervisorFallbackPolicy(stageFlowPolicy)
        );
    }

    @Test
    void fallbackRequestsHumanReviewForApprovedDocumentStage() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        SupervisorAgent supervisorAgent = newSupervisorAgent(null, artifactStore);

        RunRecord runRecord = runRecord(StageType.ANALYSIS, GatePolicy.AGENT_PLUS_HUMAN);
        SupervisorDecision decision = supervisorAgent.decide(
                tempDir,
                runRecord,
                StageType.ANALYSIS,
                new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", ""),
                false
        );

        assertEquals(WorkflowAction.REQUEST_HUMAN_REVIEW, decision.action());
        assertEquals(StageType.ANALYSIS, decision.targetStage());
    }

    @Test
    void fallbackRoutesToRepairWhenRepeatedImplementationIssueDetected() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        SupervisorAgent supervisorAgent = newSupervisorAgent(null, artifactStore);

        RunRecord runRecord = runRecord(StageType.CODE_REVIEW, GatePolicy.AGENT_PLUS_HUMAN);
        SupervisorDecision decision = supervisorAgent.decide(
                tempDir,
                runRecord,
                StageType.CODE_REVIEW,
                new ReviewResult(ReviewDecision.REVISION_REQUIRED, FixMode.PATCH, "仍有相同问题", "继续修复"),
                true
        );

        assertEquals(WorkflowAction.ROUTE_TO_REPAIR, decision.action());
        assertEquals(StageType.IMPLEMENTATION, decision.targetStage());
    }

    @Test
    void documentStageCannotRouteToImplementationRepairEvenWhenModelRequestsIt() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return """
                        {
                          "action": "ROUTE_TO_REPAIR",
                          "targetStage": "IMPLEMENTATION",
                          "mode": "PATCH",
                          "reason": "重复问题，直接去 repair",
                          "focus": ["删除无来源量化指标"],
                          "constraints": [],
                          "requiredEvidence": ["PRD 中删除量化指标"],
                          "deliveryPolicy": {
                            "mode": "PATCH",
                            "maxFiles": 1,
                            "maxSymbols": 2,
                            "preferPreciseEditing": true,
                            "forceBacklogSplit": true,
                            "requireVerificationBeforeReview": true
                          },
                          "humanRequired": false
                        }
                        """;
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
        SupervisorAgent supervisorAgent = newSupervisorAgent(provider, artifactStore);

        RunRecord runRecord = runRecord(StageType.PRD, GatePolicy.AGENT_ONLY);
        SupervisorDecision decision = supervisorAgent.decide(
                tempDir,
                runRecord,
                StageType.PRD,
                new ReviewResult(ReviewDecision.REVISION_REQUIRED, FixMode.PATCH, "文档仍有相同问题", "继续修订 PRD"),
                true
        );

        assertEquals(WorkflowAction.RETRY_STAGE, decision.action());
        assertEquals(StageType.PRD, decision.targetStage());
    }

    @Test
    void failedReviewCannotRequestHumanReviewWhenStageIsAgentOnly() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return """
                        {
                          "action": "REQUEST_HUMAN_REVIEW",
                          "targetStage": "IMPLEMENTATION",
                          "mode": "PATCH",
                          "reason": "尝试转人工",
                          "focus": [],
                          "constraints": [],
                          "requiredEvidence": [],
                          "deliveryPolicy": {
                            "mode": "PATCH",
                            "maxFiles": 2,
                            "maxSymbols": 2,
                            "preferPreciseEditing": true,
                            "forceBacklogSplit": false,
                            "requireVerificationBeforeReview": true
                          },
                          "humanRequired": true
                        }
                        """;
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
        SupervisorAgent supervisorAgent = newSupervisorAgent(provider, artifactStore);

        RunRecord runRecord = runRecord(StageType.IMPLEMENTATION, GatePolicy.AGENT_ONLY);
        SupervisorDecision decision = supervisorAgent.decide(
                tempDir,
                runRecord,
                StageType.IMPLEMENTATION,
                new ReviewResult(ReviewDecision.REVISION_REQUIRED, FixMode.PATCH, "需要修补", "补一个点"),
                false
        );

        assertEquals(WorkflowAction.RETRY_STAGE, decision.action());
        assertEquals(StageType.IMPLEMENTATION, decision.targetStage());
    }

    @Test
    void approvedReviewCannotBypassHumanGateWhenStageRequiresHumanReview() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return """
                        {
                          "action": "ADVANCE_STAGE",
                          "targetStage": "PRD",
                          "mode": "NONE",
                          "reason": "直接进入下一阶段",
                          "focus": [],
                          "constraints": [],
                          "requiredEvidence": [],
                          "deliveryPolicy": {
                            "mode": "INCREMENTAL",
                            "maxFiles": 2,
                            "maxSymbols": 4,
                            "preferPreciseEditing": true,
                            "forceBacklogSplit": false,
                            "requireVerificationBeforeReview": true
                          },
                          "humanRequired": false
                        }
                        """;
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
        SupervisorAgent supervisorAgent = newSupervisorAgent(provider, artifactStore);

        RunRecord runRecord = runRecord(StageType.ANALYSIS, GatePolicy.AGENT_PLUS_HUMAN);
        SupervisorDecision decision = supervisorAgent.decide(
                tempDir,
                runRecord,
                StageType.ANALYSIS,
                new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", ""),
                false
        );

        assertEquals(WorkflowAction.REQUEST_HUMAN_REVIEW, decision.action());
        assertEquals(StageType.ANALYSIS, decision.targetStage());
    }

    @Test
    void generationFailureFallsBackToRetryThenRepair() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        SupervisorAgent supervisorAgent = newSupervisorAgent(null, artifactStore);

        GenerationFailureReport report = new GenerationFailureReport(
                "game.js",
                "PATCH",
                "precise-code",
                GenerationFailureType.MODEL_OUTPUT_INVALID,
                3,
                true,
                "game.js 的符号级精确改写 JSON 非法。",
                "No JSON object found in model response",
                "请只返回合法 JSON；必须提供 targetPath、baseContentHash、oldText、newText、replaceAll，且 oldText 范围必须留在当前编辑单元。"
        );

        GenerationRecoveryDecision firstDecision = supervisorAgent.decideGenerationFailure(
                tempDir,
                runRecord(StageType.IMPLEMENTATION, GatePolicy.AGENT_ONLY),
                report,
                1,
                "修复 game.js",
                "更新 tick 逻辑",
                "",
                DeliveryPolicy.patchSafe()
        );
        GenerationRecoveryDecision repeatedDecision = supervisorAgent.decideGenerationFailure(
                tempDir,
                runRecord(StageType.IMPLEMENTATION, GatePolicy.AGENT_ONLY),
                report,
                2,
                "修复 game.js",
                "更新 tick 逻辑",
                "",
                DeliveryPolicy.patchSafe()
        );

        assertEquals(GenerationRecoveryAction.RETRY_SUBTASK, firstDecision.action());
        assertTrue(firstDecision.deliveryPolicy().preferPreciseEditing());
        assertEquals(GenerationRecoveryAction.ROUTE_TO_REPAIR, repeatedDecision.action());
        assertTrue(repeatedDecision.deliveryPolicy().preferPreciseEditing());
    }

    @Test
    void supervisorMayKeepPreciseEditingForPreciseGenerationFailure() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return """
                        {
                          "action": "RETRY_SUBTASK",
                          "reason": "继续精确改写",
                          "focus": ["重试当前 patch"],
                          "constraints": ["保持当前策略"],
                          "requiredEvidence": [],
                          "deliveryPolicy": {
                            "mode": "PATCH",
                            "maxFiles": 1,
                            "maxSymbols": 1,
                            "preferPreciseEditing": true,
                            "forceBacklogSplit": false,
                            "requireVerificationBeforeReview": true
                          }
                        }
                        """;
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
        SupervisorAgent supervisorAgent = newSupervisorAgent(provider, artifactStore);

        GenerationFailureReport report = new GenerationFailureReport(
                "game.js",
                "INCREMENTAL",
                "precise-code",
                GenerationFailureType.OUTPUT_TRUNCATED,
                3,
                true,
                "game.js 的精确改写输出被截断。",
                "done_reason=length",
                "请收缩改单范围。"
        );

        GenerationRecoveryDecision decision = supervisorAgent.decideGenerationFailure(
                tempDir,
                runRecord(StageType.IMPLEMENTATION, GatePolicy.AGENT_ONLY),
                report,
                1,
                "修复 game.js",
                "补齐 tick 逻辑",
                "",
                DeliveryPolicy.recoverySafe()
        );

        assertEquals(GenerationRecoveryAction.RETRY_SUBTASK, decision.action());
        assertTrue(decision.deliveryPolicy().preferPreciseEditing());
    }

    @Test
    void approvedDocumentDecisionPreservesStructuredGuidanceWithoutProseGuessing() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return """
                        {
                          "action": "ADVANCE_STAGE",
                          "targetStage": "PRD",
                          "mode": "NONE",
                          "reason": "继续推进",
                          "focus": ["明确像素风格的像素大小", "保留用户要求中的开始/暂停/重开"],
                          "constraints": ["控制响应时间小于 100ms", "不要偏离纯网页版约束"],
                          "requiredEvidence": ["量化指标的定义与评估方法", "网页入口可直接打开"],
                          "deliveryPolicy": {
                            "mode": "INCREMENTAL",
                            "maxFiles": 2,
                            "maxSymbols": 4,
                            "preferPreciseEditing": true,
                            "forceBacklogSplit": false,
                            "requireVerificationBeforeReview": true
                          },
                          "humanRequired": false
                        }
                        """;
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
        SupervisorAgent supervisorAgent = newSupervisorAgent(provider, artifactStore);

        RunRecord runRecord = runRecord(StageType.ANALYSIS, GatePolicy.AGENT_ONLY);
        SupervisorDecision decision = supervisorAgent.decide(
                tempDir,
                runRecord,
                StageType.ANALYSIS,
                new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "分析已满足要求", ""),
                false
        );

        assertTrue(decision.focus().contains("明确像素风格的像素大小"));
        assertTrue(decision.constraints().contains("控制响应时间小于 100ms"));
        assertTrue(decision.requiredEvidence().contains("量化指标的定义与评估方法"));
        assertEquals(WorkflowAction.ADVANCE_STAGE, decision.action());
    }

    private RunRecord runRecord(StageType currentStage, GatePolicy gatePolicy) {
        EnumMap<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        stageStates.put(currentStage, new StageExecution(currentStage, StageStatus.RUNNING, 1, null, null, null, null));
        Map<StageType, GatePolicy> policies = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            policies.put(stageType, GatePolicy.AGENT_ONLY);
        }
        policies.put(currentStage, gatePolicy);
        return new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "实现一个可玩的网页版俄罗斯方块",
                "需要纯网页版、可直接打开运行、像素风、支持开始/暂停/重开、方向键控制、显示得分和下一个方块预览",
                new RunConfig(policies, 5),
                currentStage,
                RunStatus.IN_PROGRESS,
                stageStates,
                Instant.now(),
                Instant.now()
        );
    }
}
