package devflow.agent.executor;

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
                classifier.classify(PatchFailure.of(GenerationFailureType.INVALID_PATCH_JSON, "bad json"))
        );
        assertEquals(
                PatchFailureClass.MECHANICAL,
                classifier.classify(PatchFailure.of(GenerationFailureType.TREE_SITTER_PARSE_FAILED, "parse failed"))
        );
        assertEquals(
                PatchFailureClass.NON_MECHANICAL,
                classifier.classify(PatchFailure.of(GenerationFailureType.EDIT_UNIT_SCOPE_VIOLATION, "scope"))
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
                PatchFailure.of(GenerationFailureType.EDIT_UNIT_SCOPE_VIOLATION, "scope")
        ));
        assertFalse(classifier.supportsSyntaxRepair(
                PatchFailure.of(GenerationFailureType.OUTPUT_TRUNCATED, "length")
        ));
        assertTrue(classifier.supportsSyntaxRepair(
                PatchFailure.fromToolResult(
                        ToolResult.failure(
                                ToolName.CONTENT_VERIFY,
                                ToolFailureCode.TREE_SITTER_PARSE_FAILED,
                                "parse failed",
                                ""
                        ),
                        GenerationFailureType.RESULT_FILE_INVALID
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
                        GenerationFailureType.RESULT_FILE_INVALID
                )
        ));
    }
}
