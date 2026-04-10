package devflow.agent.executor;

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
                GenerationFailureType.INVALID_PATCH_JSON,
                classifier.classify(new StructuredPayloadException(StructuredPayloadFailureReason.JSON_PAYLOAD_INVALID, "bad json"))
        );
    }

    @Test
    void mapsPreciseEditReasonsToStableFailureTypes() {
        assertEquals(
                GenerationFailureType.SYMBOL_NOT_FOUND,
                classifier.classify(new PreciseEditException(PreciseEditFailureReason.SYMBOL_NOT_FOUND, "missing"))
        );
        assertEquals(
                GenerationFailureType.PATCH_SCHEMA_INVALID,
                classifier.classify(new PreciseEditException(PreciseEditFailureReason.PATCH_SCHEMA_INVALID, "invalid"))
        );
        assertEquals(
                GenerationFailureType.EDIT_UNIT_SCOPE_VIOLATION,
                classifier.classify(new PreciseEditException(PreciseEditFailureReason.EDIT_UNIT_SCOPE_VIOLATION, "scope"))
        );
    }
}
