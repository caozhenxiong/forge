package devflow.agent.artifact;

import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ProductContract;
import devflow.agent.context.ValidationMetadata;

record DocumentCompositionContracts(
        ProductContract productContract,
        ExecutionContract executionContract,
        ValidationMetadata validationMetadata
) {
    static DocumentCompositionContracts empty() {
        return new DocumentCompositionContracts(null, null, null);
    }
}
