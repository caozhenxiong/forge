package devflow.agent.context;

import java.util.List;

/**
 * 集中定义 Contract Metadata 中机器可消费的稳定 key。
 *
 * <p>这些 key 会同时被：
 * 1. 文档模板；
 * 2. Contract 解析；
 * 3. 结构护栏；
 * 4. 测试与执行策略
 * 复用。集中维护可以避免在不同模块里散落魔法字符串。
 */
public final class ContractMetadataKeys {

    public static final String RUNTIME_ENTRY_REQUIRED = "runtime.entryRequired";
    public static final String RUNTIME_ENTRY_KIND = "runtime.entryKind";
    public static final String RUNTIME_LAUNCH_REQUIRED = "runtime.launchRequired";
    public static final String RUNTIME_SURFACE_REQUIRED = "runtime.surfaceRequired";
    public static final String RUNTIME_ACCEPTANCE_SIGNALS = "runtime.acceptanceSignals";

    public static final String VALIDATION_PERFORMANCE_MEASUREMENT_REQUIRED = "validation.performanceMeasurementRequired";
    public static final String VALIDATION_PAGE_LOAD_MAX_MS = "validation.pageLoadMaxMs";
    public static final String VALIDATION_INTERACTION_MAX_MS = "validation.interactionMaxMs";

    private static final List<String> RUNTIME_KEYS = List.of(
            RUNTIME_ENTRY_REQUIRED,
            RUNTIME_ENTRY_KIND,
            RUNTIME_LAUNCH_REQUIRED,
            RUNTIME_SURFACE_REQUIRED,
            RUNTIME_ACCEPTANCE_SIGNALS
    );

    private static final List<String> VALIDATION_KEYS = List.of(
            VALIDATION_PERFORMANCE_MEASUREMENT_REQUIRED,
            VALIDATION_PAGE_LOAD_MAX_MS,
            VALIDATION_INTERACTION_MAX_MS
    );

    private ContractMetadataKeys() {
    }

    public static List<String> runtimeKeys() {
        return RUNTIME_KEYS;
    }

    public static List<String> validationKeys() {
        return VALIDATION_KEYS;
    }
}
