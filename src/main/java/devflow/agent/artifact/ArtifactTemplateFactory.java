package devflow.agent.artifact;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
/**
 * 阶段模板工厂门面。
 *
 * <p>它只负责按阶段路由到具体模板构造器，不再继续内嵌所有文档/执行阶段模板。
 */
public class ArtifactTemplateFactory {

    private final DocumentStageTemplateBuilder documentStageTemplateBuilder;
    private final ExecutionStageTemplateBuilder executionStageTemplateBuilder;
    private final LanguagePolicy languagePolicy;

    public ArtifactTemplateFactory() {
        this(new LanguagePolicy());
    }

    @Autowired
    public ArtifactTemplateFactory(LanguagePolicy languagePolicy) {
        ArtifactTemplateSupport support = new ArtifactTemplateSupport();
        this.documentStageTemplateBuilder = new DocumentStageTemplateBuilder(support);
        this.executionStageTemplateBuilder = new ExecutionStageTemplateBuilder(support);
        this.languagePolicy = languagePolicy;
    }

    public String create(StageType stageType, RunRecord runRecord, String note) {
        return create(stageType, runRecord, note, languagePolicy.resolve(runRecord.goal(), runRecord.constraints(), note));
    }

    public String create(StageType stageType, RunRecord runRecord, String note, DocumentLanguage language) {
        return switch (stageType) {
            case ANALYSIS, PRD, DESIGN -> documentStageTemplateBuilder.create(stageType, runRecord, note, language);
            case IMPLEMENTATION, CODE_REVIEW, TEST -> executionStageTemplateBuilder.create(stageType, runRecord, note, language);
        };
    }
}
