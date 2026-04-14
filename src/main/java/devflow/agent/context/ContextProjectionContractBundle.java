package devflow.agent.context;

import devflow.agent.i18n.DocumentLanguage;

record ContextProjectionContractBundle(
        DocumentLanguage language,
        ContractView contractView,
        String authorityCorpus,
        String upstreamContract,
        String authoritativeRequirementCatalog
) {
}
