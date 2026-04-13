package devflow.agent.artifact;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.prompt.PromptTemplateCatalog;

/**
 * 需求分析阶段 prompt builder。
 *
 * <p>负责 `ANALYSIS` 阶段三种草稿模式的 prompt 模板，
 * 不再让总装配器同时维护三份长 prompt。
 */
final class AnalysisDocumentPromptBuilder {

    private final PromptTemplateCatalog promptTemplateCatalog;
    private final DocumentDraftAssembler draftAssembler;

    AnalysisDocumentPromptBuilder(PromptTemplateCatalog promptTemplateCatalog, DocumentDraftAssembler draftAssembler) {
        this.promptTemplateCatalog = promptTemplateCatalog;
        this.draftAssembler = draftAssembler;
    }

    DocumentGenerationPrompt build(RunRecord runRecord, String note, DocumentDraftContext context) {
        DocumentLanguage language = context.language();
        String system = promptTemplateCatalog.documentGenerationSystemPrompt(StageType.ANALYSIS, language);
        String user = switch (context.mode()) {
            case FULL_DRAFT -> """
                    请根据以下信息输出《需求分析与调研》文档。

                    目标：
                    %s

                    约束：
                    %s

                    当前备注：
                    %s

                    Source Metadata（系统提供的权威来源，hard.* 必须原样保留，不得新增、删除或改写）：
                    %s

                    上一轮草稿（如果为空表示首次生成）：
                    %s

                    输出要求：
                    1. 使用 Markdown
                    2. 标题为“# 需求分析与调研”
                    3. 必须严格按照下面模板输出，保留章节编号和标题
                    4. 每个二级章节都要有实质内容，不能留空、不能写 [TODO]
                    5. 内容要具体，避免空泛表述；只有在用户要求或上游事实已明确给出时才量化，否则保持定性描述
                    6. “明确不做”和“待确认问题”必须写出来
                    7. 如果当前备注里包含修订意见，优先保留上一轮已经合格的章节内容，只补齐缺失或不合格章节，再输出完整文档
                    8. 不要因为修订某几个章节而删掉前面已经写好的章节
                    9. 优先保证所有主章节都完整出现，再补充细节，不要在前几个章节写得过长导致后面章节缺失
                    10. %s
                    11. %s
                    12. %s
                    13. %s
                    14. %s

                    输出模板：
                    %s
                    """.formatted(
                    runRecord.goal(),
                    DocumentPromptValueSupport.blankIfNull(runRecord.constraints()),
                    note,
                    context.authoritativeSourceMetadataBlock(),
                    DocumentPromptValueSupport.blankIfNull(context.previousDraft()),
                    language.proseInstruction(),
                    promptTemplateCatalog.directLaunchClarification(language),
                    promptTemplateCatalog.sourceAndConstraintGuidance(language, StageType.ANALYSIS),
                    promptTemplateCatalog.sourceMetadataRequirement(language, 7),
                    promptTemplateCatalog.sourceMetadataSemantics(language),
                    context.template()
            );
            case FILL_MISSING_SECTIONS -> """
                    请基于上一轮《需求分析与调研》草稿，只补齐下列缺失章节，然后输出这些章节的完整 Markdown 内容。

                    目标：
                    %s

                    约束：
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
                    1. 只输出上述缺失章节，不要重写整篇文档
                    2. 每个章节都必须保留原编号和标题
                    3. 每个章节都要包含足够内容，不能写 [TODO]
                    4. 输出顺序必须与文档顺序一致
                    5. 每个缺失章节优先用紧凑的要点列表，不要写成长篇散文
                    6. %s
                    7. %s
                    8. %s
                    9. %s
                    10. %s
                    """.formatted(
                    runRecord.goal(),
                    DocumentPromptValueSupport.blankIfNull(runRecord.constraints()),
                    note,
                    context.authoritativeSourceMetadataBlock(),
                    DocumentPromptValueSupport.blankIfNull(context.previousOutline()),
                    draftAssembler.renderRequestedSections(context.targetSections()),
                    language.proseInstruction(),
                    promptTemplateCatalog.directLaunchClarification(language),
                    promptTemplateCatalog.sourceAndConstraintGuidance(language, StageType.ANALYSIS),
                    promptTemplateCatalog.sourceMetadataRequirement(language, 7),
                    promptTemplateCatalog.sourceMetadataSemantics(language)
            );
            case REVISE_WITH_EXISTING_DRAFT -> """
                    请基于上一轮《需求分析与调研》草稿进行修订，并输出需要替换的顶层章节完整 Markdown 内容。

                    目标：
                    %s

                    约束：
                    %s

                    当前备注：
                    %s

                    Source Metadata（系统提供的权威来源，hard.* 必须原样保留，不得新增、删除或改写）：
                    %s

                    上一轮草稿章节提纲：
                    %s

                    输出要求：
                    1. 只输出需要修订的顶层章节，不要重写整篇文档
                    2. 每个章节都必须保留原编号和标题
                    3. 修订重点是解决当前备注指出的逻辑矛盾、信息缺失或表达问题
                    4. 不要删除未被修订的章节，程序会把你输出的章节合并回旧稿
                    5. 输出顺序必须与文档顺序一致
                    6. %s
                    7. %s
                    8. %s
                    9. %s
                    10. %s
                    """.formatted(
                    runRecord.goal(),
                    DocumentPromptValueSupport.blankIfNull(runRecord.constraints()),
                    note,
                    context.authoritativeSourceMetadataBlock(),
                    DocumentPromptValueSupport.blankIfNull(context.previousOutline()),
                    language.proseInstruction(),
                    promptTemplateCatalog.directLaunchClarification(language),
                    promptTemplateCatalog.sourceAndConstraintGuidance(language, StageType.ANALYSIS),
                    promptTemplateCatalog.sourceMetadataRequirement(language, 7),
                    promptTemplateCatalog.sourceMetadataSemantics(language)
            );
        };
        return new DocumentGenerationPrompt(system, user);
    }
}
