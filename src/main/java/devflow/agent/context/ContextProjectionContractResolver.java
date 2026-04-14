package devflow.agent.context;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.LanguagePolicy;

public final class ContextProjectionContractResolver {

    private final ContractExtractor contractExtractor;
    private final LanguagePolicy languagePolicy;

    public ContextProjectionContractResolver(ContractExtractor contractExtractor, LanguagePolicy languagePolicy) {
        this.contractExtractor = contractExtractor;
        this.languagePolicy = languagePolicy;
    }

    ContextProjectionContractBundle resolve(
            RunRecord runRecord,
            StageType currentStage,
            ContextProjectionArtifacts artifacts
    ) {
        DocumentLanguage language = languagePolicy.resolve(runRecord.goal(), runRecord.constraints());
        ContractView contractView = contractExtractor.extractContractView(
                runRecord.goal(),
                runRecord.constraints(),
                artifacts.analysis(),
                artifacts.prd(),
                artifacts.design()
        );
        String authorityCorpus = ConstraintAuthoritySupport.buildAuthorityCorpus(
                runRecord.goal(),
                runRecord.constraints(),
                contractView.constraintSourceMetadata(),
                contractView.executionContract()
        );
        String upstreamContract = readUpstreamContract(currentStage, artifacts, authorityCorpus);
        return new ContextProjectionContractBundle(
                language,
                contractView,
                authorityCorpus,
                upstreamContract,
                contractView.productRequirementCatalogMarkdown(language)
        );
    }

    private String readUpstreamContract(StageType currentStage, ContextProjectionArtifacts artifacts, String authorityCorpus) {
        StringBuilder builder = new StringBuilder();
        appendStage(builder, currentStage, StageType.ANALYSIS, artifacts.analysis(), authorityCorpus);
        appendStage(builder, currentStage, StageType.PRD, artifacts.prd(), authorityCorpus);
        appendStage(builder, currentStage, StageType.DESIGN, artifacts.design(), authorityCorpus);
        return builder.toString();
    }

    private void appendStage(
            StringBuilder builder,
            StageType currentStage,
            StageType sourceStage,
            String artifact,
            String authorityCorpus
    ) {
        if (sourceStage.ordinal() > currentStage.ordinal() || artifact == null || artifact.isBlank()) {
            return;
        }
        String filtered = ArtifactContextSanitizer.sanitizeForProjection(artifact, sourceStage, authorityCorpus);
        if (filtered.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append("## ").append(sourceStage).append("\n").append(filtered);
    }
}
