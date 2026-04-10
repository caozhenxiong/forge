package devflow.agent.artifact;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.i18n.DocumentLanguage;
import java.util.List;

/**
 * 文档阶段一次草稿生成所需的稳定上下文。
 *
 * <p>它只描述 intake 之后的只读输入，不承担任何流程控制职责。
 */
record DocumentDraftContext(
        DocumentLanguage language,
        String template,
        ConstraintSourceMetadata authoritativeSourceMetadata,
        String authoritativeSourceMetadataBlock,
        String previousDraft,
        List<Integer> targetSections,
        String previousOutline,
        DocumentDraftMode mode
) {
}
