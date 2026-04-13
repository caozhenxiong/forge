package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.AuthoritativeCoverageCatalog;
import devflow.agent.context.ExecutionContract;
import devflow.agent.quality.CapabilityMatrix;
import devflow.agent.quality.CoveragePolicy;
import devflow.agent.quality.ExperiencePolicy;
import devflow.agent.quality.FeatureProfile;
import devflow.agent.quality.QualityChecklist;
import devflow.agent.quality.QualityIntent;
import devflow.agent.quality.QualityPlan;
import devflow.agent.quality.StructurePolicy;
import devflow.agent.quality.StructureRiskLevel;
import devflow.agent.quality.StructureRiskReport;
import devflow.agent.review.FixMode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devflow.agent.executor.subtask.Subtask;
class ImplementationPlanNormalizationSupportTests {

    @Test
    void normalizePreservesExplicitRuntimeOwnershipInsteadOfInferringExternalization() {
        ImplementationPlanNormalizationSupport support = new ImplementationPlanNormalizationSupport();
        ImplementationPlan plan = new ImplementationPlan(
                "plan",
                List.of(new Subtask(
                        "实现入口",
                        "补齐入口与 companion runtime",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("页面可运行"),
                        true,
                        DeliveryMode.INCREMENTAL,
                        List.of(
                                new FileChange(
                                        "index.html",
                                        ChangeAction.WRITE,
                                        "补齐宿主接线",
                                        FileEditScope.HOST_HTML_PATCH,
                                        RuntimeOwnershipMode.EXTERNAL_COMPANION
                                ),
                                new FileChange("index.app.js", ChangeAction.WRITE, "补齐运行脚本")
                        )
                ))
        );

        ImplementationPlan normalized = support.normalize(
                plan,
                FixMode.NONE,
                false,
                DeliveryPolicyEnvelope.defaultPolicy(),
                new AuthoritativeCoverageCatalog(List.of()),
                new ExecutionContract(true, "html-entry", true, true, List.of("runtime-surface-renders")),
                ImplementationContinuationConstraints.empty()
        );

        Subtask subtask = normalized.subtasks().getFirst();
        assertEquals(2, subtask.changes().size());
        assertEquals(RuntimeOwnershipMode.EXTERNAL_COMPANION, subtask.changes().getFirst().runtimeOwnership());
        assertEquals(FileEditScope.HOST_HTML_PATCH, subtask.changes().getFirst().effectiveEditScope());
        assertTrue(subtask.changes().stream().anyMatch(change -> "index.app.js".equals(change.path())));
    }

    @Test
    void normalizeDoesNotSilentlyRewriteStandaloneAssetScopes() {
        ImplementationPlanNormalizationSupport support = new ImplementationPlanNormalizationSupport();
        ImplementationPlan plan = new ImplementationPlan(
                "plan",
                List.of(new Subtask(
                        "补脚本",
                        "修复运行脚本",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("脚本可解析"),
                        false,
                        DeliveryMode.PATCH,
                        List.of(new FileChange("game.js", ChangeAction.WRITE, "补齐主逻辑", FileEditScope.INLINE_SCRIPT_PATCH))
                ))
        );

        ImplementationPlan normalized = support.normalize(
                plan,
                FixMode.PATCH,
                false,
                DeliveryPolicyEnvelope.defaultPolicy(),
                new AuthoritativeCoverageCatalog(List.of()),
                null,
                ImplementationContinuationConstraints.empty()
        );

        assertEquals(FileEditScope.INLINE_SCRIPT_PATCH, normalized.subtasks().getFirst().changes().getFirst().effectiveEditScope());
    }

    @Test
    void continuationDoesNotInferSkeletonForExistingHtmlEntry() {
        ImplementationPlanNormalizationSupport support = new ImplementationPlanNormalizationSupport();
        ImplementationPlan plan = new ImplementationPlan(
                "plan",
                List.of(new Subtask(
                        "继续补入口",
                        "修复现有入口",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("入口可运行"),
                        true,
                        null,
                        List.of(new FileChange(
                                "index.html",
                                ChangeAction.WRITE,
                                "继续补齐入口",
                                FileEditScope.HOST_HTML_PATCH,
                                RuntimeOwnershipMode.INLINE_HOST
                        ))
                ))
        );

        ImplementationPlan normalized = support.normalize(
                plan,
                FixMode.NONE,
                true,
                DeliveryPolicyEnvelope.defaultPolicy(),
                new AuthoritativeCoverageCatalog(List.of()),
                new ExecutionContract(true, "html-entry", true, true, List.of("runtime-surface-renders")),
                new ImplementationContinuationConstraints(
                        List.of("index.html"),
                        List.of(new ImplementationContinuationConstraints.ProtectedHtmlEntryConstraint(
                                "index.html",
                                RuntimeOwnershipMode.INLINE_HOST
                        ))
                )
        );

        assertTrue(normalized.subtasks().getFirst().deliveryMode() != DeliveryMode.SKELETON);
    }

    private QualityPlan qualityPlan() {
        return new QualityPlan(
                new FeatureProfile(true, false, true, true, true, true, false, true, false),
                QualityIntent.empty(),
                StructureRiskReport.low(),
                new StructurePolicy(true, true, StructureRiskLevel.MEDIUM),
                new CoveragePolicy(3, true, true),
                new ExperiencePolicy(false, false),
                CapabilityMatrix.empty(),
                QualityChecklist.empty()
        );
    }
}
