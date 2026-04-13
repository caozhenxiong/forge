package devflow.agent.review;

import devflow.agent.context.ContractExtractor;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentStructureGuardTests {

    private final DocumentStructureGuard guard = new DocumentStructureGuard();
    private final ContractExtractor contractExtractor = new ContractExtractor();

    @Test
    void rejectsMissingRequiredSection() {
        ReviewResult result = guard.enforce(
                dummyRun(),
                StageType.PRD,
                """
                # 产品需求文档

                ## 1. 产品目标
                目标
                """,
                "PRD",
                new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "")
        );

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertTrue(result.summary().contains("缺少规范章节"));
    }

    @Test
    void noLongerGuessesUnsourcedQuantitativeConstraintFromBodyProse() {
        ReviewResult result = guard.enforce(
                dummyRun(),
                StageType.PRD,
                withProductContractBlock("""
                # 产品需求文档

                ## 1. 产品目标
                目标

                ## 2. 目标用户与使用场景
                场景

                ## 3. 功能范围
                - 提供基础交互

                ## 4. 非功能要求
                页面响应时间必须小于 100ms。

                ## 5. 验收标准
                - 页面可打开

                ## 6. 不做什么
                不做联网

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens

                ## 8. Source Metadata
                - hard.userRequirements: 纯网页版、可直接打开运行
                - hard.upstreamFacts: 需要页面可打开
                """),
                "PRD",
                new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "")
        );

        assertEquals(ReviewDecision.APPROVED, result.decision());
    }

    @Test
    void rejectsInconsistentProductContractBlock() {
        ReviewResult result = guard.enforce(
                dummyRun(),
                StageType.PRD,
                """
                # 产品需求文档

                ## 1. 产品目标
                目标

                ## 2. 目标用户与使用场景
                场景

                ## 3. 功能范围
                功能

                ## 4. 非功能要求
                保持流畅体验。

                ## 5. 验收标准
                标准

                ## 6. 不做什么
                不做联网

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens

                ## 8. Source Metadata
                - hard.userRequirements: 纯网页版、可直接打开运行
                - hard.upstreamFacts: 需要页面可打开

                <!-- DEVFLOW:PRODUCT_CONTRACT:BEGIN -->
                {
                  "objectives" : [ "提供可直接运行的网页交付物" ],
                  "userScenarios" : [ "打开页面开始游戏" ],
                  "requiredCapabilities" : [ "支持开始游戏" ],
                  "optionalCapabilities" : [ ],
                  "nonFunctionalRequirements" : [ "页面加载时间不超过 2 秒", "游戏帧率稳定在 30 FPS 以上", "键盘响应延迟不超过 50ms" ],
                  "acceptanceCriteria" : [ "页面加载时间小于 2 秒" ],
                  "nonGoals" : [ "联网功能" ],
                  "requirementReferences" : [
                    {
                      "id" : "CAP-1",
                      "category" : "required-capability",
                      "text" : "支持开始游戏",
                      "obligation" : "PLANNING_REQUIRED"
                    },
                    {
                      "id" : "ACC-1",
                      "category" : "acceptance-criterion",
                      "text" : "页面加载时间小于 2 秒",
                      "obligation" : "FINAL_ACCEPTANCE"
                    }
                  ]
                }
                <!-- DEVFLOW:PRODUCT_CONTRACT:END -->

                <!-- DEVFLOW:VALIDATION_METADATA:BEGIN -->
                {
                  "performanceMeasurementRequired" : false,
                  "pageLoadMaxMs" : null,
                  "interactionMaxMs" : null
                }
                <!-- DEVFLOW:VALIDATION_METADATA:END -->
                """,
                "PRD",
                new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "")
        );

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertTrue(result.summary().contains("PRODUCT_CONTRACT"));
    }

    private RunRecord dummyRun() {
        EnumMap<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        stageStates.put(StageType.ANALYSIS, new StageExecution(StageType.ANALYSIS, StageStatus.APPROVED, 1, "", ReviewDecision.APPROVED, "", ""));
        return new RunRecord(
                UUID.randomUUID(),
                Path.of("."),
                "做一个纯网页版俄罗斯方块",
                "可直接打开运行",
                RunConfig.defaultConfig(),
                StageType.PRD,
                RunStatus.IN_PROGRESS,
                stageStates,
                Instant.now(),
                Instant.now()
        );
    }

    private String withProductContractBlock(String prd) {
        return prd + "\n\n" + StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.PRODUCT_CONTRACT,
                contractExtractor.projectProductContractFromPrd(prd)
        );
    }
}
