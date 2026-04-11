package devflow.agent.artifact;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.RunConfig;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageStatus;
import devflow.agent.orchestrator.StageType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactTemplateFactoryTests {

    private final ArtifactTemplateFactory factory = new ArtifactTemplateFactory();

    @Test
    void createsChinesePrdTemplateWithStructuredMetadataSections() {
        String template = factory.create(StageType.PRD, runRecord(), "当前备注", DocumentLanguage.ZH);

        assertTrue(template.contains("# 产品需求文档"));
        assertTrue(template.contains("## 7. Contract Metadata"));
        assertTrue(template.contains("## 8. Source Metadata"));
        assertFalse(template.contains("当前备注"));
        assertFalse(template.contains("Current Notes"));
    }

    @Test
    void createsEnglishTestTemplateThroughExecutionBuilder() {
        String template = factory.create(StageType.TEST, runRecord(), "Current notes", DocumentLanguage.EN);

        assertTrue(template.contains("# Test Report"));
        assertTrue(template.contains("## Test Plan"));
        assertTrue(template.contains("## Test Results"));
        assertFalse(template.contains("Current notes"));
        assertFalse(template.contains("Current Notes"));
    }

    private RunRecord runRecord() {
        EnumMap<StageType, StageExecution> stages = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stages.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        return new RunRecord(
                UUID.randomUUID(),
                Path.of("/tmp/demo"),
                "实现一个可玩的网页版俄罗斯方块",
                "需要纯网页版、可直接打开运行",
                RunConfig.defaultConfig(),
                StageType.ANALYSIS,
                RunStatus.IN_PROGRESS,
                stages,
                Instant.now(),
                Instant.now()
        );
    }
}
