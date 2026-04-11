package devflow.agent.protocol;

/**
 * 统一定义 machine-readable artifact block 的种类。
 *
 * <p>这些 block 是流程真正依赖的机器协议，和人类可读 markdown 分离。
 * 后续流程判断只应消费这些结构化 block，不再从 prose 或零散标签里猜语义。
 */
public enum ArtifactBlockKind {
    SOURCE_METADATA("SOURCE_METADATA"),
    PRODUCT_CONTRACT("PRODUCT_CONTRACT"),
    EXECUTION_CONTRACT("EXECUTION_CONTRACT"),
    VALIDATION_METADATA("VALIDATION_METADATA"),
    TOOL_RESULTS("TOOL_RESULTS"),
    REVIEW_RESULT("REVIEW_RESULT"),
    QUALITY_LEDGER("QUALITY_LEDGER"),
    UI_RUNTIME_CONTRACT("UI_RUNTIME_CONTRACT"),
    EXPERIENCE_FAILURE_DISPOSITION("EXPERIENCE_FAILURE_DISPOSITION"),
    REVIEW_HISTORY_ENTRY("REVIEW_HISTORY_ENTRY"),
    IMPLEMENTATION_STAGE_STATUS("IMPLEMENTATION_STAGE_STATUS"),
    EXECUTION_DIRECTIVES("EXECUTION_DIRECTIVES");

    private static final String PREFIX = "<!-- DEVFLOW:";
    private static final String BEGIN_SUFFIX = ":BEGIN -->";
    private static final String END_SUFFIX = ":END -->";

    private final String id;

    ArtifactBlockKind(String id) {
        this.id = id;
    }

    public String beginMarker() {
        return PREFIX + id + BEGIN_SUFFIX;
    }

    public String endMarker() {
        return PREFIX + id + END_SUFFIX;
    }
}
