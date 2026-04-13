package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ExecutionContract;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeWorkingSetResolverTests {

    private final RuntimeWorkingSetResolver resolver = new RuntimeWorkingSetResolver();

    @Test
    void scriptTaskIncludesResolvedHtmlEntryInSupplementalWorkingSet() {
        ProjectFingerprint fingerprint = new ProjectFingerprint(
                "web-static",
                "",
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                "index.html",
                Set.of("index.html", "game.js", "style.css"),
                List.of()
        );
        ExecutionContract contract = new ExecutionContract(true, "html-entry", true, true, List.of("page-opens"));

        List<Path> supplemental = resolver.resolveSupplementalPaths(
                fingerprint,
                contract,
                List.of(Path.of("game.js")),
                Path.of("game.js")
        );

        assertEquals(List.of(Path.of("index.html")), supplemental);
    }

    @Test
    void htmlTaskIncludesSiblingRuntimeAssetsInSupplementalWorkingSet() {
        ProjectFingerprint fingerprint = new ProjectFingerprint(
                "web-static",
                "",
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                "index.html",
                Set.of("index.html", "game.js", "style.css", "README.md"),
                List.of()
        );
        ExecutionContract contract = new ExecutionContract(true, "html-entry", true, true, List.of("page-opens"));

        List<Path> supplemental = resolver.resolveSupplementalPaths(
                fingerprint,
                contract,
                List.of(Path.of("index.html")),
                Path.of("index.html")
        );

        assertTrue(supplemental.contains(Path.of("game.js")));
        assertTrue(supplemental.contains(Path.of("style.css")));
    }
}
