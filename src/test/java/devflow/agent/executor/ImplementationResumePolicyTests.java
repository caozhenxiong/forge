package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.toolloop.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationResumePolicyTests {

    @Test
    void restoresIncompleteSubtaskExecutionStateWithFilePatchProgress() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ImplementationResumePolicy policy = new ImplementationResumePolicy(objectMapper);
        String previousStateJson = objectMapper.writeValueAsString(new ImplementationStateSnapshot(
                "继续完成当前文件 patch",
                List.of(
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "完成入口",
                                "写入口",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("入口存在"),
                                true,
                                "PATCH",
                                List.of(new ImplementationStateSnapshot.FileChangeState("index.html", "WRITE", "入口"))
                        ),
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "补齐逻辑",
                                "继续补齐 app.js",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("逻辑完成"),
                                false,
                                "PATCH",
                                List.of(new ImplementationStateSnapshot.FileChangeState("app.js", "WRITE", "逻辑"))
                        )
                ),
                List.of(
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                                "完成入口",
                                true,
                                List.of()
                        ),
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                                "补齐逻辑",
                                false,
                                List.of(),
                                "PATCH",
                                true,
                                List.of(new ImplementationStateSnapshot.FileEditAttemptStateSnapshot(
                                        "app.js",
                                        FileEditProtocolNames.TARGETED_REWRITE,
                                        FileEditStrategyNames.PRECISE_CODE,
                                        "export function tick() {}\n",
                                        "hash-1",
                                        List.of("code-unit-1", "code-unit-2"),
                                        "code-unit-16"
                                ))
                        )
                ),
                List.of(),
                "补齐逻辑",
                false,
                false,
                List.of("补齐逻辑")
        ));

        ReusableImplementationState reusableState = policy.loadReusableImplementationState(
                previousStateJson,
                FixMode.PATCH,
                ImplementationPatchTarget.NONE,
                List.of(),
                DocumentLanguage.ZH
        );

        assertNotNull(reusableState);
        assertEquals(1, reusableState.completedReports().size());
        assertNotNull(reusableState.resumedExecutionState());
        assertEquals(DeliveryMode.PATCH, reusableState.resumedExecutionState().deliveryMode());
        assertTrue(reusableState.resumedExecutionState().preferPreciseEditing());
        FileEditAttemptState progressState = reusableState.resumedExecutionState().fileEditAttemptState(Path.of("app.js"));
        assertNotNull(progressState);
        assertEquals(FileEditStrategyNames.PRECISE_CODE, progressState.strategyName());
        assertEquals("export function tick() {}\n", progressState.workingContent());
        assertEquals("code-unit-16", progressState.currentTargetLabel());
        assertEquals(List.of("code-unit-1", "code-unit-2"), progressState.completedTargetLabels());
    }

    @Test
    void reopensOwningCompletedSubtaskForRuntimeWiringPatch() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ImplementationResumePolicy policy = new ImplementationResumePolicy(objectMapper);
        String previousStateJson = objectMapper.writeValueAsString(new ImplementationStateSnapshot(
                "修复 runtime wiring",
                List.of(new ImplementationStateSnapshot.PlannedSubtaskState(
                        "建立入口",
                        "创建入口",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("入口可运行"),
                        true,
                        "PATCH",
                        List.of(
                                new ImplementationStateSnapshot.FileChangeState(
                                        "index.html",
                                        "WRITE",
                                        "补齐宿主接线",
                                        FileEditScope.HOST_HTML_PATCH.name(),
                                        RuntimeOwnershipMode.EXTERNAL_COMPANION.name()
                                ),
                                new ImplementationStateSnapshot.FileChangeState("index.app.js", "WRITE", "补齐 companion runtime")
                        )
                )),
                List.of(new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                        "建立入口",
                        true,
                        List.of()
                )),
                List.of(),
                null,
                true,
                false,
                new ImplementationStateSnapshot.ContractGateState(
                        ArchitectIntegrationCheckScope.STAGE_COMPLETION.name(),
                        false,
                        ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID.name(),
                        "index.app.js 存在，但 index.html 未接线",
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING.name(),
                        new ImplementationStateSnapshot.RuntimeContractState(
                                "index.html",
                                RuntimeOwnershipMode.EXTERNAL_COMPANION.name(),
                                List.of("index.app.js")
                        )
                ),
                "",
                "",
                "",
                "",
                "",
                List.of(),
                "",
                "",
                List.of()
        ));

        ReusableImplementationState reusableState = policy.loadReusableImplementationState(
                previousStateJson,
                FixMode.PATCH,
                ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                List.of(),
                DocumentLanguage.ZH
        );

        assertNotNull(reusableState);
        assertEquals(1, reusableState.plan().subtasks().size());
        assertTrue(reusableState.completedReports().isEmpty());
        assertNotNull(reusableState.resumedExecutionState());
        assertEquals(DeliveryMode.PATCH, reusableState.resumedExecutionState().deliveryMode());
        assertEquals(2, reusableState.resumedExecutionState().effectiveChanges().size());
        assertEquals("index.html", reusableState.resumedExecutionState().effectiveChanges().getFirst().path());
        assertEquals(
                RuntimeOwnershipMode.EXTERNAL_COMPANION,
                reusableState.resumedExecutionState().effectiveChanges().getFirst().runtimeOwnership()
        );
        assertEquals("index.app.js", reusableState.resumedExecutionState().effectiveChanges().get(1).path());
        assertTrue(reusableState.resumedExecutionState().toolSessionState().transcript().isEmpty());
    }

    @Test
    void runtimeWiringPatchPrefersExplicitContractOverOlderInlineOwnership() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ImplementationResumePolicy policy = new ImplementationResumePolicy(objectMapper);
        String previousStateJson = objectMapper.writeValueAsString(new ImplementationStateSnapshot(
                "外提宿主运行时",
                List.of(new ImplementationStateSnapshot.PlannedSubtaskState(
                        "建立入口",
                        "先完成可运行的内联宿主入口",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("入口可运行"),
                        true,
                        "PATCH",
                        List.of(new ImplementationStateSnapshot.FileChangeState(
                                "index.html",
                                "WRITE",
                                "补齐宿主入口",
                                FileEditScope.HOST_HTML_PATCH.name(),
                                RuntimeOwnershipMode.INLINE_HOST.name()
                        ))
                )),
                List.of(new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                        "建立入口",
                        true,
                        List.of()
                )),
                List.of(),
                null,
                true,
                false,
                new ImplementationStateSnapshot.ContractGateState(
                        ArchitectIntegrationCheckScope.STAGE_COMPLETION.name(),
                        false,
                        ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID.name(),
                        "",
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING.name(),
                        new ImplementationStateSnapshot.RuntimeContractState(
                                "index.html",
                                RuntimeOwnershipMode.EXTERNAL_COMPANION.name(),
                                List.of("index.app.js")
                        )
                ),
                "",
                "",
                "",
                "",
                "",
                List.of(),
                "",
                "",
                List.of()
        ));

        ReusableImplementationState reusableState = policy.loadReusableImplementationState(
                previousStateJson,
                FixMode.PATCH,
                ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                List.of(),
                DocumentLanguage.ZH
        );

        assertNotNull(reusableState);
        assertEquals(1, reusableState.plan().subtasks().size());
        assertTrue(reusableState.completedReports().isEmpty());
        assertNotNull(reusableState.resumedExecutionState());
        assertEquals(DeliveryMode.PATCH, reusableState.resumedExecutionState().deliveryMode());
        assertEquals(2, reusableState.resumedExecutionState().effectiveChanges().size());
        assertEquals("index.html", reusableState.resumedExecutionState().effectiveChanges().getFirst().path());
        assertEquals(
                RuntimeOwnershipMode.EXTERNAL_COMPANION,
                reusableState.resumedExecutionState().effectiveChanges().getFirst().runtimeOwnership()
        );
        assertEquals("index.app.js", reusableState.resumedExecutionState().effectiveChanges().get(1).path());
    }
}
