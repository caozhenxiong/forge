package devflow.agent.testsupport;

import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.FeatureProfiler;
import devflow.agent.quality.HtmlStructureRuntimeSignalResolver;
import devflow.agent.quality.QualityIntentResolver;
import devflow.agent.quality.QualityPlanFactory;
import devflow.agent.quality.QualityPolicyResolver;
import devflow.agent.quality.QualityRulesLoader;

public final class QualityPlanFactoryTestSupport {

    private QualityPlanFactoryTestSupport() {
    }

    public static QualityPlanFactory qualityPlanFactory() {
        return qualityPlanFactory(new FileProjectWorkspace(), new TreeSitterSupport());
    }

    public static QualityPlanFactory qualityPlanFactory(
            FileProjectWorkspace workspace,
            TreeSitterSupport treeSitterSupport
    ) {
        return new QualityPlanFactory(
                new QualityRulesLoader(),
                new FeatureProfiler(),
                new QualityIntentResolver(),
                new QualityPolicyResolver(),
                new HtmlStructureRuntimeSignalResolver(workspace, treeSitterSupport)
        );
    }
}
