package devflow.agent.context;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class ContextProjector {

    private final ContextProjectionArtifactReader artifactReader;
    private final ContextProjectionContractResolver contractResolver;
    private final ContextProjectionSummaryAssembler summaryAssembler;
    private final ContextProjectionAssembler assembler;

    public ContextProjector(
            ContextProjectionArtifactReader artifactReader,
            ContextProjectionContractResolver contractResolver,
            ContextProjectionSummaryAssembler summaryAssembler,
            ContextProjectionAssembler assembler
    ) {
        this.artifactReader = artifactReader;
        this.contractResolver = contractResolver;
        this.summaryAssembler = summaryAssembler;
        this.assembler = assembler;
    }

    public ProjectedContext project(Path projectPath, RunRecord runRecord, StageType currentStage) {
        ContextProjectionArtifacts artifacts = artifactReader.readArtifacts(projectPath, runRecord, currentStage);
        ContextProjectionContractBundle contracts = contractResolver.resolve(runRecord, currentStage, artifacts);
        ContextProjectionSummaries summaries = summaryAssembler.summarize(currentStage, artifacts, contracts);
        return assembler.assemble(runRecord, currentStage, contracts, summaries, artifacts);
    }
}
