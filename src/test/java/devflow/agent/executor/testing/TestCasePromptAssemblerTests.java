package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContractView;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.quality.CapabilityIds;
import devflow.agent.quality.QualityPlan;
import devflow.agent.quality.QualityPlanFactory;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCasePromptAssemblerTests {

    @TempDir
    Path tempDir;

    @Test
    void promptIncludesAuthoritativeRequirementCatalogAlongsideRequiredCapabilityIds() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), "<!doctype html><html><body><canvas id='board'></canvas></body></html>");

        String prd = """
                # 产品需求文档

                ## 1. 产品目标
                - 实现一个可直接打开运行的网页应用

                ## 2. 目标用户与使用场景
                - 用户打开页面即可使用

                ## 3. 功能范围

                ### 3.1 核心功能
                - 显示下一项预览
                - 支持重置到初始状态

                ## 4. 非功能要求
                - 可直接打开运行

                ## 5. 验收标准
                - 页面可打开
                """;
        String design = """
                # 技术方案设计

                ## 1. 技术目标
                - 交付静态网页入口

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: entry-owned
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, runtime-surface-renders
                """;

        ContractExtractor extractor = new ContractExtractor();
        String prdWithBlock = withProductContractBlock(extractor, prd);
        ContractView contractView = extractor.extractContractView("做一个网页应用", "中文输出", "", prdWithBlock, design);
        ProjectFingerprint fingerprint = new ProjectFingerprint(
                "static-web",
                "none",
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                "index.html",
                Set.of("index.html"),
                List.of("resolved html entry: index.html")
        );
        QualityPlan qualityPlan = new QualityPlanFactory().build(
                tempDir,
                fingerprint,
                contractView,
                ValidationMetadata.empty(),
                null,
                List.of()
        );

        TestCaseGenerationPrompt prompt = new TestCasePromptAssembler(
                new FileProjectWorkspace(),
                extractor
        ).assemble(
                tempDir,
                fingerprint,
                "做一个网页应用",
                "中文输出",
                prdWithBlock,
                design,
                "implementation report",
                null,
                qualityPlan,
                UiRuntimeContract.empty()
        );

        assertTrue(prompt.userPrompt().contains("权威覆盖引用目录"));
        assertTrue(prompt.userPrompt().contains("CAP-1"));
        assertTrue(prompt.userPrompt().contains("显示下一项预览"));
        assertTrue(prompt.userPrompt().contains(CapabilityIds.PAGE_LOAD));
        assertTrue(prompt.userPrompt().contains(CapabilityIds.RUNTIME_STABILITY));
    }

    private String withProductContractBlock(ContractExtractor extractor, String prd) {
        return prd + "\n\n" + StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.PRODUCT_CONTRACT,
                extractor.projectProductContractFromPrd(prd)
        );
    }
}
