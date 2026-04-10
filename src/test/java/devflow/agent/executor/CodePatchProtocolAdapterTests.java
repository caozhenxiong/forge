package devflow.agent.executor;

import devflow.agent.editing.CodePreciseAction;
import devflow.agent.editing.CodePreciseOperation;
import devflow.agent.editing.CodePrecisePatch;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodePatchProtocolAdapterTests {

    @Test
    void codePrecisePatchIsMappedToGenericPatchOperations() {
        CodePatchProtocolAdapter adapter = new CodePatchProtocolAdapter();
        CodePrecisePatch patch = new CodePrecisePatch(List.of(
                new CodePreciseOperation(
                        CodePreciseAction.REPLACE_SYMBOL_BODY,
                        "tick",
                        "function",
                        null,
                        List.of("return 1;")
                ),
                new CodePreciseOperation(
                        CodePreciseAction.APPEND_FILE,
                        null,
                        null,
                        null,
                        List.of("function helper() {}", "")
                )
        ));

        List<PatchOperation> operations = adapter.toOperations(patch);

        assertEquals(2, operations.size());
        assertEquals(PatchOperationType.REPLACE_BODY, operations.get(0).operationType());
        assertEquals(PatchTargetKind.SYMBOL, operations.get(0).target().targetKind());
        assertEquals("tick", operations.get(0).target().symbolName());
        assertEquals(PatchOperationType.APPEND_BLOCK, operations.get(1).operationType());
        assertEquals(PatchTargetKind.FILE_END, operations.get(1).target().targetKind());
    }
}
