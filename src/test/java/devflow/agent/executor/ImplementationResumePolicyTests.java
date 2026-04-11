package devflow.agent.executor;

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
                                List.of(new ImplementationStateSnapshot.FilePatchProgressStateSnapshot(
                                        "app.js",
                                        FileEditStrategyNames.PRECISE_CODE,
                                        "export function tick() {}\n",
                                        List.of(new ImplementationStateSnapshot.EditUnitState(
                                                EditUnitKind.CODE_SYMBOL_BATCH.name(),
                                                "code-unit-16",
                                                List.of("render"),
                                                0,
                                                0
                                        ))
                                ))
                        )
                ),
                List.of(),
                "补齐逻辑",
                false,
                true,
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
        FilePatchProgressState progressState = reusableState.resumedExecutionState().filePatchProgress(Path.of("app.js"));
        assertNotNull(progressState);
        assertEquals(FileEditStrategyNames.PRECISE_CODE, progressState.strategyName());
        assertEquals("export function tick() {}\n", progressState.workingContent());
        assertEquals("code-unit-16", progressState.pendingUnits().getFirst().label());
    }

    @Test
    void appendsReasonAwareRuntimeWiringContinuationInsteadOfGenericFileReopen() throws Exception {
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
                ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID.name(),
                "index.app.js 存在，但 index.html 未接线",
                ImplementationPatchTarget.PATCH_RUNTIME_WIRING.name(),
                new ImplementationStateSnapshot.RuntimeContractState(
                        "index.html",
                        RuntimeOwnershipMode.EXTERNAL_COMPANION.name(),
                        List.of("index.app.js")
                ),
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
        Subtask continuation = reusableState.plan().subtasks().getLast();
        assertEquals(DeliveryMode.PATCH, continuation.deliveryMode());
        assertEquals(1, continuation.changes().size());
        assertEquals(RuntimeOwnershipMode.EXTERNAL_COMPANION, continuation.changes().getFirst().runtimeOwnership());
        assertEquals("index.html", continuation.changes().getFirst().path());
    }

    @Test
    void runtimeWiringContinuationPrefersExplicitRuntimeContractOverOlderInlineOwnership() throws Exception {
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
                true,
                "",
                "",
                ImplementationPatchTarget.PATCH_RUNTIME_WIRING.name(),
                new ImplementationStateSnapshot.RuntimeContractState(
                        "index.html",
                        RuntimeOwnershipMode.EXTERNAL_COMPANION.name(),
                        List.of("index.app.js")
                ),
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
        Subtask continuation = reusableState.plan().subtasks().getLast();
        assertEquals(DeliveryMode.PATCH, continuation.deliveryMode());
        assertEquals(1, continuation.changes().size());
        assertEquals("index.html", continuation.changes().getFirst().path());
        assertEquals(RuntimeOwnershipMode.EXTERNAL_COMPANION, continuation.changes().getFirst().runtimeOwnership());
    }
}
