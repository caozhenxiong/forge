package devflow.agent.context;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintAuthoritySupportTests {

    @Test
    void authorityCorpusProjectsBindingFactsWithoutRuntimeOwnershipMode() {
        String authorityCorpus = ConstraintAuthoritySupport.buildAuthorityCorpus(
                "做成精致的网页版",
                "只做前端，不做后端",
                new ConstraintSourceMetadata(
                        List.of("必须能直接打开运行"),
                        List.of("当前交付以 HTML 入口为主"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                new ExecutionContract(
                        true,
                        "html-entry",
                        "entry-with-local-dependencies",
                        "entry-owned",
                        true,
                        true,
                        List.of("page-opens", "runtime-surface-renders")
                )
        );

        assertTrue(authorityCorpus.contains("html-entry"));
        assertTrue(authorityCorpus.contains("entry-with-local-dependencies"));
        assertTrue(authorityCorpus.contains("page-opens"));
        assertFalse(authorityCorpus.contains("entry-owned"));
    }
}
