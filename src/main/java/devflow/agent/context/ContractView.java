package devflow.agent.context;

import devflow.agent.i18n.DocumentLanguage;

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
                "> 绑定硬约束只来自 Execution Contract 与 Source Metadata 中的 hard.*；产品/设计契约只作为参考摘要，不能单独升级成硬约束。\n\n",
                "> Binding hard constraints come only from the Execution Contract and the hard.* entries in Source Metadata. Product and Design contracts are reference summaries and must not be promoted into hard constraints on their own.\n\n"
        ));
        if (productContract != null) {
            builder.append(productContract.toMarkdown(language)).append("\n\n");
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
}
