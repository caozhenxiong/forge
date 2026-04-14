package devflow.agent.executor.implementation.planning;

import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningRuntimeFactsResolverTests {

    @TempDir
    Path tempDir;

    @Test
    void doesNotInferCompanionRuntimeFromSiblingFiles() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!doctype html>
                        <html>
                        <body>
                          <main id="app"></main>
                          <script>
                            console.log('inline boot');
                          </script>
                        </body>
                        </html>
                        """
        );
        Files.writeString(tempDir.resolve("index.app.js"), "export const game = true;");

        PlanningRuntimeFacts facts = new PlanningRuntimeFactsResolver(new FileProjectWorkspace()).resolve(request(
                fingerprint(Set.of("index.html", "index.app.js")),
                ImplementationContinuationConstraints.empty()
        ));

        assertEquals(Path.of("index.html"), facts.htmlEntryPath());
        assertTrue(facts.wiredRuntimePaths().isEmpty());
        assertTrue(facts.runtimeContract() != null && facts.runtimeContract().inlineHost());
    }

    @Test
    void reusesProtectedContinuationContractBeforeObservedHtmlFacts() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!doctype html>
                        <html>
                        <body>
                          <script type="module" src="./old.js"></script>
                        </body>
                        </html>
                        """
        );

        PlanningRuntimeFacts facts = new PlanningRuntimeFactsResolver(new FileProjectWorkspace()).resolve(request(
                fingerprint(Set.of("index.html", "old.js", "new.js")),
                new ImplementationContinuationConstraints(
                        List.of("index.html"),
                        List.of(new ImplementationContinuationConstraints.ProtectedHtmlEntryConstraint(
                                "index.html",
                                new devflow.agent.executor.runtime.HtmlRuntimeOwnershipContract(
                                        Path.of("index.html"),
                                        devflow.agent.executor.runtime.RuntimeOwnershipMode.EXTERNAL_COMPANION,
                                        List.of(Path.of("new.js"))
                                )
                        ))
                )
        ));

        assertEquals(List.of(Path.of("new.js")), facts.wiredRuntimePaths());
    }

    private PlanningRequest request(
            ProjectFingerprint fingerprint,
            ImplementationContinuationConstraints continuationConstraints
    ) {
        return new PlanningRequest(
                runRecord(),
                "",
                "",
                "",
                "",
                false,
                new devflow.agent.executor.DeliveryPolicyEnvelope(
                        devflow.agent.executor.DeliveryMode.PATCH,
                        2,
                        4,
                        true,
                        false,
                        true,
                        List.of()
                ),
                null,
                devflow.agent.quality.QualityPlan.empty(),
                fingerprint,
                devflow.agent.i18n.DocumentLanguage.ZH,
                devflow.agent.review.FixMode.NONE,
                devflow.agent.review.ImplementationPatchTarget.NONE,
                "",
                continuationConstraints,
                null,
                null
        );
    }

    private ProjectFingerprint fingerprint(Set<String> fileNames) {
        return new ProjectFingerprint(
                "web",
                "",
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                "index.html",
                fileNames,
                List.of()
        );
    }

    private RunRecord runRecord() {
        Map<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        return new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "实现一个可玩的网页版俄罗斯方块",
                "需要纯网页版、可直接打开运行",
                RunConfig.defaultConfig(),
                StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                stageStates,
                Instant.now(),
                Instant.now()
        );
    }
}
