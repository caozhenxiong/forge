package devflow.agent.executor;

import java.nio.file.Path;

/**
 * patch JSON 载荷的 repair-before-regenerate 支撑。
 */
final class PatchPayloadRepairSupport {

    private final GeneratedPayloadSupport generatedPayloadSupport;
    private final DeterministicJsonPayloadRepairer deterministicJsonPayloadRepairer;
    private final ModelJsonRepairTurn modelJsonRepairTurn;
    private final PatchRepairSettings patchRepairSettings;
    private final PatchRepairClassifier patchRepairClassifier;
    private final PatchExecutionSupport executionSupport;

    PatchPayloadRepairSupport(
            GeneratedPayloadSupport generatedPayloadSupport,
            DeterministicJsonPayloadRepairer deterministicJsonPayloadRepairer,
            ModelJsonRepairTurn modelJsonRepairTurn,
            PatchRepairSettings patchRepairSettings,
            PatchRepairClassifier patchRepairClassifier,
            PatchExecutionSupport executionSupport
    ) {
        this.generatedPayloadSupport = generatedPayloadSupport;
        this.deterministicJsonPayloadRepairer = deterministicJsonPayloadRepairer;
        this.modelJsonRepairTurn = modelJsonRepairTurn;
        this.patchRepairSettings = patchRepairSettings;
        this.patchRepairClassifier = patchRepairClassifier;
        this.executionSupport = executionSupport;
    }

    String normalizeGeneratedPayload(Path relativePath, String content) {
        return generatedPayloadSupport.normalizeGeneratedPayload(relativePath, content);
    }

    <T> T readStructuredPayload(
            Path relativePath,
            EditUnit unit,
            String normalizedPayload,
            Class<T> type,
            ImplementationEventJournal eventJournal
    ) {
        return readStructuredPayload(
                relativePath,
                unit == null ? "" : unit.label(),
                normalizedPayload,
                type,
                eventJournal
        );
    }

    <T> T readStructuredPayload(
            Path relativePath,
            String operationLabel,
            String normalizedPayload,
            Class<T> type,
            ImplementationEventJournal eventJournal
    ) {
        try {
            return generatedPayloadSupport.readStructuredPayload(normalizedPayload, type);
        } catch (Exception exception) {
            if (!patchRepairClassifier.supportsJsonRepair(exception)) {
                throw exception;
            }
            String evidence = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            String deterministicCandidate = deterministicJsonPayloadRepairer.repair(normalizedPayload);
            if (deterministicCandidate != null && !deterministicCandidate.equals(normalizedPayload)) {
                executionSupport.appendImplementationEvent(
                        eventJournal,
                        ImplementationEventMessages.repairTrace(
                                "deterministic-json-repair",
                                relativePath,
                                operationLabel,
                                "applied",
                                evidence
                        )
                );
                try {
                    T repaired = generatedPayloadSupport.readStructuredPayload(deterministicCandidate, type);
                    executionSupport.appendImplementationEvent(
                        eventJournal,
                        ImplementationEventMessages.repairTrace(
                                "repair-validate",
                                relativePath,
                                operationLabel,
                                "json-success",
                                "deterministic"
                        )
                    );
                    return repaired;
                } catch (Exception deterministicFailure) {
                    evidence = deterministicFailure.getMessage() == null
                            ? deterministicFailure.getClass().getSimpleName()
                            : deterministicFailure.getMessage();
                    executionSupport.appendImplementationEvent(
                            eventJournal,
                            ImplementationEventMessages.repairTrace(
                                    "repair-validate",
                                    relativePath,
                                    operationLabel,
                                    "json-failed",
                                    evidence
                            )
                    );
                }
            }
            String candidate = deterministicCandidate == null ? normalizedPayload : deterministicCandidate;
            RuntimeException lastFailure = exception instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new IllegalStateException(exception);
            for (int attempt = 1; attempt <= patchRepairSettings.jsonModelRepairAttempts(); attempt++) {
                executionSupport.appendImplementationEvent(
                        eventJournal,
                        ImplementationEventMessages.repairTrace(
                                "model-json-repair",
                                relativePath,
                                operationLabel,
                                "attempt-" + attempt,
                                evidence
                        )
                );
                try {
                    String repairedPayload = generatedPayloadSupport.normalizeGeneratedPayload(
                            relativePath,
                            modelJsonRepairTurn.repair(relativePath, operationLabel, candidate, evidence)
                    );
                    T repaired = generatedPayloadSupport.readStructuredPayload(repairedPayload, type);
                    executionSupport.appendImplementationEvent(
                        eventJournal,
                        ImplementationEventMessages.repairTrace(
                                "repair-validate",
                                relativePath,
                                operationLabel,
                                "json-success",
                                "model"
                        )
                    );
                    return repaired;
                } catch (Exception modelFailure) {
                    lastFailure = modelFailure instanceof RuntimeException runtimeException
                            ? runtimeException
                            : new IllegalStateException(modelFailure);
                    evidence = modelFailure.getMessage() == null ? modelFailure.getClass().getSimpleName() : modelFailure.getMessage();
                    executionSupport.appendImplementationEvent(
                            eventJournal,
                            ImplementationEventMessages.repairTrace(
                                    "repair-validate",
                                    relativePath,
                                    operationLabel,
                                    "json-failed",
                                    evidence
                            )
                    );
                }
            }
            throw lastFailure;
        }
    }
}
