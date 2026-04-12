package devflow.agent.context;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;

public record ContractView(
        ProductContract productContract,
        DesignContract designContract,
        ExecutionContract executionContract,
        ConstraintSourceMetadata constraintSourceMetadata
) {

    public String toMarkdown() {
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(DocumentLanguage language) {
        StringBuilder builder = new StringBuilder(language.choose("# 结构化契约\n\n", "# Structured Contracts\n\n"));
        builder.append(language.choose(
                "> 执行硬约束只来自 Execution Contract 与 Source Metadata 中的 hard.*；Product Contract 中的 requirement refs 可作为 planning/test coverage 锚点，但不能越权改写执行契约。\n\n",
                "> Execution hard constraints come only from the Execution Contract and the hard.* entries in Source Metadata. Requirement refs in the Product Contract may anchor planning/test coverage, but must not override the execution contract.\n\n"
        ));
        if (productContract != null) {
            builder.append(productContract.toMarkdown(language)).append("\n\n");
            builder.append("## ")
                    .append(language.choose("产品需求覆盖引用", "Product Requirement Coverage Refs"))
                    .append("\n\n")
                    .append(productRequirementCatalogMarkdown(language))
                    .append("\n\n");
        }
        if (designContract != null) {
            builder.append(designContract.toMarkdown(language)).append("\n\n");
        }
        if (executionContract != null) {
            builder.append(executionContract.toMarkdown(language)).append("\n\n");
        }
        if (constraintSourceMetadata != null && !constraintSourceMetadata.isEmpty()) {
            builder.append(constraintSourceMetadata.toMarkdown(language)).append("\n");
        }
        return builder.toString().trim();
    }

    public String productRequirementCatalogMarkdown(DocumentLanguage language) {
        if (productContract == null) {
            return PlaceholderValues.none(language);
        }
        return productContract.requirementCatalogMarkdown(language);
    }
}
