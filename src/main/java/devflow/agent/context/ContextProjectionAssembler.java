package devflow.agent.context;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;

public final class ContextProjectionAssembler {

    private final ContextLayerAssembler contextLayerAssembler;

    public ContextProjectionAssembler(ContextLayerAssembler contextLayerAssembler) {
        this.contextLayerAssembler = contextLayerAssembler;
    }

    ProjectedContext assemble(
            RunRecord runRecord,
            StageType currentStage,
            ContextProjectionContractBundle contracts,
            ContextProjectionSummaries summaries,
            ContextProjectionArtifacts artifacts
    ) {
        TaskMemory taskMemory = new TaskMemory(
                runRecord.goal(),
                runRecord.constraints(),
                summaries.currentStageSummary(),
                contracts.upstreamContract(),
                contracts.authoritativeRequirementCatalog(),
                summaries.recentHistorySummary(),
                summaries.failureSummary(),
                summaries.repairSummary(),
                summaries.workingSetSummary(),
                artifacts.failures()
        );
        ContextViews contextViews = contextLayerAssembler.assemble(
                runRecord,
                currentStage,
                contracts.contractView(),
                summaries.currentStageSummary(),
                contracts.upstreamContract(),
                contracts.authoritativeRequirementCatalog(),
                summaries.recentHistorySummary(),
                summaries.failureSummary(),
                summaries.repairSummary(),
                summaries.workingSetSummary(),
                artifacts.failures()
        );
        return new ProjectedContext(
                summaries.currentStageSummary(),
                contracts.upstreamContract(),
                contracts.authoritativeRequirementCatalog(),
                summaries.recentHistorySummary(),
                summaries.failureSummary(),
                summaries.repairSummary(),
                summaries.workingSetSummary(),
                taskMemory,
                contextViews
        );
    }
}
