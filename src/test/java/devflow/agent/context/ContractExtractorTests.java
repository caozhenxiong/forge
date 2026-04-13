package devflow.agent.context;

import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ContractExtractorTests {

    private final ContractExtractor extractor = new ContractExtractor();

    @Test
    void extractsStructuredContractsFromPrdAndDesign() {
        String prd = """
                # 产品需求文档

                ## 1. 产品目标
                - 实现一个纯网页版数独

                ## 2. 目标用户与使用场景
                - 用户可以选择 6x6 或 9x9 开始游戏

                ## 3. 功能范围
                - 支持填数、标记、回退、完成后自动下一题

                ## 4. 非功能要求
                - 纯静态部署

                ## 5. 验收标准
                - 6x6/9x9 均可开始并完成

                ## 6. 不做什么
                - 不做联网与账号体系

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, mode-switch-works, puzzle-renders
                """;
        String design = """
                # 技术方案设计

                ## 1. 技术目标
                - 维持纯静态网页运行形态

                ## 2. 系统边界与模块划分
                - 页面层、状态层、规则校验层分离

                ## 3. 核心数据模型
                - 棋盘、候选数、运行模式

                ## 4. 关键流程
                - 初始化、输入、校验、完成切题

                ## 5. 接口、页面或命令设计
                - 页面必须暴露 6x6/9x9 选择控件

                ## 6. 测试与验证策略
                - 通过浏览器 testcase 验证关键流程

                ## 7. 风险与取舍
                - 优先保证规则正确，再做视觉增强

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, mode-switch-works, puzzle-renders
                """;

        ContractView contractView = extractor.extractContractView(withProductContractBlock(prd), design);
        String markdown = contractView.toMarkdown();

        assertTrue(markdown.contains("## Product Contract"));
        assertTrue(markdown.contains("Reference Only"));
        assertTrue(markdown.contains("实现一个纯网页版数独"));
        assertTrue(markdown.contains("支持填数、标记、回退、完成后自动下一题"));
        assertTrue(markdown.contains("## Design Contract"));
        assertTrue(markdown.contains("reference summary"));
        assertTrue(markdown.contains("页面必须暴露 6x6/9x9 选择控件"));
        assertTrue(markdown.contains("优先保证规则正确，再做视觉增强"));
        assertTrue(markdown.contains("## Execution Contract"));
        assertTrue(markdown.contains("Binding"));
        assertTrue(markdown.contains("entryKind: html-entry"));
        assertTrue(markdown.contains("page-opens"));
        assertEquals("html-entry", contractView.executionContract().entryKind());
        assertTrue(contractView.executionContract().entryRequired());
        assertTrue(contractView.executionContract().surfaceRequired());
    }

    @Test
    void extractsContractsFromNumberedSectionsEvenWhenHeadingsAreEnglish() {
        String prd = """
                # Product Requirements

                ## 1. Product Goals
                - Build a browser-playable Tetris experience.

                ## 2. Target Users And Scenarios
                - Players should be able to start, pause, and restart a session.

                ## 3. Scope
                - Deliver a playable game loop and visible score.

                ## 4. Non-Functional Requirements
                - Run as a static web page.

                ## 5. Acceptance Criteria
                - The page opens directly in a browser and responds to keyboard input.

                ## 6. Out Of Scope
                - No network features.

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, keyboard-input-works
                """;
        String design = """
                # Technical Design

                ## 1. Technical Goals
                - Keep the deliverable launchable as a single static web entry.

                ## 2. System Boundaries
                - Split rendering, input, and game state into separate modules.

                ## 3. Data Model
                - Track board, active piece, next piece, and score.

                ## 4. Critical Flows
                - Start, tick, move, rotate, lock, clear lines, restart.

                ## 5. Interface Or Page Design
                - Expose a visible play surface and start/pause controls.

                ## 6. Test Strategy
                - Validate the page through browser automation.

                ## 7. Risks And Tradeoffs
                - Prefer a runnable surface before animation polish.

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, play-surface-renders
                """;

        ContractView contractView = extractor.extractContractView(prd, design);

        assertEquals("html-entry", contractView.executionContract().entryKind());
        assertTrue(contractView.executionContract().entryRequired());
        assertTrue(contractView.executionContract().launchRequired());
        assertTrue(contractView.executionContract().surfaceRequired());
        assertTrue(contractView.designContract().technicalGoals().contains("Keep the deliverable launchable as a single static web entry."));
    }

    @Test
    void normalizesExecutionContractMetadataUsingContractSemantics() {
        String prd = """
                # 产品需求文档

                ## 1. 产品目标
                - 提供可直接打开运行的网页交付物

                ## 2. 目标用户与使用场景
                - 用户打开页面即可看到可运行界面

                ## 3. 功能范围
                - 提供基础交互

                ## 4. 非功能要求
                - 可直接启动

                ## 5. 验收标准
                - 页面可打开且表面渲染

                ## 6. 不做什么
                - 不做联网

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: false
                - runtime.surfaceRequired: false
                - runtime.acceptanceSignals: runtime-surface-renders
                """;

        ContractView contractView = extractor.extractContractView(prd, "");

        assertTrue(contractView.executionContract().entryRequired());
        assertEquals("html-entry", contractView.executionContract().entryKind());
        assertTrue(contractView.executionContract().launchRequired());
        assertTrue(contractView.executionContract().surfaceRequired());
        assertTrue(contractView.executionContract().acceptanceSignals().contains("page-opens"));
        assertTrue(contractView.executionContract().acceptanceSignals().contains("runtime-surface-renders"));
    }

    @Test
    void extractsSourceMetadataOnlyFromStructuredSection() {
        String analysis = """
                # 需求分析与调研

                ## 4. 初步调研与假设
                - 推断：用户可能更偏好深色像素背景
                - 建议：后续设计阶段再评估是否需要独立资源文件

                ## 6. 风险与待确认问题
                - 待确认问题：是否需要支持移动端触控操作

                ## 7. Source Metadata
                - hard.userRequirements: 纯网页版, 可直接打开运行
                - hard.upstreamFacts: (none)
                - soft.inferences: (none)
                - soft.designDecisions: (none)
                - soft.recommendations: (none)
                - open.questions: (none)
                """;

        ConstraintSourceMetadata metadata = extractor.extractConstraintSourceMetadata(analysis);

        assertTrue(metadata.softInferences().isEmpty());
        assertTrue(metadata.softRecommendations().isEmpty());
        assertTrue(metadata.openQuestions().isEmpty());
    }

    @Test
    void usesPrimaryScopeSubsectionsForRequiredCapabilitiesAndDoesNotPromoteEdgeCases() {
        String prd = """
                # 产品需求文档

                ## 1. 产品目标
                - 实现一个可玩的网页版俄罗斯方块

                ## 2. 目标用户与使用场景
                - 用户打开页面即可开始游玩

                ## 3. 功能范围

                ### 3.1 核心功能
                - 支持开始、暂停、重新开始
                - 支持方块左右移动、旋转和加速下落

                ### 3.2 可选增强
                - 显示当前得分
                - 显示下一个方块预览

                ### 3.3 异常与边界场景
                - 网络问题：网络不稳定可能导致游戏断开或延迟
                - 浏览器窗口缩放时界面需要保持可见

                ## 4. 非功能要求
                - 可直接打开运行

                ## 5. 验收标准
                - 页面可打开并能完成一轮游戏

                ## 6. 不做什么
                - 不做联网对战
                """;

        ProductContract contract = extractor.extractProductContract(prd);
        assertTrue(contract == null);
    }

    @Test
    void projectsProductContractFromPrdSectionsForCanonicalBlockGeneration() {
        String prd = """
                # 产品需求文档

                ## 1. 产品目标
                - 实现一个可玩的网页版俄罗斯方块

                ## 2. 目标用户与使用场景
                - 用户打开页面即可开始游玩

                ## 3. 功能范围

                ### 3.1 核心功能
                - 支持开始、暂停、重新开始
                - 支持方块左右移动、旋转和加速下落

                ### 3.2 可选增强
                - 显示当前得分
                - 显示下一个方块预览

                ### 3.3 异常与边界场景
                - 网络问题：网络不稳定可能导致游戏断开或延迟
                - 浏览器窗口缩放时界面需要保持可见

                ## 4. 非功能要求
                - 可直接打开运行

                ## 5. 验收标准
                - 页面可打开并能完成一轮游戏

                ## 6. 不做什么
                - 不做联网对战
                """;

        ProductContract contract = extractor.projectProductContractFromPrd(prd);

        assertTrue(contract.requiredCapabilities().contains("支持开始、暂停、重新开始"));
        assertTrue(contract.requiredCapabilities().contains("支持方块左右移动、旋转和加速下落"));
        assertTrue(contract.optionalCapabilities().contains("显示当前得分"));
        assertTrue(contract.optionalCapabilities().contains("显示下一个方块预览"));
        assertFalse(contract.requiredCapabilities().contains("网络问题：网络不稳定可能导致游戏断开或延迟"));
    }

    @Test
    void excludesPendingAndQuestionFormItemsFromProjectedProductContract() {
        String prd = """
                # 产品需求文档

                ## 1. 产品目标
                - 实现一个可玩的网页版俄罗斯方块
                - 是否需要在首版支持触屏操作？

                ## 2. 目标用户与使用场景
                - 用户打开页面即可开始游玩

                ## 3. 功能范围

                ### 3.1 核心功能
                - 支持开始、暂停、重新开始
                - 是否需要支持触屏手势控制？

                ### 3.2 可选增强
                - 显示当前得分
                - 暂停/继续功能（待确认）
                - 是否需要显示下一个方块预览？

                ## 4. 非功能要求
                - 可直接打开运行

                ## 5. 验收标准
                - 页面可打开并能完成一轮游戏
                - 是否需要支持移动端触控操作？

                ## 6. 不做什么
                - 不做联网对战
                """;

        ProductContract contract = extractor.projectProductContractFromPrd(prd);

        assertEquals(List.of("实现一个可玩的网页版俄罗斯方块"), contract.objectives());
        assertEquals(List.of("支持开始、暂停、重新开始"), contract.requiredCapabilities());
        assertEquals(List.of("显示当前得分"), contract.optionalCapabilities());
        assertEquals(List.of("页面可打开并能完成一轮游戏"), contract.acceptanceCriteria());
        assertTrue(contract.bindingRequirements().stream().noneMatch(reference -> reference.text().contains("待确认")));
        assertTrue(contract.bindingRequirements().stream().noneMatch(reference -> reference.text().contains("是否")));
    }

    @Test
    void productContractKeepsRequiredAndOptionalCapabilitiesInSeparateBuckets() {
        String prd = """
                # 产品需求文档

                ## 1. 产品目标
                - 实现一个可玩的网页应用

                ## 2. 目标用户与使用场景
                - 用户打开页面即可游玩

                ## 3. 功能范围

                ### 3.1 核心功能
                - 能力 1
                - 能力 2
                - 能力 3
                - 能力 4
                - 能力 5
                - 能力 6
                - 能力 7
                - 能力 8

                ### 3.2 可选增强
                - 能力 9
                - 能力 10

                ## 4. 非功能要求
                - 可直接打开运行

                ## 5. 验收标准
                - 页面可打开

                ## 6. 不做什么
                - 不做后端
                """;

        ProductContract contract = extractor.projectProductContractFromPrd(prd);

        assertEquals(8, contract.requiredCapabilities().size());
        assertEquals(2, contract.optionalCapabilities().size());
        assertTrue(contract.optionalCapabilities().contains("能力 9"));
        assertTrue(contract.optionalCapabilities().contains("能力 10"));
        assertTrue(contract.bindingRequirements().stream().anyMatch(reference -> "CAP-10".equals(reference.id())));
    }

    @Test
    void prdProjectionExcludesExplicitLowAuthorityLabelsWhileDesignKeepsAuthoredReferenceContent() {
        String prd = """
                # 产品需求文档

                ## 1. 产品目标
                - 实现一个可运行的网页小游戏

                ## 2. 目标用户与使用场景
                - 用户打开页面即可开始游玩

                ## 3. 功能范围
                - 支持开始、暂停、重开
                - Recommendation: 可以做成单个 HTML 文件

                ## 4. 非功能要求
                - 游戏运行流畅
                - Recommendation: 输入响应保持在 100ms 内

                ## 5. 验收标准
                - 页面可打开且基础交互可用

                ## 6. 不做什么
                - 不做联网能力

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, controls-work
                """;
        String design = """
                # 技术方案设计

                ## 1. 技术目标
                - 构建可直接打开运行的网页交付物
                - Design Choice: 所有代码封装在单个 HTML 文件中

                ## 2. 系统边界与模块划分
                - 运行时需要可见游戏表面
                - Design Choice: 页面、状态、渲染逻辑全部内联

                ## 3. 核心数据模型
                - 维护游戏状态与得分

                ## 4. 关键流程
                - 用户点击开始后进入运行态

                ## 5. 接口、页面或命令设计
                - 提供可见按钮和游戏区域

                ## 6. 测试与验证策略
                - 验证页面可打开

                ## 7. 风险与取舍
                - 优先保证可运行

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, controls-work
                """;

        ContractView contractView = extractor.extractContractView(withProductContractBlock(prd), design);
        String markdown = contractView.toMarkdown();

        assertTrue(markdown.contains("支持开始、暂停、重开"));
        assertFalse(markdown.contains("Recommendation: 可以做成单个 HTML 文件"));
        assertFalse(markdown.contains("Recommendation: 输入响应保持在 100ms 内"));
        assertTrue(markdown.contains("Design Choice: 所有代码封装在单个 HTML 文件中"));
        assertTrue(markdown.contains("Design Choice: 页面、状态、渲染逻辑全部内联"));
    }

    @Test
    void keepsReferenceStatementsInsteadOfRewritingBodySemantics() {
        String prd = """
                # 产品需求文档

                ## 1. 产品目标
                - 实现一个可玩的网页版俄罗斯方块

                ## 2. 目标用户与使用场景
                - 用户打开页面后即可开始游戏

                ## 3. 功能范围
                - 支持开始、暂停、重开

                ## 4. 非功能要求
                - 页面应保持流畅
                - 控制响应时间小于 100ms

                ## 5. 验收标准
                - 页面可直接打开运行

                ## 6. 不做什么
                - 不做联网能力

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, controls-work
                """;
        String design = """
                # 技术方案设计

                ## 1. 技术目标
                - 构建可直接打开运行的网页交付物

                ## 2. 系统边界与模块划分
                - 系统边界为单个 HTML 文件

                ## 3. 核心数据模型
                - 维护棋盘、方块和得分

                ## 4. 关键流程
                - 用户点击开始后进入运行态

                ## 5. 接口、页面或命令设计
                - 页面必须暴露开始、暂停、重开按钮

                ## 6. 测试与验证策略
                - 使用浏览器自动化验证页面可运行

                ## 7. 风险与取舍
                - 优先保证可运行

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, controls-work
                """;

        ContractView contractView = extractor.extractContractView(
                "实现一个可玩的网页版俄罗斯方块",
                "需要纯网页版、可直接打开运行、像素风、支持开始/暂停/重开、方向键控制、显示得分和下一个方块预览",
                "",
                withProductContractBlock(prd),
                design
        );
        String markdown = contractView.toMarkdown();

        assertTrue(markdown.contains("- 控制响应时间小于 100ms"), markdown);
        assertTrue(markdown.contains("- 系统边界为单个 HTML 文件"), markdown);
        assertTrue(markdown.contains("- 页面必须暴露开始、暂停、重开按钮"), markdown);
    }

    @Test
    void ignoresNoneMarkersInSourceMetadataAndLooseLists() {
        String analysis = """
                # 需求分析与调研

                ## 1. 背景与问题定义
                内容

                ## 2. 目标与成功标准
                内容

                ## 3. 关键约束
                内容

                ## 4. 初步调研与假设
                内容

                ## 5. 边界与非目标
                内容

                ## 6. 风险与待确认问题
                内容

                ## 7. Source Metadata
                - hard.userRequirements: (none)
                - hard.upstreamFacts: (none)
                - soft.inferences: (none)
                - soft.designDecisions: (none)
                - soft.recommendations: (none)
                - open.questions: (none)
                """;

        ConstraintSourceMetadata metadata = extractor.extractConstraintSourceMetadata(analysis);
        assertTrue(metadata.hardUserRequirements().isEmpty());
        assertTrue(metadata.hardUpstreamFacts().isEmpty());
        assertTrue(metadata.softInferences().isEmpty());

        ConstraintSourceMetadata authoritative = extractor.buildAuthoritativeSourceMetadata(
                "build a browser game",
                "(none)",
                analysis
        );
        assertEquals(1, authoritative.hardUserRequirements().size());
        assertEquals("build a browser game", authoritative.hardUserRequirements().getFirst());
        assertTrue(authoritative.hardUpstreamFacts().isEmpty());
    }

    private String withProductContractBlock(String prd) {
        return prd + "\n\n" + StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.PRODUCT_CONTRACT,
                extractor.projectProductContractFromPrd(prd)
        );
    }
}
