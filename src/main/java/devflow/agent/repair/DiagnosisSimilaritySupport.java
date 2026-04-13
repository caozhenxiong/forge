package devflow.agent.repair;

import devflow.agent.executor.generation.GenerationBudgetProfile;
import devflow.agent.executor.llm.LlmGenerateRequest;
import devflow.agent.executor.llm.LlmOptions;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;
import devflow.agent.executor.llm.StructuredPayloadReader;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.review.FixMode;
import java.util.List;

/**
 * 统一处理 diagnosis 的“是否属于同一问题簇”判断。
 *
 * <p>这里专门负责 similarity prompt 调用与结构化结果解析，
 * 避免 DiagnosisAgent 同时承担历史阈值判断、相似度模型调用和 repair brief 组装。
 */
final class DiagnosisSimilaritySupport {

    private final LlmProvider llmProvider;
    private final StructuredPayloadReader structuredPayloadReader;
    private final DiagnosisPromptAssembler diagnosisPromptAssembler;

    DiagnosisSimilaritySupport(
            LlmProvider llmProvider,
            StructuredPayloadReader structuredPayloadReader,
            DiagnosisPromptAssembler diagnosisPromptAssembler
    ) {
        this.llmProvider = llmProvider;
        this.structuredPayloadReader = structuredPayloadReader;
        this.diagnosisPromptAssembler = diagnosisPromptAssembler;
    }

    boolean isSameIssue(
            RunRecord runRecord,
            StageType stageType,
            FixMode requestedMode,
            List<DiagnosisAgent.FailureEntry> recentEntries,
            String summary,
            String changeRequest
    ) {
        DiagnosisPrompt prompt = diagnosisPromptAssembler.buildSimilarityPrompt(
                runRecord,
                stageType,
                requestedMode,
                recentEntries,
                summary,
                changeRequest
        );
        try {
            String response = llmProvider.generate(LlmGenerateRequest.workingPrompt(
                    prompt.system(),
                    prompt.user(),
                    LlmOptions.outputBudgetRatio(GenerationBudgetProfile.diagnosisSimilarityOutputRatio()),
                    ModelRole.DIAGNOSIS
            ));
            SimilarityPayload payload = structuredPayloadReader.readJsonObject(response, SimilarityPayload.class);
            return payload.sameIssue();
        } catch (Exception exception) {
            return false;
        }
    }

    private record SimilarityPayload(
            boolean sameIssue,
            String reason
    ) {
    }
}
