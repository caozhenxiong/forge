package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.AuthoritativeCoverageCatalog;
import devflow.agent.context.ContractView;
import devflow.agent.context.SharedContextBundle;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.domain.RunRecord;
import devflow.agent.protocol.ExecutionDirectivePayload;

/**
 * 统一构造 implementation 阶段共享上下文。
 *
 * <p>这层只负责 durable context 的稳定裁剪与产物目录渲染，
 * 避免上下文门面继续关心摘要长度、默认占位值和产品需求目录格式。
 */
final class ImplementationSharedContextFactory {

    private static final int REPAIR_SUMMARY_MAX_CHARS = 2400;
    private static final int WORKSPACE_SUMMARY_MAX_CHARS = 2800;

    SharedContextBundle build(
            RunRecord runRecord,
            String note,
            String workspaceContext,
            ContractView contractView,
            ExecutionDirectivePayload directives
    ) {
        return new SharedContextBundle(
                runRecord.goal(),
                runRecord.constraints(),
                contractView,
                directives.requiredEvidence(),
                directives.mustFixFirst(),
                directives.forbiddenDirections(),
                summarize(note, REPAIR_SUMMARY_MAX_CHARS),
                summarize(workspaceContext, WORKSPACE_SUMMARY_MAX_CHARS)
        );
    }

    String renderAuthoritativeCoverageCatalog(
            AuthoritativeCoverageCatalog authoritativeCoverageCatalog,
            DocumentLanguage language
    ) {
        if (authoritativeCoverageCatalog == null) {
            return PlaceholderValues.none(language);
        }
        return authoritativeCoverageCatalog.toMarkdown(language);
    }

    private String summarize(String content, int maxChars) {
        return PlaceholderValues.truncateMiddle(content, maxChars);
    }
}
