package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.context.ProductContract;
import devflow.agent.context.SharedContextBundle;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.quality.QualityCoverageRefCatalog;
import devflow.agent.quality.QualityPlan;

/**
 * 统一构造 implementation 阶段共享上下文。
 *
 * <p>这层只负责 durable context 的稳定裁剪与产物目录渲染，
 * 避免上下文门面继续关心摘要长度、默认占位值和产品需求目录格式。
 */
final class ImplementationSharedContextFactory {

    private static final int REPAIR_SUMMARY_MAX_CHARS = 2400;
    private static final int WORKSPACE_SUMMARY_MAX_CHARS = 2800;

    SharedContextBundle build(
            RunRecord runRecord,
            String note,
            String workspaceContext,
            ContractView contractView,
            ExecutionDirectivePayload directives
    ) {
        return new SharedContextBundle(
                runRecord.goal(),
                runRecord.constraints(),
                contractView,
                directives.requiredEvidence(),
                directives.mustFixFirst(),
                directives.forbiddenDirections(),
                summarize(note, REPAIR_SUMMARY_MAX_CHARS),
                summarize(workspaceContext, WORKSPACE_SUMMARY_MAX_CHARS)
        );
    }

    String renderProductRequirementCatalog(ProductContract productContract, QualityPlan qualityPlan, DocumentLanguage language) {
        String productCatalog = productContract == null
                ? PlaceholderValues.none(language)
                : productContract.requirementCatalogMarkdown(language);
        String qualityCatalog = QualityCoverageRefCatalog.renderCatalog(qualityPlan, language);
        if (qualityCatalog.equals(PlaceholderValues.none(language))) {
            return productCatalog;
        }
        return """
                %s

                ## %s
                %s
                """.formatted(
                productCatalog,
                language.choose("质量能力覆盖引用", "Quality Capability Coverage Refs"),
                qualityCatalog
        ).trim();
    }

    private String summarize(String content, int maxChars) {
        return PlaceholderValues.truncateMiddle(content, maxChars);
    }
}
