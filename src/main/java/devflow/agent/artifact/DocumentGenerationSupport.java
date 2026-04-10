package devflow.agent.artifact;

import devflow.agent.executor.GenerationBudgetProfile;
import devflow.agent.executor.LlmOptions;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;

/**
 * 统一维护文档阶段的模型生成调用。
 *
 * <p>这样各阶段 composer 只关心 intake / merge / sanitize，
 * 不再各自持有一套相同的预算和 provider 调用逻辑。
 */
final class DocumentGenerationSupport {

    private final LlmProvider llmProvider;

    DocumentGenerationSupport(LlmProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    String generate(DocumentGenerationPrompt prompt, DocumentDraftMode mode, ModelRole role) {
        return llmProvider.generate(
                prompt.system(),
                prompt.user(),
                LlmOptions.outputBudgetRatio(
                        mode == DocumentDraftMode.FULL_DRAFT
                                ? GenerationBudgetProfile.documentFullDraftOutputRatio()
                                : GenerationBudgetProfile.documentPatchOutputRatio()
                ),
                role
        );
    }
}
