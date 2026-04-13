package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

/**
 * 运行时观测 contract 的确定性校验结果。
 */
public record UiRuntimeContractValidation(
        UiRuntimeContractValidationKind kind,
        boolean valid,
        List<String> issues
) {

    public UiRuntimeContractValidation {
        kind = kind == null ? UiRuntimeContractValidationKind.VALID : kind;
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static UiRuntimeContractValidation success() {
        return new UiRuntimeContractValidation(UiRuntimeContractValidationKind.VALID, true, List.of());
    }

    public static UiRuntimeContractValidation failure(UiRuntimeContractValidationKind kind, List<String> issues) {
        return new UiRuntimeContractValidation(kind, false, issues);
    }

    public String evidence() {
        if (issues.isEmpty()) {
            return "";
        }
        return String.join(" | ", issues);
    }
}
