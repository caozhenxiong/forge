package devflow.agent.orchestrator;

import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.quality.CapabilitySurface;
import devflow.agent.quality.QualityLedger;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.repair.RepairAgent;
import devflow.agent.repair.RepairBrief;
import devflow.agent.review.FixMode;
import devflow.agent.supervisor.SupervisorDecision;
import java.nio.file.Path;
import java.util.List;

/**
 * 统一处理阶段回流时的 diagnosis 与 repair note 生成。
 *
 * <p>StageRevisionSupport 只负责状态迁移与 reroute，
 * 这层则负责“是否触发 diagnosis、如何落 repair brief、如何生成最终修订说明”。
 */
final class StageRevisionRepairSupport {

    private final FileArtifactStore artifactStore;
    private final EventLogStore eventLogStore;
    private final DiagnosisAgent diagnosisAgent;
    private final RepairAgent repairAgent;
    private final StageRevisionNoteBuilder stageRevisionNoteBuilder;

    StageRevisionRepairSupport(
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            DiagnosisAgent diagnosisAgent,
            RepairAgent repairAgent,
            StageRevisionNoteBuilder stageRevisionNoteBuilder
    ) {
        this.artifactStore = artifactStore;
        this.eventLogStore = eventLogStore;
        this.diagnosisAgent = diagnosisAgent;
        this.repairAgent = repairAgent;
        this.stageRevisionNoteBuilder = stageRevisionNoteBuilder;
    }

    String buildRevisionNote(
            Path projectPath,
            RunRecord runRecord,
            StageType sourceStage,
            StageType rerouteStage,
            FixMode fixMode,
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            SupervisorDecision supervisorDecision,
            boolean forceRepair,
            boolean repeatedIssue
    ) {
        List<String> requiredCapabilitySurfaces = loadRequiredCapabilitySurfaces(projectPath, runRecord, sourceStage);
        String revisionNote = stageRevisionNoteBuilder.build(
                fixMode,
                summary,
                changeRequest,
                evidence,
                actionItems,
                supervisorDecision,
                requiredCapabilitySurfaces
        );
        if (rerouteStage != StageType.IMPLEMENTATION || (!forceRepair && !repeatedIssue)) {
            return revisionNote;
        }
        RepairBrief repairBrief = diagnosisAgent.diagnose(
                projectPath,
                runRecord,
                sourceStage,
                fixMode,
                summary,
                changeRequest
        );
        DocumentLanguage language = DocumentLanguage.detect(runRecord.goal(), runRecord.constraints());
        artifactStore.writeAuxiliaryArtifact(
                projectPath,
                runRecord.runId(),
                AuxiliaryArtifactNames.REPAIR_BRIEF,
                repairBrief.toMarkdown(language)
        );
        eventLogStore.append(
                projectPath,
                runRecord.runId(),
                WorkflowEventMessages.diagnosisTriggered(sourceStage, repairBrief.recommendedMode().name())
        );
        return repairAgent.buildRepairNote(
                fixMode,
                summary,
                changeRequest,
                evidence,
                actionItems,
                requiredCapabilitySurfaces,
                repairBrief,
                language
        );
    }

    private List<String> loadRequiredCapabilitySurfaces(Path projectPath, RunRecord runRecord, StageType sourceStage) {
        if (sourceStage != StageType.TEST) {
            return List.of();
        }
        String testReport;
        try {
            testReport = artifactStore.readArtifact(projectPath, runRecord.runId(), StageType.TEST);
        } catch (IllegalStateException ignored) {
            testReport = "";
        }
        if (testReport.isBlank()) {
            testReport = artifactStore.readAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.TEST_EXECUTION);
        }
        QualityLedger qualityLedger = StructuredArtifactBlocks.readFirstJsonBlock(
                testReport,
                ArtifactBlockKind.QUALITY_LEDGER,
                QualityLedger.class
        );
        if (qualityLedger == null || qualityLedger.coverageLedger() == null) {
            return List.of();
        }
        return qualityLedger.coverageLedger().missingRequiredSurfaces().stream()
                .map(CapabilitySurface::wireValue)
                .sorted()
                .toList();
    }
}
