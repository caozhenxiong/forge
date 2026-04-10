package devflow.agent.context;

import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 负责把当前已有的上下文摘要装配成四层视图。
 *
 * <p>这一层先只做“分层与边界落盘”，不负责：
 * 1. 高级压缩；
 * 2. 长期记忆；
 * 3. 智能裁剪。
 */
@Component
public class ContextLayerAssembler {

    public ContextViews assemble(
            RunRecord runRecord,
            StageType currentStage,
            ContractView contractView,
            String currentStageSummary,
            String upstreamContractSummary,
            String structuredContractSummary,
            String recentHistorySummary,
            String failureSummary,
            String repairSummary,
            String workingSetSummary,
            List<FailureDigest> recentFailures
    ) {
        return new ContextViews(
                new DurableContextView(
                        runRecord.goal(),
                        runRecord.constraints(),
                        upstreamContractSummary,
                        structuredContractSummary,
                        contractView,
                        repairSummary
                ),
                new WorkingContextView(
                        currentStage,
                        currentStageSummary,
                        workingSetSummary
                ),
                new EvidenceContextView(
                        failureSummary,
                        recentFailures
                ),
                new TraceContextView(
                        currentStage,
                        recentHistorySummary
                )
        );
    }
}
