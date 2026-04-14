package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.editing.precise.ExactReplaceEdit;
import java.nio.file.Path;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.implementation.ImplementationEventMessages;
/**
 * exact-replace 的语义 repair-before-regenerate。
 *
 * <p>这层只在当前文件、当前 unit 内修 payload：
 * 1. 先修宿主可确定的 targetPath/baseContentHash；
 * 2. 再用最小 repair turn 让模型补正 oldText/newText；
 * 3. repair 失败后把失败留在当前 unit，由 split/abort 决策接手。
 *
 * <p>它不负责整文件 fallback，也不负责把失败升级成子任务级重跑。
 */
public final class ExactReplaceSemanticRepairSupport {

    private final PatchPayloadRepairSupport patchPayloadRepairSupport;
    private final DeterministicExactReplaceRepairer deterministicExactReplaceRepairer;
    private final ExactReplaceSemanticRepairTurn exactReplaceSemanticRepairTurn;
    private final PatchRepairSettings patchRepairSettings;
    private final PatchRepairClassifier patchRepairClassifier;
    private final PatchExecutionSupport executionSupport;

    public ExactReplaceSemanticRepairSupport(
            PatchPayloadRepairSupport patchPayloadRepairSupport,
            DeterministicExactReplaceRepairer deterministicExactReplaceRepairer,
            ExactReplaceSemanticRepairTurn exactReplaceSemanticRepairTurn,
            PatchRepairSettings patchRepairSettings,
            PatchRepairClassifier patchRepairClassifier,
            PatchExecutionSupport executionSupport
    ) {
        this.patchPayloadRepairSupport = patchPayloadRepairSupport;
        this.deterministicExactReplaceRepairer = deterministicExactReplaceRepairer;
        this.exactReplaceSemanticRepairTurn = exactReplaceSemanticRepairTurn;
        this.patchRepairSettings = patchRepairSettings;
        this.patchRepairClassifier = patchRepairClassifier;
        this.executionSupport = executionSupport;
    }

    ExactReplaceEdit repair(
            Path relativePath,
            EditUnit unit,
            String currentContent,
            String normalizedPayload,
            ExactReplaceEdit edit,
            PatchFailure failure,
            ImplementationEventJournal eventJournal
    ) {
        if (edit == null || !patchRepairClassifier.supportsExactReplaceSemanticRepair(failure)) {
            return null;
        }
        String unitLabel = unit == null ? "patch" : unit.label();
        String evidence = failure == null ? "" : failure.evidence();
        ExactReplaceEdit deterministicRepair = deterministicExactReplaceRepairer.repair(
                relativePath,
                currentContent,
                edit,
                failure
        );
        if (deterministicRepair != null) {
            executionSupport.appendImplementationEvent(
                    eventJournal,
                    ImplementationEventMessages.repairTrace(
                            "semantic-patch-repair",
                            relativePath,
                            unitLabel,
                            "deterministic-applied",
                            evidence
                    )
            );
            return deterministicRepair;
        }
        for (int attempt = 1; attempt <= patchRepairSettings.semanticModelRepairAttempts(); attempt++) {
            executionSupport.appendImplementationEvent(
                    eventJournal,
                    ImplementationEventMessages.repairTrace(
                            "semantic-patch-repair",
                            relativePath,
                            unitLabel,
                            "model-attempt-" + attempt,
                            evidence
                    )
            );
            try {
                String repairedPayload = patchPayloadRepairSupport.normalizeGeneratedPayload(
                        relativePath,
                        exactReplaceSemanticRepairTurn.repair(
                                relativePath,
                                unit,
                                currentContent,
                                normalizedPayload,
                                evidence
                        )
                );
                ExactReplaceEdit repairedEdit = patchPayloadRepairSupport.readStructuredPayload(
                        relativePath,
                        unit,
                        repairedPayload,
                        ExactReplaceEdit.class,
                        eventJournal
                );
                executionSupport.appendImplementationEvent(
                        eventJournal,
                        ImplementationEventMessages.repairTrace(
                                "repair-validate",
                                relativePath,
                                unitLabel,
                                "semantic-json-success",
                                "model"
                        )
                );
                return repairedEdit;
            } catch (Exception repairFailure) {
                evidence = repairFailure.getMessage() == null
                        ? repairFailure.getClass().getSimpleName()
                        : repairFailure.getMessage();
                executionSupport.appendImplementationEvent(
                        eventJournal,
                        ImplementationEventMessages.repairTrace(
                                "repair-validate",
                                relativePath,
                                unitLabel,
                                "semantic-json-failed",
                                evidence
                        )
                );
            }
        }
        return null;
    }
}
