package devflow.agent.context;

import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ContextProjector {

    private final FileArtifactStore artifactStore;
    private final FileProjectWorkspace workspace;
    private final ArtifactSummaryBuilder summaryBuilder;
    private final ContractExtractor contractExtractor;
    private final ContextLayerAssembler contextLayerAssembler;
    private final ContextProjectionArtifactReader artifactReader;

    @Autowired
    public ContextProjector(
            FileArtifactStore artifactStore,
            FileProjectWorkspace workspace,
            ArtifactSummaryBuilder summaryBuilder,
            ContractExtractor contractExtractor,
            ContextLayerAssembler contextLayerAssembler
    ) {
        this.artifactStore = artifactStore;
        this.workspace = workspace;
        this.summaryBuilder = summaryBuilder;
        this.contractExtractor = contractExtractor;
        this.contextLayerAssembler = contextLayerAssembler;
        this.artifactReader = new ContextProjectionArtifactReader(artifactStore);
    }

    public ProjectedContext project(Path projectPath, RunRecord runRecord, StageType currentStage) {
        DocumentLanguage language = DocumentLanguage.detect(runRecord.goal(), runRecord.constraints());
        String analysis = currentStage.ordinal() >= StageType.ANALYSIS.ordinal()
                ? artifactReader.readCurrentArtifact(projectPath, runRecord, StageType.ANALYSIS)
                : "";
        String prd = currentStage.ordinal() >= StageType.PRD.ordinal()
                ? artifactReader.readCurrentArtifact(projectPath, runRecord, StageType.PRD)
                : "";
        String design = currentStage.ordinal() >= StageType.DESIGN.ordinal()
                ? artifactReader.readCurrentArtifact(projectPath, runRecord, StageType.DESIGN)
                : "";
        ContractView contractView = contractExtractor.extractContractView(runRecord.goal(), runRecord.constraints(), analysis, prd, design);
        String authorityCorpus = ConstraintAuthoritySupport.buildAuthorityCorpus(
                runRecord.goal(),
                runRecord.constraints(),
                contractView.constraintSourceMetadata(),
                contractView.executionContract()
        );

        String currentStageSummary = summaryBuilder.summarizeMarkdown(
                ArtifactContextSanitizer.sanitizeForProjection(
                        artifactReader.readCurrentArtifact(projectPath, runRecord, currentStage),
                        currentStage,
                        authorityCorpus
                ),
                1800
        );
        String upstreamContractSummary = summaryBuilder.summarizeMarkdown(readUpstreamContract(projectPath, runRecord, currentStage, authorityCorpus), 2600);
        // requirement refs 是下游 planning / test / review 的权威覆盖锚点，
        // 这里必须直接保留结构化目录，不能再先压成单行摘要后交给后续阶段消费。
        String authoritativeRequirementCatalog = contractView.productRequirementCatalogMarkdown(language);
        String recentHistorySummary = summaryBuilder.summarizeMarkdown(artifactReader.readRecentHistory(projectPath, runRecord, currentStage), 2200);
        String repairSummary = summaryBuilder.summarizeMarkdown(artifactReader.readRepairBrief(projectPath, runRecord), 1800);
        String workingSetSummary = summaryBuilder.summarizeMarkdown(workspace.collectContext(projectPath, 6, 900, 5000), 2200);
        List<FailureDigest> failures = artifactReader.collectRecentFailures(projectPath, runRecord);
        String failureSummary = failures.isEmpty()
                ? ""
                : summaryBuilder.renderBulletList(
                failures.stream()
                        .map(failure -> failure.stageType() + ": " + blank(failure.summary()) + " | " + blank(failure.changeRequest()))
                        .toList()
        );

        TaskMemory taskMemory = new TaskMemory(
                runRecord.goal(),
                runRecord.constraints(),
                currentStageSummary,
                upstreamContractSummary,
                authoritativeRequirementCatalog,
                recentHistorySummary,
                failureSummary,
                repairSummary,
                workingSetSummary,
                failures
        );
        ContextViews contextViews = contextLayerAssembler.assemble(
                runRecord,
                currentStage,
                contractView,
                currentStageSummary,
                upstreamContractSummary,
                authoritativeRequirementCatalog,
                recentHistorySummary,
                failureSummary,
                repairSummary,
                workingSetSummary,
                failures
        );
        return new ProjectedContext(
                currentStageSummary,
                upstreamContractSummary,
                authoritativeRequirementCatalog,
                recentHistorySummary,
                failureSummary,
                repairSummary,
                workingSetSummary,
                taskMemory,
                contextViews
        );
    }

    private String readUpstreamContract(Path projectPath, RunRecord runRecord, StageType currentStage, String authorityCorpus) {
        StringBuilder builder = new StringBuilder();
        for (StageType stageType : List.of(StageType.ANALYSIS, StageType.PRD, StageType.DESIGN)) {
            if (stageType.ordinal() > currentStage.ordinal()) {
                break;
            }
            String artifact = artifactReader.readCurrentArtifact(projectPath, runRecord, stageType);
            if (artifact.isBlank()) {
                continue;
            }
            String filtered = ArtifactContextSanitizer.sanitizeForProjection(artifact, stageType, authorityCorpus);
            if (filtered.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("## ").append(stageType).append("\n").append(filtered);
        }
        return builder.toString();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
