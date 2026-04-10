package devflow.agent.protocol;

import java.util.List;

/**
 * execution directive 的统一入口。
 *
 * <p>这层负责把一段 note/feedback 中的多个 directive block 合并成单一真相，
 * 让 implementation/review/test/repair 不再各自维护标签解析逻辑。
 */
public final class ExecutionDirectiveProtocol {

    private ExecutionDirectiveProtocol() {
    }

    public static String renderBlock(ExecutionDirectivePayload payload) {
        return StructuredArtifactBlocks.renderJsonBlock(ArtifactBlockKind.EXECUTION_DIRECTIVES, payload);
    }

    public static ExecutionDirectivePayload parseMerged(String content) {
        List<ExecutionDirectivePayload> payloads = StructuredArtifactBlocks.readAllJsonBlocks(
                content,
                ArtifactBlockKind.EXECUTION_DIRECTIVES,
                ExecutionDirectivePayload.class
        );
        ExecutionDirectivePayload merged = ExecutionDirectivePayload.empty();
        for (ExecutionDirectivePayload payload : payloads) {
            merged = merged.merge(payload);
        }
        return merged;
    }
}
