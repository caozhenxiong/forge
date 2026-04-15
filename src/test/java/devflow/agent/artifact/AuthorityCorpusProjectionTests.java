package devflow.agent.artifact;

import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ExecutionContract;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageType;
import devflow.agent.i18n.LanguagePolicy;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorityCorpusProjectionTests {

    @Test
    void documentStageIntakeDoesNotLoopRuntimeOwnershipBackIntoAuthorityCorpus() {
        DocumentStageIntake intake = new DocumentStageIntake(
                null,
                null,
                new ContractExtractor(),
                new LanguagePolicy(),
                new DocumentDraftAssembler()
        );

        String authorityCorpus = intake.buildAuthorityCorpus(
                runRecord(),
                new ExecutionContract(
                        true,
                        "html-entry",
                        "entry-with-local-dependencies",
                        "companion-owned",
                        true,
                        true,
                        List.of("page-opens")
                )
        );

        assertTrue(authorityCorpus.contains("html-entry"));
        assertTrue(authorityCorpus.contains("entry-with-local-dependencies"));
        assertFalse(authorityCorpus.contains("companion-owned"));
    }

    @Test
    void stageArtifactInputResolverDoesNotLoopRuntimeOwnershipBackIntoAuthorityCorpus() {
        StageArtifactInputResolver resolver = new StageArtifactInputResolver(
                null,
                new ContractExtractor(),
                new LanguagePolicy()
        );

        String authorityCorpus = resolver.buildAuthorityCorpus(
                runRecord(),
                new ExecutionContract(
                        true,
                        "html-entry",
                        "self-contained-entry",
                        "entry-owned",
                        true,
                        true,
                        List.of("page-opens")
                )
        );

        assertTrue(authorityCorpus.contains("self-contained-entry"));
        assertFalse(authorityCorpus.contains("entry-owned"));
    }

    private RunRecord runRecord() {
        return new RunRecord(
                UUID.randomUUID(),
                Path.of("/tmp/project"),
                "做成精致的网页版",
                "必须直接打开运行",
                null,
                StageType.DESIGN,
                RunStatus.IN_PROGRESS,
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }
}
