package devflow.agent.repair;

import devflow.agent.executor.generation.GenerationBudgetProfile;
import devflow.agent.executor.llm.LlmGenerateRequest;
import devflow.agent.executor.llm.LlmOptions;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;
import devflow.agent.executor.llm.StructuredPayloadReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.review.FixMode;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DiagnosisAgent {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisAgent.class);

    private final LlmProvider llmProvider;
    private final FileArtifactStore artifactStore;
    private final StructuredPayloadReader structuredPayloadReader;
    private final DiagnosisHistoryReader diagnosisHistoryReader;
    private final DiagnosisPromptAssembler diagnosisPromptAssembler;
    private final DiagnosisSimilaritySupport diagnosisSimilaritySupport;
    private final DiagnosisFallbackBriefBuilder diagnosisFallbackBriefBuilder;
    private final DiagnosisArtifactReader diagnosisArtifactReader;

    public DiagnosisAgent(LlmProvider llmProvider, FileArtifactStore artifactStore, ObjectMapper objectMapper) {
        this.llmProvider = llmProvider;
        this.artifactStore = artifactStore;
        this.structuredPayloadReader = new StructuredPayloadReader(objectMapper);
        this.diagnosisHistoryReader = new DiagnosisHistoryReader();
        this.diagnosisPromptAssembler = new DiagnosisPromptAssembler();
        this.diagnosisSimilaritySupport = new DiagnosisSimilaritySupport(
                llmProvider,
                structuredPayloadReader,
                diagnosisPromptAssembler
        );
        this.diagnosisFallbackBriefBuilder = new DiagnosisFallbackBriefBuilder();
        this.diagnosisArtifactReader = new DiagnosisArtifactReader(artifactStore);
    }

    public boolean shouldDiagnose(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            FixMode requestedMode,
            String summary,
            String changeRequest
    ) {
        String history = artifactStore.readReviewHistory(projectPath, runRecord.runId(), stageType);
        List<FailureEntry> entries = diagnosisHistoryReader.parse(history);
        int threshold = DiagnosisPolicy.thresholdFor(requestedMode);
        if (entries.size() < threshold) {
            return false;
        }
        List<FailureEntry> recentEntries = tail(entries, threshold);
        return diagnosisSimilaritySupport.isSameIssue(
                runRecord,
                stageType,
                requestedMode,
                recentEntries,
                summary,
                changeRequest
        );
    }

    public RepairBrief diagnose(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            FixMode requestedMode,
            String summary,
            String changeRequest
    ) {
        String history = artifactStore.readReviewHistory(projectPath, runRecord.runId(), stageType);
        List<FailureEntry> recentEntries = tail(diagnosisHistoryReader.parse(history), DiagnosisPolicy.thresholdFor(requestedMode));
        DiagnosisArtifactEvidence artifactEvidence = diagnosisArtifactReader.read(projectPath, runRecord);
        DiagnosisPrompt prompt = diagnosisPromptAssembler.buildDiagnosisPrompt(
                runRecord,
                stageType,
                requestedMode,
                recentEntries,
                summary,
                changeRequest,
                artifactEvidence
        );
        try {
            String response = llmProvider.generate(LlmGenerateRequest.workingPrompt(
                    prompt.system(),
                    prompt.user(),
                    devflow.agent.executor.llm.LlmOptions.outputBudgetRatio(GenerationBudgetProfile.diagnosisOutputRatio()),
                    ModelRole.DIAGNOSIS
            ));
            DiagnosisPayload payload = structuredPayloadReader.readJsonObject(response, DiagnosisPayload.class);
            return new RepairBrief(
                    payload.failureCluster(),
                    safeList(payload.repeatedErrors()),
                    payload.rootCauseHypothesis(),
                    safeList(payload.affectedFiles()),
                    safeList(payload.evidence()),
                    payload.recommendedMode() == null ? requestedModeOrDefault(requestedMode) : payload.recommendedMode(),
                    safeList(payload.mustFixFirst()),
                    safeList(payload.forbiddenDirections()),
                    safeList(payload.doNotChange()),
                    safeList(payload.acceptanceTarget()),
                    safeList(payload.acceptanceChecks())
            );
        } catch (Exception exception) {
            log.warn(
                    "Diagnosis fallback applied for run {} stage {} mode {}",
                    runRecord == null ? null : runRecord.runId(),
                    stageType,
                    requestedMode,
                    exception
            );
            return diagnosisFallbackBriefBuilder.build(recentEntries, requestedMode, summary, changeRequest);
        }
    }

    private List<FailureEntry> tail(List<FailureEntry> entries, int size) {
        if (entries.size() <= size) {
            return entries;
        }
        return entries.subList(entries.size() - size, entries.size());
    }

    private List<String> safeList(List<String> items) {
        return items == null ? List.of() : items;
    }

    private FixMode requestedModeOrDefault(FixMode requestedMode) {
        return requestedMode == null || requestedMode == FixMode.NONE ? FixMode.PATCH : requestedMode;
    }

    static record FailureEntry(
            int attempt,
            String summary,
            String changeRequest,
            String evidence,
            String actionItems
    ) {
    }

    private record DiagnosisPayload(
            String failureCluster,
            List<String> repeatedErrors,
            String rootCauseHypothesis,
            List<String> affectedFiles,
            List<String> evidence,
            FixMode recommendedMode,
            List<String> mustFixFirst,
            List<String> forbiddenDirections,
            List<String> doNotChange,
            List<String> acceptanceTarget,
            List<String> acceptanceChecks
    ) {
    }

}
