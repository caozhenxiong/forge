package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationFailureClassifier;
import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.llm.LlmFailureReason;
import devflow.agent.executor.llm.LlmInvocationException;
import devflow.agent.executor.llm.StructuredPayloadException;
import devflow.agent.executor.llm.StructuredPayloadFailureReason;

import devflow.agent.editing.PreciseEditException;
import devflow.agent.editing.PreciseEditFailureReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenerationFailureClassifierTests {

    private final GenerationFailureClassifier classifier = new GenerationFailureClassifier();

    @Test
    void mapsLlmTimeoutToAttemptTimeout() {
        assertEquals(
                GenerationFailureType.ATTEMPT_TIMEOUT,
                classifier.classify(new LlmInvocationException(LlmFailureReason.TIMEOUT, "timeout"))
        );
    }

    @Test
    void mapsStructuredPayloadFailureToInvalidPatchJson() {
        assertEquals(
                GenerationFailureType.MODEL_OUTPUT_INVALID,
                classifier.classify(new StructuredPayloadException(StructuredPayloadFailureReason.JSON_PAYLOAD_INVALID, "bad json"))
        );
    }

    @Test
    void mapsPreciseEditReasonsToStableFailureTypes() {
        assertEquals(
                GenerationFailureType.TARGET_NOT_FOUND,
                classifier.classify(new PreciseEditException(PreciseEditFailureReason.TARGET_NOT_FOUND, "missing"))
        );
        assertEquals(
                GenerationFailureType.MODEL_OUTPUT_INVALID,
                classifier.classify(new PreciseEditException(PreciseEditFailureReason.MODEL_OUTPUT_INVALID, "invalid"))
        );
        assertEquals(
                GenerationFailureType.TARGET_SCOPE_VIOLATION,
                classifier.classify(new PreciseEditException(PreciseEditFailureReason.TARGET_SCOPE_VIOLATION, "scope"))
        );
    }
}
