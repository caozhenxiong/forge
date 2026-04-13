package devflow.agent.artifact;

import devflow.agent.context.ContractMetadataKeys;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.prompt.PromptTemplateCatalog;

/**
 * 技术方案阶段 prompt builder。
 *
 * <p>负责 `DESIGN` 阶段的 prompt 模板和性能指导语，不再让总装配器
 * 和总 prompt builder 同时维护一套长文本。
 */
final class DesignDocumentPromptBuilder {

    private final PromptTemplateCatalog promptTemplateCatalog;
    private final DocumentDraftAssembler draftAssembler;

    DesignDocumentPromptBuilder(PromptTemplateCatalog promptTemplateCatalog, DocumentDraftAssembler draftAssembler) {
        this.promptTemplateCatalog = promptTemplateCatalog;
        this.draftAssembler = draftAssembler;
    }

    DocumentGenerationPrompt build(
            RunRecord runRecord,
            String note,
            DocumentDraftContext context,
            String prdForPrompt
    ) {
        DocumentLanguage language = context.language();
        String system = promptTemplateCatalog.documentGenerationSystemPrompt(StageType.DESIGN, language);
        String performanceGuidance = designPerformanceGuidance();
        String user = switch (context.mode()) {
            case FULL_DRAFT -> """
                    基于下面的 PRD，输出《技术方案设计》。

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
                    2. 标题为“# 技术方案设计”
                    3. 必须严格按照下面模板输出，保留章节编号和标题
                    4. 模块划分、数据模型、关键流程必须尽量落到当前项目上下文
                    5. 测试与验证策略必须写出具体方法，不要只写“补充测试”
                    6. 风险与取舍必须明确说明为什么这样选
                    7. 不要留 [TODO]
                    8. 如果当前备注里包含修订意见，优先保留上一轮已经合格的章节内容，只补齐缺失或不合格章节，再输出完整技术方案
                    9. 不要因为修订某一节而删掉其他已存在章节
                    10. 先确保所有主章节都完整出现，再扩展细节；不要在前几个章节写得过长导致后面章节缺失
                    11. %s
                    12. %s
                    13. Contract Metadata 必须是对正文交付方式的结构化归纳，不要编造与正文不一致的值
                    14. %s
                    15. %s
                    16. %s
                    17. %s
                    18. %s

                    输出模板：
                    %s
                    """.formatted(
                    prdForPrompt,
                    note,
                    context.authoritativeSourceMetadataBlock(),
                    DocumentPromptValueSupport.blankIfNull(context.previousDraft()),
                    performanceGuidance,
                    promptTemplateCatalog.contractMetadataRequirement(language, 8, "page-opens, runtime-surface-renders"),
                    language.proseInstruction(),
                    promptTemplateCatalog.directLaunchClarification(language),
                    promptTemplateCatalog.sourceAndConstraintGuidance(language, StageType.DESIGN),
                    promptTemplateCatalog.sourceMetadataRequirement(language, 9),
                    promptTemplateCatalog.sourceMetadataSemantics(language),
                    context.template()
            );
            case FILL_MISSING_SECTIONS -> """
                    基于下面的 PRD 和上一轮《技术方案设计》草稿，只补齐缺失章节，不要重写整篇文档。

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
                    1. 只输出上述缺失章节，不要重复输出其他章节
                    2. 每个章节保留原编号和标题
                    3. 每个章节都要落到工程实现层面
                    4. 输出顺序必须与文档顺序一致
                    5. 缺失章节优先直接给出方案要点，不要重复扩写已有章节
                    6. %s
                    7. 如果需要补“Contract Metadata”章节，必须保留英文键名和布尔值格式
                    8. %s
                    9. %s
                    10. %s
                    11. %s
                    12. %s
                    """.formatted(
                    prdForPrompt,
                    note,
                    context.authoritativeSourceMetadataBlock(),
                    DocumentPromptValueSupport.blankIfNull(context.previousOutline()),
                    draftAssembler.renderRequestedSections(context.targetSections()),
                    performanceGuidance,
                    language.proseInstruction(),
                    promptTemplateCatalog.directLaunchClarification(language),
                    promptTemplateCatalog.sourceAndConstraintGuidance(language, StageType.DESIGN),
                    promptTemplateCatalog.sourceMetadataRequirement(language, 9),
                    promptTemplateCatalog.sourceMetadataSemantics(language)
            );
            case REVISE_WITH_EXISTING_DRAFT -> """
                    基于下面的 PRD 和上一轮《技术方案设计》草稿，输出需要替换的顶层章节完整 Markdown 内容。

                    输入材料：
                    %s

                    当前备注：
                    %s

                    Source Metadata（系统提供的权威来源，hard.* 必须原样保留，不得新增、删除或改写）：
                    %s

                    上一轮草稿章节提纲：
                    %s

                    输出要求：
                    1. 只输出需要修订的顶层章节，不要重写整篇技术方案
                    2. 每个章节保留原编号和标题
                    3. 修订重点是解决当前备注指出的方案逻辑、接口定义或验证策略问题
                    4. 不要删除未被修订的章节，程序会把你输出的章节合并回旧稿
                    5. 输出顺序必须与文档顺序一致
                    6. %s
                    7. 如果修订“Contract Metadata”，必须保留英文键名不变
                    8. %s
                    9. %s
                    10. %s
                    11. %s
                    12. %s
                    """.formatted(
                    prdForPrompt,
                    note,
                    context.authoritativeSourceMetadataBlock(),
                    DocumentPromptValueSupport.blankIfNull(context.previousOutline()),
                    performanceGuidance,
                    language.proseInstruction(),
                    promptTemplateCatalog.directLaunchClarification(language),
                    promptTemplateCatalog.sourceAndConstraintGuidance(language, StageType.DESIGN),
                    promptTemplateCatalog.sourceMetadataRequirement(language, 9),
                    promptTemplateCatalog.sourceMetadataSemantics(language)
            );
        };
        return new DocumentGenerationPrompt(system, user);
    }

    String designPerformanceGuidance() {
        return "只有在上游已经通过 Contract Metadata.validation.* 或明确的 hard.* 约束声明了性能/测量要求时，才填写 %s、%s、%s；否则保持为空，不要从正文自由推断性能阈值。".formatted(
                ContractMetadataKeys.VALIDATION_PERFORMANCE_MEASUREMENT_REQUIRED,
                ContractMetadataKeys.VALIDATION_PAGE_LOAD_MAX_MS,
                ContractMetadataKeys.VALIDATION_INTERACTION_MAX_MS
        );
    }
}
