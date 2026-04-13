package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolName;
import devflow.agent.executor.tools.ToolResult;

import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.llm.LlmFailureReason;
import devflow.agent.executor.llm.LlmInvocationException;
import devflow.agent.executor.llm.StructuredPayloadException;
import devflow.agent.executor.llm.StructuredPayloadFailureReason;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchRepairClassifierTests {

    private final PatchRepairClassifier classifier = new PatchRepairClassifier();

    @Test
    void classifiesMechanicalAndNonMechanicalFailures() {
        assertEquals(
                PatchFailureClass.MECHANICAL,
                classifier.classify(PatchFailure.of(GenerationFailureType.MODEL_OUTPUT_INVALID, "bad json"))
        );
        assertEquals(
                PatchFailureClass.MECHANICAL,
                classifier.classify(PatchFailure.of(GenerationFailureType.SYNTAX_INVALID, "parse failed"))
        );
        assertEquals(
                PatchFailureClass.NON_MECHANICAL,
                classifier.classify(PatchFailure.of(GenerationFailureType.TARGET_SCOPE_VIOLATION, "scope"))
        );
        assertEquals(
                PatchFailureClass.NON_MECHANICAL,
                classifier.classify(PatchFailure.of(GenerationFailureType.OUTPUT_TRUNCATED, "length"))
        );
    }

    @Test
    void onlyJsonPayloadErrorsEnterJsonRepair() {
        assertTrue(classifier.supportsJsonRepair(
                new StructuredPayloadException(StructuredPayloadFailureReason.JSON_PAYLOAD_INVALID, "bad json")
        ));
        assertFalse(classifier.supportsJsonRepair(
                new LlmInvocationException(LlmFailureReason.OUTPUT_TRUNCATED, "length")
        ));
    }

    @Test
    void scopeViolationAndTruncationNeverEnterSyntaxRepair() {
        assertFalse(classifier.supportsSyntaxRepair(
                PatchFailure.of(GenerationFailureType.TARGET_SCOPE_VIOLATION, "scope")
        ));
        assertFalse(classifier.supportsSyntaxRepair(
                PatchFailure.of(GenerationFailureType.OUTPUT_TRUNCATED, "length")
        ));
        assertTrue(classifier.supportsSyntaxRepair(
                PatchFailure.fromToolResult(
                        ToolResult.failure(
                                ToolName.CONTENT_VERIFY,
                                ToolFailureCode.SYNTAX_INVALID,
                                "parse failed",
                                ""
                        ),
                        GenerationFailureType.VALIDATION_FAILED
                )
        ));
        assertTrue(classifier.supportsSyntaxRepair(
                PatchFailure.fromToolResult(
                        ToolResult.failure(
                                ToolName.CONTENT_VERIFY,
                                ToolFailureCode.JAVASCRIPT_STRUCTURE_INVALID,
                                "duplicated wrapper",
                                ""
                        ),
                        GenerationFailureType.VALIDATION_FAILED
                )
        ));
    }
}
