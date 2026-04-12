package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.context.ProductContract;
import devflow.agent.context.RequirementReference;
import devflow.agent.context.SharedContextBundle;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.quality.CapabilityIds;
import devflow.agent.quality.CapabilityMatrixEntry;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.quality.QualityPlan;
import java.util.LinkedHashSet;
import java.util.Set;

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
        String qualityCatalog = renderQualityRequirementCatalog(productContract, qualityPlan, language);
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

    private String renderQualityRequirementCatalog(
            ProductContract productContract,
            QualityPlan qualityPlan,
            DocumentLanguage language
    ) {
        if (qualityPlan == null || qualityPlan.qualityIntent().requiredCapabilityIds().isEmpty()) {
            return PlaceholderValues.none(language);
        }
        Set<String> normalizedProductRequirementIds = normalizedProductRequirementIds(productContract);
        StringBuilder builder = new StringBuilder();
        for (CapabilityMatrixEntry entry : qualityPlan.capabilityMatrix().entries()) {
            if (entry == null || entry.capabilityId().isBlank() || !entry.required()) {
                continue;
            }
            // CAP-* 已经由产品 requirement catalog 承载；这里保留纯质量能力，
            // 避免 planner 同时看到 CAP-1 和 QCAP-CAP_1 两套重复锚点。
            if (normalizedProductRequirementIds.contains(CapabilityIds.normalize(entry.capabilityId()))) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ")
                    .append(devflow.agent.quality.QualityCoverageRefCatalog.referenceId(entry.capabilityId()))
                    .append(": ")
                    .append(entry.capabilityId())
                    .append(" [")
                    .append(entry.expectation().name())
                    .append("]");
            if (entry.rationale() != null && !entry.rationale().isBlank()) {
                builder.append(" - ").append(entry.rationale().trim());
            }
        }
        return builder.isEmpty() ? PlaceholderValues.none(language) : builder.toString();
    }

    private Set<String> normalizedProductRequirementIds(ProductContract productContract) {
        if (productContract == null) {
            return Set.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (RequirementReference reference : productContract.bindingRequirements()) {
            if (reference == null || reference.id() == null || reference.id().isBlank()) {
                continue;
            }
            ids.add(CapabilityIds.normalize(reference.id()));
        }
        return Set.copyOf(ids);
    }
}
