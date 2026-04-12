package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContractView;
import devflow.agent.context.ContextLayerAssembler;
import devflow.agent.context.DesignContract;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ProductContract;
import devflow.agent.orchestrator.RunConfig;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.CapabilityIds;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.validation.ProjectInspector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationContextResolverTests {

    @TempDir
    Path tempDir;

    @Test
    void resolveUsesAuthoritativeContractAndBuildsStableExecutionContext() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), "<!doctype html><html><body><div id='app'></div></body></html>");

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ImplementationContextResolver resolver = new ImplementationContextResolver(
                workspace,
                new ProjectInspector(workspace),
                new ContractExtractor(),
                new ContextLayerAssembler(),
                new ObjectMapper(),
                2,
                3
        );
        ContractView authoritativeContract = new ContractView(
                ProductContract.projectedFromPrdSections(
                        List.of("实现俄罗斯方块"),
                        List.of("玩家打开页面后立即可见游戏表面"),
                        List.of("支持开始/暂停/重开"),
                        List.of("空格键快速下落"),
                        List.of("运行流畅"),
                        List.of("可以开始游戏"),
                        List.of()
                ),
                new DesignContract(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens")),
                null
        );
        RunRecord runRecord = new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "做一个俄罗斯方块网页游戏",
                "中文输出",
                RunConfig.defaultConfig(),
                devflow.agent.orchestrator.StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                Map.of(),
                Instant.now(),
                Instant.now()
        );

        ImplementationExecutionContext context = resolver.resolve(
                tempDir,
                runRecord,
                "analysis",
                "prd",
                "design",
                ExecutionDirectiveProtocol.renderBlock(
                        new ExecutionDirectivePayload(
                                "PATCH",
                                devflow.agent.review.ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION.name(),
                                List.of(),
                                false,
                                false,
                                "SKELETON",
                                9,
                                6,
                                true,
                                true,
                                true,
                                List.of("页面可打开"),
                                List.of("先给出最小可运行入口"),
                                List.of("不要引入后端"),
                                List.of(),
                                List.of(CapabilityIds.TIMED_STATE_PROGRESSION),
                                List.of(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(),
                                List.of(),
                                null,
                                null,
                                null,
                                null
                        )
                ),
                authoritativeContract,
                new ObjectMapper().writeValueAsString(new ImplementationStateSnapshot(
                        "继续已有入口",
                        List.of(new ImplementationStateSnapshot.PlannedSubtaskState(
                                "建立入口",
                                "创建入口",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("入口可运行"),
                                true,
                                "SKELETON",
                                List.of(new ImplementationStateSnapshot.FileChangeState("index.html", "WRITE", "入口"))
                        )),
                        List.of(new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                                "建立入口",
                                true,
                                List.of()
                        )),
                        List.of(),
                        "",
                        true,
                        false,
                        List.of()
                ))
        );

        assertSame(authoritativeContract, context.contractView());
        assertEquals(FixMode.PATCH, context.fixMode());
        assertEquals(ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION, context.implementationPatchTarget());
        assertEquals(DeliveryMode.SKELETON, context.deliveryPolicy().mode());
        assertEquals(3, context.deliveryPolicy().maxFiles());
        assertEquals(6, context.deliveryPolicy().maxSymbols());
        assertEquals(false, context.preferSkeletonFlow());
        assertTrue(context.sharedContextBundle().mustFixFirst().contains("先给出最小可运行入口"));
        assertTrue(context.sharedContextBundle().forbiddenDirections().contains("不要引入后端"));
        assertTrue(context.sharedContextBundle().requiredEvidence().contains("页面可打开"));
        assertTrue(context.authoritativeCoverageCatalog().contains("CAP-1"));
        assertTrue(context.authoritativeCoverageCatalog().contains("QCAP-TIMED_STATE_PROGRESSION"));
        assertFalse(context.authoritativeCoverageCatalog().contains("QCAP-CAP_1"));
        assertTrue(context.qualityPlan().capabilityMatrix().requires(CapabilityIds.TIMED_STATE_PROGRESSION));
        assertTrue(context.continuationConstraints().active());
        assertTrue(context.continuationConstraints().marksExistingPath("index.html"));
        assertTrue(context.continuationConstraints().protectsHtmlEntry("index.html"));
    }
}
