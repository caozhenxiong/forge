package devflow.agent.executor.testing;

import devflow.agent.executor.gate.TestEvidenceGate;

record TestEvidenceComponents(
        TestEvidenceCollector testEvidenceCollector,
        TestEvidenceGate testEvidenceGate,
        CoverageLedgerBuilder coverageLedgerBuilder,
        ExperienceFailureDispositionResolver experienceFailureDispositionResolver,
        TestArtifactRenderer testArtifactRenderer
) {
}
