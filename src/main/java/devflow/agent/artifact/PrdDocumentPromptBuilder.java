package devflow.agent.artifact;

import devflow.agent.context.ContractMetadataKeys;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import devflow.agent.prompt.PromptTemplateCatalog;

/**
 * PRD 阶段 prompt builder。
 *
 * <p>负责 `PRD` 阶段的 prompt 模板，避免总装配器同时维护分析、PRD、
 * 技术设计三套长文本模板。
 */
final class PrdDocumentPromptBuilder {

    private final PromptTemplateCatalog promptTemplateCatalog;
    private final DocumentDraftAssembler draftAssembler;

    PrdDocumentPromptBuilder(PromptTemplateCatalog promptTemplateCatalog, DocumentDraftAssembler draftAssembler) {
        this.promptTemplateCatalog = promptTemplateCatalog;
        this.draftAssembler = draftAssembler;
    }

    DocumentGenerationPrompt build(
            RunRecord runRecord,
            String note,
            DocumentDraftContext context,
            String analysisForPrompt
    ) {
        DocumentLanguage language = context.language();
        String system = promptTemplateCatalog.documentGenerationSystemPrompt(StageType.PRD, language);
        String performanceGuidance = prdPerformanceGuidance();
        String user = switch (context.mode()) {
            case FULL_DRAFT -> """
                    基于下面的需求分析内容，输出《产品需求文档》。

                    输入材料：
                    %s

                    当前备注：
                    %s

                    Source Metadata（系统提供的权威来源，hard.* 必须原样保留，不得新增、删除或改写）：
                    %s

                    上一轮草稿（如果为空表示首次生成）：
                    %s

                    输出要求：
                    1. 使用 Markdown
                    2. 标题为“# 产品需求文档”
                    3. 必须严格按照下面模板输出，保留章节编号和标题
                    4. 功能范围要分“核心功能 / 辅助功能 / 异常与边界场景”
                    5. 验收标准必须可测、可执行，不能写成口号
                    6. “不做什么”必须明确列出
                    7. 不要留 [TODO]
                    8. 如果当前备注里包含修订意见，优先保留上一轮已经合格的章节内容，只补齐缺失或不合格章节，再输出完整 PRD
                    9. 不要因为补某个章节而删掉已经存在的章节
                    10. 先确保 1-6 所有主章节都完整出现，再扩展细节；不要在前面章节写得过长导致后续章节缺失
                    11. 每个三级小节优先使用 2-4 条高信息密度条目，不要写成长篇散文
                    12. %s
                    13. Contract Metadata 必须与 PRD 内容一致，不要发明与正文冲突的运行方式
                    14. %s
                    15. %s
                    16. %s
                    17. %s
                    18. %s
                    19. %s

                    输出模板：
                    %s
                    """.formatted(
                    analysisForPrompt,
                    note,
                    context.authoritativeSourceMetadataBlock(),
                    DocumentPromptValueSupport.blankIfNull(context.previousDraft()),
                    promptTemplateCatalog.contractMetadataRequirement(language, 7, "page-opens, input-works"),
                    language.proseInstruction(),
                    performanceGuidance,
                    promptTemplateCatalog.directLaunchClarification(language),
                    promptTemplateCatalog.sourceAndConstraintGuidance(language, StageType.PRD),
                    promptTemplateCatalog.sourceMetadataRequirement(language, 8),
                    promptTemplateCatalog.sourceMetadataSemantics(language),
                    context.template()
            );
            case FILL_MISSING_SECTIONS -> """
                    基于下面的需求分析和上一轮 PRD 草稿，只补齐缺失章节，不要重写整篇文档。

                    输入材料：
                    %s

                    当前备注：
                    %s

                    Source Metadata（系统提供的权威来源，hard.* 必须原样保留，不得新增、删除或改写）：
                    %s

                    上一轮草稿章节提纲：
                    %s

                    需要补齐的章节：
                    %s

                    输出要求：
                    1. 只输出上述缺失章节，不要重复输出已合格章节
                    2. 每个章节保留原编号和标题
                    3. 每个章节内容要具体、可执行、可验收
                    4. 输出顺序必须与文档顺序一致
                    5. 每个缺失章节优先用高信息密度条目，不要扩写整篇背景
                    6. %s
                    7. %s
                    8. %s
                    9. %s
                    10. %s
                    11. %s
                    12. %s
                    """.formatted(
                    analysisForPrompt,
                    note,
                    context.authoritativeSourceMetadataBlock(),
                    DocumentPromptValueSupport.blankIfNull(context.previousOutline()),
                    draftAssembler.renderRequestedSections(context.targetSections()),
                    promptTemplateCatalog.contractMetadataRuntimeKeyReminder(language),
                    language.proseInstruction(),
                    performanceGuidance,
                    promptTemplateCatalog.directLaunchClarification(language),
                    promptTemplateCatalog.sourceAndConstraintGuidance(language, StageType.PRD),
                    promptTemplateCatalog.sourceMetadataRequirement(language, 8),
                    promptTemplateCatalog.sourceMetadataSemantics(language)
            );
            case REVISE_WITH_EXISTING_DRAFT -> """
                    基于下面的需求分析和上一轮 PRD 草稿，输出需要替换的顶层章节完整 Markdown 内容。

                    输入材料：
                    %s

                    当前备注：
                    %s

                    Source Metadata（系统提供的权威来源，hard.* 必须原样保留，不得新增、删除或改写）：
                    %s

                    上一轮草稿章节提纲：
                    %s

                    输出要求：
                    1. 只输出需要修订的顶层章节，不要重写整篇 PRD
                    2. 每个章节保留原编号和标题
                    3. 修订重点是解决当前备注指出的逻辑矛盾、信息缺失或表达不清问题
                    4. 不要删除未被修订的章节，程序会把你输出的章节合并回旧稿
                    5. 输出顺序必须与文档顺序一致
                    6. 如果修订“Contract Metadata”，必须保留英文键名不变
                    7. %s
                    8. %s
                    9. %s
                    10. %s
                    11. %s
                    12. %s
                    """.formatted(
                    analysisForPrompt,
                    note,
                    context.authoritativeSourceMetadataBlock(),
                    DocumentPromptValueSupport.blankIfNull(context.previousOutline()),
                    language.proseInstruction(),
                    performanceGuidance,
                    promptTemplateCatalog.directLaunchClarification(language),
                    promptTemplateCatalog.sourceAndConstraintGuidance(language, StageType.PRD),
                    promptTemplateCatalog.sourceMetadataRequirement(language, 8),
                    promptTemplateCatalog.sourceMetadataSemantics(language)
            );
        };
        return new DocumentGenerationPrompt(system, user);
    }

    String prdPerformanceGuidance() {
        return "只有在上游已经通过 Contract Metadata.validation.* 或明确的 hard.* 约束声明了页面加载/交互响应等量化门槛时，才允许把它们写进 ## 4.1 性能 或 ## 5.2 质量验收；否则必须保持定性表达，不要自行发明 %s、%s、FPS 等数值阈值。".formatted(
                ContractMetadataKeys.VALIDATION_PAGE_LOAD_MAX_MS,
                ContractMetadataKeys.VALIDATION_INTERACTION_MAX_MS
        );
    }
}
