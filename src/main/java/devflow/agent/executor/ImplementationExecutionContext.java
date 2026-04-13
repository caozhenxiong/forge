package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractView;
import devflow.agent.context.SharedContextBundle;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.validation.ProjectFingerprint;

/**
 * implementation 执行前的准备上下文。
 *
 * <p>这份对象把语言、contract、delivery policy、工作区摘要和共享上下文收成一个稳定输入，
 * 让执行器只负责编排，不再自己负责解析和拼装这些准备数据。
 */
record ImplementationExecutionContext(
        DocumentLanguage language,
        String workspaceContext,
        String plannerContextMarkdown,
        String coderContextMarkdown,
        String performanceValidationGuidance,
        DeliveryPolicyEnvelope deliveryPolicy,
        FixMode fixMode,
        ImplementationPatchTarget implementationPatchTarget,
        java.util.List<FileChange> overrideChanges,
        ProjectFingerprint fingerprint,
        ContractView contractView,
        QualityPlan qualityPlan,
        boolean preferSkeletonFlow,
        SharedContextBundle sharedContextBundle,
        String authoritativeCoverageCatalog,
        ImplementationContinuationConstraints continuationConstraints
) {
}
