package devflow.agent.context;

import java.util.List;

record ContextProjectionArtifacts(
        String analysis,
        String prd,
        String design,
        String currentArtifact,
        String recentHistory,
        String repairBrief,
        String workingSet,
        List<FailureDigest> failures
) {
}
