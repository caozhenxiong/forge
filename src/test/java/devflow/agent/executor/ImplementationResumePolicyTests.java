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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationResumePolicyTests {

    private static final RuntimeWiringRetryChangeFactory RUNTIME_WIRING_RETRY_CHANGE_FACTORY =
            new RuntimeWiringRetryChangeFactory();

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
                RUNTIME_WIRING_RETRY_CHANGE_FACTORY.build(HtmlRuntimeOwnershipContract.externalCompanion(
                        Path.of("index.html"),
                        List.of(Path.of("index.app.js"))
                )),
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
                        List.of(
                                new ImplementationStateSnapshot.FileChangeState(
                                        "index.html",
                                        "WRITE",
                                        "补齐宿主入口",
                                        FileEditScope.HOST_HTML_PATCH.name(),
                                        RuntimeOwnershipMode.INLINE_HOST.name()
                                ),
                                new ImplementationStateSnapshot.FileChangeState(
                                        "index.app.js",
                                        "WRITE",
                                        "既有 companion runtime root"
                                )
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
                RUNTIME_WIRING_RETRY_CHANGE_FACTORY.build(HtmlRuntimeOwnershipContract.externalCompanion(
                        Path.of("index.html"),
                        List.of(Path.of("index.app.js"))
                )),
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

    @Test
    void runtimeWiringPatchRejectsExternalCompanionContractWithoutRuntimeRoots() throws Exception {
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
                                RuntimeOwnershipMode.EXTERNAL_COMPANION.name()
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
                                List.of()
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

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> policy.loadReusableImplementationState(
                        previousStateJson,
                        FixMode.PATCH,
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                        List.of(),
                        DocumentLanguage.ZH
                )
        );
    }

    @Test
    void runtimeWiringPatchReopensRuntimeRootOwnerInsteadOfLaterSharedHtmlSubtask() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ImplementationResumePolicy policy = new ImplementationResumePolicy(objectMapper);
        String previousStateJson = objectMapper.writeValueAsString(new ImplementationStateSnapshot(
                "修复 runtime wiring",
                List.of(
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "建立入口",
                                "创建入口与 companion runtime",
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
                        ),
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "补充布局",
                                "继续调整宿主页布局",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("布局完成"),
                                false,
                                "PATCH",
                                List.of(
                                        new ImplementationStateSnapshot.FileChangeState(
                                                "index.html",
                                                "WRITE",
                                                "调整宿主页布局"
                                        ),
                                        new ImplementationStateSnapshot.FileChangeState("styles.css", "WRITE", "补齐样式")
                                )
                        )
                ),
                List.of(
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                                "建立入口",
                                true,
                                List.of()
                        ),
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                                "补充布局",
                                true,
                                List.of()
                        )
                ),
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
        assertEquals(0, reusableState.completedReports().size());
        assertNotNull(reusableState.resumedExecutionState());
        assertEquals(2, reusableState.resumedExecutionState().effectiveChanges().size());
        assertEquals("index.html", reusableState.resumedExecutionState().effectiveChanges().getFirst().path());
        assertEquals("index.app.js", reusableState.resumedExecutionState().effectiveChanges().get(1).path());
    }

    @Test
    void runtimeWiringPatchDerivesCanonicalOverrideChangesFromContractGate() throws Exception {
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
        assertNotNull(reusableState.resumedExecutionState());
        assertEquals(2, reusableState.resumedExecutionState().effectiveChanges().size());
        assertEquals("index.html", reusableState.resumedExecutionState().effectiveChanges().getFirst().path());
        assertEquals("index.app.js", reusableState.resumedExecutionState().effectiveChanges().get(1).path());
    }

    @Test
    void completedPlanPatchExistingRejectsCrossSubtaskOverridePackage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ImplementationResumePolicy policy = new ImplementationResumePolicy(objectMapper);
        String previousStateJson = objectMapper.writeValueAsString(new ImplementationStateSnapshot(
                "修复跨子任务问题",
                List.of(
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "入口",
                                "修入口",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("入口完成"),
                                true,
                                "PATCH",
                                List.of(new ImplementationStateSnapshot.FileChangeState("index.html", "WRITE", "修入口"))
                        ),
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "逻辑",
                                "修逻辑",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("逻辑完成"),
                                true,
                                "PATCH",
                                List.of(new ImplementationStateSnapshot.FileChangeState("src/app.js", "WRITE", "修逻辑"))
                        )
                ),
                List.of(
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot("入口", true, List.of()),
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot("逻辑", true, List.of())
                ),
                List.of(),
                null,
                true,
                false,
                new ImplementationStateSnapshot.ContractGateState(
                        ArchitectIntegrationCheckScope.STAGE_COMPLETION.name(),
                        false,
                        ArchitectIntegrationFailureReason.IMPLEMENTATION_INCOMPLETE.name(),
                        "需要继续修补实现",
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION.name(),
                        null
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

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> policy.loadReusableImplementationState(
                        previousStateJson,
                        FixMode.PATCH,
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                        List.of(
                                new FileChange("index.html", ChangeAction.WRITE, "修入口"),
                                new FileChange("src/app.js", ChangeAction.WRITE, "修逻辑")
                        ),
                        DocumentLanguage.ZH
                )
        );

        assertTrue(exception.getMessage().contains("single completed subtask"));
    }

    @Test
    void runtimeWiringPatchUsesDeclaredOwnerInsteadOfNarrowedSuccessfulRetryScope() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ImplementationResumePolicy policy = new ImplementationResumePolicy(objectMapper);
        String previousStateJson = objectMapper.writeValueAsString(new ImplementationStateSnapshot(
                "修复 runtime wiring",
                List.of(
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "建立入口",
                                "创建入口与 companion runtime",
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
                        ),
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "补充布局",
                                "继续调整宿主页布局",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("布局完成"),
                                false,
                                "PATCH",
                                List.of(
                                        new ImplementationStateSnapshot.FileChangeState("index.html", "WRITE", "调整宿主页布局"),
                                        new ImplementationStateSnapshot.FileChangeState("styles.css", "WRITE", "补齐样式")
                                )
                        )
                ),
                List.of(
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                                "建立入口",
                                true,
                                List.of(),
                                "PATCH",
                                true,
                                List.of(),
                                List.of(new ImplementationStateSnapshot.FileChangeState("index.html", "WRITE", "成功那轮只修了宿主 HTML"))
                        ),
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                                "补充布局",
                                true,
                                List.of()
                        )
                ),
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
        assertEquals(0, reusableState.completedReports().size());
        assertNotNull(reusableState.resumedExecutionState());
        assertEquals(2, reusableState.resumedExecutionState().effectiveChanges().size());
        assertEquals("index.html", reusableState.resumedExecutionState().effectiveChanges().getFirst().path());
        assertEquals("index.app.js", reusableState.resumedExecutionState().effectiveChanges().get(1).path());
    }

    @Test
    void runtimeWiringPatchRejectsInlineHostWhenCompletedHtmlOwnerIsAmbiguous() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ImplementationResumePolicy policy = new ImplementationResumePolicy(objectMapper);
        String previousStateJson = objectMapper.writeValueAsString(new ImplementationStateSnapshot(
                "修复 runtime wiring",
                List.of(
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "入口",
                                "修入口",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("入口完成"),
                                true,
                                "PATCH",
                                List.of(
                                        new ImplementationStateSnapshot.FileChangeState("index.html", "WRITE", "入口 owner"),
                                        new ImplementationStateSnapshot.FileChangeState("src/app.js", "WRITE", "入口逻辑")
                                )
                        ),
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "布局",
                                "修布局",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("布局完成"),
                                false,
                                "PATCH",
                                List.of(
                                        new ImplementationStateSnapshot.FileChangeState("index.html", "WRITE", "布局 owner"),
                                        new ImplementationStateSnapshot.FileChangeState("styles.css", "WRITE", "样式")
                                )
                        )
                ),
                List.of(
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot("入口", true, List.of()),
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot("布局", true, List.of())
                ),
                List.of(),
                null,
                true,
                false,
                new ImplementationStateSnapshot.ContractGateState(
                        ArchitectIntegrationCheckScope.STAGE_COMPLETION.name(),
                        false,
                        ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID.name(),
                        "inline host owner is ambiguous",
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING.name(),
                        new ImplementationStateSnapshot.RuntimeContractState(
                                "index.html",
                                RuntimeOwnershipMode.INLINE_HOST.name(),
                                List.of()
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

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> policy.loadReusableImplementationState(
                        previousStateJson,
                        FixMode.PATCH,
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                        List.of(),
                        DocumentLanguage.ZH
                )
        );

        assertTrue(exception.getMessage().contains("could not find an owning subtask"));
    }

    @Test
    void runtimeWiringPatchRejectsExternalCompanionWhenCompletedOwnerIsNotResolvable() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ImplementationResumePolicy policy = new ImplementationResumePolicy(objectMapper);
        String previousStateJson = objectMapper.writeValueAsString(new ImplementationStateSnapshot(
                "修复 runtime wiring",
                List.of(
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "入口",
                                "修入口",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("入口完成"),
                                true,
                                "PATCH",
                                List.of(
                                        new ImplementationStateSnapshot.FileChangeState("index.html", "WRITE", "入口 owner"),
                                        new ImplementationStateSnapshot.FileChangeState("styles.css", "WRITE", "样式")
                                )
                        ),
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "布局",
                                "修布局",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("布局完成"),
                                false,
                                "PATCH",
                                List.of(
                                        new ImplementationStateSnapshot.FileChangeState("index.html", "WRITE", "布局 owner"),
                                        new ImplementationStateSnapshot.FileChangeState("src/panel.js", "WRITE", "局部逻辑")
                                )
                        )
                ),
                List.of(
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot("入口", true, List.of()),
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot("布局", true, List.of())
                ),
                List.of(),
                null,
                true,
                false,
                new ImplementationStateSnapshot.ContractGateState(
                        ArchitectIntegrationCheckScope.STAGE_COMPLETION.name(),
                        false,
                        ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID.name(),
                        "runtime owner is not resolvable",
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

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> policy.loadReusableImplementationState(
                        previousStateJson,
                        FixMode.PATCH,
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                        List.of(),
                        DocumentLanguage.ZH
                )
        );

        assertTrue(exception.getMessage().contains("could not find an owning subtask"));
    }

    @Test
    void runtimeWiringPatchRejectsExternalCompanionWhenOnlyHtmlOwnerIsResolvable() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ImplementationResumePolicy policy = new ImplementationResumePolicy(objectMapper);
        String previousStateJson = objectMapper.writeValueAsString(new ImplementationStateSnapshot(
                "修复 runtime wiring",
                List.of(
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "入口",
                                "修入口",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("入口完成"),
                                true,
                                "PATCH",
                                List.of(
                                        new ImplementationStateSnapshot.FileChangeState("index.html", "WRITE", "入口 owner"),
                                        new ImplementationStateSnapshot.FileChangeState("styles.css", "WRITE", "样式")
                                )
                        ),
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "逻辑",
                                "修逻辑",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("逻辑完成"),
                                false,
                                "PATCH",
                                List.of(new ImplementationStateSnapshot.FileChangeState("src/panel.js", "WRITE", "局部逻辑"))
                        )
                ),
                List.of(
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot("入口", true, List.of()),
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot("逻辑", true, List.of())
                ),
                List.of(),
                null,
                true,
                false,
                new ImplementationStateSnapshot.ContractGateState(
                        ArchitectIntegrationCheckScope.STAGE_COMPLETION.name(),
                        false,
                        ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID.name(),
                        "runtime roots exist but only html owner is resolvable",
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

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> policy.loadReusableImplementationState(
                        previousStateJson,
                        FixMode.PATCH,
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                        List.of(),
                        DocumentLanguage.ZH
                )
        );

        assertTrue(exception.getMessage().contains("could not find an owning subtask"));
    }

    @Test
    void runtimeWiringPatchRejectsWhenMultipleCompletedSubtasksDeclareSameRuntimeRoot() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ImplementationResumePolicy policy = new ImplementationResumePolicy(objectMapper);
        String previousStateJson = objectMapper.writeValueAsString(new ImplementationStateSnapshot(
                "修复 runtime wiring",
                List.of(
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "入口",
                                "修入口",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("入口完成"),
                                true,
                                "PATCH",
                                List.of(
                                        new ImplementationStateSnapshot.FileChangeState("index.html", "WRITE", "入口 owner"),
                                        new ImplementationStateSnapshot.FileChangeState("index.app.js", "WRITE", "shared runtime root")
                                )
                        ),
                        new ImplementationStateSnapshot.PlannedSubtaskState(
                                "动效",
                                "修动效",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("动效完成"),
                                false,
                                "PATCH",
                                List.of(
                                        new ImplementationStateSnapshot.FileChangeState("landing.html", "WRITE", "另一个入口"),
                                        new ImplementationStateSnapshot.FileChangeState("index.app.js", "WRITE", "重复声明同一个 runtime root")
                                )
                        )
                ),
                List.of(
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot("入口", true, List.of()),
                        new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot("动效", true, List.of())
                ),
                List.of(),
                null,
                true,
                false,
                new ImplementationStateSnapshot.ContractGateState(
                        ArchitectIntegrationCheckScope.STAGE_COMPLETION.name(),
                        false,
                        ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID.name(),
                        "multiple completed subtasks declare the same runtime root",
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

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> policy.loadReusableImplementationState(
                        previousStateJson,
                        FixMode.PATCH,
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                        List.of(),
                        DocumentLanguage.ZH
                )
        );

        assertTrue(exception.getMessage().contains("could not find an owning subtask"));
    }
}
