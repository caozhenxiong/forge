package devflow.agent.orchestrator;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;

import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.quality.QualityLedger;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.repair.RepairAgent;
import devflow.agent.repair.RepairBrief;
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
    private final LanguagePolicy languagePolicy;

    StageRevisionRepairSupport(
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            DiagnosisAgent diagnosisAgent,
            RepairAgent repairAgent,
            StageRevisionNoteBuilder stageRevisionNoteBuilder,
            LanguagePolicy languagePolicy
    ) {
        this.artifactStore = artifactStore;
        this.eventLogStore = eventLogStore;
        this.diagnosisAgent = diagnosisAgent;
        this.repairAgent = repairAgent;
        this.stageRevisionNoteBuilder = stageRevisionNoteBuilder;
        this.languagePolicy = languagePolicy;
    }

    String buildRevisionNote(
            Path projectPath,
            RunRecord runRecord,
            StageType sourceStage,
            RevisionContext revisionContext
    ) {
        List<String> requiredCapabilitySurfaces = loadRequiredCapabilitySurfaces(projectPath, runRecord, sourceStage);
        String revisionNote = stageRevisionNoteBuilder.build(
                revisionContext.fixMode(),
                revisionContext.implementationPatchTarget(),
                revisionContext.summary(),
                revisionContext.changeRequest(),
                revisionContext.evidence(),
                revisionContext.actionItems(),
                revisionContext.overrideChanges(),
                revisionContext.supervisorDecision(),
                requiredCapabilitySurfaces
        );
        if (revisionContext.rerouteStage() != StageType.IMPLEMENTATION
                || (!revisionContext.forceRepair() && !revisionContext.repeatedIssue())) {
            return revisionNote;
        }
        RepairBrief repairBrief = diagnosisAgent.diagnose(
                projectPath,
                runRecord,
                sourceStage,
                revisionContext.fixMode(),
                revisionContext.summary(),
                revisionContext.changeRequest()
        );
        DocumentLanguage language = languagePolicy.resolve(runRecord.goal(), runRecord.constraints());
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
                revisionContext.fixMode(),
                revisionContext.implementationPatchTarget(),
                revisionContext.summary(),
                revisionContext.changeRequest(),
                revisionContext.evidence(),
                revisionContext.actionItems(),
                revisionContext.overrideChanges(),
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
        return qualityLedger.coverageLedger().missingRequiredCapabilityIds().stream()
                .sorted()
                .toList();
    }
}
